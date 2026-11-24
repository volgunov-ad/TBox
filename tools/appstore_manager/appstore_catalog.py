#!/usr/bin/env python3
"""
Логика каталога Dashing Store (без GUI).

Работает с локальной sync-папкой канала:
  <AppStore>/release/apps/  или  <AppStore>/dev/apps/

Файлы:
  catalog.meta.json  — описания для витрины (редактирует менеджер)
  catalog.json       — отдаёт Store APK (собирается из meta + version.json)
  <id>/version.json  — манифест релиза APK
  <id>/*.apk
  <id>/files/        — файлы карточки → Загрузки/<папка>/
"""

from __future__ import annotations

import hashlib
import json
import re
import shutil
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

APP_ID_RE = re.compile(r"^[a-z0-9][a-z0-9._-]{0,63}$")
SECTION_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,31}$")
META_NAME = "catalog.meta.json"
CATALOG_NAME = "catalog.json"
VERSION_NAME = "version.json"
FILES_DIR_NAME = "files"

# Path-segment forbidden characters (Windows + Android Downloads).
_FORBIDDEN_NAME_CHARS = set('\\/:*?"<>|\0') | {chr(c) for c in range(1, 32)}

HU_CHOICES: tuple[tuple[tuple[str, ...], str], ...] = (
    (("a9", "a10"), "A9 и A10"),
    (("a9",), "Только A9"),
    (("a10",), "Только A10"),
)

INSTALL_MODE_CHOICES: tuple[tuple[str, str], ...] = (
    ("hu", "На ГУ"),
    ("phone", "На телефон (QR и Загрузки)"),
)

_CATEGORY_SECTION_DEFS: tuple[tuple[str, str, str], ...] = (
    ("app", "Приложения", "Apps"),
    ("tool", "Инструменты", "Tools"),
    ("oneshot", "Утилиты", "Utilities"),
)


@dataclass
class Section:
    id: str
    title: str
    titleEn: str = ""

    def to_dict(self) -> dict[str, Any]:
        data: dict[str, Any] = {"id": self.id, "title": self.title}
        if self.titleEn:
            data["titleEn"] = self.titleEn
        return data


@dataclass
class DownloadFile:
    name: str
    sha256: str
    sizeBytes: int

    def to_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "sha256": self.sha256.lower(),
            "sizeBytes": int(self.sizeBytes),
        }


@dataclass
class AppMeta:
    id: str
    packageName: str
    title: str
    summary: str = ""
    publisher: str = "TBox"
    hu: list[str] = field(default_factory=lambda: ["a9", "a10"])
    icon: str = ""
    signingCertSha256: list[str] = field(default_factory=list)
    minStoreVersionCode: int | None = None
    sections: list[str] = field(default_factory=list)
    downloadsFolder: str = ""
    downloadFiles: list[DownloadFile] = field(default_factory=list)
    # hu (default) | phone — phone: QR + Downloads on HU, never install on HU.
    installMode: str = "hu"

    def to_meta_dict(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "id": self.id,
            "packageName": self.packageName,
            "title": self.title,
            "summary": self.summary,
            "publisher": self.publisher,
            "hu": list(self.hu),
        }
        if self.icon:
            data["icon"] = self.icon
        if self.signingCertSha256:
            data["signingCertSha256"] = list(self.signingCertSha256)
        if self.minStoreVersionCode is not None:
            data["minStoreVersionCode"] = int(self.minStoreVersionCode)
        if self.sections:
            data["sections"] = list(self.sections)
        folder = (self.downloadsFolder or "").strip()
        files = list(self.downloadFiles)
        if folder or files:
            downloads: dict[str, Any] = {"folder": folder, "files": [f.to_dict() for f in files]}
            data["downloads"] = downloads
        mode = (self.installMode or "hu").strip().lower()
        if mode and mode not in ("hu", "head-unit", "head_unit", "default", ""):
            data["installMode"] = mode
        return data

    def to_catalog_entry(
        self,
        icon_file_name: str | None = None,
        files: list[DownloadFile] | None = None,
        section_ids: list[str] | None = None,
    ) -> dict[str, Any]:
        entry: dict[str, Any] = {
            "id": self.id,
            "packageName": self.packageName,
            "title": self.title,
            "summary": self.summary,
            "publisher": self.publisher,
            "hu": list(self.hu),
            "manifest": f"yandex-disk:/apps/{self.id}/version.json",
        }
        if self.icon:
            entry["icon"] = self.icon
        elif icon_file_name:
            entry["icon"] = f"yandex-disk:/apps/{self.id}/{icon_file_name}"
        if self.signingCertSha256:
            entry["signingCertSha256"] = list(self.signingCertSha256)
        if self.minStoreVersionCode is not None:
            entry["minStoreVersionCode"] = int(self.minStoreVersionCode)
        mode = (self.installMode or "hu").strip().lower()
        if mode and mode not in ("hu", "head-unit", "head_unit", "default", ""):
            entry["installMode"] = mode
        secs = list(section_ids) if section_ids is not None else list(self.sections)
        if secs:
            entry["sections"] = secs
        publish_files = files if files is not None else list(self.downloadFiles)
        folder = (self.downloadsFolder or "").strip()
        if publish_files and folder:
            entry["downloads"] = {
                "folder": folder,
                "files": [f.to_dict() for f in publish_files],
            }
        return entry


@dataclass
class AppRelease:
    versionCode: int
    versionName: str
    apkFileName: str
    sha256: str
    flavor: str = "default"
    apkSizeBytes: int | None = None
    publishedAt: str = ""
    minSupportedVersionCode: int = 0


@dataclass
class AppRecord:
    meta: AppMeta
    release: AppRelease | None
    apk_path: Path | None
    folder: Path
    has_version_json: bool
    missing_package: bool = False
    releases: list[AppRelease] = field(default_factory=list)
    download_problems: list[str] = field(default_factory=list)


@dataclass
class MigrationReport:
    changed: bool = False
    removed_keys: dict[str, int] = field(default_factory=dict)
    category_sections: dict[str, list[str]] = field(default_factory=dict)
    unverified_ids: list[str] = field(default_factory=list)
    sidecar_files: dict[str, list[str]] = field(default_factory=dict)
    sidecar_missing: dict[str, str] = field(default_factory=dict)
    changelog_files: list[str] = field(default_factory=list)
    version_json_stripped: list[str] = field(default_factory=list)

    def summary_lines(self) -> list[str]:
        lines: list[str] = []
        if self.unverified_ids:
            ids = ", ".join(self.unverified_ids)
            lines.append(
                f"Были скрыты (Проверено = нет), теперь публикуются: {ids} — "
                f"удалите, если не нужны",
            )
        if self.removed_keys:
            parts = [f"{k}×{n}" for k, n in sorted(self.removed_keys.items())]
            lines.append("Удалены устаревшие поля: " + ", ".join(parts))
        if self.category_sections:
            for sid, app_ids in self.category_sections.items():
                lines.append(f"Раздел «{sid}»: {', '.join(app_ids)}")
        for app_id, names in self.sidecar_files.items():
            lines.append(
                f"Скопированы файлы в {app_id}/files/: {', '.join(names)}. "
                f"Это копии; после обновления источника прикрепите новый APK",
            )
        for app_id, target in self.sidecar_missing.items():
            lines.append(f"Не найдены APK источника «{target}» для карточки {app_id}")
        if self.changelog_files:
            lines.append("Changelog из version.json → " + ", ".join(self.changelog_files))
        if self.version_json_stripped:
            lines.append(
                "Убран ключ changelog из version.json: "
                + ", ".join(self.version_json_stripped),
            )
        if not lines and self.changed:
            lines.append("Каталог обновлён до нового формата")
        if not self.changed:
            lines.append("Миграция не требуется (уже новый формат)")
        return lines


def utc_now() -> str:
    return datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")


def load_json(path: Path) -> Any:
    return json.loads(path.read_text(encoding="utf-8"))


def save_json(path: Path, data: Any) -> None:
    path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while True:
            chunk = handle.read(1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    return digest.hexdigest()


def validate_app_id(app_id: str) -> str:
    value = app_id.strip().lower()
    if not APP_ID_RE.match(value):
        raise ValueError(
            "id: латиница/цифры/._-, начинается с буквы или цифры, до 64 символов",
        )
    return value


def normalize_hu(raw: Any) -> list[str]:
    """Keep only a9/a10 in that order; accept a comma string; empty → both."""
    if raw is None or raw == "":
        items: list[str] = []
    elif isinstance(raw, str):
        items = [x.strip() for x in raw.replace(";", ",").split(",") if x.strip()]
    else:
        items = [str(x).strip() for x in (raw or []) if str(x).strip()]
    seen: set[str] = set()
    out: list[str] = []
    for item in items:
        key = item.lower()
        if key in ("a9", "a10") and key not in seen:
            seen.add(key)
            out.append(key)
    if not out:
        return ["a9", "a10"]
    return sorted(out, key=lambda x: 0 if x == "a9" else 1)


def hu_label(hu: list[str] | tuple[str, ...] | None) -> str:
    normalized = tuple(normalize_hu(hu))
    for wire, label in HU_CHOICES:
        if tuple(wire) == normalized:
            return label
    return HU_CHOICES[0][1]


def hu_from_label(label: str) -> list[str]:
    text = (label or "").strip()
    for wire, known in HU_CHOICES:
        if text == known:
            return list(wire)
    # Also accept wire forms.
    if text.lower() in ("a9", "только a9"):
        return ["a9"]
    if text.lower() in ("a10", "только a10"):
        return ["a10"]
    return ["a9", "a10"]


def install_mode_label(mode: str | None) -> str:
    key = (mode or "hu").strip().lower() or "hu"
    if key in ("phone", "телефон"):
        return INSTALL_MODE_CHOICES[1][1]
    for wire, label in INSTALL_MODE_CHOICES:
        if wire == key:
            return label
    return INSTALL_MODE_CHOICES[0][1]


def install_mode_from_label(label: str) -> str:
    text = (label or "").strip()
    for wire, known in INSTALL_MODE_CHOICES:
        if text == known or text.lower() == wire:
            return wire
    if "телефон" in text.lower() or text.lower() == "phone":
        return "phone"
    return "hu"


def validate_downloads_folder(name: str) -> str:
    value = (name or "").strip()
    if not value:
        raise ValueError("Папка в Загрузках обязательна, когда есть файлы")
    if len(value) > 64:
        raise ValueError("Папка в Загрузках: не больше 64 символов")
    if value in (".", "..") or value.startswith(".") or value.endswith("."):
        raise ValueError("Папка в Загрузках: недопустимое имя")
    if any(ch in _FORBIDDEN_NAME_CHARS for ch in value):
        raise ValueError(
            'Папка в Загрузках: недопустимые символы \\ / : * ? " < > |',
        )
    return value


def validate_download_file_name(name: str) -> str:
    value = (name or "").strip()
    if not value or len(value) > 120:
        raise ValueError("Имя файла: 1–120 символов")
    if value.startswith("."):
        raise ValueError("Имя файла не должно начинаться с точки")
    if value.endswith(".") or value.endswith(" "):
        raise ValueError("Имя файла не должно оканчиваться точкой или пробелом")
    if value.lower().endswith(".part"):
        raise ValueError("Имя файла не должно оканчиваться на .part")
    if any(ch in _FORBIDDEN_NAME_CHARS for ch in value):
        raise ValueError(
            'Имя файла: недопустимые символы \\ / : * ? " < > |',
        )
    return value


def validate_section_id(section_id: str) -> str:
    value = (section_id or "").strip()
    if not SECTION_ID_RE.match(value):
        raise ValueError(
            "id раздела: латиница/цифры/_-, начинается с буквы или цифры, до 32 символов",
        )
    return value


def validate_section_title(title: str, *, en: bool = False) -> str:
    value = (title or "").strip()
    label = "Название EN" if en else "Название"
    if en:
        if len(value) > 40:
            raise ValueError(f"{label}: не больше 40 символов")
        return value
    if not value or len(value) > 40:
        raise ValueError(f"{label}: 1–40 символов")
    return value


def resolve_apps_dir(path: Path) -> Path:
    """
    Принимает путь к apps/, к release|dev/, или к корню AppStore.
    Если корень AppStore — предпочитает release/apps (создаёт при необходимости).
    """
    path = path.expanduser().resolve()
    if not path.exists():
        raise FileNotFoundError(f"Путь не найден: {path}")
    if not path.is_dir():
        raise NotADirectoryError(str(path))
    if path.name == "apps":
        return path
    if path.name in ("release", "dev"):
        apps = path / "apps"
        apps.mkdir(parents=True, exist_ok=True)
        return apps
    # AppStore root or arbitrary folder with release/dev
    release_apps = path / "release" / "apps"
    if release_apps.is_dir() or (path / "release").is_dir() or (path / "dev").is_dir():
        release_apps.mkdir(parents=True, exist_ok=True)
        (path / "dev" / "apps").mkdir(parents=True, exist_ok=True)
        return release_apps
    # Treat as apps dir itself
    return path


def list_channel_apps_dirs(appstore_root: Path) -> dict[str, Path]:
    root = appstore_root.expanduser().resolve()
    out: dict[str, Path] = {}
    for channel in ("release", "dev"):
        apps = root / channel / "apps"
        if apps.is_dir() or (root / channel).exists():
            apps.mkdir(parents=True, exist_ok=True)
            out[channel] = apps
    return out


def _download_file_from_dict(raw: dict[str, Any]) -> DownloadFile | None:
    name = str(raw.get("name", "")).strip()
    sha = str(raw.get("sha256", "")).strip().lower()
    size = raw.get("sizeBytes")
    if not name or not sha or size is None:
        return None
    try:
        size_i = int(size)
    except (TypeError, ValueError):
        return None
    return DownloadFile(name=name, sha256=sha, sizeBytes=size_i)


def _meta_from_dict(raw: dict[str, Any], fallback_id: str) -> AppMeta:
    app_id = str(raw.get("id", fallback_id)).strip() or fallback_id
    certs = raw.get("signingCertSha256") or []
    if isinstance(certs, str):
        certs = [certs]
    min_code = raw.get("minStoreVersionCode")
    sections_raw = raw.get("sections") or []
    if isinstance(sections_raw, str):
        sections = [s.strip() for s in sections_raw.split(",") if s.strip()]
    else:
        sections = [str(s).strip() for s in sections_raw if str(s).strip()]
    downloads_raw = raw.get("downloads") if isinstance(raw.get("downloads"), dict) else {}
    folder = str(downloads_raw.get("folder", "") if downloads_raw else "").strip()
    files_raw = downloads_raw.get("files") if downloads_raw else None
    download_files: list[DownloadFile] = []
    if isinstance(files_raw, list):
        for item in files_raw:
            if isinstance(item, dict):
                parsed = _download_file_from_dict(item)
                if parsed is not None:
                    download_files.append(parsed)
    return AppMeta(
        id=app_id,
        packageName=str(raw.get("packageName", "")).strip(),
        title=str(raw.get("title", "")).strip() or app_id,
        summary=str(raw.get("summary", "")),
        publisher=str(raw.get("publisher", "TBox")),
        hu=normalize_hu(raw.get("hu", ["a9", "a10"])),
        icon=str(raw.get("icon", "")),
        signingCertSha256=[str(c).lower() for c in certs if str(c).strip()],
        minStoreVersionCode=int(min_code) if min_code is not None and str(min_code) != "" else None,
        sections=sections,
        downloadsFolder=folder,
        downloadFiles=download_files,
        installMode=str(
            raw.get("installMode") or raw.get("distribution") or "hu",
        ).strip().lower() or "hu",
    )


def _section_from_dict(raw: dict[str, Any]) -> Section | None:
    sid = str(raw.get("id", "")).strip()
    title = str(raw.get("title", "")).strip()
    if not sid or not title:
        return None
    if not SECTION_ID_RE.match(sid):
        return None
    return Section(
        id=sid,
        title=title,
        titleEn=str(raw.get("titleEn", "") or "").strip(),
    )


def load_sections(apps_dir: Path) -> list[Section]:
    path = apps_dir / META_NAME
    if not path.is_file():
        return []
    raw = load_json(path)
    if not isinstance(raw, dict):
        return []
    items = raw.get("sections") or []
    if not isinstance(items, list):
        return []
    out: list[Section] = []
    seen: set[str] = set()
    for item in items:
        if not isinstance(item, dict):
            continue
        section = _section_from_dict(item)
        if section is None or section.id in seen:
            continue
        seen.add(section.id)
        out.append(section)
    return out


def load_meta_map(apps_dir: Path) -> dict[str, AppMeta]:
    path = apps_dir / META_NAME
    if not path.is_file():
        return {}
    raw = load_json(path)
    apps = raw.get("apps", []) if isinstance(raw, dict) else []
    result: dict[str, AppMeta] = {}
    for item in apps:
        if not isinstance(item, dict):
            continue
        app_id = str(item.get("id", "")).strip()
        if not app_id:
            continue
        result[app_id] = _meta_from_dict(item, app_id)
    return result


def save_meta_map(
    apps_dir: Path,
    metas: dict[str, AppMeta],
    sections: list[Section] | None = None,
) -> None:
    if sections is None:
        sections = load_sections(apps_dir)
    payload: dict[str, Any] = {}
    if sections:
        payload["sections"] = [s.to_dict() for s in sections]
    payload["apps"] = [metas[key].to_meta_dict() for key in sorted(metas)]
    save_json(apps_dir / META_NAME, payload)


def save_sections(apps_dir: Path, sections: list[Section]) -> None:
    metas = load_meta_map(apps_dir)
    defined = {s.id for s in sections}
    for meta in metas.values():
        meta.sections = [sid for sid in meta.sections if sid in defined]
    save_meta_map(apps_dir, metas, sections=sections)
    write_catalog(apps_dir)


def _next_section_id(existing: list[Section]) -> str:
    used = {s.id for s in existing}
    n = 1
    while f"s{n}" in used:
        n += 1
    return f"s{n}"


def _ensure_unique_title(
    sections: list[Section],
    title: str,
    *,
    exclude_id: str | None = None,
) -> None:
    needle = title.casefold()
    for section in sections:
        if exclude_id and section.id == exclude_id:
            continue
        if section.title.casefold() == needle:
            raise ValueError(f"Раздел с названием «{title}» уже есть")


def add_section(apps_dir: Path, title: str, title_en: str = "") -> Section:
    title_v = validate_section_title(title)
    title_en_v = validate_section_title(title_en, en=True)
    sections = load_sections(apps_dir)
    _ensure_unique_title(sections, title_v)
    section = Section(id=_next_section_id(sections), title=title_v, titleEn=title_en_v)
    sections.append(section)
    save_sections(apps_dir, sections)
    return section


def update_section(
    apps_dir: Path,
    section_id: str,
    title: str,
    title_en: str = "",
) -> Section:
    section_id = validate_section_id(section_id)
    title_v = validate_section_title(title)
    title_en_v = validate_section_title(title_en, en=True)
    sections = load_sections(apps_dir)
    _ensure_unique_title(sections, title_v, exclude_id=section_id)
    found: Section | None = None
    for section in sections:
        if section.id == section_id:
            section.title = title_v
            section.titleEn = title_en_v
            found = section
            break
    if found is None:
        raise KeyError(f"Раздел не найден: {section_id}")
    save_sections(apps_dir, sections)
    return found


def delete_section(apps_dir: Path, section_id: str) -> int:
    section_id = validate_section_id(section_id)
    sections = load_sections(apps_dir)
    before = len(sections)
    sections = [s for s in sections if s.id != section_id]
    if len(sections) == before:
        raise KeyError(f"Раздел не найден: {section_id}")
    metas = load_meta_map(apps_dir)
    detached = 0
    for meta in metas.values():
        if section_id in meta.sections:
            meta.sections = [sid for sid in meta.sections if sid != section_id]
            detached += 1
    save_meta_map(apps_dir, metas, sections=sections)
    write_catalog(apps_dir)
    return detached


def move_section(apps_dir: Path, section_id: str, delta: int) -> None:
    section_id = validate_section_id(section_id)
    sections = load_sections(apps_dir)
    index = next((i for i, s in enumerate(sections) if s.id == section_id), None)
    if index is None:
        raise KeyError(f"Раздел не найден: {section_id}")
    new_index = index + int(delta)
    if new_index < 0 or new_index >= len(sections):
        return
    sections[index], sections[new_index] = sections[new_index], sections[index]
    save_sections(apps_dir, sections)


def files_dir(apps_dir: Path, app_id: str) -> Path:
    return apps_dir / validate_app_id(app_id) / FILES_DIR_NAME


def add_download_files(
    apps_dir: Path,
    app_id: str,
    sources: list[Path] | tuple[Path, ...],
    replace: bool = False,
) -> list[DownloadFile]:
    app_id = validate_app_id(app_id)
    metas = load_meta_map(apps_dir)
    meta = metas.get(app_id) or AppMeta(id=app_id, packageName="", title=app_id)
    dest_dir = files_dir(apps_dir, app_id)
    dest_dir.mkdir(parents=True, exist_ok=True)

    incoming: list[tuple[Path, str]] = []
    for source in sources:
        src = Path(source).expanduser().resolve()
        if not src.is_file():
            raise FileNotFoundError(f"Файл не найден: {src}")
        name = validate_download_file_name(src.name)
        incoming.append((src, name))

    existing_by_lower = {f.name.casefold(): f for f in meta.downloadFiles}
    clashes = [
        name
        for _, name in incoming
        if name.casefold() in existing_by_lower and not replace
    ]
    # Also clash against other incoming names case-insensitively.
    seen_in: set[str] = set()
    for _, name in incoming:
        key = name.casefold()
        if key in seen_in:
            clashes.append(name)
        seen_in.add(key)
    if clashes and not replace:
        unique = sorted(set(clashes), key=str.casefold)
        raise FileExistsError(", ".join(unique))

    added: list[DownloadFile] = []
    for src, name in incoming:
        dest = dest_dir / name
        # Drop previous entry with same case-insensitive name.
        meta.downloadFiles = [
            f for f in meta.downloadFiles if f.name.casefold() != name.casefold()
        ]
        if dest.resolve() != src:
            shutil.copy2(src, dest)
        digest = sha256_file(dest)
        entry = DownloadFile(name=name, sha256=digest, sizeBytes=dest.stat().st_size)
        meta.downloadFiles.append(entry)
        added.append(entry)

    if not meta.downloadsFolder.strip():
        try:
            meta.downloadsFolder = validate_downloads_folder(meta.title or app_id)
        except ValueError:
            meta.downloadsFolder = app_id

    metas[app_id] = meta
    (apps_dir / app_id).mkdir(parents=True, exist_ok=True)
    save_meta_map(apps_dir, metas)
    write_catalog(apps_dir)
    return added


def remove_download_files(apps_dir: Path, app_id: str, names: list[str]) -> None:
    app_id = validate_app_id(app_id)
    metas = load_meta_map(apps_dir)
    meta = metas.get(app_id)
    if meta is None:
        raise KeyError(f"Приложение не найдено: {app_id}")
    drop = {n.casefold() for n in names if n.strip()}
    if not drop:
        return
    dest_dir = files_dir(apps_dir, app_id)
    kept: list[DownloadFile] = []
    for entry in meta.downloadFiles:
        if entry.name.casefold() in drop:
            path = dest_dir / entry.name
            if path.is_file():
                path.unlink(missing_ok=True)
        else:
            kept.append(entry)
    meta.downloadFiles = kept
    if not meta.downloadFiles and not meta.downloadsFolder.strip():
        meta.downloadsFolder = ""
    metas[app_id] = meta
    save_meta_map(apps_dir, metas)
    write_catalog(apps_dir)


def _verified_download_files(record: AppRecord) -> tuple[list[DownloadFile], list[str]]:
    """Return (publishable files, problem messages)."""
    problems: list[str] = []
    ok: list[DownloadFile] = []
    folder = record.folder / FILES_DIR_NAME
    for entry in record.meta.downloadFiles:
        path = folder / entry.name
        if not path.is_file():
            problems.append(f"нет файла {entry.name}")
            continue
        size = path.stat().st_size
        if entry.sizeBytes and size != int(entry.sizeBytes):
            problems.append(f"файл {entry.name} изменён")
            continue
        ok.append(entry)
    return ok, problems


def detect_flavor_from_filename(name: str) -> str | None:
    """
    Guess ru/en from APK file name (…-ru-…, …_en.apk, store-0.1.0-ru-debug.apk).
    Returns None when no language token is found → caller uses default/explicit.
    """
    stem = Path(name).stem.lower()
    # Prefer explicit language tokens bounded by separators.
    match = re.search(r"(?:^|[._\-])(ru|en)(?:[._\-]|$)", stem)
    if match:
        return match.group(1)
    return None


def _release_from_dict(item: dict[str, Any]) -> AppRelease | None:
    version_code = int(item.get("versionCode", -1))
    version_name = str(item.get("versionName", "")).strip()
    apk_name = str(item.get("apkFileName", "")).strip()
    sha256 = str(item.get("sha256", "")).strip().lower()
    if version_code < 0 or not version_name or not apk_name or not sha256:
        return None
    size = item.get("apkSizeBytes")
    return AppRelease(
        versionCode=version_code,
        versionName=version_name,
        apkFileName=apk_name,
        sha256=sha256,
        flavor=str(item.get("flavor", "default") or "default"),
        apkSizeBytes=int(size) if size else None,
        publishedAt=str(item.get("publishedAt", "")),
        minSupportedVersionCode=int(item.get("minSupportedVersionCode", 0) or 0),
    )


def parse_all_releases(version_path: Path) -> list[AppRelease]:
    if not version_path.is_file():
        return []
    raw = load_json(version_path)
    releases = raw.get("releases") if isinstance(raw, dict) else None
    if not isinstance(releases, list) or not releases:
        return []
    out: list[AppRelease] = []
    for item in releases:
        if not isinstance(item, dict):
            continue
        parsed = _release_from_dict(item)
        if parsed is not None:
            out.append(parsed)
    return out


def parse_release(version_path: Path) -> AppRelease | None:
    releases = parse_all_releases(version_path)
    if not releases:
        return None
    for item in releases:
        if item.flavor.lower() in ("default", "all"):
            return item
    # Prefer ru when both language flavors exist (common first-party default).
    for item in releases:
        if item.flavor.lower() == "ru":
            return item
    return releases[0]


def _release_to_dict(release: AppRelease) -> dict[str, Any]:
    data: dict[str, Any] = {
        "versionCode": int(release.versionCode),
        "versionName": release.versionName,
        "flavor": release.flavor or "default",
        "apkFileName": release.apkFileName,
        "sha256": release.sha256.lower(),
        "publishedAt": release.publishedAt or utc_now(),
        "minSupportedVersionCode": int(release.minSupportedVersionCode or 0),
    }
    size = int(release.apkSizeBytes or 0) or None
    if size is not None:
        data["apkSizeBytes"] = size
    return data


def write_version_json(folder: Path, release: AppRelease) -> None:
    """Replace the whole version.json with a single release (legacy helper)."""
    save_json(folder / VERSION_NAME, {"schemaVersion": 1, "releases": [_release_to_dict(release)]})


def upsert_release(folder: Path, release: AppRelease) -> list[AppRelease]:
    """Insert/replace one flavor entry; keep other flavors' releases."""
    existing = parse_all_releases(folder / VERSION_NAME)
    flavor_key = (release.flavor or "default").lower()
    kept = [r for r in existing if r.flavor.lower() != flavor_key]
    kept.append(release)
    # Stable order: default, ru, en, then others alpha
    order = {"default": 0, "all": 0, "ru": 1, "en": 2}

    def sort_key(item: AppRelease) -> tuple:
        return (order.get(item.flavor.lower(), 9), item.flavor.lower(), -item.versionCode)

    kept.sort(key=sort_key)
    save_json(
        folder / VERSION_NAME,
        {"schemaVersion": 1, "releases": [_release_to_dict(r) for r in kept]},
    )
    return kept


def list_apks(folder: Path) -> list[Path]:
    if not folder.is_dir():
        return []
    return sorted(
        p
        for p in folder.iterdir()
        if p.is_file() and p.suffix.lower() == ".apk"
    )


ICON_NAMES = ("icon.png", "icon.webp", "icon.jpg", "icon.jpeg")
CHANGELOG_RU_NAME = "changelog.ru.txt"
CHANGELOG_EN_NAME = "changelog.en.txt"


def changelog_file_name(lang: str) -> str:
    return CHANGELOG_EN_NAME if str(lang).lower() == "en" else CHANGELOG_RU_NAME


def find_changelog_file(folder: Path, lang: str) -> Path | None:
    path = folder / changelog_file_name(lang)
    return path if path.is_file() else None


def read_changelog(folder: Path, lang: str) -> str:
    path = find_changelog_file(folder, lang)
    if path is None:
        return ""
    return path.read_text(encoding="utf-8")


def write_changelog(folder: Path, lang: str, text: str) -> Path:
    folder.mkdir(parents=True, exist_ok=True)
    path = folder / changelog_file_name(lang)
    path.write_text(text.replace("\r\n", "\n").rstrip() + ("\n" if text.strip() else ""), encoding="utf-8")
    return path


def find_icon_file(folder: Path) -> Path | None:
    if not folder.is_dir():
        return None
    for name in ICON_NAMES:
        candidate = folder / name
        if candidate.is_file():
            return candidate
    return None


def scan_apps(apps_dir: Path) -> list[AppRecord]:
    apps_dir = apps_dir.resolve()
    meta_map = load_meta_map(apps_dir)
    ids: set[str] = set(meta_map)
    if apps_dir.is_dir():
        for child in apps_dir.iterdir():
            if child.is_dir() and not child.name.startswith("."):
                ids.add(child.name)
    records: list[AppRecord] = []
    for app_id in sorted(ids):
        folder = apps_dir / app_id
        folder.mkdir(parents=True, exist_ok=True)
        meta = meta_map.get(app_id) or AppMeta(
            id=app_id,
            packageName="",
            title=app_id,
        )
        releases = parse_all_releases(folder / VERSION_NAME)
        release = parse_release(folder / VERSION_NAME)
        apk_path = None
        if release is not None:
            candidate = folder / release.apkFileName
            if candidate.is_file():
                apk_path = candidate
        if apk_path is None:
            apks = list_apks(folder)
            apk_path = apks[0] if apks else None
        record = AppRecord(
            meta=meta,
            release=release,
            apk_path=apk_path,
            folder=folder,
            has_version_json=(folder / VERSION_NAME).is_file(),
            missing_package=not bool(meta.packageName.strip()),
            releases=releases,
        )
        _, problems = _verified_download_files(record)
        record.download_problems = problems
        records.append(record)
    return records


def build_catalog(apps_dir: Path) -> dict[str, Any]:
    records = scan_apps(apps_dir)
    sections = load_sections(apps_dir)
    defined = {s.id for s in sections}
    apps: list[dict[str, Any]] = []
    for record in records:
        if record.missing_package:
            continue
        if not record.has_version_json:
            continue
        icon = find_icon_file(record.folder)
        publish_files, _ = _verified_download_files(record)
        filtered_sections = [sid for sid in record.meta.sections if sid in defined]
        apps.append(
            record.meta.to_catalog_entry(
                icon_file_name=icon.name if icon is not None else None,
                files=publish_files,
                section_ids=filtered_sections,
            ),
        )
    result: dict[str, Any] = {
        "schemaVersion": 1,
        "publishedAt": utc_now(),
    }
    if sections:
        result["sections"] = [s.to_dict() for s in sections]
    result["apps"] = apps
    return result


def write_catalog(apps_dir: Path) -> Path:
    out = apps_dir / CATALOG_NAME
    save_json(out, build_catalog(apps_dir))
    return out


def upsert_meta(apps_dir: Path, meta: AppMeta) -> None:
    meta.id = validate_app_id(meta.id)
    if not meta.packageName.strip():
        raise ValueError("packageName обязателен")
    if not meta.title.strip():
        meta.title = meta.id
    folder = (meta.downloadsFolder or "").strip()
    if meta.downloadFiles:
        meta.downloadsFolder = validate_downloads_folder(folder)
    elif folder:
        meta.downloadsFolder = validate_downloads_folder(folder)
    else:
        meta.downloadsFolder = ""
    sections = load_sections(apps_dir)
    defined = {s.id for s in sections}
    meta.sections = [sid for sid in meta.sections if sid in defined]
    meta.hu = normalize_hu(meta.hu)
    metas = load_meta_map(apps_dir)
    # Preserve downloadFiles when caller didn't carry them (shouldn't happen with deepcopy).
    existing = metas.get(meta.id)
    if existing is not None and not meta.downloadFiles and existing.downloadFiles:
        # Only keep existing files when the new meta explicitly has an empty list
        # that came from a form that forgot them — caller should pass them.
        pass
    metas[meta.id] = meta
    (apps_dir / meta.id).mkdir(parents=True, exist_ok=True)
    save_meta_map(apps_dir, metas, sections=sections)


def add_app(
    apps_dir: Path,
    meta: AppMeta,
    apk_source: Path | None = None,
    version_code: int | None = None,
    version_name: str | None = None,
    flavor: str | None = None,
    remove_other_apks: bool = True,
) -> AppRecord:
    meta.id = validate_app_id(meta.id)
    folder = apps_dir / meta.id
    if folder.exists() and any(folder.iterdir()):
        # Allow re-add only if empty-ish; otherwise require explicit update
        if (folder / VERSION_NAME).exists() or list_apks(folder):
            raise FileExistsError(f"Приложение уже есть: {meta.id}")
    upsert_meta(apps_dir, meta)
    if apk_source is not None:
        set_apk(
            apps_dir,
            meta.id,
            apk_source,
            version_code=version_code,
            version_name=version_name,
            flavor=flavor,
            remove_other_apks=remove_other_apks,
        )
    write_catalog(apps_dir)
    return next(r for r in scan_apps(apps_dir) if r.meta.id == meta.id)


class CertPinMismatchError(ValueError):
    """Raised when APK signer differs from catalog signingCertSha256 pin."""

    def __init__(self, existing: list[str], actual: str) -> None:
        self.existing = list(existing)
        self.actual = actual
        pinned = ", ".join(x[:16] + "…" for x in existing) or "(пусто)"
        super().__init__(
            f"Подпись APK ({actual[:16]}…) не совпадает с pin в каталоге ({pinned}). "
            f"Смена ключа — только осознанно (allow_cert_change).",
        )


def set_apk(
    apps_dir: Path,
    app_id: str,
    apk_source: Path,
    version_code: int | None = None,
    version_name: str | None = None,
    flavor: str | None = None,
    remove_other_apks: bool = True,
    min_supported_version_code: int = 0,
    sync_package_from_apk: bool = True,
    sync_cert_from_apk: bool = True,
    allow_cert_change: bool = False,
    extract_icon_from_apk: bool = True,
) -> AppRelease:
    """
    Copy APK into apps/<id>/ and upsert its flavor into version.json.

    versionCode / versionName default to values from the APK manifest
    (aapt or binary AndroidManifest.xml). Pass them explicitly only to override.

    flavor defaults to ru/en guessed from the file name, else \"default\".
    Other flavors' APKs and version.json entries are kept. When
    remove_other_apks is True, only the previous APK for the same flavor is removed.

    Also (by default): pin signingCertSha256 from the APK signer and extract a
    launcher icon into apps/<id>/icon.png when a raster icon exists.
    """
    from apk_manifest import ApkInfo, extract_apk_icon, read_apk_info

    app_id = validate_app_id(app_id)
    apk_source = apk_source.expanduser().resolve()
    if not apk_source.is_file():
        raise FileNotFoundError(f"APK не найден: {apk_source}")
    if apk_source.suffix.lower() != ".apk":
        raise ValueError("Нужен файл .apk")

    info: ApkInfo | None
    try:
        info = read_apk_info(apk_source)
    except Exception as exc:
        # Allow explicit version override only when manifest cannot be read
        # (e.g. unit tests with a stub file). GUI always uses a real APK.
        if version_code is None or not version_name:
            raise ValueError(f"Не удалось прочитать манифест APK: {exc}") from exc
        info = None

    if info is not None:
        if version_code is None:
            version_code = info.versionCode
        if not version_name:
            version_name = info.versionName
    if int(version_code or 0) <= 0:
        raise ValueError("versionCode должен быть > 0")
    if not str(version_name or "").strip():
        raise ValueError("versionName пустой")

    resolved_flavor = (flavor or detect_flavor_from_filename(apk_source.name) or "default").strip()
    if not resolved_flavor:
        resolved_flavor = "default"

    folder = apps_dir / app_id
    folder.mkdir(parents=True, exist_ok=True)

    dest_name = apk_source.name
    dest = folder / dest_name
    if dest.resolve() != apk_source:
        shutil.copy2(apk_source, dest)

    previous_same_flavor = [
        r
        for r in parse_all_releases(folder / VERSION_NAME)
        if r.flavor.lower() == resolved_flavor.lower()
    ]
    if remove_other_apks:
        # Only remove the previous APK for this flavor; keep ru↔en siblings.
        for prev in previous_same_flavor:
            old = folder / prev.apkFileName
            if old.is_file() and old.resolve() != dest.resolve():
                old.unlink(missing_ok=True)

    digest = sha256_file(dest)
    release = AppRelease(
        versionCode=int(version_code),
        versionName=str(version_name),
        apkFileName=dest_name,
        sha256=digest,
        flavor=resolved_flavor,
        apkSizeBytes=dest.stat().st_size,
        publishedAt=utc_now(),
        minSupportedVersionCode=int(min_supported_version_code or 0),
    )
    upsert_release(folder, release)

    # Ensure meta exists; keep packageName / cert pin in sync with the APK when asked.
    metas = load_meta_map(apps_dir)
    meta = metas.get(app_id) or AppMeta(id=app_id, packageName="", title=app_id)
    if sync_package_from_apk and info is not None and info.packageName:
        existing_pkg = meta.packageName.strip()
        if existing_pkg and existing_pkg != info.packageName:
            raise ValueError(
                f"packageName в каталоге ({existing_pkg}) не совпадает с APK "
                f"({info.packageName})",
            )
        if not existing_pkg:
            meta.packageName = info.packageName
        if (not meta.title.strip() or meta.title == app_id) and info.label:
            meta.title = info.label

    if sync_cert_from_apk and info is not None and info.signingCertSha256:
        cert = info.signingCertSha256.lower()
        existing_certs = [c.lower() for c in meta.signingCertSha256 if c.strip()]
        if not existing_certs:
            meta.signingCertSha256 = [cert]
        elif cert not in existing_certs:
            if not allow_cert_change:
                raise CertPinMismatchError(existing_certs, cert)
            meta.signingCertSha256 = [cert]
        # else: already pinned — keep the list (may include historical alts)

    if extract_icon_from_apk:
        try:
            icon_path = extract_apk_icon(dest, folder)
        except Exception:
            icon_path = None
        if icon_path is not None:
            meta.icon = f"yandex-disk:/apps/{app_id}/{icon_path.name}"

    metas[app_id] = meta
    save_meta_map(apps_dir, metas)

    write_catalog(apps_dir)
    return release


def set_icon(apps_dir: Path, app_id: str, icon_source: Path) -> Path:
    """Copy image as apps/<id>/icon.<ext> and refresh catalog / meta.icon."""
    app_id = validate_app_id(app_id)
    icon_source = icon_source.expanduser().resolve()
    if not icon_source.is_file():
        raise FileNotFoundError(f"Иконка не найдена: {icon_source}")
    ext = icon_source.suffix.lower()
    if ext not in (".png", ".webp", ".jpg", ".jpeg"):
        raise ValueError("Иконка: PNG / WebP / JPEG")
    folder = apps_dir / app_id
    folder.mkdir(parents=True, exist_ok=True)
    # Normalize to icon.png / icon.webp / icon.jpg
    dest_name = "icon.jpg" if ext == ".jpeg" else f"icon{ext}"
    dest = folder / dest_name
    if dest.resolve() != icon_source:
        shutil.copy2(icon_source, dest)
    for old in ICON_NAMES:
        old_path = folder / old
        if old_path.resolve() != dest.resolve() and old_path.is_file():
            old_path.unlink(missing_ok=True)
    metas = load_meta_map(apps_dir)
    meta = metas.get(app_id) or AppMeta(id=app_id, packageName="", title=app_id)
    meta.icon = f"yandex-disk:/apps/{app_id}/{dest_name}"
    metas[app_id] = meta
    save_meta_map(apps_dir, metas)
    write_catalog(apps_dir)
    return dest


def delete_app(apps_dir: Path, app_id: str, remove_files: bool = True) -> None:
    app_id = validate_app_id(app_id)
    metas = load_meta_map(apps_dir)
    metas.pop(app_id, None)
    save_meta_map(apps_dir, metas)
    folder = apps_dir / app_id
    if remove_files and folder.is_dir():
        shutil.rmtree(folder)
    write_catalog(apps_dir)


def _normalize_legacy_category(raw: Any) -> str:
    value = str(raw or "app").strip().lower().replace("-", "_")
    if value in ("tool", "tools"):
        return "tool"
    if value in ("oneshot", "one_shot", "one-shot"):
        return "oneshot"
    return "app"


def migrate_legacy_catalog(apps_dir: Path) -> MigrationReport:
    """
    One-time / idempotent migration of catalog.meta.json + version.json
    to the new sections / downloads / no-changelog shape.
    """
    report = MigrationReport()
    apps_dir = apps_dir.resolve()
    apps_dir.mkdir(parents=True, exist_ok=True)
    meta_path = apps_dir / META_NAME

    raw_apps: list[dict[str, Any]] = []
    raw_sections: list[dict[str, Any]] = []
    if meta_path.is_file():
        raw = load_json(meta_path)
        if isinstance(raw, dict):
            raw_apps = [a for a in (raw.get("apps") or []) if isinstance(a, dict)]
            raw_sections = [s for s in (raw.get("sections") or []) if isinstance(s, dict)]

    legacy_keys = (
        "origin",
        "verified",
        "reviewNotes",
        "category",
        "copyApkToDownloadsAppId",
        "downloadsSidecarAppId",
    )
    used_categories: dict[str, list[str]] = {}
    sidecars: list[tuple[str, str]] = []
    meta_changed = False
    cleaned_apps: list[dict[str, Any]] = []

    for item in raw_apps:
        app_id = str(item.get("id", "")).strip()
        if not app_id:
            continue
        item = dict(item)

        if item.get("verified") is False:
            report.unverified_ids.append(app_id)

        has_sections = bool(item.get("sections"))
        if not has_sections and "category" in item:
            cat = _normalize_legacy_category(item.get("category"))
            item["sections"] = [cat]
            used_categories.setdefault(cat, []).append(app_id)
            meta_changed = True

        target = str(
            item.get("copyApkToDownloadsAppId") or item.get("downloadsSidecarAppId") or "",
        ).strip()
        if target:
            sidecars.append((app_id, target))

        for key in legacy_keys:
            if key in item:
                item.pop(key, None)
                report.removed_keys[key] = report.removed_keys.get(key, 0) + 1
                meta_changed = True

        cleaned_apps.append(item)

    # Build sections list: keep existing, append missing category sections.
    sections: list[Section] = []
    seen_ids: set[str] = set()
    for raw_sec in raw_sections:
        section = _section_from_dict(raw_sec)
        if section is None or section.id in seen_ids:
            continue
        seen_ids.add(section.id)
        sections.append(section)

    for sid, title, title_en in _CATEGORY_SECTION_DEFS:
        if sid in used_categories and sid not in seen_ids:
            sections.append(Section(id=sid, title=title, titleEn=title_en))
            seen_ids.add(sid)
            meta_changed = True
            report.category_sections[sid] = list(used_categories[sid])
        elif sid in used_categories:
            report.category_sections[sid] = list(used_categories[sid])

    if meta_changed:
        payload: dict[str, Any] = {}
        if sections:
            payload["sections"] = [s.to_dict() for s in sections]
        payload["apps"] = cleaned_apps
        save_json(meta_path, payload)
        report.changed = True

    # Sidecar → card files
    metas = load_meta_map(apps_dir)
    for app_id, target_id in sidecars:
        meta = metas.get(app_id)
        if meta is None:
            continue
        if meta.downloadFiles:
            continue
        target_folder = apps_dir / target_id
        target_releases = parse_all_releases(target_folder / VERSION_NAME)
        sources: list[Path] = []
        for rel in target_releases:
            apk = target_folder / rel.apkFileName
            if apk.is_file():
                sources.append(apk)
        if not sources:
            report.sidecar_missing[app_id] = target_id
            report.changed = True
            continue
        target_meta = metas.get(target_id)
        folder_name = (target_meta.title if target_meta else "") or target_id
        try:
            folder_name = validate_downloads_folder(folder_name)
        except ValueError:
            folder_name = target_id
        meta.downloadsFolder = folder_name
        metas[app_id] = meta
        save_meta_map(apps_dir, metas)
        added = add_download_files(apps_dir, app_id, sources, replace=True)
        report.sidecar_files[app_id] = [f.name for f in added]
        report.changed = True
        metas = load_meta_map(apps_dir)

    # version.json changelog → changelog.<lang>.txt
    if apps_dir.is_dir():
        for child in sorted(apps_dir.iterdir()):
            if not child.is_dir() or child.name.startswith("."):
                continue
            version_path = child / VERSION_NAME
            if not version_path.is_file():
                continue
            raw = load_json(version_path)
            if not isinstance(raw, dict):
                continue
            releases = raw.get("releases")
            if not isinstance(releases, list):
                continue
            had_changelog_key = False
            written_langs: set[str] = set()
            new_releases: list[dict[str, Any]] = []
            for item in releases:
                if not isinstance(item, dict):
                    continue
                item = dict(item)
                if "changelog" in item:
                    had_changelog_key = True
                    text = str(item.pop("changelog") or "").strip()
                    if text:
                        flavor = str(item.get("flavor", "default") or "default").lower()
                        lang = "en" if flavor == "en" else "ru"
                        if lang not in written_langs:
                            existing = read_changelog(child, lang).strip()
                            if not existing:
                                path = write_changelog(child, lang, text)
                                report.changelog_files.append(
                                    f"{child.name}/{path.name}",
                                )
                                report.changed = True
                            written_langs.add(lang)
                new_releases.append(item)
            if had_changelog_key:
                save_json(
                    version_path,
                    {"schemaVersion": int(raw.get("schemaVersion", 1) or 1), "releases": new_releases},
                )
                report.version_json_stripped.append(child.name)
                report.changed = True

    if report.changed:
        write_catalog(apps_dir)

    return report


def _default_seed_path() -> Path:
    return (
        Path(__file__).resolve().parents[2]
        / "store"
        / "src"
        / "test"
        / "resources"
        / "sample_catalog.meta.json"
    )


def ensure_seed_meta(apps_dir: Path, seed_path: Path | None = None) -> bool:
    """
    Ensure catalog.meta.json exists and contains all seed apps.

    - Missing file → copy full seed (or empty map).
    - Existing file → merge in any seed ids that are absent (e.g. new tbox-store),
      without overwriting curated fields.
    Returns True if the meta file was created or changed.
    """
    target = apps_dir / META_NAME
    if seed_path is None:
        seed_path = _default_seed_path()

    if not target.is_file():
        if seed_path.is_file():
            shutil.copy2(seed_path, target)
            return True
        save_meta_map(apps_dir, {})
        return True

    if not seed_path.is_file():
        return False

    seed_raw = load_json(seed_path)
    seed_apps = seed_raw.get("apps", []) if isinstance(seed_raw, dict) else []
    current = load_meta_map(apps_dir)
    sections = load_sections(apps_dir)
    # Also seed sections if the seed defines them and current has none.
    if not sections and isinstance(seed_raw, dict):
        for item in seed_raw.get("sections") or []:
            if isinstance(item, dict):
                section = _section_from_dict(item)
                if section is not None:
                    sections.append(section)
    added = False
    for item in seed_apps:
        if not isinstance(item, dict):
            continue
        app_id = str(item.get("id", "")).strip()
        if not app_id or app_id in current:
            continue
        current[app_id] = _meta_from_dict(item, app_id)
        (apps_dir / app_id).mkdir(parents=True, exist_ok=True)
        added = True
    if added:
        save_meta_map(apps_dir, current, sections=sections or None)
    return added
