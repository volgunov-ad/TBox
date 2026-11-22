#!/usr/bin/env python3
"""Fail when an app's versionName is not the top block of its Changelog.dm.

    python tools/check_app_versions.py
"""

from __future__ import annotations

import sys
from pathlib import Path

from app_releases import APPS, ROOT, changelog_error, read_version


def check(root: Path = ROOT) -> list[str]:
    errors: list[str] = []
    for app in APPS:
        try:
            version_code, version_name = read_version(root, app)
        except ValueError as error:
            errors.append(str(error))
            continue
        changelog_path = root / app.changelog
        if not changelog_path.is_file():
            errors.append(f"{app.key}: missing {app.changelog}")
            continue
        message = changelog_error(
            app,
            version_code,
            version_name,
            changelog_path.read_text(encoding="utf-8"),
        )
        if message:
            errors.append(message)
    return errors


def main() -> int:
    errors = check()
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    print(f"OK: {len(APPS)} apps, changelog header matches versionName")
    return 0


if __name__ == "__main__":
    sys.exit(main())
