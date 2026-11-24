#!/usr/bin/env python3
"""
Собрать apps/catalog.json из папок apps/<id>/version.json + overlay метаданных.

Ожидаемая раскладка AppStore share (см. docs/APP_STORE_PLAN_RU.md):

  AppStore/release/apps/   или   AppStore/dev/apps/
      mqtt/version.json
      camd/version.json
      …
      catalog.meta.json   # опционально: sections + apps[]

Public key: https://disk.yandex.ru/d/KDaHLxbFtPAjYA
Зеркало:   https://cloud.mail.ru/public/voi3/4Lx1RhqV9

GUI для наполнения: tools/appstore_manager/ (Запуск.cmd).

Пример:

  python tools/build_apps_catalog.py --apps-dir /path/to/AppStore/release/apps
  python tools/build_apps_catalog.py --apps-dir /path/to/AppStore/release/apps --write
  python tools/build_apps_catalog.py --apps-dir /path/to/AppStore/release/apps --migrate --write
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_MANAGER = Path(__file__).resolve().parent / "appstore_manager"
if str(_MANAGER) not in sys.path:
    sys.path.insert(0, str(_MANAGER))

from appstore_catalog import build_catalog, migrate_legacy_catalog, write_catalog  # noqa: E402


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
    parser.add_argument(
        "--migrate",
        action="store_true",
        help="Migrate legacy catalog.meta.json / version.json to the new format first",
    )
    args = parser.parse_args()
    apps_dir: Path = args.apps_dir
    if not apps_dir.is_dir():
        print(f"error: not a directory: {apps_dir}", file=sys.stderr)
        return 1
    if args.migrate:
        report = migrate_legacy_catalog(apps_dir)
        for line in report.summary_lines():
            print(line, file=sys.stderr)
    if args.write:
        out = write_catalog(apps_dir)
        catalog = json.loads(out.read_text(encoding="utf-8"))
        print(f"wrote {out} ({len(catalog['apps'])} apps)", file=sys.stderr)
    else:
        catalog = build_catalog(apps_dir)
        sys.stdout.write(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
