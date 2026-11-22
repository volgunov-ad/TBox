#!/usr/bin/env python3
"""First-party APK catalog: where the version lives, the changelog, and the file name.

Monitor keeps the ``tbox-`` prefix. Every other app is ``<slug>-<version>-<type>.apk``.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

VERSION_HEADER_RE = re.compile(r"^\d+\.\d+(?:\.\d+)*$")
GRADLE_CODE_RE = re.compile(r"versionCode\s*=\s*(\d+)")
GRADLE_NAME_RE = re.compile(r'versionName\s*=\s*"([^"]+)"')
MANIFEST_CODE_RE = re.compile(r'android:versionCode="(\d+)"')
MANIFEST_NAME_RE = re.compile(r'android:versionName="([^"]+)"')


@dataclass(frozen=True)
class AppRelease:
    key: str
    title: str
    slug: str
    version_file: str
    kind: str
    changelog: str
    flavors: tuple[str, ...]
    gradle_module: str | None


APPS: tuple[AppRelease, ...] = (
    AppRelease(
        "app",
        "TBox Monitor",
        "tbox-monitor",
        "app/build.gradle.kts",
        "gradle",
        "Changelog.dm",
        ("ru", "en"),
        "app",
    ),
    AppRelease(
        "store",
        "Dashing Store",
        "store",
        "store/build.gradle.kts",
        "gradle",
        "store/Changelog.dm",
        ("ru", "en"),
        "store",
    ),
    AppRelease(
        "voice",
        "VAD Voice",
        "voice",
        "voice/build.gradle.kts",
        "gradle",
        "voice/Changelog.dm",
        (),
        "voice",
    ),
    AppRelease(
        "phone",
        "TBox Phone",
        "phone",
        "phone/build.gradle.kts",
        "gradle",
        "phone/Changelog.dm",
        (),
        "phone",
    ),
    AppRelease(
        "mqtt",
        "TBox MQTT",
        "mqtt",
        "mqtt/build.gradle.kts",
        "gradle",
        "mqtt/Changelog.dm",
        (),
        "mqtt",
    ),
    AppRelease(
        "dataresize",
        "Расширение /data",
        "dataresize",
        "dataresize/build.gradle.kts",
        "gradle",
        "dataresize/Changelog.dm",
        (),
        "dataresize",
    ),
    AppRelease(
        "usbaudio",
        "USB-аудио ГУ",
        "usbaudio",
        "usbaudio/build.gradle.kts",
        "gradle",
        "usbaudio/Changelog.dm",
        (),
        "usbaudio",
    ),
    AppRelease(
        "fwunlock",
        "Файрвол ГУ",
        "fwunlock",
        "fwunlock/build.gradle.kts",
        "gradle",
        "fwunlock/Changelog.dm",
        (),
        "fwunlock",
    ),
    AppRelease(
        "winpatch",
        "AA/CarPlay окна ГУ",
        "winpatch",
        "winpatch/build.gradle.kts",
        "gradle",
        "winpatch/Changelog.dm",
        (),
        "winpatch",
    ),
    AppRelease(
        "camview",
        "Тест камер ГУ",
        "camview",
        "camview/build.gradle.kts",
        "gradle",
        "camview/Changelog.dm",
        (),
        "camview",
    ),
    AppRelease(
        "camd",
        "Камеры ГУ",
        "camd",
        "camd/build.gradle.kts",
        "gradle",
        "camd/Changelog.dm",
        (),
        "camd",
    ),
    AppRelease(
        "dclinstall",
        "Установка DCL",
        "dclinstall",
        "tools/dcl_install/AndroidManifest.xml",
        "manifest",
        "tools/dcl_install/Changelog.dm",
        (),
        None,
    ),
)


def app_by_key(key: str) -> AppRelease:
    for app in APPS:
        if app.key == key:
            return app
    known = ", ".join(app.key for app in APPS)
    raise KeyError(f"Unknown app '{key}'. Known: {known}")


def read_version(root: Path, app: AppRelease) -> tuple[int, str]:
    text = (root / app.version_file).read_text(encoding="utf-8")
    if app.kind == "gradle":
        code_match = GRADLE_CODE_RE.search(text)
        name_match = GRADLE_NAME_RE.search(text)
    elif app.kind == "manifest":
        code_match = MANIFEST_CODE_RE.search(text)
        name_match = MANIFEST_NAME_RE.search(text)
    else:
        raise ValueError(f"{app.key}: unknown version kind {app.kind}")
    if not code_match or not name_match:
        raise ValueError(f"{app.key}: versionCode/versionName not found in {app.version_file}")
    return int(code_match.group(1)), name_match.group(1)


def version_headers(changelog_text: str) -> list[str]:
    return [
        line.strip()
        for line in changelog_text.splitlines()
        if VERSION_HEADER_RE.fullmatch(line.strip())
    ]


def changelog_error(app: AppRelease, version_code: int, version_name: str, changelog_text: str) -> str | None:
    if version_code <= 0:
        return f"{app.key}: versionCode {version_code} must be > 0"
    if not version_name.strip():
        return f"{app.key}: versionName is empty"
    headers = version_headers(changelog_text)
    if not headers:
        return f"{app.key}: no version header in {app.changelog}"
    if headers[0] != version_name:
        return (
            f"{app.key}: {app.changelog} starts with {headers[0]!r}, "
            f"version file has {version_name!r}"
        )
    if headers.count(version_name) != 1:
        return f"{app.key}: version {version_name} appears {headers.count(version_name)} times in {app.changelog}"
    return None


def apk_file_name(app: AppRelease, version_name: str, build_type: str, flavor: str | None = None) -> str:
    if flavor:
        return f"{app.slug}-{version_name}-{flavor}-{build_type}.apk"
    return f"{app.slug}-{version_name}-{build_type}.apk"


def apk_output_path(
    root: Path,
    app: AppRelease,
    version_name: str,
    build_type: str,
    flavor: str | None = None,
) -> Path:
    if app.gradle_module is None:
        raise ValueError(f"{app.key} is not a Gradle module")
    name = apk_file_name(app, version_name, build_type, flavor)
    base = root / app.gradle_module / "build" / "outputs" / "apk"
    if flavor:
        return base / flavor / build_type / name
    return base / build_type / name


def gradle_tasks(app: AppRelease, build_type: str, flavors: tuple[str, ...]) -> tuple[str, ...]:
    if app.gradle_module is None:
        raise ValueError(f"{app.key} is not a Gradle module")
    suffix = build_type[:1].upper() + build_type[1:]
    if app.flavors:
        if not flavors:
            raise ValueError(f"{app.key}: pick at least one flavor")
        unknown = [flavor for flavor in flavors if flavor not in app.flavors]
        if unknown:
            raise ValueError(f"{app.key}: unknown flavors {unknown}")
        return tuple(
            f":{app.gradle_module}:assemble{flavor[:1].upper()}{flavor[1:]}{suffix}"
            for flavor in flavors
        )
    if flavors:
        raise ValueError(f"{app.key} has no language flavors")
    return (f":{app.gradle_module}:assemble{suffix}",)
