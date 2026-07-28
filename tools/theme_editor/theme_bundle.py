"""
Read/write helpers for TBox Monitor `.tboxtheme` bundles.

A `.tboxtheme` file is a ZIP archive compatible with Android
`ThemeBundleExport` / `ThemeLayoutExport` (formatVersion 1, type tbox_theme).
"""

from __future__ import annotations

import io
import json
import re
import time
import zipfile
from copy import deepcopy
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Mapping, MutableMapping
from uuid import uuid4

THEME_FILE_EXTENSION = "tboxtheme"
THEME_JSON_ENTRY = "theme.json"
FORMAT_VERSION = 1
THEME_TYPE = "tbox_theme"

ASSETS_WALLPAPER_LIGHT_DIR = "assets/wallpaper/light/"
ASSETS_WALLPAPER_DARK_DIR = "assets/wallpaper/dark/"
ASSETS_ICONS_DIR = "assets/icons/"
ASSETS_HTTP_REQUEST_ICONS_DIR = "assets/http_request_icons/"
ASSETS_TILE_BG_DIR = "assets/tile_backgrounds/"

SECTION_MAIN_SCREEN = "mainScreen"
SECTION_FLOATING_PANELS = "floatingPanels"
SECTION_APP_ICONS = "appIcons"
ALL_SECTIONS = (SECTION_MAIN_SCREEN, SECTION_FLOATING_PANELS, SECTION_APP_ICONS)

IMAGE_EXTENSIONS = frozenset(
    {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"}
)
MAX_ASSET_BYTES = 10 * 1024 * 1024

FONT_SLUGS = (
    "default",
    "sans_serif",
    "serif",
    "monospace",
    "cabin",
    "nunito",
    "roboto",
)

DEFAULT_CANVAS_LIGHT = "#FFF8F9FA"
DEFAULT_CANVAS_DARK = "#FF292F3B"
DEFAULT_CORNER_BG_LIGHT = "#00000000"
DEFAULT_CORNER_BG_DARK = "#00000000"
DEFAULT_CORNER_ICON_LIGHT = "#FF1A1C1E"
DEFAULT_CORNER_ICON_DARK = "#FFE2E2E6"
DEFAULT_COLOR_PRESETS = [
    "#FF1A1C1E",
    "#FF1A1C1E",
    "#FFE2E2E6",
    "#FFE2E2E6",
    "#FFFFFFFF",
    "#FFF8F9FA",
    "#FF131C2D",
    "#FF292F3B",
]

_UNSAFE_NAME_RE = re.compile(r'[\\/:*?"<>|]')


def normalize_zip_entry_path(raw: str) -> str:
    path = raw.replace("\\", "/").strip()
    while path.startswith("./"):
        path = path[2:]
    return path.lstrip("/")


def theme_json_zip_entry_priority(normalized_path: str) -> int | None:
    if normalized_path == THEME_JSON_ENTRY:
        return 0
    if normalized_path.endswith(f"/{THEME_JSON_ENTRY}"):
        return len(normalized_path)
    return None


def looks_like_zip_archive(data: bytes) -> bool:
    return len(data) >= 2 and data[0] == ord("P") and data[1] == ord("K")


def sanitize_theme_export_base_name(input_name: str) -> str | None:
    name = input_name.strip()
    suffix = f".{THEME_FILE_EXTENSION}"
    if name.lower().endswith(suffix):
        name = name[: -len(suffix)].strip()
    name = _UNSAFE_NAME_RE.sub("_", name).strip(".")
    if not name or name in {".", ".."}:
        return None
    if len(name) > 120:
        name = name[:120]
    return name


def theme_file_name_from_base_name(base_name: str) -> str:
    return f"{base_name}.{THEME_FILE_EXTENSION}"


def is_image_filename(name: str) -> bool:
    ext = Path(name).suffix.lower().lstrip(".")
    return ext in IMAGE_EXTENSIONS


def _zip_asset_suffix(normalized_path: str, assets_dir: str) -> str | None:
    idx = normalized_path.find(assets_dir)
    if idx < 0:
        return None
    suffix = normalized_path[idx + len(assets_dir) :]
    if not suffix or suffix.endswith("/"):
        return None
    return suffix


def _is_allowed_tile_bg_rel(rel: str) -> bool:
    normalized = rel.strip().replace("\\", "/")
    if ".." in normalized:
        return False
    return True


@dataclass
class ThemeBundle:
    """In-memory representation of a `.tboxtheme` archive."""

    theme: dict[str, Any] = field(default_factory=dict)
    light_wallpapers: MutableMapping[str, bytes] = field(default_factory=dict)
    dark_wallpapers: MutableMapping[str, bytes] = field(default_factory=dict)
    icons: MutableMapping[str, bytes] = field(default_factory=dict)
    http_request_icons: MutableMapping[str, bytes] = field(default_factory=dict)
    tile_backgrounds: MutableMapping[str, bytes] = field(default_factory=dict)
    source_path: str | None = None

    @classmethod
    def new_empty(
        cls,
        sections: list[str] | None = None,
        *,
        include_main_screen_defaults: bool = True,
    ) -> "ThemeBundle":
        chosen = list(sections or [SECTION_MAIN_SCREEN])
        for key in chosen:
            if key not in ALL_SECTIONS:
                raise ValueError(f"unknown section: {key}")
        root: dict[str, Any] = {
            "formatVersion": FORMAT_VERSION,
            "type": THEME_TYPE,
            "exportedAtMillis": int(time.time() * 1000),
            "sections": chosen,
        }
        if SECTION_MAIN_SCREEN in chosen and include_main_screen_defaults:
            root[SECTION_MAIN_SCREEN] = default_main_screen_section()
        if SECTION_FLOATING_PANELS in chosen:
            root[SECTION_FLOATING_PANELS] = {"panels": []}
        if SECTION_APP_ICONS in chosen:
            root[SECTION_APP_ICONS] = {"packages": [], "httpRequestIconKeys": []}
        return cls(theme=root)

    @classmethod
    def load_bytes(cls, data: bytes, source_path: str | None = None) -> "ThemeBundle":
        if not looks_like_zip_archive(data):
            raise ValueError("not_a_zip_archive")
        theme_json: str | None = None
        theme_priority = 10**9
        light: dict[str, bytes] = {}
        dark: dict[str, bytes] = {}
        icons: dict[str, bytes] = {}
        http_icons: dict[str, bytes] = {}
        tile_bgs: dict[str, bytes] = {}

        with zipfile.ZipFile(io.BytesIO(data), "r") as zf:
            for info in zf.infolist():
                if info.is_dir():
                    continue
                normalized = normalize_zip_entry_path(info.filename)
                priority = theme_json_zip_entry_priority(normalized)
                raw = zf.read(info)
                if priority is not None:
                    if priority < theme_priority:
                        theme_json = raw.decode("utf-8")
                        theme_priority = priority
                    continue
                if suffix := _zip_asset_suffix(normalized, ASSETS_ICONS_DIR):
                    icons[suffix] = raw
                    continue
                if suffix := _zip_asset_suffix(normalized, ASSETS_HTTP_REQUEST_ICONS_DIR):
                    http_icons[suffix] = raw
                    continue
                if suffix := _zip_asset_suffix(normalized, ASSETS_TILE_BG_DIR):
                    if _is_allowed_tile_bg_rel(suffix):
                        tile_bgs[suffix] = raw
                    continue
                if suffix := _zip_asset_suffix(normalized, ASSETS_WALLPAPER_LIGHT_DIR):
                    light[suffix] = raw
                    continue
                if suffix := _zip_asset_suffix(normalized, ASSETS_WALLPAPER_DARK_DIR):
                    dark[suffix] = raw
                    continue

        if theme_json is None:
            raise ValueError("theme.json not found")
        theme = json.loads(theme_json)
        if not isinstance(theme, dict):
            raise ValueError("invalid_json")
        return cls(
            theme=theme,
            light_wallpapers=light,
            dark_wallpapers=dark,
            icons=icons,
            http_request_icons=http_icons,
            tile_backgrounds=tile_bgs,
            source_path=source_path,
        )

    @classmethod
    def load_path(cls, path: str | Path) -> "ThemeBundle":
        p = Path(path)
        return cls.load_bytes(p.read_bytes(), source_path=str(p))

    def validate(self) -> list[str]:
        errors: list[str] = []
        theme = self.theme
        if theme.get("type") != THEME_TYPE:
            errors.append('type должен быть "tbox_theme"')
        version = theme.get("formatVersion")
        if not isinstance(version, int) or version < 1:
            errors.append("formatVersion должен быть целым числом >= 1")
        sections = theme.get("sections")
        if not isinstance(sections, list) or not sections:
            errors.append("sections должен быть непустым массивом")
        else:
            for item in sections:
                if item not in ALL_SECTIONS:
                    errors.append(f"неизвестный раздел: {item}")
            if SECTION_MAIN_SCREEN in sections and SECTION_MAIN_SCREEN not in theme:
                errors.append("в theme.json нет объекта mainScreen")
            if SECTION_FLOATING_PANELS in sections and SECTION_FLOATING_PANELS not in theme:
                errors.append("в theme.json нет объекта floatingPanels")
            if SECTION_APP_ICONS in sections and SECTION_APP_ICONS not in theme:
                errors.append("в theme.json нет объекта appIcons")

        main = theme.get(SECTION_MAIN_SCREEN)
        if isinstance(main, dict):
            selection = main.get("wallpaperSelectionByPage")
            if isinstance(selection, dict):
                for side, folder, assets in (
                    ("light", ASSETS_WALLPAPER_LIGHT_DIR, self.light_wallpapers),
                    ("dark", ASSETS_WALLPAPER_DARK_DIR, self.dark_wallpapers),
                ):
                    side_map = selection.get(side)
                    if not isinstance(side_map, dict):
                        continue
                    for page, filename in side_map.items():
                        if not isinstance(filename, str) or not filename.strip():
                            errors.append(f"пустой файл обоев для {side}/страница {page}")
                        elif filename not in assets:
                            errors.append(
                                f"обои «{filename}» назначены на {side}/стр.{page}, "
                                f"но файла нет в {folder}"
                            )

        for name, data in self.light_wallpapers.items():
            if len(data) > MAX_ASSET_BYTES:
                errors.append(f"обои light/{name}: больше 10 МБ")
        for name, data in self.dark_wallpapers.items():
            if len(data) > MAX_ASSET_BYTES:
                errors.append(f"обои dark/{name}: больше 10 МБ")
        return errors

    def ensure_sections_consistency(self) -> None:
        sections = [s for s in self.theme.get("sections", []) if s in ALL_SECTIONS]
        if not sections:
            sections = [SECTION_MAIN_SCREEN]
        self.theme["sections"] = sections
        self.theme["formatVersion"] = FORMAT_VERSION
        self.theme["type"] = THEME_TYPE
        self.theme["exportedAtMillis"] = int(time.time() * 1000)

        if SECTION_MAIN_SCREEN in sections:
            main = self.theme.setdefault(SECTION_MAIN_SCREEN, {})
            if not isinstance(main, dict):
                main = {}
                self.theme[SECTION_MAIN_SCREEN] = main
            if self.light_wallpapers:
                main["wallpaperLightFolderBundledPath"] = ASSETS_WALLPAPER_LIGHT_DIR
            else:
                main.pop("wallpaperLightFolderBundledPath", None)
            if self.dark_wallpapers:
                main["wallpaperDarkFolderBundledPath"] = ASSETS_WALLPAPER_DARK_DIR
            else:
                main.pop("wallpaperDarkFolderBundledPath", None)
        else:
            self.theme.pop(SECTION_MAIN_SCREEN, None)

        if SECTION_FLOATING_PANELS in sections:
            floating = self.theme.setdefault(SECTION_FLOATING_PANELS, {"panels": []})
            if not isinstance(floating, dict):
                self.theme[SECTION_FLOATING_PANELS] = {"panels": []}
            else:
                floating.setdefault("panels", [])
        else:
            self.theme.pop(SECTION_FLOATING_PANELS, None)

        if SECTION_APP_ICONS in sections:
            icons_section = self.theme.setdefault(
                SECTION_APP_ICONS,
                {"packages": [], "httpRequestIconKeys": []},
            )
            if not isinstance(icons_section, dict):
                icons_section = {"packages": [], "httpRequestIconKeys": []}
                self.theme[SECTION_APP_ICONS] = icons_section
            packages = sorted(
                {
                    Path(name).stem
                    for name in self.icons
                    if name.lower().endswith(".png")
                }
                | {
                    p
                    for p in icons_section.get("packages", [])
                    if isinstance(p, str) and p.strip()
                }
            )
            http_keys = sorted(
                {
                    Path(name).stem
                    for name in self.http_request_icons
                }
                | {
                    k
                    for k in icons_section.get("httpRequestIconKeys", [])
                    if isinstance(k, str) and k.strip()
                }
            )
            icons_section["packages"] = packages
            icons_section["httpRequestIconKeys"] = http_keys
        else:
            self.theme.pop(SECTION_APP_ICONS, None)

    def theme_json_text(self, *, indent: int | None = 2) -> str:
        self.ensure_sections_consistency()
        if indent is None:
            return json.dumps(self.theme, ensure_ascii=False, separators=(",", ":"))
        return json.dumps(self.theme, ensure_ascii=False, indent=indent) + "\n"

    def set_theme_from_json_text(self, text: str) -> None:
        parsed = json.loads(text)
        if not isinstance(parsed, dict):
            raise ValueError("корень JSON должен быть объектом")
        self.theme = parsed
        self.ensure_sections_consistency()

    def to_bytes(self) -> bytes:
        self.ensure_sections_consistency()
        errors = self.validate()
        if errors:
            raise ValueError("; ".join(errors))
        buf = io.BytesIO()
        with zipfile.ZipFile(buf, "w", compression=zipfile.ZIP_DEFLATED) as zf:
            zf.writestr(THEME_JSON_ENTRY, self.theme_json_text())
            for name, data in sorted(self.light_wallpapers.items()):
                zf.writestr(f"{ASSETS_WALLPAPER_LIGHT_DIR}{name}", data)
            for name, data in sorted(self.dark_wallpapers.items()):
                zf.writestr(f"{ASSETS_WALLPAPER_DARK_DIR}{name}", data)
            for name, data in sorted(self.icons.items()):
                zf.writestr(f"{ASSETS_ICONS_DIR}{name}", data)
            for name, data in sorted(self.http_request_icons.items()):
                zf.writestr(f"{ASSETS_HTTP_REQUEST_ICONS_DIR}{name}", data)
            for name, data in sorted(self.tile_backgrounds.items()):
                zf.writestr(f"{ASSETS_TILE_BG_DIR}{name}", data)
        return buf.getvalue()

    def save_path(self, path: str | Path) -> Path:
        p = Path(path)
        if p.suffix.lower() != f".{THEME_FILE_EXTENSION}":
            p = p.with_suffix(f".{THEME_FILE_EXTENSION}")
        p.write_bytes(self.to_bytes())
        self.source_path = str(p)
        return p

    def clone(self) -> "ThemeBundle":
        return ThemeBundle(
            theme=deepcopy(self.theme),
            light_wallpapers=dict(self.light_wallpapers),
            dark_wallpapers=dict(self.dark_wallpapers),
            icons=dict(self.icons),
            http_request_icons=dict(self.http_request_icons),
            tile_backgrounds=dict(self.tile_backgrounds),
            source_path=self.source_path,
        )

    def summary(self) -> Mapping[str, Any]:
        main = self.theme.get(SECTION_MAIN_SCREEN) if isinstance(
            self.theme.get(SECTION_MAIN_SCREEN), dict
        ) else {}
        floating = self.theme.get(SECTION_FLOATING_PANELS) if isinstance(
            self.theme.get(SECTION_FLOATING_PANELS), dict
        ) else {}
        panels = main.get("panels") if isinstance(main, dict) else None
        floating_panels = floating.get("panels") if isinstance(floating, dict) else None
        return {
            "sections": list(self.theme.get("sections", [])),
            "pageCount": main.get("pageCount") if isinstance(main, dict) else None,
            "currentPage": main.get("currentPage") if isinstance(main, dict) else None,
            "mainPanels": len(panels) if isinstance(panels, list) else 0,
            "floatingPanels": len(floating_panels) if isinstance(floating_panels, list) else 0,
            "lightWallpapers": len(self.light_wallpapers),
            "darkWallpapers": len(self.dark_wallpapers),
            "icons": len(self.icons),
            "httpRequestIcons": len(self.http_request_icons),
            "tileBackgrounds": len(self.tile_backgrounds),
        }


def default_main_screen_section() -> dict[str, Any]:
    return {
        "pageCount": 1,
        "currentPage": 1,
        "theme": {
            "canvasBackground": {
                "light": DEFAULT_CANVAS_LIGHT,
                "dark": DEFAULT_CANVAS_DARK,
            },
            "cornerButtons": {
                "sizeDp": 50,
                "background": {
                    "light": DEFAULT_CORNER_BG_LIGHT,
                    "dark": DEFAULT_CORNER_BG_DARK,
                },
                "icon": {
                    "light": DEFAULT_CORNER_ICON_LIGHT,
                    "dark": DEFAULT_CORNER_ICON_DARK,
                },
            },
            "wallpaperCrop": True,
            "colorPresets": list(DEFAULT_COLOR_PRESETS),
            "typography": {"fontFamily": "default"},
        },
        "settingsButton": {"x": 0.02, "y": 0.02},
        "addButton": {"x": 0.92, "y": 0.02},
        "pagePrevButton": {"x": 0.02, "y": 0.9},
        "pageNextButton": {"x": 0.92, "y": 0.9},
        "exitWindowModeButton": {"x": 0.21, "y": 0.02},
        "restoreWindowModeButton": {"x": 0.41, "y": 0.02},
        "panels": [],
        "wallpaperSelectionByPage": {},
    }


def get_main_screen(theme: dict[str, Any]) -> dict[str, Any]:
    main = theme.get(SECTION_MAIN_SCREEN)
    if not isinstance(main, dict):
        main = default_main_screen_section()
        theme[SECTION_MAIN_SCREEN] = main
    return main


def get_visual_theme(theme: dict[str, Any]) -> dict[str, Any]:
    main = get_main_screen(theme)
    visual = main.get("theme")
    if not isinstance(visual, dict):
        visual = default_main_screen_section()["theme"]
        main["theme"] = visual
    return visual


def get_floating_panels_section(theme: dict[str, Any]) -> dict[str, Any]:
    floating = theme.get(SECTION_FLOATING_PANELS)
    if not isinstance(floating, dict):
        floating = {"panels": []}
        theme[SECTION_FLOATING_PANELS] = floating
    floating.setdefault("panels", [])
    if not isinstance(floating["panels"], list):
        floating["panels"] = []
    return floating


def _unique_panel_id(prefix: str, existing: set[str]) -> str:
    for _ in range(64):
        candidate = f"{prefix}{uuid4().hex[:8]}"
        if candidate not in existing:
            return candidate
    raise RuntimeError("could not allocate unique panel id")


def default_main_screen_panel(
    *,
    panel_id: str | None = None,
    name: str | None = None,
    page_number: int = 1,
    existing_ids: set[str] | None = None,
    cascade_index: int = 0,
) -> dict[str, Any]:
    """Defaults aligned with SettingsViewModel.createDefaultMainScreenPanel."""
    ids = existing_ids or set()
    pid = panel_id or _unique_panel_id("main-screen-", ids)
    offset = 0.04 * max(0, cascade_index)
    return {
        "id": pid,
        "name": name or "Панель",
        "enabled": True,
        "positionMode": "absolute",
        "grid": {"rows": 1, "cols": 1},
        "position": {
            "x": min(0.05 + offset, 0.55),
            "y": min(0.1 + offset, 0.55),
        },
        "size": {"width": 0.4, "height": 0.3},
        "background": False,
        "clickAction": False,
        "showTboxDisconnectIndicator": False,
        "pageNumber": max(1, int(page_number)),
        "widgets": [],
    }


def default_floating_panel(
    *,
    panel_id: str | None = None,
    name: str | None = None,
    existing_ids: set[str] | None = None,
    cascade_index: int = 0,
) -> dict[str, Any]:
    """Defaults aligned with SettingsViewModel.createDefaultFloatingDashboard.

    ``enabled`` is True here so the panel is visible on the layout canvas and
    useful in a theme; on-device UI default for a brand-new floating panel is false.
    """
    ids = existing_ids or set()
    pid = panel_id or _unique_panel_id("floating-", ids)
    offset = 24 * max(0, cascade_index)
    return {
        "id": pid,
        "name": name or "Плавающая",
        "enabled": True,
        "grid": {"rows": 1, "cols": 1},
        "width": 100,
        "height": 100,
        "startX": 50 + offset,
        "startY": 50 + offset,
        "background": False,
        "clickAction": True,
        "showTboxDisconnectIndicator": True,
        "widgets": [],
    }


def ensure_section_enabled(theme: dict[str, Any], section: str) -> None:
    sections = theme.setdefault("sections", [])
    if not isinstance(sections, list):
        sections = []
        theme["sections"] = sections
    if section not in sections:
        sections.append(section)


def normalize_hex_color(value: str, *, default: str = "#FFFFFFFF") -> str:
    raw = value.strip()
    if not raw:
        return default
    if not raw.startswith("#"):
        raw = f"#{raw}"
    body = raw[1:]
    if len(body) == 3:
        body = "".join(ch * 2 for ch in body)
        body = f"FF{body}"
    elif len(body) == 6:
        body = f"FF{body}"
    elif len(body) != 8:
        return default
    try:
        int(body, 16)
    except ValueError:
        return default
    return f"#{body.upper()}"
