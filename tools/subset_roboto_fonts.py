#!/usr/bin/env python3
"""Subset Roboto TTFs for app/res/font (Latin + Cyrillic + common UI symbols).

Requires: pip install fonttools brotli
Source: @fontpkg/roboto (Apache/OFL, same files as Google Fonts static Roboto).

Run from repo root:
  python3 tools/subset_roboto_fonts.py
"""

from __future__ import annotations

import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "font"

# Latin, Latin-1, Cyrillic, punctuation (en/em dash, quotes, ellipsis), Σ, ℃
UNICODE_RANGES = (
    "U+0020-007E,U+00A0-00FF,U+0400-04FF,U+2000-206F,U+03A3,U+2103"
)

WEIGHTS = (
    ("roboto_regular.ttf", "Roboto-Regular.ttf"),
    ("roboto_medium.ttf", "Roboto-Medium.ttf"),
    ("roboto_semibold.ttf", "Roboto-SemiBold.ttf"),
    ("roboto_bold.ttf", "Roboto-Bold.ttf"),
)

FONT_PKG_URL = "https://registry.npmjs.org/@fontpkg/roboto/-/roboto-3.9.1.tgz"


def download_fontpkg(dest: Path) -> Path:
    archive = dest / "roboto.tgz"
    urllib.request.urlretrieve(FONT_PKG_URL, archive)
    subprocess.run(["tar", "-xzf", str(archive), "-C", str(dest)], check=True)
    return dest / "package"


def subset_font(src: Path, dst: Path) -> None:
    subprocess.run(
        [
            sys.executable,
            "-m",
            "fontTools.subset",
            str(src),
            f"--unicodes={UNICODE_RANGES}",
            "--layout-features=*",
            "--glyph-names",
            "--symbol-cmap",
            "--legacy-cmap",
            "--notdef-outline",
            "--recalc-bounds",
            "--recalc-timestamp",
            "--recommended-glyphs",
            f"--output-file={dst}",
        ],
        check=True,
    )


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        pkg = download_fontpkg(Path(tmp))
        for out_name, src_name in WEIGHTS:
            src = pkg / src_name
            if not src.is_file():
                sys.exit(f"Missing source font: {src_name}")
            subset_font(src, OUT_DIR / out_name)
            print(f"Wrote {OUT_DIR / out_name} ({(OUT_DIR / out_name).stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
