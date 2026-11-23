#!/usr/bin/env python3
"""Fail when app versions, changelogs, or the module list drift apart.

    python tools/check_app_versions.py
    python tools/check_app_versions.py --base origin/preRelease

Always: versionName is the top block of the app's Changelog.dm, and every
application module in settings.gradle.kts is in tools/app_releases.py.
With --base: versionCode never goes down, and a new versionName comes with a
higher versionCode. Apps missing at the base ref are skipped.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

from app_releases import (
    APPS,
    ROOT,
    changelog_error,
    module_catalog_errors,
    parse_version,
    read_version,
    version_bump_error,
)


def git_show(root: Path, ref: str, path: str) -> str | None:
    result = subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        cwd=root,
        capture_output=True,
        text=True,
        encoding="utf-8",
    )
    if result.returncode != 0:
        return None
    return result.stdout


def check(root: Path = ROOT, base_ref: str | None = None) -> list[str]:
    errors = module_catalog_errors(root)
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
        if base_ref is None:
            continue
        base_text = git_show(root, base_ref, app.version_file)
        if base_text is None:
            continue
        try:
            base_version = parse_version(app, base_text)
        except ValueError:
            continue
        message = version_bump_error(app, base_version, (version_code, version_name), base_ref)
        if message:
            errors.append(message)
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base", help="git ref to compare versionCode against, e.g. origin/preRelease")
    args = parser.parse_args()
    if args.base and git_show(ROOT, args.base, "settings.gradle.kts") is None:
        print(f"git ref {args.base!r} not found (fetch it first)", file=sys.stderr)
        return 2
    errors = check(base_ref=args.base)
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1
    against = f", versionCode not behind {args.base}" if args.base else ""
    print(f"OK: {len(APPS)} apps, changelog header matches versionName{against}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
