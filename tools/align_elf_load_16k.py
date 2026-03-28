#!/usr/bin/env python3
"""
Pad an ELF64 shared object so PT_LOAD segments satisfy Android 16 KB page-size
loaders: for each PT_LOAD, p_align >= 16384 and (p_vaddr - p_offset) % 16384 == 0.

Typical bad OEM .so: two LOADs with p_align=4096 and vaddr - offset = 0x1000.
We insert padding before the second LOAD's file start so offsets shift up.
"""
from __future__ import annotations

import argparse
import struct
import sys
from pathlib import Path

PAGE = 16384
PT_LOAD = 1
EI_CLASS = 4
EI_DATA = 5
ELFCLASS64 = 2
ELFDATA2LSB = 1


def u16(b: bytes, o: int) -> int:
    return struct.unpack_from("<H", b, o)[0]


def u32(b: bytes, o: int) -> int:
    return struct.unpack_from("<I", b, o)[0]


def u64(b: bytes, o: int) -> int:
    return struct.unpack_from("<Q", b, o)[0]


def w64(b: bytearray, o: int, v: int) -> None:
    struct.pack_into("<Q", b, o, v)


def parse_phdrs(data: bytes, e_phoff: int, phentsize: int, phnum: int) -> list[dict]:
    out = []
    for i in range(phnum):
        o = e_phoff + i * phentsize
        p_type = u32(data, o)
        p_flags = u32(data, o + 4)
        p_offset = u64(data, o + 8)
        p_vaddr = u64(data, o + 16)
        p_paddr = u64(data, o + 24)
        p_filesz = u64(data, o + 32)
        p_memsz = u64(data, o + 40)
        p_align = u64(data, o + 48)
        out.append(
            {
                "i": i,
                "o": o,
                "type": p_type,
                "flags": p_flags,
                "offset": p_offset,
                "vaddr": p_vaddr,
                "paddr": p_paddr,
                "filesz": p_filesz,
                "memsz": p_memsz,
                "align": p_align,
            }
        )
    return out


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("input", type=Path)
    ap.add_argument("-o", "--output", type=Path, required=True)
    args = ap.parse_args()
    data = bytearray(args.input.read_bytes())
    if data[:4] != b"\x7fELF":
        print("Not an ELF file", file=sys.stderr)
        return 1
    if data[EI_CLASS] != ELFCLASS64 or data[EI_DATA] != ELFDATA2LSB:
        print("Need ELF64 little-endian", file=sys.stderr)
        return 1
    e_phoff = u64(data, 32)
    e_shoff_orig = u64(data, 40)
    e_phentsize = u16(data, 54)
    e_phnum = u16(data, 56)
    e_shentsize = u16(data, 58)
    e_shnum = u16(data, 60)

    phdrs = parse_phdrs(data, e_phoff, e_phentsize, e_phnum)
    loads = [p for p in phdrs if p["type"] == PT_LOAD]
    if len(loads) < 2:
        print("Need at least 2 PT_LOAD segments", file=sys.stderr)
        return 1

    loads.sort(key=lambda p: p["offset"])
    second = loads[1]
    insert_off = second["offset"]
    diff = (second["vaddr"] - second["offset"]) & 0xFFFFFFFFFFFFFFFF
    # After inserting `need` zero bytes before second LOAD, new_offset = old_offset + need.
    # Want (vaddr - new_offset) % PAGE == 0  =>  (diff - need) % PAGE == 0  =>  need = diff % PAGE.
    need = diff % PAGE
    if need == 0 and all(
        (p["vaddr"] - p["offset"]) % PAGE == 0 and p["align"] >= PAGE for p in loads
    ):
        args.output.write_bytes(data)
        print(f"{args.input}: already 16 KB–compatible, copied")
        return 0

    print(
        f"{args.input}: inserting {need} bytes before file offset {insert_off:#x} "
        f"(fix vaddr-offset mod {PAGE})",
    )

    data[insert_off:insert_off] = b"\x00" * need

    # Shift file offsets for everything at/after insertion point
    if e_shoff_orig >= insert_off:
        w64(data, 40, e_shoff_orig + need)
    e_shoff = u64(data, 40)

    for p in phdrs:
        o = p["o"]
        po = u64(data, o + 8)
        if po >= insert_off:
            w64(data, o + 8, po + need)
            # Do not bump p_filesz: the inserted bytes are already inside the
            # on-disk span of this LOAD; file length grew by exactly `need`.

    for i in range(e_shnum):
        so = e_shoff + i * e_shentsize
        if so + 24 + 8 > len(data):
            break
        sh_off = u64(data, so + 24)
        if sh_off >= insert_off:
            w64(data, so + 24, sh_off + need)

    # Bump p_align for all PT_LOAD; ensure congruence holds after pad
    phdrs2 = parse_phdrs(data, e_phoff, e_phentsize, e_phnum)
    for p in phdrs2:
        if p["type"] != PT_LOAD:
            continue
        o = p["o"]
        align = u64(data, o + 48)
        if align < PAGE:
            w64(data, o + 48, PAGE)
        off = u64(data, o + 8)
        va = u64(data, o + 16)
        if (va - off) % PAGE != 0:
            print(
                f"ERROR: LOAD still incongruent: off={off:#x} vaddr={va:#x}",
                file=sys.stderr,
            )
            return 1

    args.output.write_bytes(data)
    print(f"Wrote {args.output} ({len(data)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
