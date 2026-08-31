#!/usr/bin/env python3
"""Find a node by text in uiautomator dump and print tap X Y."""
from __future__ import annotations

import re
import sys
from pathlib import Path

def main() -> int:
    if len(sys.argv) < 3:
        print("usage: click_from_dump.py dump.xml TEXT", file=sys.stderr)
        return 2
    xml = Path(sys.argv[1]).read_text(encoding="utf-8", errors="replace")
    needle = sys.argv[2]
    pat = re.compile(
        r'(?:text|content-desc)="' + re.escape(needle) + r'"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
        r'|bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"[^>]*(?:text|content-desc)="' + re.escape(needle) + r'"'
    )
    m = pat.search(xml)
    if not m:
        print("NOT_FOUND", file=sys.stderr)
        return 1
    g = [int(x) for x in m.groups() if x is not None]
    x = (g[0] + g[2]) // 2
    y = (g[1] + g[3]) // 2
    print(f"{x} {y}")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
