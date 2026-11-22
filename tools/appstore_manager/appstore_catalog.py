#!/usr/bin/env python3
"""
Логика каталога TBox Store (без GUI).

Работает с локальной sync-папкой канала:
  <AppStore>/release/apps/  или  <AppStore>/dev/apps/

Файлы:
  catalog.meta.json  — описания для витрины (редактирует менеджер)
  catalog.json       — отдаёт Store APK (собирается из meta + version.json)
  <id>/version.json  — манифест релиза APK
  <id>/*.apk
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
META_NAME = "catalog.meta.json"
CATALOG_NAME = "catalog.json"
VERSION_NAME = "version.json"


@dataclass
class AppMeta:
    id: str
    packageName: str
    title: str
    summary: str = ""
    publisher: str = "TBox"
    origin: str = "first-party"
    hu: list[str] = field(default_factory=lambda: ["a9", "a10"])
    category: str = "app"
    verified: bool = True
    icon: str = ""
    signingCertSha256: list[str] = field(default_factory=list)
    minStoreVersionCode: int | None = None
    reviewNotes: str = ""
    # When set (e.g. dclinstall → dcl-launcher), Store also saves that app's APK
    # into public Downloads after download (for tools that pick an APK from storage).
    copyApkToDownloadsAppId: str = ""

    def to_meta_dict(self) -> dict[str, Any]:
        data: dict[str, Any] = {
            "id": self.id,
            "packageName": self.packageName,
            "title": self.title,
            "summary": self.summary,
            "publisher": self.publisher,
            "origin": self.origin,
            "hu": list(self.hu),
            "category": self.category,
            "verified": bool(self.verified),
        }
        if self.icon:
            data["icon"] = self.icon
        if self.signingCertSha256:
            data["signingCertSha256"] = list(self.signingCertSha256)
        if self.minStoreVersionCode is not None:
            data["minStoreVersionCode"] = int(self.minStoreVersionCode)
        if self.reviewNotes:
            data["reviewNotes"] = self.reviewNotes
        if self.copyApkToDownloadsAppId:
            data["copyApkToDownloadsAppId"] = self.copyApkToDownloadsAppId
        return data

    def to_catalog_entry(self, icon_file_name: str | None = None) -> dict[str, Any]:
        entry = self.to_meta_dict()
        entry["manifest"] = f"yandex-disk:/apps/{self.id}/version.json"
        if self.icon:
            entry["icon"] = self.icon
        elif icon_file_name:
            entry["icon"] = f"yandex-disk:/apps/{self.id}/{icon_file_name}"
        return entry


@dataclass
class AppRelease:
    versionCode: int
    versionName: str
    apkFileName: str
    sha256: str
    flavor: str = "default"
    apkSizeBytes: int | None = None
    changelog: str = ""
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


def _meta_from_dict(raw: dict[str, Any], fallback_id: str) -> AppMeta:
    app_id = str(raw.get("id", fallback_id)).strip() or fallback_id
    hu_raw = raw.get("hu", ["a9", "a10"])
    if isinstance(hu_raw, str):
        hu = [hu_raw]
    else:
        hu = [str(x) for x in (hu_raw or ["a9", "a10"])]
    certs = raw.get("signingCertSha256") or []
    if isinstance(certs, str):
        certs = [certs]
    min_code = raw.get("minStoreVersionCode")
    return AppMeta(
        id=app_id,
        packageName=str(raw.get("packageName", "")).strip(),
        title=str(raw.get("title", "")).strip() or app_id,
        summary=str(raw.get("summary", "")),
        publisher=str(raw.get("publisher", "TBox")),
        origin=str(raw.get("origin", "first-party")),
        hu=hu,
        category=str(raw.get("category", "app")),
        verified=bool(raw.get("verified", True)),
        icon=str(raw.get("icon", "")),
        signingCertSha256=[str(c).lower() for c in certs if str(c).strip()],
        minStoreVersionCode=int(min_code) if min_code is not None and str(min_code) != "" else None,
        reviewNotes=str(raw.get("reviewNotes", "")),
        copyApkToDownloadsAppId=str(
            raw.get("copyApkToDownloadsAppId") or raw.get("downloadsSidecarAppId") or "",
        ).strip(),
    )


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


def save_meta_map(apps_dir: Path, metas: dict[str, AppMeta]) -> None:
    ordered = [metas[key].to_meta_dict() for key in sorted(metas)]
    save_json(apps_dir / META_NAME, {"apps": ordered})


def detect_flavor_from_filename(name: str) -> str | None:
    """
    Guess ru/en from APK file name (…-ru-…, …_en.apk, tbox-store-0.1.0-ru-debug.apk).
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
        changelog=str(item.get("changelog", "")),
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
        "changelog": release.changelog,
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
    return sorted(p for p in folder.iterdir() if p.is_file() and p.suffix.lower() == ".apk")


ICON_NAMES = ("icon.png", "icon.webp", "icon.jpg", "icon.jpeg")


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
        records.append(
            AppRecord(
                meta=meta,
                release=release,
                apk_path=apk_path,
                folder=folder,
                has_version_json=(folder / VERSION_NAME).is_file(),
                missing_package=not bool(meta.packageName.strip()),
                releases=releases,
            ),
        )
    return records


def build_catalog(apps_dir: Path) -> dict[str, Any]:
    records = scan_apps(apps_dir)
    apps: list[dict[str, Any]] = []
    for record in records:
        if record.missing_package:
            continue
        if not record.has_version_json:
            continue
        if not record.meta.verified:
            continue
        icon = find_icon_file(record.folder)
        apps.append(
            record.meta.to_catalog_entry(
                icon_file_name=icon.name if icon is not None else None,
            ),
        )
    return {
        "schemaVersion": 1,
        "publishedAt": utc_now(),
        "apps": apps,
    }


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
    metas = load_meta_map(apps_dir)
    metas[meta.id] = meta
    (apps_dir / meta.id).mkdir(parents=True, exist_ok=True)
    save_meta_map(apps_dir, metas)


def add_app(
    apps_dir: Path,
    meta: AppMeta,
    apk_source: Path | None = None,
    version_code: int | None = None,
    version_name: str | None = None,
    changelog: str = "",
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
            changelog=changelog,
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
    changelog: str = "",
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
        changelog=changelog,
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
        save_meta_map(apps_dir, current)
    return added
