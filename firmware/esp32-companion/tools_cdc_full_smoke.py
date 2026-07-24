#!/usr/bin/env python3
"""Local PC smoke-test: USB CDC NDJSON with ESP32 companion + UM980."""

from __future__ import annotations

import json
import sys
import time
from collections import Counter
from typing import Any, Callable, Optional

import serial
from serial import SerialException

PORT = sys.argv[1] if len(sys.argv) > 1 else "COM30"
BAUD_CDC = 115200

# Safe/config commands first (Companion tab). RESET variants last (slow / may stall UART).
UM980_CONFIG_COMMANDS: list[tuple[str, str]] = [
    ("GPGGA 0.5", "GPGGA 0.5"),
    ("GPRMC 0.5", "GPRMC 0.5"),
    ("GPGSA 1", "GPGSA 1"),
    ("GPGSV 1", "GPGSV 1"),
    ("GPZDA 2", "GPZDA 2"),
    ("GPVTG 2", "GPVTG 2"),
    ("DGPS TIMEOUT 600", "CONFIG DGPS TIMEOUT 600"),
    ("RTK OFF timeout", "CONFIG RTK TIMEOUT 0"),
    ("RTK OFF", "CONFIG RTK OFF"),
    ("STANDALONE ENABLE", "CONFIG STANDALONE ENABLE"),
    ("INS RESET", "CONFIG INS RESET"),
    ("MODE AUTOMOTIVE", "MODE ROVER AUTOMOTIVE"),
    ("MMP ENABLE", "CONFIG MMP ENABLE"),
    ("AGNSS ENABLE", "CONFIG AGNSS ENABLE"),
    ("ANTIJAM FORCE", "CONFIG ANTIJAM FORCE"),
    ("SIGNALGROUP 2", "CONFIG SIGNALGROUP 2"),
    ("PVTALG MULTI", "CONFIG PVTALG MULTI"),
    ("CONFIG dump", "CONFIG"),
    ("MODE dump", "MODE"),
    ("SAVECONFIG", "SAVECONFIG"),
    ("STANDALONE DISABLE", "CONFIG STANDALONE DISABLE"),
    ("STANDALONE ENABLE again", "CONFIG STANDALONE ENABLE"),
    ("MMP DISABLE", "CONFIG MMP DISABLE"),
    ("MMP ENABLE again", "CONFIG MMP ENABLE"),
    ("AGNSS DISABLE", "CONFIG AGNSS DISABLE"),
    ("AGNSS ENABLE again", "CONFIG AGNSS ENABLE"),
    ("ANTIJAM AUTO", "CONFIG ANTIJAM AUTO"),
    ("ANTIJAM FORCE again", "CONFIG ANTIJAM FORCE"),
    ("SIGNALGROUP 1", "CONFIG SIGNALGROUP 1"),
    ("SIGNALGROUP 2 again", "CONFIG SIGNALGROUP 2"),
    ("PVTALG SINGLE", "CONFIG PVTALG SINGLE"),
    ("PVTALG MULTI again", "CONFIG PVTALG MULTI"),
    ("MODE UAV", "MODE ROVER UAV"),
    ("MODE ROVER", "MODE ROVER"),
    ("MODE AUTOMOTIVE again", "MODE ROVER AUTOMOTIVE"),
    ("DGPS TIMEOUT 60", "CONFIG DGPS TIMEOUT 60"),
    ("DGPS TIMEOUT 300", "CONFIG DGPS TIMEOUT 300"),
    ("DGPS TIMEOUT 600 again", "CONFIG DGPS TIMEOUT 600"),
    ("RTK ENABLE timeout", "CONFIG RTK TIMEOUT 600"),
    ("RTK ENABLE", "CONFIG RTK ENABLE"),
    ("RTK OFF restore t", "CONFIG RTK TIMEOUT 0"),
    ("RTK OFF restore", "CONFIG RTK OFF"),
]

UM980_RESET_COMMANDS: list[tuple[str, str]] = [
    ("RESET hot", "RESET"),
    ("RESET warm", "RESET EPHEM"),
    ("RESET cold", "RESET EPHEM ALMANAC IONUTC POSITION"),
]


def log(msg: str) -> None:
    print(msg, flush=True)


class CdcSession:
    def __init__(self, port: str) -> None:
        self.port = port
        self.ser: Optional[serial.Serial] = None
        self.buf = b""
        self.types: Counter[str] = Counter()
        self.last_gps: Optional[dict[str, Any]] = None
        self.last_hb: Optional[dict[str, Any]] = None
        self.last_hello: Optional[dict[str, Any]] = None
        self.open()

    def open(self) -> None:
        self.close()
        self.ser = serial.Serial(self.port, BAUD_CDC, timeout=0.05)
        self.ser.dtr = True
        self.ser.rts = False
        time.sleep(0.7)
        try:
            self.ser.reset_input_buffer()
        except Exception:
            pass
        self.buf = b""

    def close(self) -> None:
        if self.ser is not None:
            try:
                self.ser.close()
            except Exception:
                pass
        self.ser = None

    def reopen(self, wait: float = 2.0) -> bool:
        log(f"INFO  reopening {self.port} after {wait:.1f}s …")
        self.close()
        time.sleep(wait)
        for attempt in range(1, 8):
            try:
                self.open()
                log(f"INFO  reopen ok (attempt {attempt})")
                return True
            except Exception as e:
                log(f"INFO  reopen attempt {attempt} failed: {e}")
                time.sleep(1.0)
        return False

    def write_json(self, obj: dict[str, Any]) -> None:
        assert self.ser is not None
        line = json.dumps(obj, separators=(",", ":")) + "\n"
        self.ser.write(line.encode("utf-8"))
        self.ser.flush()

    def pump(self, timeout: float = 0.0) -> list[dict[str, Any]]:
        assert self.ser is not None
        end = time.time() + timeout
        out: list[dict[str, Any]] = []
        while True:
            try:
                n = self.ser.in_waiting
            except SerialException as e:
                raise e
            if n:
                self.buf += self.ser.read(n)
                while b"\n" in self.buf:
                    raw, self.buf = self.buf.split(b"\n", 1)
                    s = raw.decode("utf-8", errors="replace").strip()
                    if not s.startswith("{"):
                        continue
                    try:
                        o = json.loads(s)
                    except Exception:
                        continue
                    if not isinstance(o, dict):
                        continue
                    t = str(o.get("t", ""))
                    self.types[t] += 1
                    if t == "gps":
                        self.last_gps = o
                    elif t == "hb":
                        self.last_hb = o
                    elif t == "hello":
                        self.last_hello = o
                    out.append(o)
            if time.time() >= end:
                break
            time.sleep(0.02)
        return out

    def wait_for(
        self,
        pred: Callable[[dict[str, Any]], bool],
        timeout: float,
        also_send: Optional[dict[str, Any]] = None,
    ) -> Optional[dict[str, Any]]:
        if also_send is not None:
            self.write_json(also_send)
        end = time.time() + timeout
        while time.time() < end:
            for o in self.pump(0.05):
                if pred(o):
                    return o
        return None


def um980_cmd(cmd: str) -> dict[str, Any]:
    return {"v": 1, "t": "um980Cmd", "cmd": cmd}


def send_um980(s: CdcSession, label: str, cmd: str, timeout: float = 4.0) -> tuple[bool, str]:
    try:
        rsp = s.wait_for(
            lambda o, c=cmd: o.get("t") == "um980Rsp"
            and str(o.get("cmd", "")).strip().upper() == c.upper(),
            timeout=timeout,
            also_send=um980_cmd(cmd),
        )
        if not rsp:
            # Accept any um980Rsp shortly after (cmd echo may differ).
            rsp = s.wait_for(lambda o: o.get("t") == "um980Rsp", timeout=1.0)
        if not rsp:
            return False, "no um980Rsp"
        lines = rsp.get("lines") or []
        preview = " | ".join(str(x)[:80] for x in lines[:3])
        status = "ok" if rsp.get("ok") else "ok=false"
        return True, f"{status}; echo={rsp.get('cmd')!r}; lines={len(lines)}; {preview}"
    except SerialException as e:
        if not s.reopen(2.5):
            return False, f"SerialException and reopen failed: {e}"
        # one retry after reopen
        try:
            s.wait_for(
                lambda o: o.get("t") == "hello",
                timeout=4.0,
                also_send={"v": 1, "t": "hello"},
            )
            rsp = s.wait_for(
                lambda o: o.get("t") == "um980Rsp",
                timeout=timeout,
                also_send=um980_cmd(cmd),
            )
            if rsp:
                return True, f"recovered; ok={rsp.get('ok')}; lines={len(rsp.get('lines') or [])}"
            return False, f"SerialException then no rsp: {e}"
        except SerialException as e2:
            return False, f"SerialException after reopen: {e2}"


def main() -> int:
    fails = 0

    def ok(name: str, detail: str = "") -> None:
        log(f"PASS  {name}" + (f" — {detail}" if detail else ""))

    def fail(name: str, detail: str) -> None:
        nonlocal fails
        fails += 1
        log(f"FAIL  {name} — {detail}")

    log(f"Opening {PORT} @ {BAUD_CDC} …")
    try:
        s = CdcSession(PORT)
    except Exception as e:
        log(f"FAIL  open port: {e}")
        return 2

    try:
        hello = s.wait_for(
            lambda o: o.get("t") == "hello",
            timeout=5.0,
            also_send={"v": 1, "t": "hello"},
        )
        if not hello:
            fail("hello", "no hello within 5s")
        else:
            ok(
                "hello",
                f"fw={hello.get('fw')} baud={hello.get('baud')} "
                f"gpioIn={hello.get('gpioIn')} relays={hello.get('relays')} um980={hello.get('um980')}",
            )
            fw = str(hello.get("fw", ""))
            if fw.startswith("0.4"):
                ok("fw_version", fw)
            else:
                fail("fw_version", f"expected 0.4.x, got {fw}")

        hb = s.wait_for(lambda o: o.get("t") == "hb", timeout=4.0)
        if hb:
            ok("heartbeat", f"uptimeMs={hb.get('uptimeMs')}")
        else:
            fail("heartbeat", "no hb within 4s")

        s.pump(4.0)
        if s.last_gps:
            g = s.last_gps
            ok(
                "gps_from_um980",
                f"fix={g.get('fix')} lat={g.get('lat')} lon={g.get('lon')} "
                f"sats={g.get('satsUsed')}/{g.get('satsVis')} speed={g.get('speedKmh')} utc={g.get('utc')}",
            )
        else:
            fail("gps_from_um980", "no gps frame in 4s")

        mask0 = int((hello or {}).get("relays") or 0) & 0x0F
        try:
            relay = s.wait_for(
                lambda o: o.get("t") == "relay",
                timeout=2.0,
                also_send={"v": 1, "t": "relaySet", "mask": mask0 ^ 0x1},
            )
            if relay:
                ok("relaySet", f"mask={relay.get('mask')}")
                s.write_json({"v": 1, "t": "relaySet", "mask": mask0})
                s.pump(0.4)
            else:
                fail("relaySet", "no relay ack")
        except SerialException as e:
            fail("relaySet", str(e))
            if not s.reopen():
                log("RESULT: FAIL (port lost)")
                return 1

        try:
            ack = s.wait_for(
                lambda o: o.get("t") == "um980Baud",
                timeout=4.0,
                also_send={"v": 1, "t": "um980Baud", "baud": 115200},
            )
            if ack and ack.get("ok") is True and int(ack.get("baud") or 0) == 115200:
                ok("um980Baud_115200", str(ack))
            else:
                fail("um980Baud_115200", str(ack))

            bad = s.wait_for(
                lambda o: o.get("t") == "um980Baud",
                timeout=3.0,
                also_send={"v": 1, "t": "um980Baud", "baud": 12345},
            )
            if bad and bad.get("ok") is False:
                ok("um980Baud_reject", str(bad))
            else:
                fail("um980Baud_reject", str(bad))
        except SerialException as e:
            fail("um980Baud", str(e))
            if not s.reopen():
                log("RESULT: FAIL (port lost)")
                return 1

        cmd_ok = 0
        cmd_fail = 0
        for label, cmd in UM980_CONFIG_COMMANDS:
            good, detail = send_um980(s, label, cmd)
            if good:
                ok(f"um980:{label}", detail)
                cmd_ok += 1
            else:
                fail(f"um980:{label}", detail)
                cmd_fail += 1
            s.pump(0.1)

        for label, cmd in UM980_RESET_COMMANDS:
            good, detail = send_um980(s, label, cmd, timeout=6.0)
            if good:
                ok(f"um980:{label}", detail)
                cmd_ok += 1
            else:
                fail(f"um980:{label}", detail)
                cmd_fail += 1
            # Give UM980 time to come back; CDC may drop.
            time.sleep(2.0)
            try:
                s.pump(0.5)
            except SerialException:
                s.reopen(3.0)
                s.wait_for(
                    lambda o: o.get("t") == "hello",
                    timeout=5.0,
                    also_send={"v": 1, "t": "hello"},
                )

        log(
            f"INFO  um980_cmd_summary ok={cmd_ok} fail={cmd_fail} "
            f"total={len(UM980_CONFIG_COMMANDS) + len(UM980_RESET_COMMANDS)}"
        )

        try:
            before = s.types["gps"]
            s.pump(3.5)
            after = s.types["gps"]
            if after > before:
                ok("gps_after_commands", f"+{after - before} frames")
            else:
                fail("gps_after_commands", "no new gps after command batch")

            hb2 = s.wait_for(lambda o: o.get("t") == "hb", timeout=3.0)
            if hb2:
                ok("heartbeat_after_commands", f"uptimeMs={hb2.get('uptimeMs')}")
            else:
                fail("heartbeat_after_commands", "no hb")
        except SerialException as e:
            fail("post_checks", str(e))

        log(f"INFO  rx_types {dict(s.types)}")
        log("INFO  skipped FRESET (destructive) and companion reboot")

    finally:
        s.close()

    if fails:
        log(f"RESULT: FAIL ({fails} failed)")
        return 1
    log("RESULT: PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
