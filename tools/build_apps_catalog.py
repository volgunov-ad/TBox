#!/usr/bin/env python3
"""
Собрать apps/catalog.json из папок apps/<id>/version.json + overlay метаданных.

Ожидаемая раскладка AppStore share (см. docs/APP_STORE_PLAN_RU.md):

  AppStore/release/apps/   или   AppStore/dev/apps/
      mqtt/version.json
      camd/version.json
      …
      catalog.meta.json   # опционально: id → title/summary/hu/…

Public key: https://disk.yandex.ru/d/KDaHLxbFtPAjYA

Без catalog.meta.json для каждой папки с version.json создаётся минимальная
запись (id=имя папки, packageName/title нужно дописать в meta).

Пример:

  python tools/build_apps_catalog.py --apps-dir /path/to/AppStore/release/apps
  python tools/build_apps_catalog.py --apps-dir /path/to/AppStore/release/apps --write
"""

from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path


def load_json(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"))


def build_catalog(apps_dir: Path) -> dict:
    meta_path = apps_dir / "catalog.meta.json"
    meta_by_id: dict[str, dict] = {}
    if meta_path.is_file():
        raw = load_json(meta_path)
        for item in raw.get("apps", []):
            app_id = str(item.get("id", "")).strip()
            if app_id:
                meta_by_id[app_id] = item

    apps: list[dict] = []
    for child in sorted(apps_dir.iterdir()):
        if not child.is_dir():
            continue
        version_path = child / "version.json"
        if not version_path.is_file():
            continue
        app_id = child.name
        meta = dict(meta_by_id.get(app_id, {}))
        package_name = str(meta.get("packageName", "")).strip()
        title = str(meta.get("title", "")).strip() or app_id
        if not package_name:
            print(
                f"warn: {app_id}: packageName отсутствует в catalog.meta.json",
                file=sys.stderr,
            )
            continue
        entry = {
            "id": app_id,
            "packageName": package_name,
            "title": title,
            "summary": meta.get("summary", ""),
            "publisher": meta.get("publisher", "TBox"),
            "origin": meta.get("origin", "first-party"),
            "hu": meta.get("hu", ["a9", "a10"]),
            "category": meta.get("category", "app"),
            "verified": bool(meta.get("verified", True)),
            "manifest": f"yandex-disk:/apps/{app_id}/version.json",
        }
        if meta.get("icon"):
            entry["icon"] = meta["icon"]
        if meta.get("signingCertSha256"):
            entry["signingCertSha256"] = meta["signingCertSha256"]
        if meta.get("minStoreVersionCode") is not None:
            entry["minStoreVersionCode"] = meta["minStoreVersionCode"]
        if meta.get("reviewNotes"):
            entry["reviewNotes"] = meta["reviewNotes"]
        apps.append(entry)

    return {
        "schemaVersion": 1,
        "publishedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "apps": apps,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Build apps/catalog.json")
    parser.add_argument(
        "--apps-dir",
        type=Path,
        required=True,
        help="Path to release/apps or dev/apps",
    )
    parser.add_argument(
        "--write",
        action="store_true",
        help="Write catalog.json into apps-dir (default: print to stdout)",
    )
    args = parser.parse_args()
    apps_dir: Path = args.apps_dir
    if not apps_dir.is_dir():
        print(f"error: not a directory: {apps_dir}", file=sys.stderr)
        return 1
    catalog = build_catalog(apps_dir)
    text = json.dumps(catalog, ensure_ascii=False, indent=2) + "\n"
    if args.write:
        out = apps_dir / "catalog.json"
        out.write_text(text, encoding="utf-8")
        print(f"wrote {out} ({len(catalog['apps'])} apps)", file=sys.stderr)
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
