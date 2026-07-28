"""Clickable rows×cols panel grid with template tile previews."""

from __future__ import annotations

import tkinter as tk
from tkinter import ttk
from typing import Any, Callable

from tile_preview import TilePreview
from widget_config import normalize_widgets


class PanelGridEditor(ttk.Frame):
    def __init__(
        self,
        master: tk.Misc,
        *,
        on_cell_click: Callable[[int], None] | None = None,
        cell_width: int = 120,
        cell_height: int = 80,
    ) -> None:
        super().__init__(master)
        self.on_cell_click = on_cell_click
        self.cell_width = cell_width
        self.cell_height = cell_height
        self._tiles: list[TilePreview] = []
        self._panel: dict[str, Any] = {}
        self._dark = False
        self.grid_frame = ttk.Frame(self)
        self.grid_frame.pack(fill="both", expand=True)

    def set_panel(self, panel: dict[str, Any], *, dark: bool = False) -> None:
        self._panel = panel
        self._dark = dark
        self.rebuild()

    def rebuild(self) -> None:
        for child in self.grid_frame.winfo_children():
            child.destroy()
        self._tiles.clear()
        panel = self._panel
        grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
        try:
            rows = max(1, int(grid.get("rows", 1)))
        except (TypeError, ValueError):
            rows = 1
        try:
            cols = max(1, int(grid.get("cols", 1)))
        except (TypeError, ValueError):
            cols = 1
        widgets = normalize_widgets(rows, cols, panel.get("widgets") if isinstance(panel.get("widgets"), list) else [])
        panel["widgets"] = widgets
        for r in range(rows):
            self.grid_frame.rowconfigure(r, weight=1)
        for c in range(cols):
            self.grid_frame.columnconfigure(c, weight=1)
        idx = 0
        for r in range(rows):
            for c in range(cols):
                cell_index = idx
                tile = TilePreview(
                    self.grid_frame,
                    width=self.cell_width,
                    height=self.cell_height,
                )
                tile.set_widget(widgets[idx], dark=self._dark)
                tile.grid(row=r, column=c, sticky="nsew", padx=3, pady=3)
                tile.bind("<Button-1>", lambda _e, i=cell_index: self._click(i))
                # Also bind children-less canvas
                tile.bind("<Double-Button-1>", lambda _e, i=cell_index: self._click(i))
                self._tiles.append(tile)
                idx += 1

    def refresh_cells(self) -> None:
        panel = self._panel
        grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
        try:
            rows = max(1, int(grid.get("rows", 1)))
            cols = max(1, int(grid.get("cols", 1)))
        except (TypeError, ValueError):
            rows, cols = 1, 1
        widgets = normalize_widgets(rows, cols, panel.get("widgets") if isinstance(panel.get("widgets"), list) else [])
        panel["widgets"] = widgets
        for i, tile in enumerate(self._tiles):
            if i < len(widgets):
                tile.set_widget(widgets[i], dark=self._dark)

    def _click(self, index: int) -> None:
        if self.on_cell_click is not None:
            self.on_cell_click(index)
