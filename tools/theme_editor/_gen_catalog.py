# Generate tools/theme_editor/widget_catalog.py from Android sources.
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = Path(__file__).resolve().parent / "widget_catalog.py"
VM = (ROOT / "app/src/main/java/vad/dashing/tbox/ViewModels.kt").read_text(encoding="utf-8")

const_map: dict[str, str] = {}
for path in (ROOT / "app/src/main/java/vad/dashing/tbox").rglob("*.kt"):
    text = path.read_text(encoding="utf-8")
    for m in re.finditer(r'const val ([A-Z0-9_]+)\s*=\s*"([^"]+)"', text):
        const_map[m.group(1)] = m.group(2)

strings = (ROOT / "app/src/main/res/values/strings.xml").read_text(encoding="utf-8")
str_map = {
    m.group(1): m.group(2)
    for m in re.finditer(r'<string name="([^"]+)">([^<]*)</string>', strings)
}

block = re.search(
    r"private val dataKeyTitlesWidgets = mapOf\((.*?)\)\n\n    private val widgetDescriptions",
    VM,
    re.S,
)
assert block
raw = re.sub(r"\s+", " ", block.group(1))
entries: list[tuple[str, str, str]] = []
for m in re.finditer(
    r'(?:"([^"]+)"|([A-Z0-9_]+))\s+to\s+DataTitle\(\s*R\.string\.(\w+)(?:,\s*R\.string\.(\w+))?\s*\)',
    raw,
):
    key = m.group(1) or const_map.get(m.group(2), m.group(2))
    title_res = m.group(3)
    unit_res = m.group(4)
    title = str_map.get(title_res, title_res)
    if title_res == "currentFuelConsumption":
        title = str_map.get("currentFuelConsumption", title)
    unit = str_map.get(unit_res, "") if unit_res else ""
    entries.append((key, title, unit))

# Sample display values for template preview (not live data).
SAMPLES: dict[str, str] = {
    "voltage": "14.2",
    "steerAngle": "12",
    "steerSpeed": "30",
    "engineRPM": "2100",
    "carSpeed": "87",
    "carSpeedAccurate": "86.4",
    "cruiseSetSpeed": "90",
    "odometer": "45230",
    "distanceToNextMaintenance": "3200",
    "distanceToFuelEmpty": "412",
    "fuelLevelPercentage": "64",
    "fuelLevelPercentageFiltered": "63",
    "fuelLevelLiters": "35.2",
    "fuelLevelLitersActual": "34.8",
    "currentFuelConsumption": "8.4",
    "breakingForce": "18",
    "engineTemperature": "92",
    "gearBoxOilTemperature": "78",
    "gearBoxCurrentGear": "D3",
    "gearBoxPreparedGear": "4",
    "gearBoxChangeGear": "—",
    "gearBoxMode": "D",
    "gearBoxDriveMode": "Normal",
    "gearBoxWork": "OK",
    "gnssSpeed": "85",
    "visibleSatellites": "14",
    "longitude": "37.62",
    "latitude": "55.75",
    "altitude": "156",
    "trueDirection": "NE",
    "outsideTemperature": "18",
    "insideTemperature": "22",
    "outsideAirQuality": "Good",
    "insideAirQuality": "Good",
    "motorHours": "1240.5",
    "motorHoursTrip": "1.2",
    "motorHoursWidget": "1240 / 1.2",
    "timeWidget": "16:05",
    "dateWidget": "27.07.2026",
    "activeTripWidget": "42 км · 0:38",
    "activeTripWidgetSimple": "42 км",
    "activeTripWidgetMini": "42",
    "activeTripWidgetCustom": "42 км · 8.1 л",
    "netWidget": "LTE 4",
    "netWidgetNew": "LTE 4",
    "netWidgetColored": "LTE 4",
    "locWidget": "OK",
    "voltage+engineTemperatureWidget": "14.2 В · 92 °C",
    "gearBoxWidget": "D · 78 °C",
    "gearBoxModeCurrentGear": "D3",
    "driveModeWidget": "Normal",
    "wheel1Pressure": "2.4",
    "wheel2Pressure": "2.4",
    "wheel3Pressure": "2.3",
    "wheel4Pressure": "2.3",
    "wheel1Temperature": "28",
    "wheel2Temperature": "29",
    "wheel3Temperature": "27",
    "wheel4Temperature": "27",
    "wheelsPressureWidget": "2.4",
    "wheelsPressureTemperatureWidget": "2.4 / 28",
    "tempInOutWidget": "18 / 22",
    "fuelLevelWidget": "64% · 35 л",
    "airQualityWidget": "Good",
    "steeringWheelHeatWidget": "ON",
    "wiperMaintenanceWidget": "OFF",
    "parkingRadarWidget": "ON",
    "slaSpeedLimitWidget": "60",
    "frontWindscreenHeatWidget": "OFF",
    "rearWindowMirrorsDefrostWidget": "OFF",
    "hvacAirRecirculationWidget": "OFF",
    "hvacAcWidget": "ON",
    "hvacAutoWidget": "AUTO",
    "hvacDefrosterFrontWidget": "OFF",
    "hvacSyncWidget": "SYNC",
    "hvacFanWidgetHorizontal": "3",
    "hvacFanWidgetVertical": "3",
    "hvacTempLeftWidgetHorizontal": "22",
    "hvacTempLeftWidgetVertical": "22",
    "hvacTempRightWidgetHorizontal": "22",
    "hvacTempRightWidgetVertical": "22",
    "hvacBlowModeCycleWidget": "FACE",
    "hvacBlowModePanelWidgetHorizontal": "FACE",
    "hvacBlowModePanelWidgetVertical": "FACE",
    "trunkDoorWidget": "Closed",
    "mirrorAdjustModeWidget": "OFF",
    "mirrorFoldWidget": "Unfolded",
    "dayNightThemeWidget": "Day",
    "frontLeftSeatHeatVentWidget": "Heat 2",
    "frontRightSeatHeatVentWidget": "Vent 1",
    "frontLeftSeatHeatVentSingleWidget": "Heat 2",
    "frontRightSeatHeatVentSingleWidget": "Vent 1",
    "rearLeftSeatHeatWidget": "OFF",
    "rearRightSeatHeatWidget": "OFF",
    "musicWidget": "Artist — Title",
    "musicButtonsWidgetHorizontal": "▶",
    "musicButtonsWidgetVertical": "▶",
    "mediaVolumeWidgetHorizontal": "12",
    "mediaVolumeWidgetVertical": "12",
    "appLauncherWidget": "App",
    "httpRequestWidget": "OK",
    "emptyTileWidget": "",
    "restartTbox": "Restart",
    "externalAppWidget": "Widget",
    "hideFloatingPanelsWidget": "Hide",
    "toggleFloatingPanelsEnabledWidget": "On/Off",
}

NO_UNIT = {
    "netWidget", "netWidgetNew", "netWidgetColored", "locWidget", "airQualityWidget",
    "steeringWheelHeatWidget", "parkingRadarWidget", "slaSpeedLimitWidget",
    "frontWindscreenHeatWidget", "rearWindowMirrorsDefrostWidget",
    "hvacAirRecirculationWidget", "hvacAcWidget", "hvacAutoWidget", "hvacDefrosterFrontWidget",
    "hvacSyncWidget", "hvacFanWidgetHorizontal", "hvacFanWidgetVertical",
    "hvacTempLeftWidgetHorizontal", "hvacTempLeftWidgetVertical",
    "hvacTempRightWidgetHorizontal", "hvacTempRightWidgetVertical",
    "hvacBlowModeCycleWidget", "hvacBlowModePanelWidgetHorizontal", "hvacBlowModePanelWidgetVertical",
    "trunkDoorWidget", "mirrorAdjustModeWidget", "mirrorFoldWidget", "dayNightThemeWidget",
    "frontLeftSeatHeatVentWidget", "frontRightSeatHeatVentWidget",
    "frontLeftSeatHeatVentSingleWidget", "frontRightSeatHeatVentSingleWidget",
    "rearLeftSeatHeatWidget", "rearRightSeatHeatWidget", "externalAppWidget",
    "appLauncherWidget", "emptyTileWidget", "musicWidget",
    "musicButtonsWidgetHorizontal", "musicButtonsWidgetVertical",
    "mediaVolumeWidgetHorizontal", "mediaVolumeWidgetVertical",
    "hideFloatingPanelsWidget", "toggleFloatingPanelsEnabledWidget",
    "httpRequestWidget", "timeWidget", "dateWidget", "driveModeWidget",
    "gearBoxModeCurrentGear", "wiperMaintenanceWidget", "restartTbox",
}
DUAL = {
    "gearBoxWidget", "motorHoursWidget", "tempInOutWidget",
    "voltage+engineTemperatureWidget", "fuelLevelWidget", "airQualityWidget",
    "frontLeftSeatHeatVentWidget", "frontRightSeatHeatVentWidget",
}
MBCAN = {
    "mediaVolumeWidgetHorizontal", "mediaVolumeWidgetVertical",
    "engineRPM", "engineTemperature", "carSpeed", "odometer",
    "fuelLevelPercentage", "outsideTemperature",
}
MUSIC = {
    "musicWidget", "musicButtonsWidgetHorizontal", "musicButtonsWidgetVertical",
}
STEPPER = {
    "hvacFanWidgetHorizontal", "hvacFanWidgetVertical",
    "hvacTempLeftWidgetHorizontal", "hvacTempLeftWidgetVertical",
    "hvacTempRightWidgetHorizontal", "hvacTempRightWidgetVertical",
    "mediaVolumeWidgetHorizontal", "mediaVolumeWidgetVertical",
}
TRIP = {
    "activeTripWidget", "activeTripWidgetSimple", "activeTripWidgetMini", "activeTripWidgetCustom",
}
DATETIME = {"timeWidget", "dateWidget"}

lines: list[str] = []
lines.append('"""Widget catalog for TBox Theme Editor (ported from WidgetsRepository)."""')
lines.append("from __future__ import annotations")
lines.append("")
lines.append("from dataclasses import dataclass")
lines.append("")
lines.append("")
lines.append("@dataclass(frozen=True)")
lines.append("class WidgetType:")
lines.append("    data_key: str")
lines.append("    title: str")
lines.append("    unit: str = \"\"")
lines.append("    sample: str = \"\"")
lines.append("    supports_show_unit: bool = True")
lines.append("    supports_value_accuracy: bool = True")
lines.append("    supports_single_line_dual: bool = False")
lines.append("    supports_mbcan: bool = False")
lines.append("    supports_datetime_format: bool = False")
lines.append("    supports_stepper_icons: bool = False")
lines.append("    is_music: bool = False")
lines.append("    is_launcher: bool = False")
lines.append("    is_http: bool = False")
lines.append("    is_trip: bool = False")
lines.append("    is_drive_mode: bool = False")
lines.append("")
lines.append("")
lines.append("def _entry(")
lines.append("    data_key: str,")
lines.append("    title: str,")
lines.append("    unit: str = \"\",")
lines.append("    sample: str = \"\",")
lines.append(") -> WidgetType:")
lines.append("    trip = data_key in TRIP_KEYS")
lines.append("    no_unit = data_key in NO_SHOW_UNIT or trip or not data_key")
lines.append("    no_acc = data_key in NO_VALUE_ACCURACY or trip or not data_key")
lines.append("    return WidgetType(")
lines.append("        data_key=data_key,")
lines.append("        title=title,")
lines.append("        unit=unit,")
lines.append("        sample=sample,")
lines.append("        supports_show_unit=not no_unit,")
lines.append("        supports_value_accuracy=not no_acc,")
lines.append("        supports_single_line_dual=data_key in DUAL_KEYS,")
lines.append("        supports_mbcan=data_key in MBCAN_KEYS,")
lines.append("        supports_datetime_format=data_key in DATETIME_KEYS,")
lines.append("        supports_stepper_icons=data_key in STEPPER_KEYS,")
lines.append("        is_music=data_key in MUSIC_KEYS,")
lines.append("        is_launcher=data_key == \"appLauncherWidget\",")
lines.append("        is_http=data_key == \"httpRequestWidget\",")
lines.append("        is_trip=trip,")
lines.append("        is_drive_mode=data_key == \"driveModeWidget\",")
lines.append("    )")
lines.append("")
lines.append("")
lines.append(f"NO_SHOW_UNIT = {sorted(NO_UNIT)!r}")
lines.append(f"NO_VALUE_ACCURACY = {sorted(NO_UNIT | {'restartTbox'})!r}")
lines.append(f"DUAL_KEYS = {sorted(DUAL)!r}")
lines.append(f"MBCAN_KEYS = {sorted(MBCAN)!r}")
lines.append(f"MUSIC_KEYS = {sorted(MUSIC)!r}")
lines.append(f"STEPPER_KEYS = {sorted(STEPPER)!r}")
lines.append(f"TRIP_KEYS = {sorted(TRIP)!r}")
lines.append(f"DATETIME_KEYS = {sorted(DATETIME)!r}")
lines.append("")
lines.append("")
lines.append("WIDGET_TYPES: list[WidgetType] = [")
lines.append('    _entry("", "(пусто)", sample=""),')
for key, title, unit in entries:
    sample = SAMPLES.get(key, "—")
    lines.append(
        f"    _entry({key!r}, {title!r}, {unit!r}, {sample!r}),"
    )
lines.append("]")
lines.append("")
lines.append("")
lines.append("WIDGET_BY_KEY: dict[str, WidgetType] = {w.data_key: w for w in WIDGET_TYPES}")
lines.append("")
lines.append("")
lines.append("def get_widget_type(data_key: str) -> WidgetType:")
lines.append("    return WIDGET_BY_KEY.get(data_key) or _entry(data_key, data_key or \"(пусто)\")")
lines.append("")
lines.append("")
lines.append("def format_sample_value(data_key: str, value_accuracy: int | None = None) -> str:")
lines.append("    w = get_widget_type(data_key)")
lines.append("    sample = w.sample")
lines.append("    if value_accuracy is None or sample in {\"\", \"—\"}:")
lines.append("        return sample")
lines.append("    try:")
lines.append("        num = float(sample.replace(\",\", \".\"))")
lines.append("    except ValueError:")
lines.append("        return sample")
lines.append("    if value_accuracy == 0:")
lines.append("        return str(int(round(num)))")
lines.append("    return f\"{num:.{value_accuracy}f}\"")
lines.append("")

OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
print(f"Wrote {OUT} with {len(entries)} widgets")
