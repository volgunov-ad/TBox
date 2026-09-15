#!/usr/bin/env python3
"""Rebuild mbCAN OEM catalogs from native .so + Java enums + app/docs coverage.

Outputs:
  docs/MBCAN_OEM_FULL_CATALOG_RU.md
  docs/MBCAN_JNI_PUSH_FIELDS_RU.md
  docs/generated/mbcan_so_catalog.json

Usage:
  python3 tools/mbcan_so_catalog.py
"""

from __future__ import annotations

import json
import re
import struct
import subprocess
from collections import Counter, defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

CLIENT_SO = ROOT / "mbcan" / "libmbcanclient.so"
MBCAN_SO = ROOT / "mbcan" / "libmbCan.so"
VEHICLE_ENUM = ROOT / "app/src/main/java/com/mengbo/mbCan/defines/MBVehicleProperty.java"
AUDIO_ENUM = ROOT / "app/src/main/java/com/mengbo/mbCan/defines/MBAudioProperty.java"
CATALOG_KT = ROOT / "app/src/main/java/vad/dashing/tbox/mbcan/MbCanCatalog.kt"
PARAMS_DOC = ROOT / "docs/MBCAN_VHAL_PARAMETERS_RU.md"

OUT_CATALOG = ROOT / "docs/MBCAN_OEM_FULL_CATALOG_RU.md"
OUT_JNI = ROOT / "docs/MBCAN_JNI_PUSH_FIELDS_RU.md"
OUT_JSON = ROOT / "docs/generated/mbcan_so_catalog.json"

DATA_VA = 0x20000
DATA_OFF = 0x10000
VEHICLE_TABLE_VA = 0x20280
VEHICLE_TABLE_N = 324
AUDIO_TABLE_VA = 0x20198
AUDIO_TABLE_N = 37

STATUS_LABEL = {
    "in_app_and_docs": "в приложении и в MBCAN_VHAL_PARAMETERS_RU",
    "in_app_only": "в приложении (MbCanKnown*), в справочнике параметров не описан",
    "in_docs_only": "упомянут в справочнике, нет константы в MbCanKnown*",
    "oem_only_unverified": "только OEM enum / native table — на Dashing не подтверждён",
}


def load_u32_table(blob: bytes, va: int, n: int) -> list[int]:
    off = DATA_OFF + (va - DATA_VA)
    return list(struct.unpack("<" + "I" * n, blob[off : off + 4 * n]))


def parse_enum(path: Path) -> dict[int, str]:
    text = path.read_text(encoding="utf-8")
    out: dict[int, str] = {}
    for name, raw in re.findall(r"(\w+)\(([^)]+)\)", text):
        if not name.startswith("e"):
            continue
        if "BuildConfig" in raw or "VERSION_CODE" in raw:
            if "AVH" in name:
                out[142] = name
            continue
        try:
            value = int(raw.strip())
        except ValueError:
            continue
        out[value] = name
    return {i: n for i, n in out.items() if not n.endswith("_COUNT")}


def read_all_kt() -> str:
    parts = []
    for path in (ROOT / "app/src/main/java").rglob("*.kt"):
        parts.append(path.read_text(encoding="utf-8", errors="ignore"))
    return "\n".join(parts)


def known_vehicle_consts(kt_text: str, all_kt: str) -> dict[int, str]:
    match = re.search(
        r"object MbCanKnownVehiclePropertyId \{(.*?)\nobject ",
        kt_text,
        re.S,
    )
    if not match:
        raise SystemExit("MbCanKnownVehiclePropertyId not found")
    block = match.group(1)
    consts: list[tuple[str, int, str]] = []
    for mm in re.finditer(
        r"(?P<comment>(?:/\*\*[\s\S]*?\*/|//[^\n]*\n)\s*)?"
        r"const val (?P<name>\w+)\s*=\s*(?P<val>\d+)",
        block,
    ):
        consts.append((mm.group("name"), int(mm.group("val")), mm.group("comment") or ""))

    assigns = set(
        re.findall(r"propertyId\s*=\s*MbCanKnownVehiclePropertyId\.([A-Z0-9_]+)", all_kt)
    )
    refs = set(re.findall(r"MbCanKnownVehiclePropertyId\.([A-Z0-9_]+)", all_kt))
    value_name = re.compile(
        r"^(LIGHTCONTROL_(AUTO|PARK|LOW|OFF)|LAS_MODE_|HVAC_CUSTOM_(ECO|COMFORT|STRONG)$|"
        r"HVAC_FAN_DIRECTION_|HVAC_AIR_RECIRCULATION_VALUE_|DRIVE_MODE_|EPS_MODE_|.*_VALUE_.*)"
    )

    scored: dict[int, tuple[str, int]] = {}
    for name, value, comment in consts:
        is_value = bool(value_name.match(name))
        mentions_prop = bool(
            re.search(
                r"MBVehicleProperty|eVEHICLE_|eHVAC_|eAVM_|property id|mbCAN",
                comment,
                re.I,
            )
        )
        if is_value and not mentions_prop:
            continue
        in_assign = name in assigns
        in_refs = name in refs
        if not (mentions_prop or in_assign or (in_refs and not is_value)):
            continue
        score = (2 if in_assign else 0) + (2 if mentions_prop else 0) + (1 if in_refs else 0)
        prev = scored.get(value)
        if prev is None or score > prev[1] or (score == prev[1] and len(name) > len(prev[0])):
            scored[value] = (name, score)
    return {i: name for i, (name, _) in scored.items()}


def known_audio_consts(kt_text: str, all_kt: str) -> dict[int, str]:
    match = re.search(r"object MbCanKnownAudioPropertyId \{(.*?)\}", kt_text, re.S)
    if not match:
        return {}
    refs = set(re.findall(r"MbCanKnownAudioPropertyId\.([A-Z0-9_]+)", all_kt))
    out: dict[int, str] = {}
    for name, raw in re.findall(r"const val (\w+)\s*=\s*(\d+)", match.group(1)):
        if name in refs:
            out[int(raw)] = name
    return out


def doc_coverage(doc_text: str) -> tuple[set[int], set[str]]:
    ids = {int(x) for x in re.findall(r"\*\*(\d{1,3})\*\*", doc_text)}
    enums = set(re.findall(r"e[A-Z][A-Za-z0-9_]+", doc_text))
    return ids, enums


def coverage_status(
    pid: int,
    enum_name: str,
    app_map: dict[int, str],
    doc_ids: set[int],
    doc_enums: set[str],
) -> str:
    in_app = pid in app_map
    in_docs = pid in doc_ids or enum_name in doc_enums
    if in_app and in_docs:
        return "in_app_and_docs"
    if in_app:
        return "in_app_only"
    if in_docs:
        return "in_docs_only"
    return "oem_only_unverified"


def jni_field_groups(so_path: Path) -> dict[str, list[str]]:
    strings = subprocess.check_output(
        ["strings", "-a", str(so_path)],
        text=True,
        errors="ignore",
    ).splitlines()
    field_re = re.compile(r"^[nfbms][A-Z][A-Za-z0-9_]{3,}$")
    noise = re.compile(
        r"(JNIEnv|JavaVM|Throwable|String|Object|Class|Array|Method|Field|Byte|Int|Long|"
        r"Float|Double|Boolean|Void|android|java_|JNI_)"
    )
    fields = sorted({s for s in strings if field_re.match(s) and not noise.search(s)})

    def group_key(name: str) -> str:
        body = name[1:]
        parts = body.split("_")
        if len(parts) >= 2 and parts[1].isdigit():
            return f"{parts[0]}_{parts[1]}"
        return parts[0]

    groups: dict[str, list[str]] = defaultdict(list)
    for field in fields:
        groups[group_key(field)].append(field)
    return dict(sorted(groups.items(), key=lambda kv: (-len(kv[1]), kv[0])))


def build_payload() -> dict:
    client = CLIENT_SO.read_bytes()
    vehicle_ops = load_u32_table(client, VEHICLE_TABLE_VA, VEHICLE_TABLE_N)
    audio_ops = load_u32_table(client, AUDIO_TABLE_VA, AUDIO_TABLE_N)
    vehicle_enum = parse_enum(VEHICLE_ENUM)
    audio_enum = parse_enum(AUDIO_ENUM)
    kt_text = CATALOG_KT.read_text(encoding="utf-8")
    all_kt = read_all_kt()
    known_v = known_vehicle_consts(kt_text, all_kt)
    known_a = known_audio_consts(kt_text, all_kt)
    doc_ids, doc_enums = doc_coverage(PARAMS_DOC.read_text(encoding="utf-8"))
    groups = jni_field_groups(MBCAN_SO)

    vehicle = []
    for pid in range(1, VEHICLE_TABLE_N + 1):
        enum_name = vehicle_enum.get(pid, f"UNKNOWN_{pid}")
        vehicle.append(
            {
                "id": pid,
                "enum": enum_name,
                "opcode": vehicle_ops[pid - 1],
                "app_const": known_v.get(pid),
                "status": coverage_status(pid, enum_name, known_v, doc_ids, doc_enums),
            }
        )

    audio = []
    max_audio = max(max(audio_enum), AUDIO_TABLE_N)
    for pid in range(1, max_audio + 1):
        enum_name = audio_enum.get(pid, f"UNKNOWN_{pid}")
        op = audio_ops[pid - 1] if pid <= AUDIO_TABLE_N else None
        audio.append(
            {
                "id": pid,
                "enum": enum_name,
                "opcode": op,
                "native_table": op is not None,
                "app_const": known_a.get(pid),
                "status": coverage_status(pid, enum_name, known_a, doc_ids, doc_enums),
            }
        )

    return {
        "vehicle": vehicle,
        "audio": audio,
        "jni_groups": groups,
        "stats": {
            "vehicle": dict(Counter(r["status"] for r in vehicle)),
            "audio": dict(Counter(r["status"] for r in audio)),
            "jni_fields": sum(len(v) for v in groups.values()),
            "jni_groups": len(groups),
        },
    }


def write_catalog_md(data: dict) -> None:
    vs = data["stats"]["vehicle"]
    aus = data["stats"]["audio"]
    lines = [
        "# Полный каталог OEM mbCAN property (из enum + libmbcanclient.so)",
        "",
        "Сгенерировано статическим разбором (`tools/mbcan_so_catalog.py`):",
        "",
        "- Java enum `MBVehicleProperty` / `MBAudioProperty`",
        "- Таблица `id → nItem` в `.data` `mbcan/libmbcanclient.so` (`MBCan_Vehicle_Get` / `MBCan_Audio_Get`)",
        "- Сверка с `MbCanKnownVehiclePropertyId` / `MbCanKnownAudioPropertyId` и `docs/MBCAN_VHAL_PARAMETERS_RU.md`",
        "",
        "Нативный слой: [MBCAN_LIB_SO_REVERSE_RU.md](MBCAN_LIB_SO_REVERSE_RU.md).  ",
        "JNI push-поля: [MBCAN_JNI_PUSH_FIELDS_RU.md](MBCAN_JNI_PUSH_FIELDS_RU.md).  ",
        "Используемые в UI параметры: [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).",
        "",
        "> **Важно:** имя `App const` — как в TBox Monitor на Dashing; оно может **не совпадать** с OEM enum",
        "> (пример: id **12** в OEM = `KEYMODE`, в приложении = `SUNROOF_TILT`).",
        "",
        "## Статусы покрытия",
        "",
        "| Статус | Смысл |",
        "|--------|--------|",
    ]
    for key, label in STATUS_LABEL.items():
        lines.append(f"| `{key}` | {label} |")

    lines += [
        "",
        "### Сводка Vehicle",
        "",
        f"| Статус | Число (из {len(data['vehicle'])}) |",
        "|--------|------|",
    ]
    for key in STATUS_LABEL:
        lines.append(f"| `{key}` | {vs.get(key, 0)} |")

    lines += [
        "",
        "### Сводка Audio",
        "",
        f"| Статус | Число (из {len(data['audio'])}) |",
        "|--------|------|",
    ]
    for key in STATUS_LABEL:
        lines.append(f"| `{key}` | {aus.get(key, 0)} |")

    lines += [
        "",
        "## Vehicle properties (`MBCan_Vehicle_Get/Set`)",
        "",
        "Native id **1…324**. `nItem` — внутренний код modular IPC (обычно **800+**).",
        "",
        "| ID | OEM enum | nItem | App const | Статус |",
        "|----|----------|------:|-----------|--------|",
    ]
    for row in data["vehicle"]:
        app = f"`{row['app_const']}`" if row.get("app_const") else "—"
        lines.append(
            f"| {row['id']} | `{row['enum']}` | {row['opcode']} | {app} | `{row['status']}` |"
        )

    lines += [
        "",
        "## Audio properties (`MBCan_Audio_Get/Set`)",
        "",
        "Native table: id **1…37**. Id **14–16** = `0xFFFFFFFF` (нет mapping). "
        "Enum: max id **37**, `eAUDIO_PROPERTY_COUNT=38`; в native table 37 слотов (id 14–16 = invalid).",
        "",
        "| ID | OEM enum | nItem | Native | App const | Статус |",
        "|----|----------|------:|:------:|-----------|--------|",
    ]
    for row in data["audio"]:
        op = row.get("opcode")
        if op is None:
            native, ops = "no", "—"
        elif op == 0xFFFFFFFF:
            native, ops = "invalid", "—"
        else:
            native, ops = "yes", str(op)
        app = f"`{row['app_const']}`" if row.get("app_const") else "—"
        lines.append(
            f"| {row['id']} | `{row['enum']}` | {ops} | {native} | {app} | `{row['status']}` |"
        )

    def subset(status: str) -> list[dict]:
        return [r for r in data["vehicle"] if r["status"] == status]

    lines += ["", "## Vehicle: в приложении, нет в справочнике параметров", ""]
    rows = subset("in_app_only")
    if rows:
        lines += ["| ID | OEM enum | App const |", "|----|----------|-----------|"]
        for row in rows:
            lines.append(f"| {row['id']} | `{row['enum']}` | `{row['app_const']}` |")
    else:
        lines.append("_нет_")

    lines += ["", "## Vehicle: в справочнике без MbCanKnown* константы", ""]
    rows = subset("in_docs_only")
    if rows:
        lines += ["| ID | OEM enum |", "|----|----------|"]
        for row in rows:
            lines.append(f"| {row['id']} | `{row['enum']}` |")
    else:
        lines.append("_нет_")

    lines += [
        "",
        "## Пересборка",
        "",
        "```bash",
        "python3 tools/mbcan_so_catalog.py",
        "```",
        "",
    ]
    OUT_CATALOG.write_text("\n".join(lines) + "\n", encoding="utf-8")


def write_jni_md(data: dict) -> None:
    lines = [
        "# JNI push-поля из libmbCan.so",
        "",
        "Имена полей push-структур (`GetFieldID`), извлечённые из `mbcan/libmbCan.so`.",
        "Это **телеметрия subscribe/parseCanData**, не каталог `canGetVehicleParam(id)`.",
        "",
        f"Уникальных имён: **{data['stats']['jni_fields']}**, групп: **{data['stats']['jni_groups']}**.",
        "",
        "См. [MBCAN_LIB_SO_REVERSE_RU.md](MBCAN_LIB_SO_REVERSE_RU.md), "
        "[MBCAN_OEM_FULL_CATALOG_RU.md](MBCAN_OEM_FULL_CATALOG_RU.md).",
        "",
    ]
    for group, fields in data["jni_groups"].items():
        lines += [f"## `{group}` ({len(fields)})", "", "```", *fields, "```", ""]
    OUT_JNI.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    if not CLIENT_SO.exists() or not MBCAN_SO.exists():
        raise SystemExit(f"missing native libs under {ROOT / 'mbcan'}")
    data = build_payload()
    OUT_JSON.parent.mkdir(parents=True, exist_ok=True)
    OUT_JSON.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    write_catalog_md(data)
    write_jni_md(data)
    print(
        "wrote",
        OUT_CATALOG.relative_to(ROOT),
        OUT_JNI.relative_to(ROOT),
        OUT_JSON.relative_to(ROOT),
    )
    print("vehicle", data["stats"]["vehicle"])
    print("audio", data["stats"]["audio"])
    print(
        "jni_fields",
        data["stats"]["jni_fields"],
        "groups",
        data["stats"]["jni_groups"],
    )


if __name__ == "__main__":
    main()
