#!/usr/bin/env python3
"""Generate Android vector drawables with text labels for dashboard widget icons."""

from __future__ import annotations

from pathlib import Path

from fontTools.misc.transform import Transform
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.transformPen import TransformPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.ttLib import TTFont

FONT_PATH = Path("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf")
OUT_DIR = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable"
VIEWPORT = 48
PADDING = 3

LABELS: dict[str, str] = {
    "ic_widget_label_ldw": "LDW",
    "ic_widget_label_lka": "LKA",
    "ic_widget_label_tja_ica": "TJA/ICA",
    "ic_widget_label_hma": "HMA",
    "ic_widget_label_nor": "NOR",
    "ic_widget_label_spt": "SPT",
    "ic_widget_label_sand": "SAND",
    "ic_widget_label_mud": "MUD",
    "ic_widget_label_snow": "SNOW",
    "ic_widget_label_auto": "AUTO",
    "ic_widget_label_park": "PARK",
    "ic_widget_label_low": "LOW",
    "ic_widget_label_off": "OFF",
}


def layout_text(font: TTFont, text: str, target_height: float) -> tuple[str, float, float, float, float]:
    glyph_set = font.getGlyphSet()
    cmap = font.getBestCmap()
    upem = font["head"].unitsPerEm
    unit_scale = target_height / (upem * 0.72)
    bounds_pen = BoundsPen(glyph_set)
    x = 0.0
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            continue
        tpen = TransformPen(bounds_pen, Transform().scale(unit_scale, -unit_scale).translate(x, target_height))
        glyph_set[gname].draw(tpen)
        x += glyph_set[gname].width * unit_scale
    min_x, min_y, max_x, max_y = bounds_pen.bounds
    text_w = max_x - min_x
    text_h = max_y - min_y
    fit = min((VIEWPORT - 2 * PADDING) / max(text_w, 1.0), (VIEWPORT - 2 * PADDING) / max(text_h, 1.0))
    final_scale = unit_scale * fit
    bounds_pen2 = BoundsPen(glyph_set)
    x = 0.0
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            continue
        tpen = TransformPen(bounds_pen2, Transform().scale(final_scale, -final_scale).translate(x, target_height * fit))
        glyph_set[gname].draw(tpen)
        x += glyph_set[gname].width * final_scale
    min_x, min_y, max_x, max_y = bounds_pen2.bounds
    text_w = max_x - min_x
    text_h = max_y - min_y
    x_offset = (VIEWPORT - text_w) / 2 - min_x
    y_offset = (VIEWPORT - text_h) / 2 - min_y
    commands: list[str] = []
    x = x_offset
    baseline_y = target_height * fit + y_offset
    for ch in text:
        gname = cmap.get(ord(ch))
        if gname is None:
            continue
        pen = SVGPathPen(glyph_set)
        tpen = TransformPen(pen, Transform().scale(final_scale, -final_scale).translate(x, baseline_y))
        glyph_set[gname].draw(tpen)
        path = pen.getCommands()
        if path:
            commands.append(path)
        x += glyph_set[gname].width * final_scale
    return (" ".join(commands), min_x + x_offset, min_y + y_offset, max_x + x_offset, max_y + y_offset)


def write_vector(name: str, text: str, font: TTFont) -> None:
    path_data, _, _, _, _ = layout_text(font, text, target_height=16.0)
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{VIEWPORT}dp"
    android:height="{VIEWPORT}dp"
    android:viewportWidth="{VIEWPORT}"
    android:viewportHeight="{VIEWPORT}">
    <path
        android:fillColor="@android:color/white"
        android:fillAlpha="0.89"
        android:pathData="{path_data}" />
</vector>
"""
    out_path = OUT_DIR / f"{name}.xml"
    out_path.write_text(xml, encoding="utf-8")
    print(f"wrote {out_path.name}")


def main() -> None:
    font = TTFont(FONT_PATH)
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    for name, text in LABELS.items():
        write_vector(name, text, font)


if __name__ == "__main__":
    main()
