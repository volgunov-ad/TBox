#!/usr/bin/env python3
"""
Сборка APK для OTA-обновлений TBox Monitor и подготовка version.json.

Скрипт:
  1. Спрашивает канал: Разработка (debug/release) или Релиз
  2. Собирает ru/en APK через Gradle
  3. Копирует APK в папку загрузки на Яндекс.Диск
  4. Создаёт version.json с sha256 и размером файлов

Имена APK в папке назначения:
  tbox_monitor-v.0.16.0-ru.apk, tbox_monitor-v.0.16.0-en.apk

Папки по умолчанию (Windows):
  Разработка -> C:\\Users\\volgu\\AndroidStudioProjects\\TBM\\dev
  Релиз      -> C:\\Users\\volgu\\AndroidStudioProjects\\TBM\\release

Changelog для version.json берётся из Changelog.dm (секция текущей versionName),
если не передан --changelog. Markdown (**жирный**, `код`, *курсив*) снимается.

Запуск из корня репозитория:
  python tools/build_ota_release.py

Или с аргументами:
  python tools/build_ota_release.py --channel dev
  python tools/build_ota_release.py --channel dev-release
  python tools/build_ota_release.py --channel release
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
CHANGELOG_FILE = Path("Changelog.dm")
FLAVORS = ("ru", "en")
VERSION_HEADER_RE = re.compile(r"^\d+\.\d+(?:\.\d+)*$")
MARKDOWN_BOLD_RE = re.compile(r"\*\*(.+?)\*\*")
MARKDOWN_ITALIC_RE = re.compile(r"(?<!\*)\*(?!\*)(.+?)(?<!\*)\*(?!\*)")
MARKDOWN_CODE_RE = re.compile(r"`([^`]+)`")


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
        label="Разработка (debug APK -> dev)",
        build_type="debug",
        output_dir_name="dev",
        gradle_tasks=("assembleRuDebug", "assembleEnDebug"),
    ),
    "dev_release": ChannelConfig(
        key="dev_release",
        label="Разработка (release APK -> dev)",
        build_type="release",
        output_dir_name="dev",
        gradle_tasks=("assembleRuRelease", "assembleEnRelease"),
    ),
    "release": ChannelConfig(
        key="release",
        label="Релиз (release APK -> release)",
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


def ota_apk_file_name(version_name: str, flavor: str) -> str:
    return f"tbox_monitor-v.{version_name}-{flavor}.apk"


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
    print("  2 — Разработка (release APK -> dev)")
    print("  3 — Релиз (release APK -> release)")
    while True:
        choice = input("Введите 1, 2 или 3: ").strip()
        if choice == "1":
            return CHANNELS["dev"]
        if choice == "2":
            return CHANNELS["dev_release"]
        if choice == "3":
            return CHANNELS["release"]
        print("Неверный ввод, попробуйте снова.")


def resolve_channel(args: argparse.Namespace) -> ChannelConfig:
    if args.channel:
        key = args.channel.lower().replace("-", "_")
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


def build_apk_metadata(
    project_dir: Path,
    flavor: str,
    build_type: str,
    version_name: str,
) -> BuiltApk:
    source = find_apk(project_dir, flavor, build_type)
    file_name = ota_apk_file_name(version_name, flavor)
    return BuiltApk(
        flavor=flavor,
        source_path=source,
        file_name=file_name,
        sha256=sha256_file(source),
        size_bytes=source.stat().st_size,
    )


def copy_apk(source: Path, destination_dir: Path, destination_name: str) -> Path:
    destination_dir.mkdir(parents=True, exist_ok=True)
    destination = destination_dir / destination_name
    shutil.copy2(source, destination)
    return destination


def strip_changelog_formatting(text: str) -> str:
    """Remove Markdown markers used in Changelog.dm for plain text in version.json."""
    cleaned = MARKDOWN_BOLD_RE.sub(r"\1", text)
    cleaned = MARKDOWN_CODE_RE.sub(r"\1", cleaned)
    cleaned = MARKDOWN_ITALIC_RE.sub(r"\1", cleaned)
    return cleaned


def read_changelog_section(changelog_path: Path, version_name: str) -> str:
    """Extract the numbered entries for version_name from Changelog.dm."""
    if not changelog_path.is_file():
        raise FileNotFoundError(f"Changelog file not found: {changelog_path}")

    lines = changelog_path.read_text(encoding="utf-8").splitlines()
    start_index: int | None = None
    for index, line in enumerate(lines):
        if line.strip() == version_name:
            start_index = index + 1
            break
    if start_index is None:
        raise ValueError(
            f"Version '{version_name}' not found in {changelog_path}"
        )

    section_lines: list[str] = []
    for line in lines[start_index:]:
        stripped = line.strip()
        if VERSION_HEADER_RE.fullmatch(stripped):
            break
        if stripped or section_lines:
            section_lines.append(line.rstrip())

    while section_lines and not section_lines[-1].strip():
        section_lines.pop()

    changelog = "\n".join(section_lines).strip()
    if not changelog:
        raise ValueError(
            f"Changelog section for '{version_name}' in {changelog_path} is empty"
        )
    return changelog


def resolve_changelog(
    args: argparse.Namespace,
    project_dir: Path,
    version_name: str,
) -> str:
    if args.changelog is not None:
        return strip_changelog_formatting(args.changelog)

    changelog_path = (
        args.changelog_file
        if args.changelog_file is not None
        else project_dir / CHANGELOG_FILE
    )
    if not changelog_path.is_absolute():
        changelog_path = project_dir / changelog_path

    return strip_changelog_formatting(
        read_changelog_section(changelog_path, version_name)
    )


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
        choices=["dev", "development", "debug", "dev-release", "dev_release", "release"],
        help=(
            "Канал: dev (debug -> dev), dev-release (release -> dev), "
            "release (release -> release). Без аргумента — интерактивный выбор."
        ),
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
        help="Текст изменений для version.json (иначе — секция версии из Changelog.dm)",
    )
    parser.add_argument(
        "--changelog-file",
        type=Path,
        default=None,
        help=f"Файл changelog (по умолчанию: {CHANGELOG_FILE})",
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
        changelog = resolve_changelog(args, root, version.version_name)

        print()
        print(f"Канал: {channel.label}")
        print(f"Версия: {version.version_name} ({version.version_code})")
        print(f"Папка назначения: {output_dir}")
        changelog_preview = changelog if len(changelog) <= 200 else changelog[:200] + "…"
        print(f"Changelog: {changelog_preview}")
        print()

        if not args.skip_build:
            run_gradle(root, channel.gradle_tasks)
        else:
            print("Пропуск сборки (--skip-build)")

        apks: list[BuiltApk] = []
        for flavor in FLAVORS:
            metadata = build_apk_metadata(root, flavor, channel.build_type, version.version_name)
            copied = copy_apk(metadata.source_path, output_dir, metadata.file_name)
            print(f"Copied {metadata.source_path.name} -> {copied}")
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
