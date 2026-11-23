#!/usr/bin/env python3
"""Build one app's release APK and copy it outside the repository.

Monitor and Store default to both language flavors. Other Gradle apps build
``assembleRelease``. DCL Install is not a Gradle module.

    python tools/build_app_release.py --module fwunlock --output-dir D:\\apk
    python tools/build_app_release.py --module store --flavors ru --output-dir D:\\apk
    python tools/build_app_release.py --module app --output-dir D:\\apk

The copied name matches the Gradle output. Only Monitor keeps the ``tbox-`` prefix.
"""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

from app_releases import (
    ROOT,
    apk_output_path,
    app_by_key,
    gradle_tasks,
    read_version,
)
from build_ota_release import (
    DEFAULT_GRADLE_JVM_ARGS,
    FLAVOR_SETS,
    read_changelog_section,
    run_gradle,
)
from check_app_versions import check


def resolve_flavors(app_flavors: tuple[str, ...], requested: str | None) -> tuple[str, ...]:
    if not app_flavors:
        if requested:
            raise ValueError("This app has no ru/en flavors")
        return ()
    if not requested:
        return app_flavors
    key = requested.lower().replace(" ", "")
    if key not in FLAVOR_SETS:
        raise ValueError(f"Unknown flavors: {requested}")
    chosen = FLAVOR_SETS[key]
    unknown = [flavor for flavor in chosen if flavor not in app_flavors]
    if unknown:
        raise ValueError(f"Flavors not built by this app: {', '.join(unknown)}")
    return chosen


def output_dir_outside_repo(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if resolved == ROOT or ROOT in resolved.parents:
        raise ValueError(f"output-dir must be outside the repository: {resolved}")
    return resolved


def copy_apks(
    root: Path,
    app,
    version_name: str,
    build_type: str,
    flavors: tuple[str, ...],
    destination_dir: Path,
) -> list[Path]:
    destination_dir.mkdir(parents=True, exist_ok=True)
    selected: tuple[str | None, ...] = flavors or (None,)
    copied: list[Path] = []
    for flavor in selected:
        source = apk_output_path(root, app, version_name, build_type, flavor)
        if not source.is_file():
            raise FileNotFoundError(f"APK not found: {source}")
        destination = destination_dir / source.name
        shutil.copy2(source, destination)
        copied.append(destination)
    return copied


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Build one first-party release APK")
    parser.add_argument("--module", required=True, help="app, store, voice, phone, mqtt, dataresize, usbaudio, fwunlock, winpatch, camview, camd")
    parser.add_argument("--output-dir", required=True, type=Path, help="Directory outside the git repo")
    parser.add_argument("--flavors", help="ru, en, or ru+en (app and store only)")
    parser.add_argument("--build-type", default="release", choices=("release", "debug"))
    parser.add_argument("--gradle-jvm-args", default=DEFAULT_GRADLE_JVM_ARGS)
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        app = app_by_key(args.module)
    except KeyError as error:
        print(error, file=sys.stderr)
        return 2
    if app.gradle_module is None:
        print(
            "dclinstall is built by tools/dcl_install/build.ps1, not Gradle.",
            file=sys.stderr,
        )
        return 2

    errors = [error for error in check() if error.startswith(f"{app.key}:")]
    if errors:
        for error in errors:
            print(error, file=sys.stderr)
        return 1

    try:
        flavors = resolve_flavors(app.flavors, args.flavors)
        destination = output_dir_outside_repo(args.output_dir)
        tasks = gradle_tasks(app, args.build_type, flavors)
    except ValueError as error:
        print(error, file=sys.stderr)
        return 2

    version_code, version_name = read_version(ROOT, app)
    changelog = read_changelog_section(ROOT / app.changelog, version_name)
    print(f"{app.title} {version_name} (versionCode {version_code})")
    print(changelog)
    print()

    run_gradle(ROOT, tasks, args.gradle_jvm_args)
    copied = copy_apks(ROOT, app, version_name, args.build_type, flavors, destination)
    for path in copied:
        print(f"Copied {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
