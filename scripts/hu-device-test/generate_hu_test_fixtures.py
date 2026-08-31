#!/usr/bin/env python3
"""Generate HU smoke-test fixtures: backup JSON, automations, and .tboxtheme packs."""

from __future__ import annotations

import json
import struct
import time
import zipfile
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
FIXTURES = ROOT / "fixtures"
PKG = "vad.dashing.tbox"
PREFIX = f"{PKG}."
DEVICE_DIR = "/storage/emulated/0/Download/hu_test"
FILE_URI = "file://" + DEVICE_DIR

# ECO=2, NOR=0, SPT=1 (standard VEHICLE_DRIVEMODE family)
DRIVE_THEMES = (
    ("eco", 2, (46, 125, 50), (27, 94, 32)),
    ("nor", 0, (25, 118, 210), (13, 71, 161)),
    ("spt", 1, (198, 40, 40), (136, 14, 14)),
)


def png_rgb(path: Path, width: int, height: int, rgb: tuple[int, int, int]) -> None:
    def chunk(tag: bytes, data: bytes) -> bytes:
        crc = zlib.crc32(tag + data) & 0xFFFFFFFF
        return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", crc)

    row = b"\x00" + (bytes(rgb) * width)
    raw = row * height
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    payload = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", ihdr)
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
    path.write_bytes(payload)


def pref(name: str, typ: str, value) -> dict:
    return {"n": name if name.startswith(PREFIX) else PREFIX + name, "t": typ, "v": value}


def widget(data_key: str, **extra) -> dict:
    item = {
        "dataKey": data_key,
        "showTitle": True,
        "showUnit": True,
        "singleLineDualMetrics": False,
        "shape": 12,
        "textColorLight": -16777216,
        "textColorDark": -1,
        "useMbCanVhal": bool(extra.pop("useMbCanVhal", False)),
    }
    item.update(extra)
    return item


def theme_widgets(mode: str) -> list[dict]:
    drive_can = True
    return [
        widget("driveModeWidget", selectedDriveMode=2, useMbCanVhal=drive_can, customTitle="ECO"),
        widget("driveModeWidget", selectedDriveMode=0, useMbCanVhal=drive_can, customTitle="NOR"),
        widget("driveModeWidget", selectedDriveMode=1, useMbCanVhal=drive_can, customTitle="SPT"),
        widget("driveModeCycleWidget", useMbCanVhal=drive_can, customTitle="CYCLE"),
        widget("voltage"),
        widget("carSpeed"),
        widget("engineRPM"),
        widget("odometer"),
        widget("fuelLevelPercentage"),
        widget("latitude"),
        widget("hideFloatingPanelsWidget", customTitle="HIDE"),
        widget("toggleFloatingPanelsEnabledWidget", customTitle="OVL"),
    ]


def theme_json(mode: str, light_name: str, dark_name: str, rgb) -> str:
    r, g, b = rgb
    canvas = (0xFF << 24) | (r << 16) | (g << 8) | b
    # signed 32-bit like Android ColorInt
    if canvas >= 0x80000000:
        canvas -= 0x100000000
    widgets = theme_widgets(mode)
    return json.dumps(
        {
            "formatVersion": 1,
            "type": "tbox_theme",
            "exportedAtMillis": int(time.time() * 1000),
            "sections": ["mainScreen", "floatingPanels"],
            "mainScreen": {
                "pageCount": 2,
                "currentPage": 1,
                "wallpaperCrop": True,
                "canvasBackground": {"light": canvas, "dark": canvas},
                "wallpaperSelectionByPage": {
                    "light": {"1": light_name, "2": light_name},
                    "dark": {"1": dark_name, "2": dark_name},
                },
                "panels": [
                    {
                        "id": f"hu-test-main-{mode}",
                        "name": f"HU {mode.upper()} main",
                        "enabled": True,
                        "grid": {"rows": 3, "cols": 4},
                        "position": {"x": 0.04, "y": 0.08},
                        "size": {"width": 0.92, "height": 0.84},
                        "background": True,
                        "clickAction": False,
                        "showTboxDisconnectIndicator": True,
                        "pageNumber": 1,
                        "panelBackgroundColorLight": canvas,
                        "panelBackgroundColorDark": canvas,
                        "widgets": widgets,
                    }
                ],
            },
            "floatingPanels": {
                "panels": [
                    {
                        "id": f"hu-test-float-{mode}",
                        "name": f"HU {mode.upper()} float",
                        "enabled": True,
                        "grid": {"rows": 2, "cols": 4},
                        "width": 640,
                        "height": 220,
                        "startX": 40,
                        "startY": 40,
                        "background": True,
                        "clickAction": True,
                        "showTboxDisconnectIndicator": True,
                        "panelBackgroundColorLight": canvas,
                        "panelBackgroundColorDark": canvas,
                        "widgets": widgets[:8],
                    }
                ]
            },
        },
        ensure_ascii=False,
        indent=2,
    )


def automations_doc() -> dict:
    def state_trigger(pkg: str, node_id: str) -> dict:
        return {
            "type": "state_equals",
            "id": node_id,
            "signal": "foreground_app",
            "source": "app",
            "expectedState": pkg,
            "holdMillis": 0,
            "startupBehavior": "initialize_only",
        }

    def toast(text: str) -> dict:
        return {
            "type": "builtin",
            "actionType": "show_toast",
            "intValue": 0,
            "stringValue": text,
            "boolValue": False,
        }

    def builtin(action_type: str) -> dict:
        return {
            "type": "builtin",
            "actionType": action_type,
            "intValue": 0,
            "stringValue": "",
            "boolValue": False,
        }

    return {
        "formatVersion": 1,
        "automations": [
            {
                "id": "hu-test-hide-on-settings",
                "name": "HU test: Settings hide overlays",
                "description": "Foreground Settings -> hide floating panels",
                "enabled": True,
                "triggers": [state_trigger("com.android.settings", "1")],
                "conditions": [],
                "actions": [
                    toast("HUTEST hide overlays (Settings)"),
                    builtin("toggle_hide_floating_panels"),
                ],
                "runMode": "restart",
                "maxRuns": 10,
                "conditionWaitMillis": 0,
            },
            {
                "id": "hu-test-enable-on-monitor",
                "name": "HU test: Monitor show overlays",
                "description": "TBox Monitor on screen -> enable floating panels",
                "enabled": True,
                "triggers": [state_trigger(PKG, "1")],
                "conditions": [],
                "actions": [
                    toast("HUTEST enable overlays (Monitor)"),
                    builtin("toggle_floating_panels_enabled"),
                ],
                "runMode": "restart",
                "maxRuns": 10,
                "conditionWaitMillis": 0,
            },
            {
                "id": "hu-test-navi-disable",
                "name": "HU test: Navi disable overlays",
                "description": "Yandex Navi -> disable floating panels",
                "enabled": True,
                "triggers": [state_trigger("ru.yandex.yandexnavi", "1")],
                "conditions": [],
                "actions": [
                    toast("HUTEST disable overlays (Navi)"),
                    builtin("toggle_floating_panels_enabled"),
                ],
                "runMode": "restart",
                "maxRuns": 10,
                "conditionWaitMillis": 0,
            },
            {
                "id": "hu-test-service-started",
                "name": "HU test: service started toast",
                "description": "Background service started",
                "enabled": True,
                "triggers": [
                    {
                        "type": "system_event",
                        "id": "1",
                        "event": "background_service_started",
                    }
                ],
                "conditions": [],
                "actions": [toast("HUTEST service started")],
                "runMode": "single",
                "maxRuns": 1,
                "conditionWaitMillis": 0,
            },
        ],
    }


def left_menu_layout() -> str:
    rows = [
        ("settings", True),
        ("automations", True),
        ("themes", True),
        ("floating_panels_settings", True),
        ("main_screen_settings", True),
        ("car_settings", True),
        ("logs", True),
        ("geoposition", True),
        ("modem", True),
        ("car_data", True),
        ("widgets", True),
        ("trips", True),
        ("refuels", True),
        ("can", True),
        ("info", True),
        ("at_commands", False),
        ("esp_companion", True),
    ]
    return json.dumps({"rows": [{"id": i, "enabled": e} for i, e in rows]}, ensure_ascii=False)


def floating_for_backup(mode: str = "nor") -> str:
    widgets = theme_widgets(mode)
    # DataStore uses widgetsConfig; theme import uses widgets.
    panel = {
        "id": "hu-test-float-backup",
        "name": "HU test overlay",
        "enabled": True,
        "widgetsConfig": widgets[:8],
        "rows": 2,
        "cols": 4,
        "width": 640,
        "height": 220,
        "startX": 40,
        "startY": 40,
        "background": True,
        "clickAction": True,
        "showTboxDisconnectIndicator": True,
    }
    return json.dumps([panel], ensure_ascii=False)


def main_for_backup() -> str:
    widgets = theme_widgets("nor")
    panel = {
        "id": "hu-test-main-backup",
        "name": "HU test main",
        "enabled": True,
        "widgetsConfig": widgets,
        "rows": 3,
        "cols": 4,
        "relX": 0.04,
        "relY": 0.08,
        "relWidth": 0.92,
        "relHeight": 0.84,
        "background": True,
        "clickAction": False,
        "showTboxDisconnectIndicator": True,
        "pageNumber": 1,
    }
    return json.dumps([panel], ensure_ascii=False)


def backup_json() -> dict:
    drive_paths = {
        str(raw): f"{FILE_URI}/hu_test_{mode}.tboxtheme" for mode, raw, *_ in DRIVE_THEMES
    }
    settings = [
        pref("left_menu_visible", "boolean", True),
        pref("left_menu_layout", "string", left_menu_layout()),
        pref("expert_mode", "boolean", True),
        pref("get_loc_data", "boolean", True),
        pref("get_voltages", "boolean", True),
        pref("get_can_frame", "boolean", False),
        pref("widget_show_indicator", "boolean", True),
        pref("widget_show_loc_indicator", "boolean", True),
        pref("follow_system_day_night", "boolean", True),
        pref("automations_json", "string", json.dumps(automations_doc(), ensure_ascii=False)),
        pref("floating_dashboards", "string", floating_for_backup()),
        pref("main_screen_dashboards", "string", main_for_backup()),
        pref("drive_mode_theme_paths", "string", json.dumps(drive_paths)),
        pref(
            "usage_stats_hide_floating_watch_packages",
            "string",
            json.dumps(["com.android.settings", "ru.yandex.yandexnavi"]),
        ),
        pref("usage_stats_hide_floating_panel_ids", "string", json.dumps(["hu-test-float-backup"])),
    ]
    return {
        "formatVersion": 1,
        "packageName": PKG,
        "exportedAtMillis": int(time.time() * 1000),
        "settings": settings,
        "app_data": [],
    }


def write_theme(out_dir: Path, mode: str, raw: int, light_rgb, dark_rgb) -> Path:
    work = out_dir / f"_build_{mode}"
    light_dir = work / "assets" / "wallpaper" / "light"
    dark_dir = work / "assets" / "wallpaper" / "dark"
    light_dir.mkdir(parents=True, exist_ok=True)
    dark_dir.mkdir(parents=True, exist_ok=True)
    light_name = f"{mode}_light.png"
    dark_name = f"{mode}_dark.png"
    png_rgb(light_dir / light_name, 960, 540, light_rgb)
    png_rgb(dark_dir / dark_name, 960, 540, dark_rgb)
    (work / "theme.json").write_text(
        theme_json(mode, light_name, dark_name, light_rgb),
        encoding="utf-8",
    )
    zip_path = out_dir / f"hu_test_{mode}.tboxtheme"
    with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        zf.write(work / "theme.json", "theme.json")
        zf.write(light_dir / light_name, f"assets/wallpaper/light/{light_name}")
        zf.write(dark_dir / dark_name, f"assets/wallpaper/dark/{dark_name}")
    return zip_path


def main() -> None:
    FIXTURES.mkdir(parents=True, exist_ok=True)
    for mode, raw, light, dark in DRIVE_THEMES:
        path = write_theme(FIXTURES, mode, raw, light, dark)
        print(f"wrote {path.name} ({path.stat().st_size} bytes) drive raw={raw}")
    backup_path = FIXTURES / "hu_test_backup.json"
    backup_path.write_text(json.dumps(backup_json(), ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {backup_path.name}")
    auto_path = FIXTURES / "hu_test_automations.json"
    auto_path.write_text(json.dumps(automations_doc(), ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {auto_path.name}")
    ui = {
        "settings": "Настройки",
        "import_json": "Импорт JSON",
        "choose_file": "Выбрать файл",
        "themes": "Темы",
        "drive_section": "Темы по режиму вождения",
        "yes": "да",
        "yes_cap": "Да",
        "allow": "Разрешить",
        "ok_ru": "ОК",
        "close": "Закрыть",
    }
    ui_path = ROOT / "ui-strings.json"
    ui_path.write_text(json.dumps(ui, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"wrote {ui_path.name}")
    print(f"device dir: {DEVICE_DIR}")


if __name__ == "__main__":
    main()
