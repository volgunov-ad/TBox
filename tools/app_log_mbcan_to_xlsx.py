#!/usr/bin/env python3
"""
Convert TBox Monitor app / deep-diagnostic journals (`tbox_app_log_*.txt`) to Excel.

Parses lines tagged ``MBCAN_TMP.``, ``CANDIAG_MBCAN.``, ``CANDIAG_VHAL.``, and
optionally ``TripFuel.`` into a timeline sheet plus a summary of distinct cfg /
property IDs marked known vs unknown against the app catalog
(``MbCanKnownVehiclePropertyId`` / ``MbCanKnownAudioPropertyId`` in
``MbCanCatalog.kt``).

Known/unknown is based on reflecting ``const val`` integer fields from those
Kotlin objects (same idea as ``DeepDiagnosticsCatalog.annotateMbCanItem``).
IDs absent from the catalog (e.g. 95, 80, 119 from A9 logs) are marked unknown.

Usage::

    python tools/app_log_mbcan_to_xlsx.py path/to/tbox_app_log_….txt
    python tools/app_log_mbcan_to_xlsx.py app_log.txt -o out.xlsx --no-trip-fuel

Requires ``openpyxl`` / ``tqdm`` from ``requirements.txt`` (same as ``can_log_to_xlsx.py``).
"""

from __future__ import annotations

import argparse
import csv
import re
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Optional

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from tqdm import tqdm

HEADER_ALIGNMENT = Alignment(
    text_rotation=90,
    horizontal="center",
    vertical="bottom",
    wrap_text=True,
)

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_CATALOG_KT = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "vad"
    / "dashing"
    / "tbox"
    / "mbcan"
    / "MbCanCatalog.kt"
)

LINE_RE = re.compile(
    r"^\[(\d{2}:\d{2}:\d{2})\]\s+(\w+):\s+"
    r"(MBCAN_TMP|CANDIAG_MBCAN|CANDIAG_VHAL|TripFuel)\.\s*(.*)$"
)
CONST_VAL_RE = re.compile(r"const val (\w+)\s*=\s*(-?\d+)")
OBJECT_BODY_RE = re.compile(
    r"object (MbCanKnownVehiclePropertyId|MbCanKnownAudioPropertyId)\s*\{",
    re.M,
)
ITEM_VALUE_RE = re.compile(r"\bitem=(-?\d+)\b")
VALUE_RE = re.compile(r"\bvalue=(-?\d+)\b")
PROPERTY_ID_RE = re.compile(r"\bpropertyId=(-?\d+)\b")
MODULAR_RE = re.compile(r"\bmodular=(-?\d+)\b")
REV_RE = re.compile(r"\brev=(-?\d+)\b")
DT_RE = re.compile(r"\bdt=(\S+)")
NAME_RE = re.compile(r"\bname=(\S+)")
AREA_ID_RE = re.compile(r"\bareaId=(-?\d+)\b")
TELEMETRY_ENTRY_RE = re.compile(
    r"(?P<key>[A-Za-z0-9_./+-]+)\s+count=(?P<count>\d+)\s+last=(?P<last>.*?)(?=\s*;\s*[^;]+?\s+count=\d+\s+last=|\s*$)"
)

# Telemetry keys the app coalesces under MBCAN_TMP.push_coalesced (reference list;
# unknown keys still appear in the timeline with known=blank).
KNOWN_TELEMETRY_KEYS: frozenset[str] = frozenset(
    {
        "telemetry/engine_rpm",
        "telemetry/car_speed",
        "telemetry/vehicle_gear",
        "telemetry/engine_temp",
        "telemetry/current_fuel",
        "telemetry/tires",
        "telemetry/steer",
        "telemetry/wheel_pulse",
        "telemetry/gas_pedal",
        "telemetry/turn_signals",
        "telemetry/brake_pedal",
        "telemetry/current_gear_number",
        "telemetry/epb_park_lamp",
        "telemetry/high_beam",
        "telemetry/rain_detected",
        "telemetry/reverse_gear_switch",
        "telemetry/trunk_bcm",
        "telemetry/wiper_sts",
        "telemetry/fuel_level",
        "telemetry/odometer",
        "telemetry/outside_temp",
        "gasped_ccs",
    }
)

TIMELINE_HEADERS = [
    "time",
    "level",
    "tag",
    "kind",
    "item_id",
    "property_id",
    "telemetry_key",
    "value",
    "known",
    "catalog_name",
    "modular",
    "rev",
    "data_type",
    "detail",
    "raw",
]

SUMMARY_HEADERS = [
    "item_or_property_id",
    "id_kind",
    "known",
    "catalog_name",
    "event_count",
    "distinct_values",
    "sample_values",
    "tags",
]


@dataclass(frozen=True)
class CatalogIndex:
    """id → preferred const name (alphabetical first-wins, like DeepDiagnosticsCatalog)."""

    id_to_name: dict[int, str] = field(default_factory=dict)
    source_path: Optional[Path] = None

    def name_for(self, item_id: int) -> Optional[str]:
        return self.id_to_name.get(item_id)

    def is_known(self, item_id: int) -> bool:
        return item_id in self.id_to_name


@dataclass
class LogEvent:
    time: str
    level: str
    tag: str
    kind: str
    item_id: Optional[int] = None
    property_id: Optional[int] = None
    telemetry_key: Optional[str] = None
    value: Optional[str] = None
    known: Optional[bool] = None
    catalog_name: Optional[str] = None
    modular: Optional[int] = None
    rev: Optional[int] = None
    data_type: Optional[str] = None
    detail: str = ""
    raw: str = ""


def _brace_body(text: str, open_brace_index: int) -> str:
    """Return text inside `{...}` starting at open_brace_index, respecting nesting."""
    depth = 0
    for i in range(open_brace_index, len(text)):
        ch = text[i]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return text[open_brace_index + 1 : i]
    raise ValueError("Unbalanced braces while parsing catalog Kotlin object")


def load_catalog_from_kotlin(path: Path) -> CatalogIndex:
    """
    Parse ``MbCanKnownVehiclePropertyId`` / ``MbCanKnownAudioPropertyId`` const ints.

    Mirrors ``DeepDiagnosticsCatalog.reflectIntFieldNames``: all public ``const val``
    ints, alphabetical name order, first-wins on id collisions (value enums stay in
    the map but property names usually win alphabetically, e.g. DOOR_AUTO_LOCK=1).
    """
    text = path.read_text(encoding="utf-8")
    by_id_names: dict[int, list[str]] = defaultdict(list)
    for match in OBJECT_BODY_RE.finditer(text):
        brace_at = text.find("{", match.end() - 1)
        if brace_at < 0:
            continue
        body = _brace_body(text, brace_at)
        for name, raw in CONST_VAL_RE.findall(body):
            by_id_names[int(raw)].append(name)
    id_to_name: dict[int, str] = {}
    for item_id, names in by_id_names.items():
        id_to_name[item_id] = sorted(names)[0]
    return CatalogIndex(id_to_name=id_to_name, source_path=path)


def parse_log_timestamp(line: str) -> Optional[str]:
    m = re.match(r"^\[(\d{2}:\d{2}:\d{2})\]", line)
    return m.group(1) if m else None


def extract_kv_int(pattern: re.Pattern[str], text: str) -> Optional[int]:
    m = pattern.search(text)
    return int(m.group(1)) if m else None


def extract_kv_str(pattern: re.Pattern[str], text: str) -> Optional[str]:
    m = pattern.search(text)
    return m.group(1) if m else None


def parse_push_coalesced_entries(body: str) -> list[tuple[str, int, str]]:
    """Parse ``key count=N last=…; …`` entries from a push_coalesced payload."""
    inner = body
    if inner.startswith("push_coalesced["):
        inner = inner[len("push_coalesced[") :]
        if inner.endswith("]"):
            inner = inner[:-1]
    entries: list[tuple[str, int, str]] = []
    for m in TELEMETRY_ENTRY_RE.finditer(inner):
        entries.append((m.group("key"), int(m.group("count")), m.group("last").strip()))
    return entries


def parse_trip_fuel_values(body: str) -> dict[str, str]:
    """Extract ``values[k=v …]`` pairs from a TripFuel line body."""
    m = re.search(r"values\[([^\]]*)\]", body)
    if not m:
        return {}
    out: dict[str, str] = {}
    for part in m.group(1).split():
        if "=" not in part:
            continue
        k, v = part.split("=", 1)
        out[k] = v
    return out


def parse_trip_fuel_sources(body: str) -> str:
    m = re.search(r"sources\[([^\]]*)\]", body)
    return m.group(1).strip() if m else ""


def annotate_item(catalog: CatalogIndex, item_id: Optional[int]) -> tuple[Optional[bool], Optional[str]]:
    if item_id is None:
        return None, None
    name = catalog.name_for(item_id)
    return catalog.is_known(item_id), name


def annotate_telemetry_key(key: Optional[str]) -> tuple[Optional[bool], Optional[str]]:
    if key is None:
        return None, None
    if key in KNOWN_TELEMETRY_KEYS:
        return True, key
    return False, None


def parse_line(line: str, catalog: CatalogIndex, include_trip_fuel: bool) -> list[LogEvent]:
    """Parse one journal line into zero or more timeline events."""
    m = LINE_RE.match(line.rstrip("\n"))
    if not m:
        return []
    time_s, level, tag, body = m.group(1), m.group(2), m.group(3), m.group(4)
    raw = line.rstrip("\n")

    if tag == "TripFuel":
        if not include_trip_fuel:
            return []
        values = parse_trip_fuel_values(body)
        sources = parse_trip_fuel_sources(body)
        events: list[LogEvent] = []
        if values:
            for key, val in values.items():
                events.append(
                    LogEvent(
                        time=time_s,
                        level=level,
                        tag=tag,
                        kind="trip_fuel_value",
                        telemetry_key=key,
                        value=val,
                        known=None,
                        detail=f"sources={sources}" if sources else "",
                        raw=raw,
                    )
                )
        else:
            events.append(
                LogEvent(
                    time=time_s,
                    level=level,
                    tag=tag,
                    kind="trip_fuel",
                    detail=body[:500],
                    raw=raw,
                )
            )
        return events

    if tag == "CANDIAG_VHAL":
        prop_id = extract_kv_int(PROPERTY_ID_RE, body)
        # VHAL values may be float / null / arrays — take free-form token after value=
        vm = re.search(r"\bvalue=(\S+)", body)
        value = vm.group(1) if vm else None
        known, catalog_name = annotate_item(catalog, prop_id)
        log_name = extract_kv_str(NAME_RE, body)
        return [
            LogEvent(
                time=time_s,
                level=level,
                tag=tag,
                kind="vhal",
                property_id=prop_id,
                value=value,
                known=known,
                catalog_name=catalog_name or log_name,
                modular=extract_kv_int(AREA_ID_RE, body),
                detail=body[:500],
                raw=raw,
            )
        ]

    if tag == "CANDIAG_MBCAN":
        item_id = extract_kv_int(ITEM_VALUE_RE, body)
        value = extract_kv_str(VALUE_RE, body)
        known, catalog_name = annotate_item(catalog, item_id)
        log_name = extract_kv_str(NAME_RE, body)
        return [
            LogEvent(
                time=time_s,
                level=level,
                tag=tag,
                kind="mbcan_cfg",
                item_id=item_id,
                value=value,
                known=known,
                catalog_name=catalog_name or log_name,
                modular=extract_kv_int(MODULAR_RE, body),
                rev=extract_kv_int(REV_RE, body),
                data_type=extract_kv_str(DT_RE, body),
                detail=body[:500],
                raw=raw,
            )
        ]

    # MBCAN_TMP
    if body.startswith("cfgVehiclePush"):
        item_id = extract_kv_int(ITEM_VALUE_RE, body)
        value = extract_kv_str(VALUE_RE, body)
        known, catalog_name = annotate_item(catalog, item_id)
        return [
            LogEvent(
                time=time_s,
                level=level,
                tag=tag,
                kind="cfgVehiclePush",
                item_id=item_id,
                value=value,
                known=known,
                catalog_name=catalog_name,
                modular=extract_kv_int(MODULAR_RE, body),
                rev=extract_kv_int(REV_RE, body),
                detail=body[:500],
                raw=raw,
            )
        ]

    if body.startswith("push_coalesced"):
        events = []
        for key, count, last in parse_push_coalesced_entries(body):
            known, _ = annotate_telemetry_key(key)
            events.append(
                LogEvent(
                    time=time_s,
                    level=level,
                    tag=tag,
                    kind="push_coalesced",
                    telemetry_key=key,
                    value=last,
                    known=known,
                    catalog_name=key if known else None,
                    detail=f"count={count}",
                    raw=raw,
                )
            )
        return events

    # Other MBCAN_TMP chatter (refresh*, execute*, …) — keep one row for context.
    kind = body.split(None, 1)[0] if body else "mbcan_tmp"
    return [
        LogEvent(
            time=time_s,
            level=level,
            tag=tag,
            kind=kind[:80],
            detail=body[:500],
            raw=raw,
        )
    ]


def parse_file(
    path: Path,
    catalog: CatalogIndex,
    include_trip_fuel: bool = True,
    show_progress: bool = True,
) -> list[LogEvent]:
    # errors=replace: some app journals contain NUL padding in SWD lines.
    text = path.read_text(encoding="utf-8", errors="replace")
    lines = text.splitlines()
    events: list[LogEvent] = []
    for line in tqdm(
        lines,
        desc="Разбор строк",
        unit="стр",
        disable=not show_progress,
        file=sys.stderr,
    ):
        events.extend(parse_line(line, catalog, include_trip_fuel))
    return events


def build_items_summary(events: Iterable[LogEvent]) -> list[dict[str, Any]]:
    """Aggregate distinct cfg item / VHAL property IDs with known flag and value samples."""
    buckets: dict[tuple[str, int], dict[str, Any]] = {}
    for ev in events:
        if ev.item_id is not None:
            key = ("item", ev.item_id)
        elif ev.property_id is not None:
            key = ("property", ev.property_id)
        else:
            continue
        bucket = buckets.get(key)
        if bucket is None:
            bucket = {
                "id_kind": key[0],
                "id": key[1],
                "known": ev.known,
                "catalog_name": ev.catalog_name,
                "count": 0,
                "values": Counter(),
                "tags": set(),
            }
            buckets[key] = bucket
        bucket["count"] += 1
        if ev.value is not None:
            bucket["values"][ev.value] += 1
        bucket["tags"].add(ev.tag)
        if ev.known is True:
            bucket["known"] = True
        elif bucket["known"] is None and ev.known is False:
            bucket["known"] = False
        if ev.catalog_name and not bucket["catalog_name"]:
            bucket["catalog_name"] = ev.catalog_name

    rows: list[dict[str, Any]] = []
    for (_kind, _id), b in sorted(
        buckets.items(),
        key=lambda kv: (kv[1]["known"] is not False, kv[0][0], kv[0][1]),
    ):
        samples = [f"{v}×{n}" for v, n in b["values"].most_common(8)]
        rows.append(
            {
                "item_or_property_id": b["id"],
                "id_kind": b["id_kind"],
                "known": b["known"],
                "catalog_name": b["catalog_name"] or "",
                "event_count": b["count"],
                "distinct_values": len(b["values"]),
                "sample_values": ", ".join(samples),
                "tags": ",".join(sorted(b["tags"])),
            }
        )
    return rows


def event_to_row(ev: LogEvent) -> list[Any]:
    known_cell: Any = ""
    if ev.known is True:
        known_cell = "known"
    elif ev.known is False:
        known_cell = "unknown"
    return [
        ev.time,
        ev.level,
        ev.tag,
        ev.kind,
        "" if ev.item_id is None else ev.item_id,
        "" if ev.property_id is None else ev.property_id,
        ev.telemetry_key or "",
        ev.value if ev.value is not None else "",
        known_cell,
        ev.catalog_name or "",
        "" if ev.modular is None else ev.modular,
        "" if ev.rev is None else ev.rev,
        ev.data_type or "",
        ev.detail,
        ev.raw,
    ]


def write_xlsx(
    events: list[LogEvent],
    summary_rows: list[dict[str, Any]],
    out_path: Path,
    show_progress: bool,
    catalog: CatalogIndex,
) -> None:
    wb = Workbook()
    ws = wb.active
    ws.title = "Timeline"
    ws.append(TIMELINE_HEADERS)
    for col in range(1, len(TIMELINE_HEADERS) + 1):
        ws.cell(row=1, column=col).alignment = HEADER_ALIGNMENT
        ws.cell(row=1, column=col).font = Font(bold=True)

    unknown_fill = PatternFill("solid", fgColor="FFF2CC")
    for ev in tqdm(
        events,
        desc="Запись Timeline",
        unit="стр",
        disable=not show_progress,
        file=sys.stderr,
    ):
        row = event_to_row(ev)
        ws.append(row)
        if ev.known is False and (ev.item_id is not None or ev.property_id is not None):
            r = ws.max_row
            for c in range(1, len(TIMELINE_HEADERS) + 1):
                ws.cell(row=r, column=c).fill = unknown_fill

    ws2 = wb.create_sheet("ItemsSummary")
    ws2.append(SUMMARY_HEADERS)
    for col in range(1, len(SUMMARY_HEADERS) + 1):
        ws2.cell(row=1, column=col).alignment = HEADER_ALIGNMENT
        ws2.cell(row=1, column=col).font = Font(bold=True)
    for srow in summary_rows:
        known_cell = ""
        if srow["known"] is True:
            known_cell = "known"
        elif srow["known"] is False:
            known_cell = "unknown"
        ws2.append(
            [
                srow["item_or_property_id"],
                srow["id_kind"],
                known_cell,
                srow["catalog_name"],
                srow["event_count"],
                srow["distinct_values"],
                srow["sample_values"],
                srow["tags"],
            ]
        )
        if srow["known"] is False:
            r = ws2.max_row
            for c in range(1, len(SUMMARY_HEADERS) + 1):
                ws2.cell(row=r, column=c).fill = unknown_fill

    ws3 = wb.create_sheet("Meta")
    ws3.append(["key", "value"])
    ws3.append(["catalog_path", str(catalog.source_path) if catalog.source_path else ""])
    ws3.append(["catalog_id_count", len(catalog.id_to_name)])
    ws3.append(["timeline_events", len(events)])
    ws3.append(["summary_ids", len(summary_rows)])
    unknown_ids = [r["item_or_property_id"] for r in summary_rows if r["known"] is False]
    ws3.append(["unknown_ids", ",".join(str(x) for x in unknown_ids)])

    if show_progress:
        tqdm.write(f"Сохранение файла: {out_path}", file=sys.stderr)
    wb.save(out_path)


def write_csv_timeline(events: list[LogEvent], out_path: Path) -> None:
    with out_path.open("w", encoding="utf-8", newline="") as f:
        w = csv.writer(f)
        w.writerow(TIMELINE_HEADERS)
        for ev in events:
            w.writerow(event_to_row(ev))


def main() -> None:
    ap = argparse.ArgumentParser(
        description=(
            "Convert TBox app / deep-diagnostic log (tbox_app_log_*.txt) to XLSX "
            "timeline of mbCAN/VHAL/TripFuel events."
        )
    )
    ap.add_argument("input", type=Path, help="Path to tbox_app_log_*.txt")
    ap.add_argument(
        "-o",
        "--output",
        type=Path,
        default=None,
        help="Output .xlsx path (default: same basename as input with .xlsx)",
    )
    ap.add_argument(
        "--catalog",
        type=Path,
        default=None,
        help=f"Path to MbCanCatalog.kt (default: {DEFAULT_CATALOG_KT})",
    )
    ap.add_argument(
        "--no-trip-fuel",
        action="store_true",
        help="Skip TripFuel. lines",
    )
    ap.add_argument(
        "--csv",
        type=Path,
        default=None,
        metavar="PATH",
        help="Also write timeline as CSV to PATH",
    )
    ap.add_argument(
        "-q",
        "--quiet",
        action="store_true",
        help="Не показывать прогресс (полезно в скриптах и при перенаправлении вывода)",
    )
    args = ap.parse_args()
    inp: Path = args.input
    if not inp.is_file():
        print(f"Input not found: {inp}", file=sys.stderr)
        sys.exit(1)
    catalog_path = args.catalog or DEFAULT_CATALOG_KT
    if not catalog_path.is_file():
        print(f"Catalog Kotlin file not found: {catalog_path}", file=sys.stderr)
        sys.exit(1)
    catalog = load_catalog_from_kotlin(catalog_path)
    show_progress = not args.quiet
    events = parse_file(
        inp,
        catalog,
        include_trip_fuel=not args.no_trip_fuel,
        show_progress=show_progress,
    )
    summary = build_items_summary(events)
    out = args.output or inp.with_suffix(".xlsx")
    write_xlsx(events, summary, out, show_progress, catalog)
    if args.csv is not None:
        write_csv_timeline(events, args.csv)
        print(f"Wrote CSV timeline ({len(events)} rows) to {args.csv}")
    unknown = [r for r in summary if r["known"] is False]
    print(
        f"Wrote {len(events)} timeline events, {len(summary)} distinct ids "
        f"({len(unknown)} unknown) to {out}"
    )
    if unknown:
        ids = ", ".join(str(r["item_or_property_id"]) for r in unknown[:20])
        more = "" if len(unknown) <= 20 else f" … (+{len(unknown) - 20})"
        print(f"Unknown ids: {ids}{more}")


if __name__ == "__main__":
    main()
