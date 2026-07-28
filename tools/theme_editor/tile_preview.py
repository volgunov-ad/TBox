"""Template tile preview for theme editor (not live vehicle data)."""

from __future__ import annotations

import tkinter as tk
from typing import Any, Sequence

from widget_catalog import format_sample_value, get_widget_type
from widget_config import (
    DEFAULT_TEXT_DARK,
    DEFAULT_TEXT_LIGHT,
    argb_to_hex,
    default_title_position,
    normalize_widgets,
)


def _argb_alpha(argb: int) -> int:
    return (int(argb) & 0xFFFFFFFF) >> 24


def _tk_rgb(argb: int | None, fallback: str) -> str:
    if argb is None:
        return fallback
    hx = argb_to_hex(argb, default=fallback)
    # Tk Canvas fill on Windows is opaque #RRGGBB (no alpha channel).
    if len(hx) == 9:
        return f"#{hx[3:]}"
    return hx


def resolve_title(widget: dict[str, Any]) -> str:
    custom = str(widget.get("customTitle") or "").strip()
    if custom:
        return custom
    return get_widget_type(str(widget.get("dataKey") or "")).title


def resolve_value_and_unit(widget: dict[str, Any]) -> tuple[str, str]:
    data_key = str(widget.get("dataKey") or "")
    meta = get_widget_type(data_key)
    acc = widget.get("valueAccuracy")
    try:
        acc_i = int(acc) if acc is not None else None
    except (TypeError, ValueError):
        acc_i = None
    value = format_sample_value(data_key, acc_i)
    unit = ""
    if widget.get("showUnit", True) and meta.supports_show_unit:
        unit = meta.unit
    return value, unit


def draw_tile_on_canvas(
    canvas: tk.Canvas,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    widget: dict[str, Any],
    *,
    dark: bool = False,
    tags: Sequence[str] = (),
    font_scale: float = 1.0,
    show_empty_outline: bool = True,
) -> None:
    """Draw one template tile into an arbitrary canvas rectangle (layout / dialog)."""
    w = max(1.0, x2 - x1)
    h = max(1.0, y2 - y1)
    data_key = str(widget.get("dataKey") or "")
    bg_key = "backgroundColorDark" if dark else "backgroundColorLight"
    text_key = "textColorDark" if dark else "textColorLight"
    bg_raw = widget.get(bg_key)
    tag_tuple = tuple(tags)

    # Explicit ARGB with alpha 0 → truly transparent (underlay shows through).
    # Unset background → default opaque surface (like the app defaults).
    transparent = False
    if bg_raw is not None:
        try:
            transparent = _argb_alpha(int(bg_raw)) == 0
        except (TypeError, ValueError):
            transparent = False

    if transparent:
        # No fill — only content text floats over the underlay / panel.
        if show_empty_outline and not data_key:
            canvas.create_rectangle(
                x1,
                y1,
                x2,
                y2,
                fill="",
                outline="#555555",
                width=1,
                dash=(2, 2),
                tags=tag_tuple,
            )
    else:
        bg = _tk_rgb(bg_raw if bg_raw is not None else None, "#131C2D" if dark else "#F5F5F5")
        canvas.create_rectangle(
            x1,
            y1,
            x2,
            y2,
            fill=bg,
            outline="",
            width=0,
            tags=tag_tuple,
        )
    try:
        scale = float(widget.get("scale", 1.0))
    except (TypeError, ValueError):
        scale = 1.0
    scale = max(0.5, min(1.8, scale)) * max(0.4, min(1.5, font_scale))
    pad_t = int(widget.get("paddingTopPercent", 0) or 0)
    pad_b = int(widget.get("paddingBottomPercent", 0) or 0)
    pad_s = int(widget.get("paddingStartPercent", 0) or 0)
    pad_e = int(widget.get("paddingEndPercent", 0) or 0)
    margin = max(2.0, min(6.0, min(w, h) * 0.04))
    inner_x0 = x1 + w * pad_s / 100.0 + margin
    inner_y0 = y1 + h * pad_t / 100.0 + margin
    inner_x1 = x2 - w * pad_e / 100.0 - margin
    inner_y1 = y2 - h * pad_b / 100.0 - margin
    if not data_key:
        canvas.create_text(
            (x1 + x2) / 2,
            (y1 + y2) / 2,
            text="·",
            fill="#999999",
            font=("Segoe UI", max(7, int(9 * font_scale))),
            tags=tag_tuple,
        )
        return

    title_pos = int(widget.get("titlePosition", default_title_position(data_key)) or 0)
    show_title = bool(widget.get("showTitle")) and bool(data_key)
    title = resolve_title(widget) if show_title else ""
    value, unit = resolve_value_and_unit(widget)
    text = _tk_rgb(
        widget.get(text_key),
        argb_to_hex(DEFAULT_TEXT_DARK if dark else DEFAULT_TEXT_LIGHT),
    )
    align = int(widget.get("textAlign", 0) or 0)
    anchor = "center" if align == 0 else ("w" if align == 1 else "e")
    cx = (inner_x0 + inner_x1) / 2
    if align == 1:
        cx = inner_x0 + 1
    elif align == 2:
        cx = inner_x1 - 1
    title_size = max(6, int(7 * scale))
    value_size = max(7, int(11 * scale))
    weight = int(widget.get("fontWeight", 1) or 1)
    bold = "bold" if weight >= 1 else "normal"
    content_top = inner_y0
    content_bottom = inner_y1
    max_chars = max(4, int(w / max(5, value_size * 0.55)))
    if show_title and title_pos == 0 and h > 28:
        canvas.create_text(
            cx,
            inner_y0,
            text=title[:max_chars],
            fill=text,
            font=("Segoe UI", title_size),
            anchor="n" if align == 0 else ("nw" if align == 1 else "ne"),
            tags=tag_tuple,
        )
        content_top = inner_y0 + title_size + 2
    if show_title and title_pos == 1 and h > 28:
        canvas.create_text(
            cx,
            inner_y1,
            text=title[:max_chars],
            fill=text,
            font=("Segoe UI", title_size),
            anchor="s" if align == 0 else ("sw" if align == 1 else "se"),
            tags=tag_tuple,
        )
        content_bottom = inner_y1 - title_size - 2
    cy = (content_top + content_bottom) / 2
    display = value
    if unit:
        display = f"{value} {unit}".strip()
    canvas.create_text(
        cx,
        cy,
        text=display[:max_chars],
        fill=text,
        font=("Segoe UI", value_size, bold),
        anchor=anchor,
        tags=tag_tuple,
    )


def draw_panel_tiles_on_canvas(
    canvas: tk.Canvas,
    x1: float,
    y1: float,
    x2: float,
    y2: float,
    panel: dict[str, Any],
    *,
    dark: bool = False,
    tags: Sequence[str] = (),
    gap: float = 3.0,
) -> None:
    """Draw rows×cols template tiles inside a panel rectangle on the layout canvas."""
    grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
    try:
        rows = max(1, int(grid.get("rows", 1)))
    except (TypeError, ValueError):
        rows = 1
    try:
        cols = max(1, int(grid.get("cols", 1)))
    except (TypeError, ValueError):
        cols = 1
    widgets = normalize_widgets(
        rows,
        cols,
        panel.get("widgets") if isinstance(panel.get("widgets"), list) else [],
    )
    panel["widgets"] = widgets
    # Match in-app layout: tiles fill the panel; only a slim outer frame is drawn by caller.
    spacing_dp = 4
    try:
        spacing_dp = max(0, int(grid.get("spacingDp", 4) or 4))
    except (TypeError, ValueError):
        spacing_dp = 4
    cell_gap = max(1.0, min(gap, float(spacing_dp)))
    inner_pad = 2.0
    usable_w = max(1.0, (x2 - x1) - 2 * inner_pad)
    usable_h = max(1.0, (y2 - y1) - 2 * inner_pad)
    cell_w = (usable_w - cell_gap * (cols - 1)) / cols
    cell_h = (usable_h - cell_gap * (rows - 1)) / rows
    if cell_w < 8 or cell_h < 8:
        return
    font_scale = max(0.45, min(1.2, min(cell_w / 90.0, cell_h / 60.0)))
    idx = 0
    for r in range(rows):
        for c in range(cols):
            cx1 = x1 + inner_pad + c * (cell_w + cell_gap)
            cy1 = y1 + inner_pad + r * (cell_h + cell_gap)
            cx2 = cx1 + cell_w
            cy2 = cy1 + cell_h
            draw_tile_on_canvas(
                canvas,
                cx1,
                cy1,
                cx2,
                cy2,
                widgets[idx],
                dark=dark,
                tags=tags,
                font_scale=font_scale,
            )
            idx += 1


class TilePreview(tk.Canvas):
    """Draws one template tile matching basic visual settings."""

    def __init__(self, master: tk.Misc, *, width: int = 160, height: int = 100, **kwargs: Any) -> None:
        super().__init__(master, width=width, height=height, highlightthickness=1, **kwargs)
        self._widget: dict[str, Any] = {}
        self._dark = False
        self.bind("<Configure>", lambda _e: self.redraw())

    def set_widget(self, widget: dict[str, Any], *, dark: bool = False) -> None:
        self._widget = dict(widget or {})
        self._dark = dark
        self.redraw()

    def redraw(self) -> None:
        self.delete("all")
        w = max(self.winfo_width(), 40)
        h = max(self.winfo_height(), 40)
        # Dialog preview sits on a checker / solid canvas — show a faint border for empty cells.
        self.configure(background="#2A2A2A" if self._dark else "#E8E8E8")
        draw_tile_on_canvas(
            self,
            1,
            1,
            w - 1,
            h - 1,
            self._widget,
            dark=self._dark,
            font_scale=1.0,
        )
