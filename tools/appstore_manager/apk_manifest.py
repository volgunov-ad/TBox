#!/usr/bin/env python3
"""
Read package / version / label from an APK (stdlib only).

Strategy:
1. Prefer `aapt dump badging` when Android build-tools are available
   (ANDROID_HOME / ANDROID_SDK_ROOT / common Windows SDK paths).
2. Fallback: parse binary AndroidManifest.xml inside the APK zip.
"""

from __future__ import annotations

import os
import re
import shutil
import struct
import subprocess
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


@dataclass
class ApkInfo:
    packageName: str
    versionCode: int
    versionName: str
    label: str = ""
    minSdkVersion: int | None = None
    targetSdkVersion: int | None = None
    source: str = ""  # "aapt" | "axml"

    def summary(self) -> str:
        parts = [
            f"{self.packageName}",
            f"v{self.versionName} ({self.versionCode})",
        ]
        if self.label:
            parts.append(f'«{self.label}»')
        return " · ".join(parts)


def read_apk_info(apk_path: Path | str) -> ApkInfo:
    path = Path(apk_path).expanduser().resolve()
    if not path.is_file():
        raise FileNotFoundError(f"APK не найден: {path}")
    if path.suffix.lower() != ".apk":
        raise ValueError("Нужен файл .apk")

    aapt = _find_aapt()
    if aapt is not None:
        try:
            return _read_via_aapt(aapt, path)
        except Exception:
            pass
    return _read_via_axml(path)


def _find_aapt() -> Path | None:
    for name in ("aapt", "aapt.exe"):
        found = shutil.which(name)
        if found:
            return Path(found)

    roots: list[Path] = []
    for env in ("ANDROID_HOME", "ANDROID_SDK_ROOT", "ANDROID_SDK"):
        raw = os.environ.get(env)
        if raw:
            roots.append(Path(raw))
    # Common Windows / Linux defaults
    local = os.environ.get("LOCALAPPDATA")
    if local:
        roots.append(Path(local) / "Android" / "Sdk")
    roots.append(Path.home() / "AppData" / "Local" / "Android" / "Sdk")
    roots.append(Path.home() / "Android" / "Sdk")
    roots.append(Path("/opt/android-sdk"))
    roots.append(Path.home() / "android-sdk")

    candidates: list[Path] = []
    for root in roots:
        bt = root / "build-tools"
        if not bt.is_dir():
            continue
        for child in bt.iterdir():
            if not child.is_dir():
                continue
            for name in ("aapt.exe", "aapt"):
                exe = child / name
                if exe.is_file():
                    candidates.append(exe)
    if not candidates:
        return None

    def sort_key(p: Path) -> tuple:
        # Prefer higher build-tools version folder name
        return tuple(int(x) if x.isdigit() else 0 for x in p.parent.name.split("."))

    candidates.sort(key=sort_key, reverse=True)
    return candidates[0]


def _read_via_aapt(aapt: Path, apk: Path) -> ApkInfo:
    proc = subprocess.run(
        [str(aapt), "dump", "badging", str(apk)],
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    if proc.returncode != 0:
        raise RuntimeError(proc.stderr.strip() or "aapt dump badging failed")
    text = proc.stdout
    pkg_line = next(
        (line for line in text.splitlines() if line.startswith("package:")),
        "",
    )
    if not pkg_line:
        raise ValueError("aapt: нет строки package:")

    def attr(name: str) -> str:
        match = re.search(rf"""{name}=(?:'([^']*)'|"([^"]*)")""", pkg_line)
        if not match:
            return ""
        return match.group(1) or match.group(2) or ""

    package = attr("name")
    version_name = attr("versionName")
    version_code_raw = attr("versionCode")
    if not package or not version_code_raw or not version_name:
        raise ValueError("aapt: неполный package/version")

    label = ""
    for line in text.splitlines():
        if line.startswith("application-label:"):
            label = line.split(":", 1)[1].strip().strip("'\"")
            break
        if line.startswith("application: "):
            m = re.search(r"""label=(?:'([^']*)'|"([^"]*)")""", line)
            if m:
                label = m.group(1) or m.group(2) or ""

    def sdk(prefix: str) -> int | None:
        for line in text.splitlines():
            if line.startswith(prefix):
                raw = line.split(":", 1)[1].strip().strip("'\"")
                try:
                    return int(raw)
                except ValueError:
                    return None
        return None

    return ApkInfo(
        packageName=package,
        versionCode=int(version_code_raw),
        versionName=version_name,
        label=label,
        minSdkVersion=sdk("sdkVersion:"),
        targetSdkVersion=sdk("targetSdkVersion:"),
        source="aapt",
    )


# --- Binary AndroidManifest.xml (AXML) ---------------------------------------

RES_STRING_POOL_TYPE = 0x0001
RES_XML_RESOURCE_MAP_TYPE = 0x0180
RES_XML_START_ELEMENT_TYPE = 0x0102

TYPE_NULL = 0x00
TYPE_REFERENCE = 0x01
TYPE_STRING = 0x03
TYPE_INT_DEC = 0x10
TYPE_INT_HEX = 0x11
TYPE_INT_BOOLEAN = 0x12


def _read_via_axml(apk: Path) -> ApkInfo:
    with zipfile.ZipFile(apk) as archive:
        try:
            data = archive.read("AndroidManifest.xml")
        except KeyError as exc:
            raise ValueError("В APK нет AndroidManifest.xml") from exc
    parsed = _parse_axml(data)
    package = str(parsed.get("package") or "").strip()
    version_name = str(parsed.get("versionName") or "").strip()
    version_code = parsed.get("versionCode")
    if not package or version_code is None or not version_name:
        raise ValueError(
            "Не удалось прочитать package/versionCode/versionName из AndroidManifest.xml "
            "(установите Android SDK build-tools / aapt или проверьте APK)",
        )
    return ApkInfo(
        packageName=package,
        versionCode=int(version_code),
        versionName=version_name,
        label=str(parsed.get("label") or "").strip(),
        minSdkVersion=_as_optional_int(parsed.get("minSdkVersion")),
        targetSdkVersion=_as_optional_int(parsed.get("targetSdkVersion")),
        source="axml",
    )


def _as_optional_int(value: Any) -> int | None:
    if value is None or value == "":
        return None
    try:
        return int(value)
    except (TypeError, ValueError):
        return None


def _parse_axml(data: bytes) -> dict[str, Any]:
    if len(data) < 8:
        raise ValueError("AndroidManifest.xml слишком короткий")
    # File header: type(u16), headerSize(u16), size(u32)
    file_type, header_size, file_size = struct.unpack_from("<HHI", data, 0)
    if file_type != 0x0003:  # RES_XML_TYPE
        raise ValueError(f"Неожиданный тип манифеста: 0x{file_type:04x}")

    offset = header_size
    strings: list[str] = []
    res_map: list[int] = []
    out: dict[str, Any] = {}

    while offset + 8 <= len(data) and offset + 8 <= file_size:
        chunk_type, chunk_header_size, chunk_size = struct.unpack_from("<HHI", data, offset)
        if chunk_size < 8 or offset + chunk_size > len(data):
            break
        if chunk_type == RES_STRING_POOL_TYPE and not strings:
            strings = _parse_string_pool(data, offset, chunk_header_size, chunk_size)
        elif chunk_type == RES_XML_RESOURCE_MAP_TYPE:
            count = (chunk_size - chunk_header_size) // 4
            res_map = list(
                struct.unpack_from(f"<{count}I", data, offset + chunk_header_size),
            )
        elif chunk_type == RES_XML_START_ELEMENT_TYPE:
            _handle_start_element(
                data,
                offset,
                chunk_header_size,
                strings,
                res_map,
                out,
            )
        offset += chunk_size
    return out


def _parse_string_pool(
    data: bytes,
    offset: int,
    header_size: int,
    chunk_size: int,
) -> list[str]:
    # After common header: stringCount, styleCount, flags, stringsStart, stylesStart
    string_count, _style_count, flags, strings_start, _styles_start = struct.unpack_from(
        "<IIIII",
        data,
        offset + 8,
    )
    utf8 = bool(flags & (1 << 8))
    offsets = struct.unpack_from(f"<{string_count}I", data, offset + header_size)
    pool: list[str] = []
    base = offset + strings_start
    for off in offsets:
        pos = base + off
        if utf8:
            # UTF-8: charLen (u8 or u16), byteLen (u8 or u16), bytes, 0
            char_len, pos = _read_length_u8_or_u16(data, pos)
            byte_len, pos = _read_length_u8_or_u16(data, pos)
            raw = data[pos : pos + byte_len]
            pool.append(raw.decode("utf-8", errors="replace"))
        else:
            # UTF-16: charLen (u16 or u32), utf16 chars, 0x0000
            char_len, pos = _read_length_u16_or_u32(data, pos)
            raw = data[pos : pos + char_len * 2]
            pool.append(raw.decode("utf-16-le", errors="replace"))
        _ = char_len  # length used for bounds only
    return pool


def _read_length_u8_or_u16(data: bytes, pos: int) -> tuple[int, int]:
    value = data[pos]
    pos += 1
    if value & 0x80:
        value = ((value & 0x7F) << 8) | data[pos]
        pos += 1
    return value, pos


def _read_length_u16_or_u32(data: bytes, pos: int) -> tuple[int, int]:
    value = struct.unpack_from("<H", data, pos)[0]
    pos += 2
    if value & 0x8000:
        value = ((value & 0x7FFF) << 16) | struct.unpack_from("<H", data, pos)[0]
        pos += 2
    return value, pos


def _string_at(strings: list[str], index: int) -> str:
    if index < 0 or index >= len(strings):
        return ""
    return strings[index]


def _handle_start_element(
    data: bytes,
    offset: int,
    header_size: int,
    strings: list[str],
    res_map: list[int],
    out: dict[str, Any],
) -> None:
    # ResXMLTree_node header is usually 16 bytes (chunk hdr + line + comment).
    # ResXMLTree_attrExt follows; attributeStart is relative to that ext.
    if header_size < 16 or offset + 16 + 20 > len(data):
        return
    (
        _ns_idx,
        name_idx,
        attr_start,
        attr_size,
        attr_count,
        _id_i,
        _class_i,
        _style_i,
    ) = struct.unpack_from("<IIHHHHHH", data, offset + 16)

    tag = _string_at(strings, name_idx)
    attrs: dict[str, Any] = {}
    attr_base = offset + 16 + attr_start
    chunk_size = struct.unpack_from("<I", data, offset + 4)[0]
    chunk_end = offset + chunk_size
    for i in range(attr_count):
        aoff = attr_base + i * max(attr_size, 20)
        if aoff + 20 > chunk_end or aoff + 20 > len(data):
            break
        # ns(u32), name(u32), rawValue(u32), size(u16), res0(u8), dataType(u8), data(u32)
        _ans, aname, raw_value, _size, _res0, data_type, typed_data = struct.unpack_from(
            "<IIIHBBI",
            data,
            aoff,
        )
        name = _string_at(strings, aname)
        if not name and 0 <= aname < len(res_map):
            name = _android_attr_name(res_map[aname])
        value = _decode_typed_value(strings, data_type, typed_data, raw_value)
        if name:
            attrs[name] = value

    if tag == "manifest":
        if "package" in attrs and "package" not in out:
            out["package"] = attrs["package"]
        if "versionCode" in attrs and "versionCode" not in out:
            out["versionCode"] = attrs["versionCode"]
        if "versionName" in attrs and "versionName" not in out:
            out["versionName"] = attrs["versionName"]
    elif tag == "uses-sdk":
        if "minSdkVersion" in attrs and "minSdkVersion" not in out:
            out["minSdkVersion"] = attrs["minSdkVersion"]
        if "targetSdkVersion" in attrs and "targetSdkVersion" not in out:
            out["targetSdkVersion"] = attrs["targetSdkVersion"]
    elif tag == "application":
        if "label" in attrs and "label" not in out:
            label = attrs["label"]
            # Skip unresolved resource refs like @0x7f0….
            if isinstance(label, str) and not label.startswith("@"):
                out["label"] = label


def _android_attr_name(res_id: int) -> str:
    mapping = {
        0x01010001: "label",
        0x01010002: "icon",
        0x01010003: "name",
        0x0101021B: "versionCode",
        0x0101021C: "versionName",
        0x01010270: "minSdkVersion",
        0x01010271: "targetSdkVersion",
        0x01010280: "maxSdkVersion",
    }
    return mapping.get(res_id, "")


def _decode_typed_value(
    strings: list[str],
    data_type: int,
    typed_data: int,
    raw_value: int,
) -> Any:
    if data_type == TYPE_STRING:
        idx = typed_data if typed_data != 0xFFFFFFFF else raw_value
        return _string_at(strings, idx)
    if data_type in (TYPE_INT_DEC, TYPE_INT_HEX, TYPE_INT_BOOLEAN):
        return int(typed_data)
    if data_type == TYPE_REFERENCE:
        return f"@{typed_data:#x}"
    if data_type == TYPE_NULL:
        return None
    # Fallback: try string from rawValue
    if raw_value != 0xFFFFFFFF:
        s = _string_at(strings, raw_value)
        if s:
            return s
    return typed_data
