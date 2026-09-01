#!/usr/bin/env python3
"""Find a node by text in uiautomator dump and print tap X Y."""
from __future__ import annotations

import re
import sys
from pathlib import Path

SAMPLE_DUMP = """<?xml version="1.0" encoding="UTF-8"?>
<hierarchy rotation="0">
  <node text="Настройки" bounds="[10,20][110,80]"/>
  <node bounds="[200,300][400,360]" text="Импорт JSON"/>
  <node content-desc="Закрыть" bounds="[900,10][980,90]"/>
</hierarchy>
"""


def find_tap_xy(xml: str, needle: str) -> tuple[int, int] | None:
    pat = re.compile(
        r'(?:text|content-desc)="' + re.escape(needle) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        r'|bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*(?:text|content-desc)="' + re.escape(needle) + r'"'
    )
    match = pat.search(xml)
    if not match:
        return None
    groups = [int(value) for value in match.groups() if value is not None]
    return (groups[0] + groups[2]) // 2, (groups[1] + groups[3]) // 2


def self_test() -> None:
    assert find_tap_xy(SAMPLE_DUMP, "Настройки") == (60, 50)
    assert find_tap_xy(SAMPLE_DUMP, "Импорт JSON") == (300, 330)
    assert find_tap_xy(SAMPLE_DUMP, "Закрыть") == (940, 50)
    assert find_tap_xy(SAMPLE_DUMP, "missing") is None


def main() -> int:
    if len(sys.argv) >= 2 and sys.argv[1] == "--self-test":
        self_test()
        print("click_from_dump self-test ok")
        return 0
    if len(sys.argv) < 3:
        print("usage: click_from_dump.py dump.xml TEXT", file=sys.stderr)
        print("       click_from_dump.py --self-test", file=sys.stderr)
        return 2
    xml = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    needle = sys.argv[2]
    coords = find_tap_xy(xml, needle)
    if coords is None:
        print("NOT_FOUND", file=sys.stderr)
        return 1
    print(f"{coords[0]} {coords[1]}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
