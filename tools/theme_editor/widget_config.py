"""Widget config helpers compatible with Android WidgetConfigCodec / theme.json."""

from __future__ import annotations

from copy import deepcopy
from typing import Any

from widget_catalog import get_widget_type

DEFAULT_SCALE = 1.0
DEFAULT_SHAPE = 0
DEFAULT_TEXT_ALIGN = 0  # center
DEFAULT_FONT_WEIGHT = 1  # medium
DEFAULT_PADDING = 0
DEFAULT_DRIVE_MODE = 2
DEFAULT_TRIP_LABEL_PCT = 60
DEFAULT_TRIP_SOURCE = 0
DEFAULT_HTTP_YAML = 'url: "http://"'
DEFAULT_FREEFORM_SIDE = "right"
DEFAULT_FREEFORM_PERCENT = 50

# Match DEFAULT_WIDGET_TEXT_COLOR_* (Java signed ARGB from 0xFF1A1C1E / 0xFFE2E2E6).
DEFAULT_TEXT_LIGHT = -15066082  # 0xFF1A1C1E
DEFAULT_TEXT_DARK = -1907994  # 0xFFE2E2E6


def to_signed_argb(value: int) -> int:
    n = int(value) & 0xFFFFFFFF
    if n >= 0x80000000:
        n -= 0x100000000
    return n


def argb_to_hex(value: int | None, *, default: str = "#FF1A1C1E") -> str:
    if value is None:
        return default
    n = int(value) & 0xFFFFFFFF
    return f"#{n:08X}"


def hex_to_argb(value: str, *, default: int = DEFAULT_TEXT_LIGHT) -> int:
    raw = (value or "").strip()
    if not raw:
        return default
    if raw.startswith("#"):
        raw = raw[1:]
    if len(raw) == 6:
        raw = "FF" + raw
    if len(raw) != 8:
        return default
    try:
        return to_signed_argb(int(raw, 16))
    except ValueError:
        return default


def default_title_position(data_key: str) -> int:
    # Launcher defaults to bottom title in the app.
    return 1 if data_key == "appLauncherWidget" else 0


def empty_widget(data_key: str = "") -> dict[str, Any]:
    return {
        "dataKey": data_key,
        "showTitle": False,
        "showUnit": True,
        "singleLineDualMetrics": False,
        "scale": DEFAULT_SCALE,
        "shape": DEFAULT_SHAPE,
        "textColorLight": DEFAULT_TEXT_LIGHT,
        "textColorDark": DEFAULT_TEXT_DARK,
        "useMbCanVhal": False,
        "mediaAutoPlayOnInit": False,
        "mediaAutoPlayOnlyWhenEngineRunning": False,
        "mediaKeepPlayerForeground": False,
    }


def normalize_widgets(rows: int, cols: int, widgets: list[Any] | None) -> list[dict[str, Any]]:
    need = max(1, int(rows)) * max(1, int(cols))
    out: list[dict[str, Any]] = []
    src = widgets if isinstance(widgets, list) else []
    for i in range(need):
        if i < len(src) and isinstance(src[i], dict):
            out.append(normalize_widget_dict(src[i]))
        else:
            out.append(empty_widget())
    return out


def normalize_widget_dict(raw: dict[str, Any]) -> dict[str, Any]:
    w = empty_widget()
    data_key = str(raw.get("dataKey") or raw.get("type") or "")
    if data_key == "launchAppWidget":
        data_key = "appLauncherWidget"
    w["dataKey"] = data_key
    w["showTitle"] = bool(raw.get("showTitle", False))
    w["showUnit"] = bool(raw.get("showUnit", True))
    w["singleLineDualMetrics"] = bool(raw.get("singleLineDualMetrics", False))
    try:
        scale = float(raw.get("scale", DEFAULT_SCALE))
    except (TypeError, ValueError):
        scale = DEFAULT_SCALE
    w["scale"] = round(max(0.1, min(2.0, scale)) * 10) / 10
    try:
        shape = int(raw.get("shape", DEFAULT_SHAPE))
    except (TypeError, ValueError):
        shape = DEFAULT_SHAPE
    w["shape"] = max(0, min(50, shape))
    w["textColorLight"] = to_signed_argb(int(raw.get("textColorLight", DEFAULT_TEXT_LIGHT)))
    w["textColorDark"] = to_signed_argb(int(raw.get("textColorDark", DEFAULT_TEXT_DARK)))
    for key in ("backgroundColorLight", "backgroundColorDark"):
        if key in raw and raw[key] is not None:
            try:
                w[key] = to_signed_argb(int(raw[key]))
            except (TypeError, ValueError):
                pass
    players = raw.get("mediaPlayers")
    if isinstance(players, list):
        cleaned = [str(p).strip() for p in players if str(p).strip()]
        if cleaned:
            w["mediaPlayers"] = cleaned
            sel = str(raw.get("mediaSelectedPlayer") or "").strip()
            if sel:
                w["mediaSelectedPlayer"] = sel
    w["mediaAutoPlayOnInit"] = bool(raw.get("mediaAutoPlayOnInit", False))
    w["mediaAutoPlayOnlyWhenEngineRunning"] = bool(
        raw.get("mediaAutoPlayOnlyWhenEngineRunning", False)
    )
    w["mediaKeepPlayerForeground"] = bool(raw.get("mediaKeepPlayerForeground", False))
    pkg = str(raw.get("launcherAppPackage") or "").strip()
    if pkg:
        w["launcherAppPackage"] = pkg
    if data_key == "appLauncherWidget" and raw.get("launcherFreeformEnabled"):
        w["launcherFreeformEnabled"] = True
        side = str(raw.get("launcherFreeformSide") or DEFAULT_FREEFORM_SIDE)
        if side not in {"left", "right", "top", "bottom"}:
            side = DEFAULT_FREEFORM_SIDE
        w["launcherFreeformSide"] = side
        try:
            pct = int(raw.get("launcherFreeformPercent", DEFAULT_FREEFORM_PERCENT))
        except (TypeError, ValueError):
            pct = DEFAULT_FREEFORM_PERCENT
        w["launcherFreeformPercent"] = max(20, min(80, (pct // 10) * 10))
    if data_key == "httpRequestWidget":
        yaml = str(raw.get("httpRequestYaml") or DEFAULT_HTTP_YAML).strip() or DEFAULT_HTTP_YAML
        w["httpRequestYaml"] = yaml
        w["httpOpenBrowser"] = bool(raw.get("httpOpenBrowser", False))
    if raw.get("appWidgetId") is not None:
        try:
            w["appWidgetId"] = int(raw["appWidgetId"])
        except (TypeError, ValueError):
            pass
    custom = str(raw.get("customTitle") or "").strip()
    if custom:
        w["customTitle"] = custom
    if "valueAccuracy" in raw and raw["valueAccuracy"] is not None:
        try:
            acc = int(raw["valueAccuracy"])
            if acc in (0, 1, 2):
                w["valueAccuracy"] = acc
        except (TypeError, ValueError):
            pass
    dtf = str(raw.get("dateTimeFormat") or "").strip()
    if dtf and data_key in {"timeWidget", "dateWidget"}:
        w["dateTimeFormat"] = dtf
    try:
        variant = int(raw.get("selectedVariant", 0))
    except (TypeError, ValueError):
        variant = 0
    if variant:
        w["selectedVariant"] = variant
    try:
        drive = int(raw.get("selectedDriveMode", DEFAULT_DRIVE_MODE))
    except (TypeError, ValueError):
        drive = DEFAULT_DRIVE_MODE
    if drive != DEFAULT_DRIVE_MODE:
        w["selectedDriveMode"] = drive
    w["useMbCanVhal"] = bool(raw.get("useMbCanVhal", False))
    try:
        stepper = int(raw.get("stepperAdjustIconStyle", 0))
    except (TypeError, ValueError):
        stepper = 0
    if stepper:
        w["stepperAdjustIconStyle"] = 1 if stepper else 0
    for key in (
        "tileBackgroundImageRelPathLight",
        "tileBackgroundImageRelPathDark",
    ):
        val = raw.get(key)
        if isinstance(val, str) and val.strip():
            w[key] = val.strip()
    meta = get_widget_type(data_key)
    if meta.is_trip:
        if raw.get("tripWidgetShowRowDividers") is False:
            w["tripWidgetShowRowDividers"] = False
        try:
            label_pct = int(raw.get("tripWidgetLabelColumnWidthPercent", DEFAULT_TRIP_LABEL_PCT))
        except (TypeError, ValueError):
            label_pct = DEFAULT_TRIP_LABEL_PCT
        if label_pct != DEFAULT_TRIP_LABEL_PCT:
            w["tripWidgetLabelColumnWidthPercent"] = max(20, min(80, label_pct))
        try:
            src = int(raw.get("tripWidgetSource", DEFAULT_TRIP_SOURCE))
        except (TypeError, ValueError):
            src = DEFAULT_TRIP_SOURCE
        if src != DEFAULT_TRIP_SOURCE:
            w["tripWidgetSource"] = 1 if src else 0
    try:
        align = int(raw.get("textAlign", DEFAULT_TEXT_ALIGN))
    except (TypeError, ValueError):
        align = DEFAULT_TEXT_ALIGN
    if align != DEFAULT_TEXT_ALIGN:
        w["textAlign"] = max(0, min(2, align))
    try:
        weight = int(raw.get("fontWeight", DEFAULT_FONT_WEIGHT))
    except (TypeError, ValueError):
        weight = DEFAULT_FONT_WEIGHT
    if weight != DEFAULT_FONT_WEIGHT:
        w["fontWeight"] = max(0, min(2, weight))
    try:
        title_pos = int(raw.get("titlePosition", default_title_position(data_key)))
    except (TypeError, ValueError):
        title_pos = default_title_position(data_key)
    if title_pos != default_title_position(data_key):
        w["titlePosition"] = 1 if title_pos else 0
    for key in (
        "paddingTopPercent",
        "paddingBottomPercent",
        "paddingStartPercent",
        "paddingEndPercent",
    ):
        try:
            pad = int(raw.get(key, DEFAULT_PADDING))
        except (TypeError, ValueError):
            pad = DEFAULT_PADDING
        if pad:
            w[key] = max(0, min(50, pad))
    for key in (
        "controlInactiveColorLight",
        "controlInactiveColorDark",
        "controlActiveColorLight",
        "controlActiveColorDark",
        "controlInactiveBackgroundColorLight",
        "controlInactiveBackgroundColorDark",
        "controlActiveBackgroundColorLight",
        "controlActiveBackgroundColorDark",
    ):
        if key in raw and raw[key] is not None:
            try:
                w[key] = to_signed_argb(int(raw[key]))
            except (TypeError, ValueError):
                pass
    if raw.get("controlShape") is not None:
        try:
            w["controlShape"] = max(0, min(50, int(raw["controlShape"])))
        except (TypeError, ValueError):
            pass
    return w


def serialize_widget(widget: dict[str, Any]) -> dict[str, Any]:
    """Produce theme.json widget object (always-write + omit-default)."""
    w = normalize_widget_dict(widget)
    out: dict[str, Any] = {
        "dataKey": w.get("dataKey", ""),
        "showTitle": bool(w.get("showTitle", False)),
        "showUnit": bool(w.get("showUnit", True)),
        "singleLineDualMetrics": bool(w.get("singleLineDualMetrics", False)),
        "scale": float(w.get("scale", DEFAULT_SCALE)),
        "shape": int(w.get("shape", DEFAULT_SHAPE)),
        "textColorLight": int(w.get("textColorLight", DEFAULT_TEXT_LIGHT)),
        "textColorDark": int(w.get("textColorDark", DEFAULT_TEXT_DARK)),
        "useMbCanVhal": bool(w.get("useMbCanVhal", False)),
        "mediaAutoPlayOnInit": bool(w.get("mediaAutoPlayOnInit", False)),
        "mediaAutoPlayOnlyWhenEngineRunning": bool(
            w.get("mediaAutoPlayOnlyWhenEngineRunning", False)
        ),
        "mediaKeepPlayerForeground": bool(w.get("mediaKeepPlayerForeground", False)),
    }
    for key in ("backgroundColorLight", "backgroundColorDark"):
        if key in w and w[key] is not None:
            out[key] = int(w[key])
    players = w.get("mediaPlayers")
    if isinstance(players, list) and players:
        out["mediaPlayers"] = list(players)
        if w.get("mediaSelectedPlayer"):
            out["mediaSelectedPlayer"] = w["mediaSelectedPlayer"]
    if w.get("launcherAppPackage"):
        out["launcherAppPackage"] = w["launcherAppPackage"]
    if w.get("dataKey") == "appLauncherWidget" and w.get("launcherFreeformEnabled"):
        out["launcherFreeformEnabled"] = True
        out["launcherFreeformSide"] = w.get("launcherFreeformSide", DEFAULT_FREEFORM_SIDE)
        out["launcherFreeformPercent"] = int(
            w.get("launcherFreeformPercent", DEFAULT_FREEFORM_PERCENT)
        )
    if w.get("dataKey") == "httpRequestWidget":
        out["httpRequestYaml"] = w.get("httpRequestYaml") or DEFAULT_HTTP_YAML
        out["httpOpenBrowser"] = bool(w.get("httpOpenBrowser", False))
    if w.get("appWidgetId") is not None:
        out["appWidgetId"] = int(w["appWidgetId"])
    if w.get("customTitle"):
        out["customTitle"] = w["customTitle"]
    if w.get("valueAccuracy") is not None:
        out["valueAccuracy"] = int(w["valueAccuracy"])
    if w.get("dateTimeFormat"):
        out["dateTimeFormat"] = w["dateTimeFormat"]
    if w.get("selectedVariant"):
        out["selectedVariant"] = int(w["selectedVariant"])
    if w.get("selectedDriveMode") is not None and int(w["selectedDriveMode"]) != DEFAULT_DRIVE_MODE:
        out["selectedDriveMode"] = int(w["selectedDriveMode"])
    if w.get("stepperAdjustIconStyle"):
        out["stepperAdjustIconStyle"] = int(w["stepperAdjustIconStyle"])
    for key in (
        "tileBackgroundImageRelPathLight",
        "tileBackgroundImageRelPathDark",
    ):
        if w.get(key):
            out[key] = w[key]
    meta = get_widget_type(str(w.get("dataKey") or ""))
    if meta.is_trip:
        if w.get("tripWidgetShowRowDividers") is False:
            out["tripWidgetShowRowDividers"] = False
        if (
            w.get("tripWidgetLabelColumnWidthPercent") is not None
            and int(w["tripWidgetLabelColumnWidthPercent"]) != DEFAULT_TRIP_LABEL_PCT
        ):
            out["tripWidgetLabelColumnWidthPercent"] = int(w["tripWidgetLabelColumnWidthPercent"])
        if w.get("tripWidgetSource"):
            out["tripWidgetSource"] = int(w["tripWidgetSource"])
    if w.get("textAlign") is not None and int(w["textAlign"]) != DEFAULT_TEXT_ALIGN:
        out["textAlign"] = int(w["textAlign"])
    if w.get("fontWeight") is not None and int(w["fontWeight"]) != DEFAULT_FONT_WEIGHT:
        out["fontWeight"] = int(w["fontWeight"])
    default_tp = default_title_position(str(w.get("dataKey") or ""))
    if w.get("titlePosition") is not None and int(w["titlePosition"]) != default_tp:
        out["titlePosition"] = int(w["titlePosition"])
    for key in (
        "paddingTopPercent",
        "paddingBottomPercent",
        "paddingStartPercent",
        "paddingEndPercent",
    ):
        if w.get(key):
            out[key] = int(w[key])
    for key in (
        "controlInactiveColorLight",
        "controlInactiveColorDark",
        "controlActiveColorLight",
        "controlActiveColorDark",
        "controlInactiveBackgroundColorLight",
        "controlInactiveBackgroundColorDark",
        "controlActiveBackgroundColorLight",
        "controlActiveBackgroundColorDark",
    ):
        if key in w and w[key] is not None:
            out[key] = int(w[key])
    if w.get("controlShape") is not None:
        out["controlShape"] = int(w["controlShape"])
    return out


def clone_widget(widget: dict[str, Any]) -> dict[str, Any]:
    return deepcopy(normalize_widget_dict(widget))
