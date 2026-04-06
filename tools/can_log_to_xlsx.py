#!/usr/bin/env python3
"""
Convert TBox Monitor CAN export (.txt) to a wide Excel table.

Parses lines saved by the app (timestamp; CAN id hex; payload hex; ; dec bytes...)
and applies the same decoding rules as CanFramesProcess.kt. Column headers match
Russian titles (+ units) from strings.xml / CarDataTabContent (via WidgetsRepository).
Trailing columns: for every CAN ID seen in the file, all 8 payload bytes as UINT8 (0–255);
headers «00 00 00 XX : 0» … : 7; last value carried forward per ID (alongside decoded fields).
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Optional

from openpyxl import Workbook
from tqdm import tqdm

# --- CAN IDs (same as CanFramesProcess.kt) ---
CAN_ID_STEER = 0x000000C4
CAN_ID_ENGINE_PARAMS = 0x000000FA
CAN_ID_PARAM_3 = 0x00000200
CAN_ID_PARAM_4 = 0x00000278
CAN_ID_DISTANCE_TO_MAINTENANCE = 0x00000287
CAN_ID_BREAKING_FORCE = 0x000002E9
CAN_ID_GEARBOX = 0x00000300
CAN_ID_CRUISE = 0x00000305
CAN_ID_WHEEL_SPEED = 0x00000310
CAN_ID_SPEED_VOLTAGE_FUEL = 0x00000430
CAN_ID_FUEL_CONSUMPTION = 0x000004E0
CAN_ID_ENGINE_TEMP = 0x00000501
CAN_ID_SPEED_ACCURATE = 0x00000502
CAN_ID_WHEELS_TPMS = 0x0000051B
CAN_ID_CLIMATE_SET = 0x0000052F
CAN_ID_DISTANCE_TO_FUEL_EMPTY = 0x00000530
CAN_ID_IN_OUT_TEMP = 0x00000535
CAN_ID_AIR_QUALITY = 0x0000053A
CAN_ID_SEAT_MODES = 0x000005C4
CAN_ID_WINDOWS_BLOCKED = 0x000005FF

GEAR_BOX_7_DRIVE_MODES = {0x1B, 0x2B, 0x3B, 0x4B, 0x5B, 0x6B, 0x7B}
GEAR_BOX_7_PREPARED_DRIVE_MODES = {0x10, 0x20, 0x30, 0x40, 0x50, 0x60, 0x70}

# Russian titles + units (default ru flavor / values/strings.xml), "title, unit" where the app uses units
RU_COLUMNS: list[tuple[str, str]] = [
    ("datetime", "Дата и время"),
    ("voltage", "Напряжение, В"),
    ("steer_angle", "Угол поворота руля, °"),
    ("steer_speed", "Скорость вращения руля"),
    ("engine_rpm", "Обороты двигателя, об/мин"),
    ("param1", "Параметр 1"),
    ("param2", "Параметр 2"),
    ("param3", "Параметр 3"),
    ("param4", "Параметр 4"),
    ("throttle_position", "Параметр 5"),
    ("car_speed", "Скорость автомобиля, км/ч"),
    ("car_speed_accurate", "Точная скорость автомобиля, км/ч"),
    ("wheel1_speed", "Скорость колеса 1, км/ч"),
    ("wheel2_speed", "Скорость колеса 2, км/ч"),
    ("wheel3_speed", "Скорость колеса 3, км/ч"),
    ("wheel4_speed", "Скорость колеса 4, км/ч"),
    ("wheel1_pressure", "Давление колеса ПЛ, бар"),
    ("wheel2_pressure", "Давление колеса ПП, бар"),
    ("wheel3_pressure", "Давление колеса ЗЛ, бар"),
    ("wheel4_pressure", "Давление колеса ЗП, бар"),
    ("wheel1_temperature", "Температура колеса ПЛ, °C"),
    ("wheel2_temperature", "Температура колеса ПП, °C"),
    ("wheel3_temperature", "Температура колеса ЗЛ, °C"),
    ("wheel4_temperature", "Температура колеса ЗП, °C"),
    ("cruise_set_speed", "Скорость круиз-контроля, км/ч"),
    ("odometer", "Одометр, км"),
    ("distance_to_next_maintenance", "Пробег до следующего ТО, км"),
    ("distance_to_fuel_empty", "Пробег на остатке топлива, км"),
    ("fuel_level_percentage", "Уровень топлива, %"),
    ("fuel_level_percentage_filtered", "Уровень топлива (сглажено), %"),
    ("fuel_level_liters", "Уровень топлива в литрах, л"),
    ("current_fuel_consumption", "Мгновенный расход топлива, л/100км"),
    ("breaking_force", "Усилие торможения"),
    ("engine_temperature", "Температура двигателя, °C"),
    ("gearbox_oil_temperature", "Температура масла КПП, °C"),
    ("gearbox_mode", "Режим КПП"),
    ("gearbox_drive_mode", "Режим движения КПП"),
    ("gearbox_work", "Работа КПП"),
    ("gearbox_current_gear", "Текущая передача КПП"),
    ("gearbox_prepared_gear", "Приготовленная передача КПП"),
    ("gearbox_change_gear", "Выполнение переключения"),
    ("front_left_seat_mode", "Режим левого переднего сиденья"),
    ("front_right_seat_mode", "Режим правого переднего сиденья"),
    ("outside_temperature", "Температура на улице, °C"),
    ("inside_temperature", "Температура в машине, °C"),
    ("outside_air_quality", "Качество воздуха на улице"),
    ("inside_air_quality", "Качество воздуха в машине"),
    ("is_windows_blocked", "Блокировка окон"),
    ("motor_hours", "Моточасы двигателя, ч"),
    ("motor_hours_trip", "Моточасы двигателя за поездку, ч"),
]


SEAT_MODE_LABELS: dict[int, str] = {
    1: "выключено",
    2: "обогрев 1",
    3: "обогрев 2",
    4: "обогрев 3",
    5: "вентиляция 1",
    6: "вентиляция 2",
    7: "вентиляция 3",
}

BOOL_SWITCHING = "переключение"
BOOL_NO = "нет"
BOOL_BLOCKED = "заблокированы"
BOOL_UNBLOCKED = "разблокированы"


def u8(b: int) -> int:
    return b & 0xFF


def s8(b: int) -> int:
    """Kotlin Byte.toInt() style (signed 8-bit)."""
    v = u8(b)
    return v - 256 if v >= 128 else v


def read_u16_be(data: bytes, offset: int) -> int:
    return (u8(data[offset]) << 8) | u8(data[offset + 1])


def read_u12_nibble_be(data: bytes, offset: int) -> int:
    return ((u8(data[offset]) & 0x0F) << 8) | u8(data[offset + 1])


def read_u20_nibble_be(data: bytes, offset: int) -> int:
    return ((u8(data[offset]) & 0x0F) << 16) | (u8(data[offset + 1]) << 8) | u8(data[offset + 2])


def read_can_id(data: bytes, offset: int) -> int:
    return (
        (u8(data[offset]) << 24)
        | (u8(data[offset + 1]) << 16)
        | (u8(data[offset + 2]) << 8)
        | u8(data[offset + 3])
    )


def extract_bits(byte_val: int, start_pos: int, length: int) -> int:
    mask = (1 << length) - 1
    return (u8(byte_val) >> start_pos) & mask


def left_nibble(b: int) -> int:
    return (u8(b) >> 4) & 0x0F


def right_nibble(b: int) -> int:
    return u8(b) & 0x0F


def hex_byte(b: int) -> str:
    return f"{u8(b):02X}"


def can_id_to_hex_spaced(can_id: int) -> str:
    """Same visual form as in the exported log (big-endian bytes)."""
    return " ".join(f"{(can_id >> s) & 0xFF:02X}" for s in (24, 16, 8, 0))


def fmt_float(v: Optional[float], decimals: int) -> Any:
    if v is None:
        return ""
    if decimals == 0:
        return int(round(v))
    return round(v, decimals)


def fmt_int(v: Optional[int]) -> Any:
    if v is None:
        return ""
    return v


class FuelLevelBuffer:
    """Same logic as vad.dashing.tbox.utils.FuelLevelBuffer (size 15 in CanFramesProcess)."""

    def __init__(self, buffer_size: int = 15) -> None:
        self._buffer_size = buffer_size
        self._buf: list[int] = []

    def add_value(self, new_value: int) -> bool:
        self._buf.append(new_value & 0xFFFF)
        if len(self._buf) > self._buffer_size:
            self._buf.pop(0)
        if len(self._buf) < self._buffer_size:
            return False
        first = self._buf[0]
        return all(x == first for x in self._buf)


@dataclass
class WheelsF:
    w1: Optional[float] = None
    w2: Optional[float] = None
    w3: Optional[float] = None
    w4: Optional[float] = None


@dataclass
class CarState:
    voltage: Optional[float] = None
    steer_angle: Optional[float] = None
    steer_speed: Optional[int] = None
    engine_rpm: Optional[float] = None
    param1: Optional[float] = None
    param2: Optional[float] = None
    param3: Optional[float] = None
    param4: Optional[float] = None
    throttle_position: Optional[float] = None
    car_speed: Optional[float] = None
    car_speed_accurate: Optional[float] = None
    wheels_speed: WheelsF = field(default_factory=WheelsF)
    wheels_pressure: WheelsF = field(default_factory=WheelsF)
    wheels_temperature: WheelsF = field(default_factory=WheelsF)
    cruise_set_speed: Optional[int] = None
    odometer: Optional[int] = None
    distance_to_next_maintenance: Optional[int] = None
    distance_to_fuel_empty: Optional[int] = None
    fuel_level_percentage: Optional[int] = None
    fuel_level_percentage_filtered: Optional[int] = None
    current_fuel_consumption: Optional[float] = None
    breaking_force: Optional[int] = None
    engine_temperature: Optional[float] = None
    gearbox_oil_temperature: Optional[int] = None
    gearbox_mode: str = "N/A"
    gearbox_drive_mode: str = "N/A"
    gearbox_work: str = ""
    gearbox_current_gear: int = 0
    gearbox_prepared_gear: int = 0
    gearbox_change_gear: Optional[bool] = None
    front_left_seat_mode: Optional[int] = None
    front_right_seat_mode: Optional[int] = None
    outside_temperature: Optional[float] = None
    inside_temperature: Optional[float] = None
    outside_air_quality: Optional[int] = None
    inside_air_quality: Optional[int] = None
    is_windows_blocked: Optional[bool] = None
    climate_set_temperature1: Optional[float] = None  # decoded but not in CarDataTab — kept for parity
    car_type: str = "1.5_6MT"
    _fuel_buf: FuelLevelBuffer = field(default_factory=FuelLevelBuffer)

    def seat_label(self, mode: Optional[int]) -> str:
        if mode is None:
            return ""
        return SEAT_MODE_LABELS.get(mode, str(mode))

    def row_values(self, dt_str: str, tank_liters: Optional[float]) -> list[Any]:
        flt = self.fuel_level_percentage_filtered
        fuel_l = ""
        if flt is not None and tank_liters is not None:
            fuel_l = round(flt * tank_liters / 100.0, 1)

        gcg = self.gearbox_change_gear
        gcg_s = ""
        if gcg is not None:
            gcg_s = BOOL_SWITCHING if gcg else BOOL_NO

        win = self.is_windows_blocked
        win_s = ""
        if win is not None:
            win_s = BOOL_BLOCKED if win else BOOL_UNBLOCKED

        return [
            dt_str,
            fmt_float(self.voltage, 1),
            fmt_float(self.steer_angle, 1),
            fmt_int(self.steer_speed),
            fmt_float(self.engine_rpm, 1),
            fmt_float(self.param1, 1),
            fmt_float(self.param2, 1),
            fmt_float(self.param3, 1),
            fmt_float(self.param4, 1),
            fmt_float(self.throttle_position, 1),
            fmt_float(self.car_speed, 1),
            fmt_float(self.car_speed_accurate, 1),
            fmt_float(self.wheels_speed.w1, 1),
            fmt_float(self.wheels_speed.w2, 1),
            fmt_float(self.wheels_speed.w3, 1),
            fmt_float(self.wheels_speed.w4, 1),
            fmt_float(self.wheels_pressure.w1, 2),
            fmt_float(self.wheels_pressure.w2, 2),
            fmt_float(self.wheels_pressure.w3, 2),
            fmt_float(self.wheels_pressure.w4, 2),
            fmt_float(self.wheels_temperature.w1, 0),
            fmt_float(self.wheels_temperature.w2, 0),
            fmt_float(self.wheels_temperature.w3, 0),
            fmt_float(self.wheels_temperature.w4, 0),
            fmt_int(self.cruise_set_speed),
            fmt_int(self.odometer),
            fmt_int(self.distance_to_next_maintenance),
            fmt_int(self.distance_to_fuel_empty),
            fmt_int(self.fuel_level_percentage),
            fmt_int(self.fuel_level_percentage_filtered),
            fuel_l,
            fmt_float(self.current_fuel_consumption, 1),
            fmt_int(self.breaking_force),
            fmt_float(self.engine_temperature, 1),
            fmt_int(self.gearbox_oil_temperature),
            self.gearbox_mode,
            self.gearbox_drive_mode,
            self.gearbox_work,
            self.gearbox_current_gear,
            self.gearbox_prepared_gear,
            gcg_s,
            self.seat_label(self.front_left_seat_mode),
            self.seat_label(self.front_right_seat_mode),
            fmt_float(self.outside_temperature, 1),
            fmt_float(self.inside_temperature, 1),
            fmt_int(self.outside_air_quality),
            fmt_int(self.inside_air_quality),
            win_s,
            "",  # motor_hours — not from CAN log
            "",  # motor_hours_trip — not from CAN log
        ]

    def process_frame(self, can_id: int, payload: bytes) -> None:
        if len(payload) < 8:
            return
        b = list(payload[:8])

        if can_id == CAN_ID_STEER:
            angle_raw = read_u16_be(payload, 0)
            if angle_raw == 65535:
                self.steer_angle = None
            else:
                self.steer_angle = (angle_raw - 32767) / 16.0
            self.steer_speed = s8(b[2])

        elif can_id == CAN_ID_ENGINE_PARAMS:
            self.engine_rpm = read_u16_be(payload, 0) / 4.0
            self.param1 = u8(b[3]) / 100.0
            self.param2 = float(read_u16_be(payload, 4))

        elif can_id == CAN_ID_PARAM_3:
            self.param3 = float(read_u16_be(payload, 4))

        elif can_id == CAN_ID_PARAM_4:
            self.engine_temperature = u8(b[0]) * 0.75 - 48.0
            self.param4 = float(u8(b[5]))

        elif can_id == CAN_ID_DISTANCE_TO_MAINTENANCE:
            self.distance_to_next_maintenance = read_u16_be(payload, 4)

        elif can_id == CAN_ID_BREAKING_FORCE:
            self.breaking_force = u8(b[2])

        elif can_id == CAN_ID_GEARBOX:
            if self.car_type != "1.6":
                self.car_type = "1.5_6DCT"
            b0 = b[0]
            if b0 in GEAR_BOX_7_DRIVE_MODES:
                self.gearbox_mode = "D"
                self.gearbox_current_gear = left_nibble(b0)
            elif b0 == 0xBE:
                self.gearbox_mode = "P"
                self.gearbox_current_gear = 0
            elif b0 == 0xAC:
                self.gearbox_mode = "N"
                self.gearbox_current_gear = 0
            elif b0 == 0xAD:
                self.gearbox_mode = "R"
                self.gearbox_current_gear = 0
            else:
                self.gearbox_mode = "N/A"
                self.gearbox_current_gear = 0

            self.gearbox_change_gear = extract_bits(b[1], 6, 1) == 1
            dm = right_nibble(b[1])
            self.gearbox_drive_mode = {0: "ECO", 1: "NOR", 2: "SPT"}.get(dm, "N/A")
            self.gearbox_oil_temperature = u8(b[2]) - 40
            b3 = b[3]
            if b3 in GEAR_BOX_7_PREPARED_DRIVE_MODES:
                self.gearbox_prepared_gear = (u8(b3) & 0xF0) >> 4
            else:
                self.gearbox_prepared_gear = 0

            b5 = b[5]
            work_map = {0x00: "0", 0xA1: "1", 0x5E: "2", 0x42: "3", 0x30: "4", 0x26: "5", 0x1F: "6", 0x1B: "7"}
            self.gearbox_work = work_map.get(b5, hex_byte(b5))

        elif can_id == CAN_ID_CRUISE:
            self.car_type = "1.6"
            self.cruise_set_speed = u8(b[0])

        elif can_id == CAN_ID_WHEEL_SPEED:
            self.wheels_speed = WheelsF(
                read_u16_be(payload, 0) / 16.0,
                read_u16_be(payload, 2) / 16.0,
                read_u16_be(payload, 4) / 16.0,
                read_u16_be(payload, 6) / 16.0,
            )

        elif can_id == CAN_ID_SPEED_VOLTAGE_FUEL:
            self.car_speed = read_u16_be(payload, 0) / 16.0
            self.voltage = u8(b[2]) / 10.0
            self.fuel_level_percentage = u8(b[4])
            self.odometer = read_u20_nibble_be(payload, 5)
            if self._fuel_buf.add_value(self.fuel_level_percentage):
                self.fuel_level_percentage_filtered = self.fuel_level_percentage

        elif can_id == CAN_ID_FUEL_CONSUMPTION:
            if b[2] != 0xFF and b[3] != 0xFF:
                self.current_fuel_consumption = read_u16_be(payload, 2) / 160.0
            else:
                self.current_fuel_consumption = None

        elif can_id == CAN_ID_ENGINE_TEMP:
            self.engine_temperature = u8(b[2]) * 0.75 - 48.0
            self.throttle_position = float(u8(b[4]))

        elif can_id == CAN_ID_SPEED_ACCURATE:
            if b[2] != 0:
                self.car_speed_accurate = read_u12_nibble_be(payload, 1) / 16.0
            else:
                self.car_speed_accurate = 0.0

        elif can_id == CAN_ID_WHEELS_TPMS:
            wt = self.wheels_temperature
            idx = b[2]
            temp_val: Optional[float]
            if b[3] != 0xFF:
                temp_val = float(u8(b[3]) - 60)
            else:
                temp_val = None
            if idx == 0:
                wt = WheelsF(temp_val, wt.w2, wt.w3, wt.w4)
            elif idx == 1:
                wt = WheelsF(wt.w1, temp_val, wt.w3, wt.w4)
            elif idx == 2:
                wt = WheelsF(wt.w1, wt.w2, temp_val, wt.w4)
            elif idx == 3:
                wt = WheelsF(wt.w1, wt.w2, wt.w3, temp_val)
            self.wheels_temperature = wt

            def p(x: int) -> Optional[float]:
                return None if x == 0xFF else u8(x) / 36.0

            self.wheels_pressure = WheelsF(p(b[4]), p(b[5]), p(b[6]), p(b[7]))

        elif can_id == CAN_ID_CLIMATE_SET:
            st = u8(b[5]) / 4.0
            if st != 0:
                self.climate_set_temperature1 = st

        elif can_id == CAN_ID_DISTANCE_TO_FUEL_EMPTY:
            self.distance_to_fuel_empty = read_u16_be(payload, 2)

        elif can_id == CAN_ID_IN_OUT_TEMP:
            inside_t = u8(b[5]) * 0.5 - 40.0
            outside_t = u8(b[6]) * 0.5 - 40.0
            if -40.0 <= outside_t < 87.0:
                self.outside_temperature = outside_t
            else:
                self.outside_temperature = None
            if -40.0 <= inside_t < 87.0:
                self.inside_temperature = inside_t
            else:
                self.inside_temperature = None

        elif can_id == CAN_ID_AIR_QUALITY:
            inside_q = read_u16_be(payload, 0)
            outside_q = read_u16_be(payload, 2)
            if 0 < inside_q < 65535:
                self.inside_air_quality = inside_q
            else:
                self.inside_air_quality = None
            if 0 < outside_q < 65535:
                self.outside_air_quality = outside_q
            else:
                self.outside_air_quality = None

        elif can_id == CAN_ID_SEAT_MODES:
            self.front_right_seat_mode = extract_bits(b[4], 3, 3)
            self.front_left_seat_mode = extract_bits(b[4], 0, 3)

        elif can_id == CAN_ID_WINDOWS_BLOCKED:
            self.is_windows_blocked = extract_bits(b[4], 0, 1) == 1


_LINE_RE = re.compile(
    r"^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2});"
    r"([0-9A-Fa-f]{2}(?: [0-9A-Fa-f]{2}){3});"
    r"([0-9A-Fa-f]{2}(?: [0-9A-Fa-f]{2}){7})"
)


def parse_hex_bytes(chunk: str) -> bytes:
    parts = chunk.strip().split()
    return bytes(int(p, 16) for p in parts)


def parse_can_line(line: str) -> Optional[tuple[str, int, bytes]]:
    line = line.strip()
    if not line:
        return None
    m = _LINE_RE.match(line)
    if not m:
        return None
    ts, can_id_s, payload_s = m.groups()
    can_id_bytes = parse_hex_bytes(can_id_s)
    payload = parse_hex_bytes(payload_s)
    if len(can_id_bytes) != 4 or len(payload) != 8:
        return None
    cid = int.from_bytes(can_id_bytes, "big")
    return ts, cid, payload


def scan_file_for_can_ids(
    path: Path,
    show_progress: bool,
) -> tuple[list[int], int]:
    """Collect non-zero CAN IDs and count lines (for progress on the second pass)."""
    ids: set[int] = set()
    n_lines = 0
    with path.open(encoding="utf-8", errors="replace") as f:
        for raw in tqdm(
            f,
            desc="Сканирование файла",
            unit="стр",
            disable=not show_progress,
            file=sys.stderr,
        ):
            n_lines += 1
            parsed = parse_can_line(raw)
            if not parsed:
                continue
            _, cid, _ = parsed
            if cid != 0:
                ids.add(cid)
    return sorted(ids), n_lines


def raw_column_headers(sorted_can_ids: list[int]) -> list[str]:
    headers: list[str] = []
    for cid in sorted_can_ids:
        prefix = can_id_to_hex_spaced(cid)
        for byte_idx in range(8):
            headers.append(f"{prefix} : {byte_idx}")
    return headers


def append_raw_uint_row(
    row: list[Any],
    sorted_can_ids: list[int],
    last_raw_by_id: dict[int, tuple[int, ...]],
) -> None:
    """Append UINT8 (0–255) for each byte of last seen payload per CAN ID."""
    for cid in sorted_can_ids:
        tup = last_raw_by_id.get(cid)
        if tup is None:
            row.extend([""] * 8)
        else:
            row.extend(tup)


def process_file(
    path: Path,
    tank_liters: Optional[float],
    show_progress: bool,
) -> tuple[list[list[Any]], list[str]]:
    sorted_can_ids, n_lines = scan_file_for_can_ids(path, show_progress)
    raw_headers = raw_column_headers(sorted_can_ids)
    headers = [t for _, t in RU_COLUMNS] + raw_headers

    state = CarState()
    last_raw_by_id: dict[int, tuple[int, ...]] = {}
    rows: list[list[Any]] = []

    with path.open(encoding="utf-8", errors="replace") as f:
        for raw in tqdm(
            f,
            total=n_lines,
            desc="Разбор CAN и строк таблицы",
            unit="стр",
            disable=not show_progress,
            file=sys.stderr,
        ):
            parsed = parse_can_line(raw)
            if not parsed:
                continue
            ts, can_id, payload = parsed
            if can_id == 0:
                continue
            state.process_frame(can_id, payload)
            last_raw_by_id[can_id] = tuple(u8(x) for x in payload[:8])
            row = state.row_values(ts, tank_liters)
            append_raw_uint_row(row, sorted_can_ids, last_raw_by_id)
            rows.append(row)

    return rows, headers


def write_xlsx(
    rows: list[list[Any]],
    headers: list[str],
    out_path: Path,
    show_progress: bool,
) -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "CAN"
    ws.append(headers)
    for row in tqdm(
        rows,
        desc="Запись строк в XLSX",
        unit="стр",
        disable=not show_progress,
        file=sys.stderr,
    ):
        ws.append(row)
    if show_progress:
        tqdm.write(f"Сохранение файла: {out_path}", file=sys.stderr)
    wb.save(out_path)


def main() -> None:
    ap = argparse.ArgumentParser(
        description="Convert TBox Monitor CAN export .txt to XLSX (decoded like CanFramesProcess.kt)."
    )
    ap.add_argument("input", type=Path, help="Path to tbox_can_*.txt")
    ap.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output .xlsx path (default: same basename as input)",
    )
    ap.add_argument(
        "--tank-liters",
        type=float,
        default=None,
        metavar="L",
        help="Fuel tank volume in liters (for «Уровень топлива в литрах» column, like app settings)",
    )
    ap.add_argument(
        "-q",
        "--quiet",
        action="store_true",
        help="Не показывать прогресс (полезно в скриптах и при перенаправлении вывода)",
    )
    args = ap.parse_args()
    inp: Path = args.input
    out = args.output or inp.with_suffix(".xlsx")
    show_progress = not args.quiet
    rows, headers = process_file(inp, args.tank_liters, show_progress)
    write_xlsx(rows, headers, out, show_progress)
    print(f"Wrote {len(rows)} rows to {out}")


if __name__ == "__main__":
    main()
