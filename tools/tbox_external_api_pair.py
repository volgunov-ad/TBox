#!/usr/bin/env python3
"""
Pair with TBox Monitor External HTTP API and smoke-check the connection.

Stdlib only. Default host is the HU LAN address used in field tests.

On the head unit first:
  Settings → API → enable server → «Подключить приложение» (pairing on).
Then run this script and approve the request on the HU screen.

Examples:
  python3 tools/tbox_external_api_pair.py
  python3 tools/tbox_external_api_pair.py --host 192.168.1.128 --port 8765
  python3 tools/tbox_external_api_pair.py --check-only
  python3 tools/tbox_external_api_pair.py --token-file ~/.tbox_external_api_token.json
"""

from __future__ import annotations

import argparse
import json
import socket
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path
from typing import Any, Optional


DEFAULT_HOST = "192.168.1.128"
DEFAULT_PORT = 8765
DEFAULT_TIMEOUT_S = 10.0
DEFAULT_PAIR_WAIT_S = 120.0
DEFAULT_CLIENT_NAME = "TBox API tools (PC)"
DEFAULT_TOKEN_FILE = Path.home() / ".tbox_external_api_token.json"

PROBE_SIGNALS = (
    ("tbox_connected", "app"),
    ("outside_temperature", "head_unit"),
    ("outside_temperature", "tbox"),
    ("car_speed", "head_unit"),
    ("fuel_level_percent", "head_unit"),
)


class ApiError(RuntimeError):
    def __init__(self, message: str, *, status: Optional[int] = None, body: Any = None):
        super().__init__(message)
        self.status = status
        self.body = body


def _pretty(data: Any) -> str:
    return json.dumps(data, ensure_ascii=False, indent=2)


def request_json(
    method: str,
    url: str,
    *,
    body: Optional[dict[str, Any]] = None,
    token: Optional[str] = None,
    timeout_s: float = DEFAULT_TIMEOUT_S,
) -> tuple[int, Any]:
    headers = {"Accept": "application/json"}
    data: Optional[bytes] = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    if token:
        headers["Authorization"] = f"Bearer {token}"

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout_s) as resp:
            raw = resp.read().decode("utf-8", errors="replace")
            status = getattr(resp, "status", 200)
            if not raw.strip():
                return status, None
            try:
                return status, json.loads(raw)
            except json.JSONDecodeError as exc:
                raise ApiError(f"Non-JSON response from {url}: {raw[:200]}", status=status) from exc
    except urllib.error.HTTPError as exc:
        raw = exc.read().decode("utf-8", errors="replace")
        parsed: Any
        try:
            parsed = json.loads(raw) if raw.strip() else None
        except json.JSONDecodeError:
            parsed = raw
        err = None
        if isinstance(parsed, dict) and isinstance(parsed.get("error"), dict):
            code = parsed["error"].get("code", "error")
            message = parsed["error"].get("message", exc.reason)
            err = f"{code}: {message}"
        raise ApiError(
            err or f"HTTP {exc.code} {exc.reason}: {raw[:300]}",
            status=exc.code,
            body=parsed,
        ) from exc
    except urllib.error.URLError as exc:
        raise ApiError(f"Connection failed ({url}): {exc.reason}") from exc
    except socket.timeout as exc:
        raise ApiError(f"Timeout talking to {url}") from exc


def base_url(host: str, port: int) -> str:
    return f"http://{host}:{port}"


def load_token(path: Path) -> Optional[dict[str, Any]]:
    if not path.is_file():
        return None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    if not isinstance(data, dict):
        return None
    token = data.get("accessToken")
    if not isinstance(token, str) or not token.strip():
        return None
    return data


def save_token(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_pretty(payload) + "\n", encoding="utf-8")
    try:
        path.chmod(0o600)
    except OSError:
        pass


def check_health(root: str, timeout_s: float) -> dict[str, Any]:
    status, data = request_json("GET", f"{root}/v1/health", timeout_s=timeout_s)
    if status != 200 or not isinstance(data, dict) or data.get("ok") is not True:
        raise ApiError(f"Unexpected health response HTTP {status}: {_pretty(data)}", status=status, body=data)
    return data


def pair(
    root: str,
    *,
    client_id: str,
    client_name: str,
    wait_s: float,
    poll_s: float,
    timeout_s: float,
) -> dict[str, Any]:
    health = check_health(root, timeout_s)
    print("health:")
    print(_pretty(health))
    if not health.get("serverEnabled"):
        raise ApiError("serverEnabled=false — включите сервер в Настройки → API")
    if not health.get("pairingActive"):
        raise ApiError(
            "pairingActive=false — на ГУ нажмите «Подключить приложение» в Настройки → API, "
            "затем сразу перезапустите скрипт",
        )

    print(
        f"\nОтправляю pair/request как «{client_name}».\n"
        "На ГУ подтвердите запрос (Разрешить).",
        flush=True,
    )
    status, pending = request_json(
        "POST",
        f"{root}/v1/pair/request",
        body={
            "clientId": client_id,
            "clientName": client_name,
            "clientKind": "tools",
        },
        timeout_s=timeout_s,
    )
    if status != 202 or not isinstance(pending, dict) or pending.get("status") != "pending":
        raise ApiError(f"Unexpected pair/request response HTTP {status}: {_pretty(pending)}", status=status, body=pending)
    request_id = pending.get("requestId")
    if not isinstance(request_id, str) or not request_id:
        raise ApiError(f"pair/request without requestId: {_pretty(pending)}", body=pending)
    print(f"requestId={request_id}", flush=True)

    deadline = time.monotonic() + wait_s
    while time.monotonic() < deadline:
        query = urllib.parse.urlencode({"requestId": request_id})
        status, state = request_json(
            "GET",
            f"{root}/v1/pair/status?{query}",
            timeout_s=timeout_s,
        )
        if status != 200 or not isinstance(state, dict):
            raise ApiError(f"Unexpected pair/status HTTP {status}: {_pretty(state)}", status=status, body=state)
        st = state.get("status")
        if st == "pending":
            left = max(0, int(deadline - time.monotonic()))
            print(f"  awaiting approve… {left}s left", flush=True)
            time.sleep(poll_s)
            continue
        if st == "denied":
            raise ApiError("Pairing denied on the head unit")
        if st == "approved":
            token = state.get("accessToken")
            if not isinstance(token, str) or not token.strip():
                raise ApiError(f"approved without accessToken: {_pretty(state)}", body=state)
            return {
                "accessToken": token,
                "clientId": state.get("clientId") or client_id,
                "clientName": client_name,
                "host": urllib.parse.urlparse(root).hostname,
                "port": urllib.parse.urlparse(root).port,
                "pairedAt": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
            }
        raise ApiError(f"Unknown pair status: {_pretty(state)}", body=state)

    raise ApiError(f"Timed out after {wait_s:.0f}s waiting for HU approve")


def verify(root: str, token: str, timeout_s: float) -> None:
    print("\n=== verify: catalog ===", flush=True)
    status, catalog = request_json(
        "GET",
        f"{root}/v1/catalog",
        token=token,
        timeout_s=timeout_s,
    )
    if status != 200 or not isinstance(catalog, dict):
        raise ApiError(f"catalog failed HTTP {status}: {_pretty(catalog)}", status=status, body=catalog)
    signals = catalog.get("signals")
    actions = catalog.get("actionTypes")
    n_signals = len(signals) if isinstance(signals, list) else 0
    n_actions = len(actions) if isinstance(actions, list) else 0
    print(
        f"catalogVersion={catalog.get('catalogVersion')} "
        f"signals={n_signals} actionTypes={n_actions}",
    )

    print("\n=== verify: signals ===", flush=True)
    for signal_id, source in PROBE_SIGNALS:
        query = urllib.parse.urlencode({"ids": signal_id, "source": source})
        try:
            status, payload = request_json(
                "GET",
                f"{root}/v1/signals?{query}",
                token=token,
                timeout_s=timeout_s,
            )
        except ApiError as exc:
            print(f"  {signal_id}@{source}: ERROR {exc}")
            continue
        if status != 200 or not isinstance(payload, dict):
            print(f"  {signal_id}@{source}: unexpected {_pretty(payload)}")
            continue
        items = payload.get("signals")
        if not isinstance(items, list) or not items:
            print(f"  {signal_id}@{source}: empty")
            continue
        item = items[0]
        available = item.get("available")
        value = item.get("value")
        print(f"  {signal_id}@{source}: available={available} value={value!r}")

    print("\n=== verify: automations ===", flush=True)
    status, autos = request_json(
        "GET",
        f"{root}/v1/automations",
        token=token,
        timeout_s=timeout_s,
    )
    if status != 200 or not isinstance(autos, dict):
        raise ApiError(f"automations failed HTTP {status}: {_pretty(autos)}", status=status, body=autos)
    rules = autos.get("automations")
    n_rules = len(rules) if isinstance(rules, list) else 0
    print(f"automations={n_rules}")
    if isinstance(rules, list):
        for rule in rules[:10]:
            if isinstance(rule, dict):
                print(
                    f"  - {rule.get('name')!r} id={rule.get('id')} enabled={rule.get('enabled')}",
                )
        if n_rules > 10:
            print(f"  … +{n_rules - 10} more")


def parse_args(argv: Optional[list[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Pair with TBox Monitor External API and verify connectivity.",
    )
    parser.add_argument("--host", default=DEFAULT_HOST, help=f"HU IP (default {DEFAULT_HOST})")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help=f"API port (default {DEFAULT_PORT})")
    parser.add_argument(
        "--token-file",
        type=Path,
        default=DEFAULT_TOKEN_FILE,
        help=f"Where to store accessToken (default {DEFAULT_TOKEN_FILE})",
    )
    parser.add_argument(
        "--client-name",
        default=DEFAULT_CLIENT_NAME,
        help="Name shown on the HU pairing dialog",
    )
    parser.add_argument(
        "--client-id",
        default="",
        help="Stable client id (default: random UUID, or reuse from token file)",
    )
    parser.add_argument(
        "--wait",
        type=float,
        default=DEFAULT_PAIR_WAIT_S,
        help="Seconds to wait for HU approve (default 120)",
    )
    parser.add_argument(
        "--poll",
        type=float,
        default=2.0,
        help="Seconds between pair/status polls (default 2)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT_S,
        help="HTTP timeout seconds (default 10)",
    )
    parser.add_argument(
        "--check-only",
        action="store_true",
        help="Skip pairing; use existing token file and only run health + authenticated probes",
    )
    parser.add_argument(
        "--health-only",
        action="store_true",
        help="Only GET /v1/health (no pairing, no token)",
    )
    return parser.parse_args(argv)


def main(argv: Optional[list[str]] = None) -> int:
    args = parse_args(argv)
    root = base_url(args.host, args.port)
    print(f"target {root}", flush=True)

    try:
        health = check_health(root, args.timeout)
        print("health:")
        print(_pretty(health))
        if args.health_only:
            return 0

        token_payload = load_token(args.token_file)
        token: Optional[str] = None

        if args.check_only:
            if token_payload is None:
                raise ApiError(f"No token in {args.token_file}; run without --check-only to pair")
            token = str(token_payload["accessToken"])
            print(f"\nusing token from {args.token_file}", flush=True)
        else:
            client_id = args.client_id.strip()
            if not client_id and token_payload and isinstance(token_payload.get("clientId"), str):
                client_id = token_payload["clientId"]
            if not client_id:
                client_id = str(uuid.uuid4())

            paired = pair(
                root,
                client_id=client_id,
                client_name=args.client_name,
                wait_s=args.wait,
                poll_s=args.poll,
                timeout_s=args.timeout,
            )
            paired["host"] = args.host
            paired["port"] = args.port
            save_token(args.token_file, paired)
            print(f"\napproved; token saved to {args.token_file}", flush=True)
            token = paired["accessToken"]

        assert token is not None
        verify(root, token, args.timeout)
        print("\nOK — API reachable and authenticated", flush=True)
        return 0
    except ApiError as exc:
        print(f"\nERROR: {exc}", file=sys.stderr)
        if exc.body is not None:
            print(_pretty(exc.body), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
