#!/usr/bin/env python3
"""
Сборка APK для OTA-обновлений TBox Monitor и подготовка version.json.

Скрипт:
  1. Спрашивает канал: Релиз или Разработка
  2. Собирает ru/en APK через Gradle
  3. Копирует APK в папку загрузки на Яндекс.Диск
  4. Создаёт version.json с sha256 и размером файлов

Папки по умолчанию (Windows):
  Разработка -> C:\\Users\\volgu\\AndroidStudioProjects\\TBM\\dev
  Релиз      -> C:\\Users\\volgu\\AndroidStudioProjects\\TBM\\release

Запуск из корня репозитория:
  python tools/build_ota_release.py

Или с аргументами:
  python tools/build_ota_release.py --channel dev
  python tools/build_ota_release.py --channel release --changelog "Исправления"
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path

DEFAULT_OUTPUT_BASE = Path(r"C:\Users\volgu\AndroidStudioProjects\TBM")
GRADLE_FILE = Path("app/build.gradle.kts")
FLAVORS = ("ru", "en")


@dataclass(frozen=True)
class ChannelConfig:
    key: str
    label: str
    build_type: str
    output_dir_name: str
    gradle_tasks: tuple[str, ...]


CHANNELS: dict[str, ChannelConfig] = {
    "dev": ChannelConfig(
        key="dev",
        label="Разработка",
        build_type="debug",
        output_dir_name="dev",
        gradle_tasks=("assembleRuDebug", "assembleEnDebug"),
    ),
    "release": ChannelConfig(
        key="release",
        label="Релиз",
        build_type="release",
        output_dir_name="release",
        gradle_tasks=("assembleRuRelease", "assembleEnRelease"),
    ),
}


@dataclass(frozen=True)
class AppVersion:
    version_code: int
    version_name: str


@dataclass(frozen=True)
class BuiltApk:
    flavor: str
    source_path: Path
    file_name: str
    sha256: str
    size_bytes: int


def project_root() -> Path:
    return Path(__file__).resolve().parent.parent


def gradle_wrapper(project_dir: Path) -> Path:
    wrapper = project_dir / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if not wrapper.is_file():
        raise FileNotFoundError(f"Gradle wrapper not found: {wrapper}")
    return wrapper


def read_app_version(gradle_path: Path) -> AppVersion:
    text = gradle_path.read_text(encoding="utf-8")
    code_match = re.search(r"versionCode\s*=\s*(\d+)", text)
    name_match = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    if not code_match or not name_match:
        raise ValueError(f"Could not parse versionCode/versionName from {gradle_path}")
    return AppVersion(
        version_code=int(code_match.group(1)),
        version_name=name_match.group(1),
    )


def choose_channel_interactive() -> ChannelConfig:
    print("Выберите канал обновлений:")
    print("  1 — Разработка (debug APK -> dev)")
    print("  2 — Релиз (release APK -> release)")
    while True:
        choice = input("Введите 1 или 2: ").strip()
        if choice == "1":
            return CHANNELS["dev"]
        if choice == "2":
            return CHANNELS["release"]
        print("Неверный ввод, попробуйте снова.")


def resolve_channel(args: argparse.Namespace) -> ChannelConfig:
    if args.channel:
        key = args.channel.lower()
        if key in ("development", "debug"):
            key = "dev"
        if key not in CHANNELS:
            raise ValueError(f"Unknown channel: {args.channel}")
        return CHANNELS[key]
    return choose_channel_interactive()


def run_gradle(project_dir: Path, tasks: tuple[str, ...]) -> None:
    wrapper = gradle_wrapper(project_dir)
    command = [str(wrapper), *tasks]
    print(f"Running: {' '.join(command)}")
    subprocess.run(command, cwd=project_dir, check=True)


def expected_apk_path(project_dir: Path, flavor: str, build_type: str) -> Path:
    return project_dir / "app/build/outputs/apk" / flavor / build_type / f"app-{flavor}-{build_type}.apk"


def find_apk(project_dir: Path, flavor: str, build_type: str) -> Path:
    expected = expected_apk_path(project_dir, flavor, build_type)
    if expected.is_file():
        return expected

    search_dir = project_dir / "app/build/outputs/apk" / flavor / build_type
    candidates = sorted(search_dir.glob("*.apk")) if search_dir.is_dir() else []
    if len(candidates) == 1:
        return candidates[0]
    if not candidates:
        raise FileNotFoundError(f"APK not found for flavor={flavor}, buildType={build_type}")
    raise FileNotFoundError(
        f"Multiple APK files found in {search_dir}; expected {expected.name}"
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as apk_file:
        for chunk in iter(lambda: apk_file.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_apk_metadata(project_dir: Path, flavor: str, build_type: str) -> BuiltApk:
    source = find_apk(project_dir, flavor, build_type)
    return BuiltApk(
        flavor=flavor,
        source_path=source,
        file_name=source.name,
        sha256=sha256_file(source),
        size_bytes=source.stat().st_size,
    )


def copy_apk(source: Path, destination_dir: Path) -> Path:
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / source.name
    shutil.copy2(source, destination)
    return destination


def prompt_changelog(default: str = "") -> str:
    text = input("Changelog (Enter — пропустить): ").strip()
    return text or default


def build_version_json(
    version: AppVersion,
    apks: list[BuiltApk],
    changelog: str,
    min_supported_version_code: int,
) -> dict:
    published_at = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    releases = []
    for apk in apks:
        releases.append(
            {
                "versionCode": version.version_code,
                "versionName": version.version_name,
                "flavor": apk.flavor,
                "apkFileName": apk.file_name,
                "sha256": apk.sha256,
                "apkSizeBytes": apk.size_bytes,
                "minSupportedVersionCode": min_supported_version_code,
                "changelog": changelog,
                "publishedAt": published_at,
            }
        )
    return {
        "schemaVersion": 1,
        "releases": releases,
    }


def write_version_json(destination_dir: Path, manifest: dict) -> Path:
    destination = destination_dir / "version.json"
    destination.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return destination


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Build OTA APKs and generate version.json")
    parser.add_argument(
        "--channel",
        choices=["dev", "development", "debug", "release"],
        help="Канал: dev (Разработка) или release (Релиз). Без аргумента — интерактивный выбор.",
    )
    parser.add_argument(
        "--output-base",
        type=Path,
        default=DEFAULT_OUTPUT_BASE,
        help=f"Базовая папка для dev/release (по умолчанию: {DEFAULT_OUTPUT_BASE})",
    )
    parser.add_argument(
        "--changelog",
        default=None,
        help="Текст изменений для version.json",
    )
    parser.add_argument(
        "--min-supported-version-code",
        type=int,
        default=0,
        help="minSupportedVersionCode в version.json",
    )
    parser.add_argument(
        "--skip-build",
        action="store_true",
        help="Не запускать Gradle, использовать уже собранные APK",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = project_root()
    gradle_file = root / GRADLE_FILE
    if not gradle_file.is_file():
        print(f"Error: {gradle_file} not found. Run the script from the TBox repository.", file=sys.stderr)
        return 1

    try:
        channel = resolve_channel(args)
        version = read_app_version(gradle_file)
        output_dir = args.output_base / channel.output_dir_name

        changelog = args.changelog
        if changelog is None and sys.stdin.isatty():
            changelog = prompt_changelog()
        changelog = changelog or ""

        print()
        print(f"Канал: {channel.label}")
        print(f"Версия: {version.version_name} ({version.version_code})")
        print(f"Папка назначения: {output_dir}")
        print()

        if not args.skip_build:
            run_gradle(root, channel.gradle_tasks)
        else:
            print("Пропуск сборки (--skip-build)")

        apks: list[BuiltApk] = []
        for flavor in FLAVORS:
            metadata = build_apk_metadata(root, flavor, channel.build_type)
            copied = copy_apk(metadata.source_path, output_dir)
            print(f"Copied {metadata.file_name} -> {copied}")
            apks.append(metadata)

        manifest = build_version_json(
            version=version,
            apks=apks,
            changelog=changelog,
            min_supported_version_code=args.min_supported_version_code,
        )
        version_json_path = write_version_json(output_dir, manifest)

        print()
        print("Готово.")
        print(f"version.json -> {version_json_path}")
        for apk in apks:
            size_mb = apk.size_bytes / (1024 * 1024)
            print(f"  {apk.file_name}: {size_mb:.1f} MB, sha256={apk.sha256}")
        return 0
    except subprocess.CalledProcessError as error:
        print(f"Gradle build failed with exit code {error.returncode}", file=sys.stderr)
        return error.returncode or 1
    except (FileNotFoundError, ValueError, OSError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
