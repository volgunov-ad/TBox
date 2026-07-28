"""Widget selection/settings dialog mirroring the app's WidgetSelectionDialog."""

from __future__ import annotations

import tkinter as tk
from pathlib import Path
from tkinter import colorchooser, filedialog, messagebox, ttk
from typing import Any, MutableMapping

from panel_grid import PanelGridEditor
from theme_bundle import IMAGE_EXTENSIONS, MAX_ASSET_BYTES, is_image_filename
from tile_preview import TilePreview
from widget_catalog import WIDGET_TYPES, get_widget_type
from widget_config import (
    DEFAULT_DRIVE_MODE,
    DEFAULT_FONT_WEIGHT,
    DEFAULT_FREEFORM_PERCENT,
    DEFAULT_FREEFORM_SIDE,
    DEFAULT_HTTP_YAML,
    DEFAULT_PADDING,
    DEFAULT_SHAPE,
    DEFAULT_TEXT_ALIGN,
    DEFAULT_TEXT_DARK,
    DEFAULT_TEXT_LIGHT,
    DEFAULT_TRIP_LABEL_PCT,
    argb_to_hex,
    clone_widget,
    default_title_position,
    empty_widget,
    hex_to_argb,
    normalize_widgets,
    serialize_widget,
)


def _askcolor_rrggbb(parent: tk.Misc, current_hex: str, *, title: str = "Цвет") -> str | None:
    """Open system color picker. Tk on Windows needs #RRGGBB, not #AARRGGBB."""
    raw = (current_hex or "").strip()
    if raw.startswith("#") and len(raw) == 9:
        initial = f"#{raw[3:]}"
    elif raw.startswith("#") and len(raw) == 7:
        initial = raw
    else:
        initial = "#FFFFFF"
    # Nested modal + grab_set often blocks askcolor; release while chooser is open.
    grabber = parent.grab_current()
    try:
        if grabber is not None:
            grabber.grab_release()
        result = colorchooser.askcolor(color=initial, title=title, parent=parent)
    finally:
        try:
            if grabber is not None and grabber.winfo_exists():
                grabber.grab_set()
        except tk.TclError:
            pass
    if not result or not result[1]:
        return None
    hx = str(result[1]).upper()
    if hx.startswith("#") and len(hx) == 7:
        return f"#FF{hx[1:]}"
    return hx if hx.startswith("#") else f"#FF{hx}"


class WidgetEditDialog(tk.Toplevel):
    """Edit one cell + optional whole-panel settings."""

    def __init__(
        self,
        master: tk.Misc,
        *,
        panel: dict[str, Any],
        cell_index: int,
        is_main_screen: bool,
        tile_backgrounds: MutableMapping[str, bytes] | None = None,
        color_presets: list[str] | None = None,
        title: str = "Плитка",
    ) -> None:
        super().__init__(master)
        self.title(title)
        self.transient(master)
        self.grab_set()
        self.resizable(True, True)
        self.geometry("980x720")
        self.result: dict[str, Any] | None = None

        self.panel = panel
        self.is_main_screen = is_main_screen
        self.tile_backgrounds = tile_backgrounds if tile_backgrounds is not None else {}
        self.color_presets = [
            p for p in (color_presets or []) if isinstance(p, str) and p.strip()
        ]
        grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
        try:
            rows = max(1, int(grid.get("rows", 1)))
            cols = max(1, int(grid.get("cols", 1)))
        except (TypeError, ValueError):
            rows, cols = 1, 1
        widgets = normalize_widgets(
            rows, cols, panel.get("widgets") if isinstance(panel.get("widgets"), list) else []
        )
        self.panel["widgets"] = widgets
        self.cell_index = max(0, min(cell_index, len(widgets) - 1))
        self.widget = clone_widget(widgets[self.cell_index])

        self.mode = tk.StringVar(value="type")
        self.search_var = tk.StringVar()
        self.dark_preview = tk.BooleanVar(value=False)
        self._building = True

        self._build()
        self._load_widget_into_form()
        self._building = False
        self._refresh_type_list()
        self._refresh_tile_bg_lists()
        self._update_preview()
        self.protocol("WM_DELETE_WINDOW", self._cancel)
        self.wait_visibility()
        self.focus_set()

    def _build(self) -> None:
        top = ttk.Frame(self, padding=8)
        top.pack(fill="both", expand=True)
        modes = ttk.Frame(top)
        modes.pack(fill="x", pady=(0, 8))
        for value, label in (
            ("type", "Тип"),
            ("advanced", "Дополнительно"),
            ("panel", "Вся панель"),
        ):
            ttk.Radiobutton(
                modes,
                text=label,
                value=value,
                variable=self.mode,
                command=self._show_mode,
            ).pack(side="left", padx=4)

        body = ttk.Frame(top)
        body.pack(fill="both", expand=True)
        body.columnconfigure(0, weight=3)
        body.columnconfigure(1, weight=2)
        body.rowconfigure(0, weight=1)

        self.pages = ttk.Frame(body)
        self.pages.grid(row=0, column=0, sticky="nsew", padx=(0, 8))
        preview_box = ttk.LabelFrame(body, text="Превью (шаблон)", padding=8)
        preview_box.grid(row=0, column=1, sticky="nsew")
        ttk.Checkbutton(
            preview_box,
            text="Тёмная тема",
            variable=self.dark_preview,
            command=self._update_preview,
        ).pack(anchor="w")
        self.preview = TilePreview(preview_box, width=220, height=140)
        self.preview.pack(fill="both", expand=True, pady=8)

        self.type_page = ttk.Frame(self.pages)
        self.adv_page = ttk.Frame(self.pages)
        self.panel_page = ttk.Frame(self.pages)
        for page in (self.type_page, self.adv_page, self.panel_page):
            page.place(relx=0, rely=0, relwidth=1, relheight=1)

        self._build_type_page()
        self._build_advanced_page()
        self._build_panel_page()
        self._show_mode()

        btns = ttk.Frame(top)
        btns.pack(fill="x", pady=(8, 0))
        ttk.Button(btns, text="OK", command=self._ok).pack(side="right", padx=4)
        ttk.Button(btns, text="Отмена", command=self._cancel).pack(side="right", padx=4)

    def _show_mode(self) -> None:
        mode = self.mode.get()
        page = {"type": self.type_page, "advanced": self.adv_page, "panel": self.panel_page}[mode]
        page.tkraise()

    def _build_type_page(self) -> None:
        frame = self.type_page
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(1, weight=1)
        bar = ttk.Frame(frame)
        bar.grid(row=0, column=0, sticky="ew", pady=(0, 4))
        ttk.Label(bar, text="Поиск:").pack(side="left")
        entry = ttk.Entry(bar, textvariable=self.search_var)
        entry.pack(side="left", fill="x", expand=True, padx=4)
        self.search_var.trace_add("write", lambda *_: self._refresh_type_list())
        self.type_list = tk.Listbox(frame, exportselection=False)
        self.type_list.grid(row=1, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(frame, orient="vertical", command=self.type_list.yview)
        scroll.grid(row=1, column=1, sticky="ns")
        self.type_list.configure(yscrollcommand=scroll.set)
        self.type_list.bind("<<ListboxSelect>>", self._on_type_selected)

    def _refresh_type_list(self) -> None:
        q = self.search_var.get().strip().lower()
        self.type_list.delete(0, "end")
        self._type_keys: list[str] = []
        current = str(self.widget.get("dataKey") or "")
        selected_index = 0
        for i, w in enumerate(WIDGET_TYPES):
            hay = f"{w.data_key} {w.title} {w.unit}".lower()
            if q and q not in hay:
                continue
            label = w.title if not w.data_key else f"{w.title}  [{w.data_key}]"
            self.type_list.insert("end", label)
            self._type_keys.append(w.data_key)
            if w.data_key == current:
                selected_index = len(self._type_keys) - 1
        if self._type_keys:
            self.type_list.selection_set(selected_index)
            self.type_list.see(selected_index)

    def _on_type_selected(self, _event: object | None = None) -> None:
        if self._building:
            return
        sel = self.type_list.curselection()
        if not sel:
            return
        key = self._type_keys[sel[0]]
        old = self.widget
        self.widget = empty_widget(key)
        # Preserve common visual settings when changing type.
        for field in (
            "showTitle",
            "customTitle",
            "scale",
            "shape",
            "textColorLight",
            "textColorDark",
            "backgroundColorLight",
            "backgroundColorDark",
            "textAlign",
            "fontWeight",
            "paddingTopPercent",
            "paddingBottomPercent",
            "paddingStartPercent",
            "paddingEndPercent",
        ):
            if field in old:
                self.widget[field] = old[field]
        self.widget["titlePosition"] = default_title_position(key)
        self._building = True
        self._load_widget_into_form()
        self._building = False
        self._update_advanced_visibility()
        self._update_preview()

    def _build_advanced_page(self) -> None:
        frame = self.adv_page
        canvas = tk.Canvas(frame, highlightthickness=0)
        scroll = ttk.Scrollbar(frame, orient="vertical", command=canvas.yview)
        inner = ttk.Frame(canvas)
        inner.bind(
            "<Configure>",
            lambda e: canvas.configure(scrollregion=canvas.bbox("all")),
        )
        canvas.create_window((0, 0), window=inner, anchor="nw")
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        self.adv_inner = inner

        self.show_title_var = tk.BooleanVar()
        self.title_pos_var = tk.IntVar(value=0)
        self.custom_title_var = tk.StringVar()
        self.show_unit_var = tk.BooleanVar(value=True)
        self.dual_var = tk.BooleanVar()
        self.scale_var = tk.DoubleVar(value=1.0)
        self.shape_var = tk.IntVar(value=0)
        self.text_align_var = tk.IntVar(value=0)
        self.font_weight_var = tk.IntVar(value=1)
        self.pad_top = tk.IntVar(value=0)
        self.pad_bottom = tk.IntVar(value=0)
        self.pad_start = tk.IntVar(value=0)
        self.pad_end = tk.IntVar(value=0)
        self.value_accuracy_var = tk.StringVar(value="default")
        self.datetime_var = tk.StringVar()
        self.mbcan_var = tk.BooleanVar()
        self.stepper_var = tk.IntVar(value=0)
        self.drive_mode_var = tk.IntVar(value=DEFAULT_DRIVE_MODE)
        self.trip_dividers_var = tk.BooleanVar(value=True)
        self.trip_label_var = tk.IntVar(value=DEFAULT_TRIP_LABEL_PCT)
        self.trip_source_var = tk.IntVar(value=0)
        self.launcher_pkg_var = tk.StringVar()
        self.freeform_var = tk.BooleanVar()
        self.freeform_side_var = tk.StringVar(value=DEFAULT_FREEFORM_SIDE)
        self.freeform_pct_var = tk.IntVar(value=DEFAULT_FREEFORM_PERCENT)
        self.http_yaml_var = tk.StringVar(value=DEFAULT_HTTP_YAML)
        self.http_browser_var = tk.BooleanVar()
        self.media_players_var = tk.StringVar()
        self.media_auto_var = tk.BooleanVar()
        self.media_engine_var = tk.BooleanVar()
        self.media_fg_var = tk.BooleanVar()
        self.controls_default_var = tk.BooleanVar(value=True)
        self.control_shape_var = tk.StringVar(value="")
        self.color_vars = {
            "textLight": tk.StringVar(value=argb_to_hex(DEFAULT_TEXT_LIGHT)),
            "textDark": tk.StringVar(value=argb_to_hex(DEFAULT_TEXT_DARK)),
            "bgLight": tk.StringVar(value=""),
            "bgDark": tk.StringVar(value=""),
        }
        self.tile_bg_light_var = tk.StringVar()
        self.tile_bg_dark_var = tk.StringVar()

        r = 0
        r = self._adv_check(inner, r, "Показывать заголовок", self.show_title_var)
        r = self._adv_label(inner, r, "Позиция заголовка (0 сверху / 1 снизу)")
        ttk.Spinbox(inner, from_=0, to=1, textvariable=self.title_pos_var, width=6, command=self._on_adv_change).grid(
            row=r, column=1, sticky="w"
        )
        r += 1
        r = self._adv_entry(inner, r, "Свой заголовок", self.custom_title_var)
        self.show_unit_row = r
        r = self._adv_check(inner, r, "Показывать единицу", self.show_unit_var)
        self.dual_row = r
        r = self._adv_check(inner, r, "Две метрики в одну строку", self.dual_var)
        r = self._adv_spin(inner, r, "Масштаб", self.scale_var, 0.1, 2.0, 0.1)
        r = self._adv_spin(inner, r, "Скругление (shape)", self.shape_var, 0, 50, 1)
        ttk.Label(inner, text="Выравнивание").grid(row=r, column=0, sticky="w", pady=2)
        self.text_align_combo = ttk.Combobox(
            inner, values=["центр", "начало", "конец"], state="readonly", width=18
        )
        self.text_align_combo.grid(row=r, column=1, sticky="w")
        self.text_align_combo.bind("<<ComboboxSelected>>", lambda _e: self._on_adv_change())
        r += 1
        ttk.Label(inner, text="Начертание").grid(row=r, column=0, sticky="w", pady=2)
        self.font_combo = ttk.Combobox(
            inner, values=["обычный", "средний", "полужирный"], state="readonly", width=18
        )
        self.font_combo.grid(row=r, column=1, sticky="w")
        self.font_combo.bind("<<ComboboxSelected>>", lambda _e: self._on_adv_change())
        r += 1
        r = self._adv_spin(inner, r, "Отступ сверху %", self.pad_top, 0, 50, 1)
        r = self._adv_spin(inner, r, "Отступ снизу %", self.pad_bottom, 0, 50, 1)
        r = self._adv_spin(inner, r, "Отступ слева %", self.pad_start, 0, 50, 1)
        r = self._adv_spin(inner, r, "Отступ справа %", self.pad_end, 0, 50, 1)

        r = self._adv_color(inner, r, "Текст light", "textLight")
        r = self._adv_color(inner, r, "Текст dark", "textDark")
        r = self._adv_color(inner, r, "Фон light (пусто=default)", "bgLight", allow_empty=True)
        r = self._adv_color(inner, r, "Фон dark (пусто=default)", "bgDark", allow_empty=True)
        r = self._adv_tile_bg(inner, r, "Фон плитки light", self.tile_bg_light_var, side="light")
        r = self._adv_tile_bg(inner, r, "Фон плитки dark", self.tile_bg_dark_var, side="dark")

        self.acc_frame = ttk.LabelFrame(inner, text="Точность / дата", padding=6)
        self.acc_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=6)
        r += 1
        ttk.Label(self.acc_frame, text="valueAccuracy").grid(row=0, column=0, sticky="w")
        self.acc_combo = ttk.Combobox(
            self.acc_frame,
            textvariable=self.value_accuracy_var,
            values=["default", "0", "1", "2"],
            state="readonly",
            width=10,
        )
        self.acc_combo.grid(row=0, column=1, sticky="w")
        self.acc_combo.bind("<<ComboboxSelected>>", lambda _e: self._on_adv_change())
        ttk.Label(self.acc_frame, text="Формат даты/времени").grid(row=1, column=0, sticky="w")
        ttk.Entry(self.acc_frame, textvariable=self.datetime_var, width=28).grid(
            row=1, column=1, sticky="w"
        )
        self.datetime_var.trace_add("write", lambda *_: self._on_adv_change())

        self.mbcan_check = ttk.Checkbutton(
            inner, text="Использовать mbCAN/VHAL", variable=self.mbcan_var, command=self._on_adv_change
        )
        self.mbcan_check.grid(row=r, column=0, columnspan=2, sticky="w")
        r += 1

        self.stepper_frame = ttk.LabelFrame(inner, text="Стиль кнопок +/-", padding=6)
        self.stepper_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        ttk.Radiobutton(
            self.stepper_frame, text="+ / −", value=0, variable=self.stepper_var, command=self._on_adv_change
        ).pack(side="left")
        ttk.Radiobutton(
            self.stepper_frame, text="Стрелки", value=1, variable=self.stepper_var, command=self._on_adv_change
        ).pack(side="left")

        self.drive_frame = ttk.LabelFrame(inner, text="Режим вождения (target)", padding=6)
        self.drive_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        ttk.Spinbox(
            self.drive_frame, from_=0, to=10, textvariable=self.drive_mode_var, width=6, command=self._on_adv_change
        ).pack(side="left")

        self.trip_frame = ttk.LabelFrame(inner, text="Поездка", padding=6)
        self.trip_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        ttk.Checkbutton(
            self.trip_frame,
            text="Разделители строк",
            variable=self.trip_dividers_var,
            command=self._on_adv_change,
        ).grid(row=0, column=0, sticky="w")
        ttk.Label(self.trip_frame, text="Ширина колонки меток %").grid(row=1, column=0, sticky="w")
        ttk.Spinbox(
            self.trip_frame, from_=20, to=80, textvariable=self.trip_label_var, width=6, command=self._on_adv_change
        ).grid(row=1, column=1, sticky="w")
        ttk.Label(self.trip_frame, text="Источник (0 текущая / 1 предыдущая)").grid(row=2, column=0, sticky="w")
        ttk.Spinbox(
            self.trip_frame, from_=0, to=1, textvariable=self.trip_source_var, width=6, command=self._on_adv_change
        ).grid(row=2, column=1, sticky="w")

        self.launcher_frame = ttk.LabelFrame(inner, text="Ярлык приложения", padding=6)
        self.launcher_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        ttk.Label(self.launcher_frame, text="package").grid(row=0, column=0, sticky="w")
        ttk.Entry(self.launcher_frame, textvariable=self.launcher_pkg_var, width=36).grid(
            row=0, column=1, sticky="w"
        )
        self.launcher_pkg_var.trace_add("write", lambda *_: self._on_adv_change())
        ttk.Checkbutton(
            self.launcher_frame,
            text="Freeform",
            variable=self.freeform_var,
            command=self._on_adv_change,
        ).grid(row=1, column=0, sticky="w")
        ttk.Combobox(
            self.launcher_frame,
            textvariable=self.freeform_side_var,
            values=["left", "right", "top", "bottom"],
            state="readonly",
            width=10,
        ).grid(row=1, column=1, sticky="w")
        self.freeform_side_var.trace_add("write", lambda *_: self._on_adv_change())
        ttk.Spinbox(
            self.launcher_frame,
            from_=20,
            to=80,
            increment=10,
            textvariable=self.freeform_pct_var,
            width=6,
            command=self._on_adv_change,
        ).grid(row=1, column=2, sticky="w")

        self.http_frame = ttk.LabelFrame(inner, text="HTTP-запрос", padding=6)
        self.http_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        self.http_text = tk.Text(self.http_frame, height=5, width=50, font=("Consolas", 9))
        self.http_text.grid(row=0, column=0, columnspan=2, sticky="ew")
        self.http_text.bind("<<Modified>>", self._on_http_modified)
        ttk.Checkbutton(
            self.http_frame,
            text="Открыть в браузере",
            variable=self.http_browser_var,
            command=self._on_adv_change,
        ).grid(row=1, column=0, sticky="w")

        self.music_frame = ttk.LabelFrame(inner, text="Музыка (пакеты плееров, по одному в строке)", padding=6)
        self.music_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        self.music_text = tk.Text(self.music_frame, height=4, width=50, font=("Consolas", 9))
        self.music_text.grid(row=0, column=0, columnspan=2, sticky="ew")
        self.music_text.bind("<<Modified>>", self._on_music_modified)
        ttk.Checkbutton(
            self.music_frame, text="Auto-play при старте", variable=self.media_auto_var, command=self._on_adv_change
        ).grid(row=1, column=0, sticky="w")
        ttk.Checkbutton(
            self.music_frame,
            text="Только при работающем двигателе",
            variable=self.media_engine_var,
            command=self._on_adv_change,
        ).grid(row=2, column=0, sticky="w")
        ttk.Checkbutton(
            self.music_frame,
            text="Держать плеер на переднем плане",
            variable=self.media_fg_var,
            command=self._on_adv_change,
        ).grid(row=3, column=0, sticky="w")

        self.controls_frame = ttk.LabelFrame(inner, text="Цвета элементов управления", padding=6)
        self.controls_frame.grid(row=r, column=0, columnspan=3, sticky="ew", pady=4)
        r += 1
        ttk.Checkbutton(
            self.controls_frame,
            text="Цвета по умолчанию",
            variable=self.controls_default_var,
            command=self._on_adv_change,
        ).grid(row=0, column=0, sticky="w")
        ttk.Label(self.controls_frame, text="controlShape (пусто=default)").grid(row=1, column=0, sticky="w")
        ttk.Entry(self.controls_frame, textvariable=self.control_shape_var, width=8).grid(
            row=1, column=1, sticky="w"
        )
        self.control_shape_var.trace_add("write", lambda *_: self._on_adv_change())

        for var in (
            self.show_title_var,
            self.show_unit_var,
            self.dual_var,
            self.mbcan_var,
        ):
            var.trace_add("write", lambda *_: self._on_adv_change())
        for var in (
            self.custom_title_var,
            self.tile_bg_light_var,
            self.tile_bg_dark_var,
        ):
            var.trace_add("write", lambda *_: self._on_adv_change())

    def _adv_label(self, parent: ttk.Frame, row: int, text: str) -> int:
        ttk.Label(parent, text=text).grid(row=row, column=0, sticky="w", pady=2)
        return row

    def _adv_check(self, parent: ttk.Frame, row: int, text: str, var: tk.BooleanVar) -> int:
        ttk.Checkbutton(parent, text=text, variable=var, command=self._on_adv_change).grid(
            row=row, column=0, columnspan=2, sticky="w", pady=2
        )
        return row + 1

    def _adv_entry(self, parent: ttk.Frame, row: int, text: str, var: tk.StringVar) -> int:
        ttk.Label(parent, text=text).grid(row=row, column=0, sticky="w", pady=2)
        ttk.Entry(parent, textvariable=var, width=36).grid(row=row, column=1, sticky="w", pady=2)
        return row + 1

    def _adv_spin(
        self,
        parent: ttk.Frame,
        row: int,
        text: str,
        var: tk.Variable,
        from_: float,
        to: float,
        increment: float,
    ) -> int:
        ttk.Label(parent, text=text).grid(row=row, column=0, sticky="w", pady=2)
        ttk.Spinbox(
            parent,
            from_=from_,
            to=to,
            increment=increment,
            textvariable=var,
            width=8,
            command=self._on_adv_change,
        ).grid(row=row, column=1, sticky="w", pady=2)
        var.trace_add("write", lambda *_: self._on_adv_change())
        return row + 1

    def _adv_color(
        self,
        parent: ttk.Frame,
        row: int,
        text: str,
        key: str,
        *,
        allow_empty: bool = False,
    ) -> int:
        ttk.Label(parent, text=text).grid(row=row, column=0, sticky="w", pady=2)
        ent = ttk.Entry(parent, textvariable=self.color_vars[key], width=14)
        ent.grid(row=row, column=1, sticky="w")
        self.color_vars[key].trace_add("write", lambda *_: self._on_adv_change())
        btns = ttk.Frame(parent)
        btns.grid(row=row, column=2, sticky="w")

        def pick(k: str = key) -> None:
            chosen = _askcolor_rrggbb(self, self.color_vars[k].get() or "#FFFFFFFF", title=text)
            if chosen:
                self.color_vars[k].set(chosen)
                self._on_adv_change()

        def clear(k: str = key, empty: bool = allow_empty) -> None:
            if empty:
                self.color_vars[k].set("")
                self._on_adv_change()

        def palette_menu(k: str = key) -> None:
            menu = tk.Menu(self, tearoff=0)
            presets = self.color_presets or []
            if not presets:
                menu.add_command(label="(нет пресетов темы)", state="disabled")
            else:
                for preset in presets:
                    p = preset.strip().upper()
                    if not p.startswith("#"):
                        p = f"#{p}"
                    if len(p) == 7:
                        p = f"#FF{p[1:]}"
                    menu.add_command(
                        label=p,
                        command=lambda value=p, kk=k: (
                            self.color_vars[kk].set(value),
                            self._on_adv_change(),
                        ),
                    )
            try:
                menu.tk_popup(
                    pal_btn.winfo_rootx(),
                    pal_btn.winfo_rooty() + pal_btn.winfo_height(),
                )
            finally:
                menu.grab_release()

        ttk.Button(btns, text="…", width=3, command=pick).pack(side="left", padx=(0, 2))
        pal_btn = ttk.Button(btns, text="Палитра", width=8)
        pal_btn.configure(command=palette_menu)
        pal_btn.pack(side="left", padx=(0, 2))
        if allow_empty:
            ttk.Button(btns, text="✕", width=3, command=clear).pack(side="left")
        return row + 1

    def _adv_tile_bg(
        self,
        parent: ttk.Frame,
        row: int,
        text: str,
        var: tk.StringVar,
        *,
        side: str,
    ) -> int:
        ttk.Label(parent, text=text).grid(row=row, column=0, sticky="nw", pady=2)
        box = ttk.Frame(parent)
        box.grid(row=row, column=1, columnspan=2, sticky="ew", pady=2)
        combo = ttk.Combobox(box, textvariable=var, width=36)
        combo.pack(side="top", fill="x")
        combo.bind("<<ComboboxSelected>>", lambda _e: self._on_adv_change())
        var.trace_add("write", lambda *_: self._on_adv_change())
        if not hasattr(self, "_tile_bg_combos"):
            self._tile_bg_combos: list[ttk.Combobox] = []
        self._tile_bg_combos.append(combo)
        row_btns = ttk.Frame(box)
        row_btns.pack(side="top", anchor="w", pady=(2, 0))

        def browse(s: str = side, v: tk.StringVar = var) -> None:
            self._browse_tile_background(v, side=s)

        def clear(v: tk.StringVar = var) -> None:
            v.set("")
            self._on_adv_change()

        ttk.Button(row_btns, text="Файл…", command=browse).pack(side="left", padx=(0, 4))
        ttk.Button(row_btns, text="Очистить", command=clear).pack(side="left")
        return row + 1

    def _refresh_tile_bg_lists(self) -> None:
        names = sorted(self.tile_backgrounds.keys())
        for combo in getattr(self, "_tile_bg_combos", []):
            combo.configure(values=names)

    def _default_tile_bg_rel_path(self, side: str, source: Path) -> str:
        panel_id = str(self.panel.get("id") or "panel").strip() or "panel"
        ext = source.suffix.lower().lstrip(".") or "png"
        if ext not in IMAGE_EXTENSIONS:
            ext = "png"
        return f"{panel_id}/{self.cell_index}_{side}.{ext}"

    def _browse_tile_background(self, var: tk.StringVar, *, side: str) -> None:
        path = filedialog.askopenfilename(
            parent=self,
            title=f"Фон плитки ({side})",
            filetypes=[
                ("Изображения", "*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp"),
                ("Все файлы", "*.*"),
            ],
        )
        if not path:
            return
        src = Path(path)
        try:
            data = src.read_bytes()
        except OSError as exc:
            messagebox.showerror("Фон плитки", f"Не удалось прочитать файл:\n{exc}", parent=self)
            return
        if len(data) > MAX_ASSET_BYTES:
            messagebox.showerror(
                "Фон плитки",
                f"Файл слишком большой (макс. {MAX_ASSET_BYTES // (1024 * 1024)} МБ).",
                parent=self,
            )
            return
        if not is_image_filename(src.name):
            messagebox.showerror("Фон плитки", "Неподдерживаемый формат изображения.", parent=self)
            return

        suggested = self._default_tile_bg_rel_path(side, src)
        current = var.get().strip()
        ask = tk.Toplevel(self)
        ask.title("Путь в теме")
        ask.transient(self)
        ask.grab_set()
        rel_var = tk.StringVar(value=current or suggested)
        ttk.Label(
            ask,
            text="Относительный путь внутри assets/tile_backgrounds/:",
        ).pack(padx=12, pady=(12, 4), anchor="w")
        ttk.Entry(ask, textvariable=rel_var, width=48).pack(padx=12, pady=4, fill="x")
        result: dict[str, str | None] = {"path": None}

        def accept() -> None:
            result["path"] = rel_var.get().strip().replace("\\", "/")
            ask.destroy()

        btns = ttk.Frame(ask)
        btns.pack(padx=12, pady=12, fill="x")
        ttk.Button(btns, text="OK", command=accept).pack(side="right", padx=4)
        ttk.Button(btns, text="Отмена", command=ask.destroy).pack(side="right")
        ask.wait_window()
        rel = result["path"]
        if not rel:
            return
        while rel.startswith("/"):
            rel = rel[1:]
        if not rel:
            messagebox.showerror("Фон плитки", "Путь не может быть пустым.", parent=self)
            return
        self.tile_backgrounds[rel] = data
        var.set(rel)
        self._refresh_tile_bg_lists()
        self._on_adv_change()

    def _build_panel_page(self) -> None:
        frame = self.panel_page
        self.panel_name_var = tk.StringVar(value=str(self.panel.get("name") or ""))
        self.panel_enabled_var = tk.BooleanVar(value=bool(self.panel.get("enabled", True)))
        self.panel_rows_var = tk.IntVar(value=int((self.panel.get("grid") or {}).get("rows", 1) or 1))
        self.panel_cols_var = tk.IntVar(value=int((self.panel.get("grid") or {}).get("cols", 1) or 1))
        self.panel_spacing_var = tk.IntVar(
            value=int((self.panel.get("grid") or {}).get("spacingDp", 4) or 4)
        )
        self.panel_click_var = tk.BooleanVar(value=bool(self.panel.get("clickAction", False)))
        self.panel_bg_var = tk.BooleanVar(value=bool(self.panel.get("background", False)))
        self.panel_tbox_var = tk.BooleanVar(
            value=bool(self.panel.get("showTboxDisconnectIndicator", False))
        )
        self.panel_page_var = tk.IntVar(value=int(self.panel.get("pageNumber", 1) or 1))

        ttk.Label(frame, text="Имя панели").grid(row=0, column=0, sticky="w", pady=2)
        ttk.Entry(frame, textvariable=self.panel_name_var, width=32).grid(row=0, column=1, sticky="w")
        ttk.Checkbutton(frame, text="Включена", variable=self.panel_enabled_var).grid(
            row=1, column=0, columnspan=2, sticky="w"
        )
        ttk.Label(frame, text="Строки").grid(row=2, column=0, sticky="w")
        ttk.Spinbox(frame, from_=1, to=12, textvariable=self.panel_rows_var, width=6).grid(
            row=2, column=1, sticky="w"
        )
        ttk.Label(frame, text="Столбцы").grid(row=3, column=0, sticky="w")
        ttk.Spinbox(frame, from_=1, to=12, textvariable=self.panel_cols_var, width=6).grid(
            row=3, column=1, sticky="w"
        )
        ttk.Label(frame, text="Отступ сетки, dp").grid(row=4, column=0, sticky="w")
        ttk.Spinbox(frame, from_=0, to=48, textvariable=self.panel_spacing_var, width=6).grid(
            row=4, column=1, sticky="w"
        )
        ttk.Checkbutton(frame, text="Действие по клику", variable=self.panel_click_var).grid(
            row=5, column=0, columnspan=2, sticky="w"
        )
        ttk.Checkbutton(frame, text="Фон панели", variable=self.panel_bg_var).grid(
            row=6, column=0, columnspan=2, sticky="w"
        )
        ttk.Checkbutton(
            frame, text="Индикатор отключения TBox", variable=self.panel_tbox_var
        ).grid(row=7, column=0, columnspan=2, sticky="w")
        if self.is_main_screen:
            ttk.Label(frame, text="Страница").grid(row=8, column=0, sticky="w")
            ttk.Spinbox(frame, from_=1, to=20, textvariable=self.panel_page_var, width=6).grid(
                row=8, column=1, sticky="w"
            )
        ttk.Label(
            frame,
            text="После OK сетка нормализуется: widgets.length = rows×cols.",
        ).grid(row=9, column=0, columnspan=2, sticky="w", pady=8)

    def _load_widget_into_form(self) -> None:
        w = self.widget
        self.show_title_var.set(bool(w.get("showTitle", False)))
        self.title_pos_var.set(int(w.get("titlePosition", default_title_position(str(w.get("dataKey") or "")))))
        self.custom_title_var.set(str(w.get("customTitle") or ""))
        self.show_unit_var.set(bool(w.get("showUnit", True)))
        self.dual_var.set(bool(w.get("singleLineDualMetrics", False)))
        self.scale_var.set(float(w.get("scale", 1.0)))
        self.shape_var.set(int(w.get("shape", DEFAULT_SHAPE)))
        align = int(w.get("textAlign", DEFAULT_TEXT_ALIGN) or 0)
        self.text_align_combo.current(max(0, min(2, align)))
        weight = int(w.get("fontWeight", DEFAULT_FONT_WEIGHT) or 1)
        self.font_combo.current(max(0, min(2, weight)))
        self.pad_top.set(int(w.get("paddingTopPercent", DEFAULT_PADDING) or 0))
        self.pad_bottom.set(int(w.get("paddingBottomPercent", DEFAULT_PADDING) or 0))
        self.pad_start.set(int(w.get("paddingStartPercent", DEFAULT_PADDING) or 0))
        self.pad_end.set(int(w.get("paddingEndPercent", DEFAULT_PADDING) or 0))
        self.color_vars["textLight"].set(argb_to_hex(int(w.get("textColorLight", DEFAULT_TEXT_LIGHT))))
        self.color_vars["textDark"].set(argb_to_hex(int(w.get("textColorDark", DEFAULT_TEXT_DARK))))
        bg_l = w.get("backgroundColorLight")
        bg_d = w.get("backgroundColorDark")
        self.color_vars["bgLight"].set(argb_to_hex(int(bg_l)) if bg_l is not None else "")
        self.color_vars["bgDark"].set(argb_to_hex(int(bg_d)) if bg_d is not None else "")
        self.tile_bg_light_var.set(str(w.get("tileBackgroundImageRelPathLight") or ""))
        self.tile_bg_dark_var.set(str(w.get("tileBackgroundImageRelPathDark") or ""))
        acc = w.get("valueAccuracy")
        self.value_accuracy_var.set("default" if acc is None else str(acc))
        self.datetime_var.set(str(w.get("dateTimeFormat") or ""))
        self.mbcan_var.set(bool(w.get("useMbCanVhal", False)))
        self.stepper_var.set(int(w.get("stepperAdjustIconStyle", 0) or 0))
        self.drive_mode_var.set(int(w.get("selectedDriveMode", DEFAULT_DRIVE_MODE) or DEFAULT_DRIVE_MODE))
        self.trip_dividers_var.set(w.get("tripWidgetShowRowDividers", True) is not False)
        self.trip_label_var.set(
            int(w.get("tripWidgetLabelColumnWidthPercent", DEFAULT_TRIP_LABEL_PCT) or DEFAULT_TRIP_LABEL_PCT)
        )
        self.trip_source_var.set(int(w.get("tripWidgetSource", 0) or 0))
        self.launcher_pkg_var.set(str(w.get("launcherAppPackage") or ""))
        self.freeform_var.set(bool(w.get("launcherFreeformEnabled", False)))
        self.freeform_side_var.set(str(w.get("launcherFreeformSide") or DEFAULT_FREEFORM_SIDE))
        self.freeform_pct_var.set(int(w.get("launcherFreeformPercent", DEFAULT_FREEFORM_PERCENT)))
        self.http_text.delete("1.0", "end")
        self.http_text.insert("1.0", str(w.get("httpRequestYaml") or DEFAULT_HTTP_YAML))
        self.http_text.edit_modified(False)
        self.http_browser_var.set(bool(w.get("httpOpenBrowser", False)))
        players = w.get("mediaPlayers") if isinstance(w.get("mediaPlayers"), list) else []
        self.music_text.delete("1.0", "end")
        self.music_text.insert("1.0", "\n".join(str(p) for p in players))
        self.music_text.edit_modified(False)
        self.media_auto_var.set(bool(w.get("mediaAutoPlayOnInit", False)))
        self.media_engine_var.set(bool(w.get("mediaAutoPlayOnlyWhenEngineRunning", False)))
        self.media_fg_var.set(bool(w.get("mediaKeepPlayerForeground", False)))
        control_keys = (
            "controlInactiveColorLight",
            "controlInactiveColorDark",
            "controlActiveColorLight",
            "controlActiveColorDark",
            "controlInactiveBackgroundColorLight",
            "controlInactiveBackgroundColorDark",
            "controlActiveBackgroundColorLight",
            "controlActiveBackgroundColorDark",
        )
        self.controls_default_var.set(all(w.get(k) is None for k in control_keys if k not in w or w.get(k) is None) and all(k not in w or w.get(k) is None for k in control_keys))
        # Simpler: default if none of control colors present
        self.controls_default_var.set(not any(k in w and w[k] is not None for k in control_keys))
        self.control_shape_var.set("" if w.get("controlShape") is None else str(w.get("controlShape")))
        self._update_advanced_visibility()

    def _update_advanced_visibility(self) -> None:
        meta = get_widget_type(str(self.widget.get("dataKey") or ""))
        # Show/hide frames
        for frame, visible in (
            (self.acc_frame, meta.supports_value_accuracy or meta.supports_datetime_format),
            (self.mbcan_check, meta.supports_mbcan),
            (self.stepper_frame, meta.supports_stepper_icons),
            (self.drive_frame, meta.is_drive_mode),
            (self.trip_frame, meta.is_trip),
            (self.launcher_frame, meta.is_launcher),
            (self.http_frame, meta.is_http),
            (self.music_frame, meta.is_music),
        ):
            if visible:
                frame.grid()
            else:
                frame.grid_remove()

    def _collect_widget_from_form(self) -> dict[str, Any]:
        w = empty_widget(str(self.widget.get("dataKey") or ""))
        # Preserve appWidgetId if present
        if self.widget.get("appWidgetId") is not None:
            w["appWidgetId"] = self.widget["appWidgetId"]
        w["showTitle"] = bool(self.show_title_var.get())
        w["titlePosition"] = int(self.title_pos_var.get())
        custom = self.custom_title_var.get().strip()
        if custom:
            w["customTitle"] = custom
        w["showUnit"] = bool(self.show_unit_var.get())
        w["singleLineDualMetrics"] = bool(self.dual_var.get())
        try:
            w["scale"] = float(self.scale_var.get())
        except (tk.TclError, ValueError):
            w["scale"] = 1.0
        try:
            w["shape"] = int(self.shape_var.get())
        except (tk.TclError, ValueError):
            w["shape"] = 0
        w["textAlign"] = max(0, self.text_align_combo.current())
        w["fontWeight"] = max(0, self.font_combo.current())
        w["paddingTopPercent"] = int(self.pad_top.get() or 0)
        w["paddingBottomPercent"] = int(self.pad_bottom.get() or 0)
        w["paddingStartPercent"] = int(self.pad_start.get() or 0)
        w["paddingEndPercent"] = int(self.pad_end.get() or 0)
        w["textColorLight"] = hex_to_argb(self.color_vars["textLight"].get(), default=DEFAULT_TEXT_LIGHT)
        w["textColorDark"] = hex_to_argb(self.color_vars["textDark"].get(), default=DEFAULT_TEXT_DARK)
        bg_l = self.color_vars["bgLight"].get().strip()
        bg_d = self.color_vars["bgDark"].get().strip()
        if bg_l:
            w["backgroundColorLight"] = hex_to_argb(bg_l)
        if bg_d:
            w["backgroundColorDark"] = hex_to_argb(bg_d)
        if self.tile_bg_light_var.get().strip():
            w["tileBackgroundImageRelPathLight"] = self.tile_bg_light_var.get().strip()
        if self.tile_bg_dark_var.get().strip():
            w["tileBackgroundImageRelPathDark"] = self.tile_bg_dark_var.get().strip()
        acc = self.value_accuracy_var.get()
        if acc in {"0", "1", "2"}:
            w["valueAccuracy"] = int(acc)
        if self.datetime_var.get().strip():
            w["dateTimeFormat"] = self.datetime_var.get().strip()
        w["useMbCanVhal"] = bool(self.mbcan_var.get())
        if int(self.stepper_var.get() or 0):
            w["stepperAdjustIconStyle"] = 1
        if int(self.drive_mode_var.get() or DEFAULT_DRIVE_MODE) != DEFAULT_DRIVE_MODE:
            w["selectedDriveMode"] = int(self.drive_mode_var.get())
        if not self.trip_dividers_var.get():
            w["tripWidgetShowRowDividers"] = False
        if int(self.trip_label_var.get() or DEFAULT_TRIP_LABEL_PCT) != DEFAULT_TRIP_LABEL_PCT:
            w["tripWidgetLabelColumnWidthPercent"] = int(self.trip_label_var.get())
        if int(self.trip_source_var.get() or 0):
            w["tripWidgetSource"] = 1
        if self.launcher_pkg_var.get().strip():
            w["launcherAppPackage"] = self.launcher_pkg_var.get().strip()
        if self.freeform_var.get():
            w["launcherFreeformEnabled"] = True
            w["launcherFreeformSide"] = self.freeform_side_var.get() or DEFAULT_FREEFORM_SIDE
            w["launcherFreeformPercent"] = int(self.freeform_pct_var.get() or DEFAULT_FREEFORM_PERCENT)
        yaml = self.http_text.get("1.0", "end-1c").strip()
        if w.get("dataKey") == "httpRequestWidget":
            w["httpRequestYaml"] = yaml or DEFAULT_HTTP_YAML
            w["httpOpenBrowser"] = bool(self.http_browser_var.get())
        players = [
            line.strip()
            for line in self.music_text.get("1.0", "end-1c").splitlines()
            if line.strip()
        ]
        if players:
            w["mediaPlayers"] = players
        w["mediaAutoPlayOnInit"] = bool(self.media_auto_var.get())
        w["mediaAutoPlayOnlyWhenEngineRunning"] = bool(self.media_engine_var.get())
        w["mediaKeepPlayerForeground"] = bool(self.media_fg_var.get())
        if not self.controls_default_var.get():
            # Keep existing control colors if any; otherwise leave unset (defaults).
            for key in (
                "controlInactiveColorLight",
                "controlInactiveColorDark",
                "controlActiveColorLight",
                "controlActiveColorDark",
                "controlInactiveBackgroundColorLight",
                "controlInactiveBackgroundColorDark",
                "controlActiveBackgroundColorLight",
                "controlActiveBackgroundColorDark",
            ):
                if key in self.widget and self.widget[key] is not None:
                    w[key] = self.widget[key]
        cs = self.control_shape_var.get().strip()
        if cs.isdigit():
            w["controlShape"] = int(cs)
        self.widget = w
        return w

    def _on_http_modified(self, _event: object | None = None) -> None:
        if self.http_text.edit_modified():
            self.http_text.edit_modified(False)
            self._on_adv_change()

    def _on_music_modified(self, _event: object | None = None) -> None:
        if self.music_text.edit_modified():
            self.music_text.edit_modified(False)
            self._on_adv_change()

    def _on_adv_change(self) -> None:
        if self._building:
            return
        self._collect_widget_from_form()
        self._update_preview()

    def _update_preview(self) -> None:
        if not self._building:
            try:
                self._collect_widget_from_form()
            except Exception:
                pass
        self.preview.set_widget(self.widget, dark=bool(self.dark_preview.get()))

    def _apply_panel_fields(self) -> None:
        self.panel["name"] = self.panel_name_var.get().strip() or self.panel.get("id", "panel")
        self.panel["enabled"] = bool(self.panel_enabled_var.get())
        grid = self.panel.setdefault("grid", {})
        if not isinstance(grid, dict):
            grid = {}
            self.panel["grid"] = grid
        rows = max(1, int(self.panel_rows_var.get() or 1))
        cols = max(1, int(self.panel_cols_var.get() or 1))
        grid["rows"] = rows
        grid["cols"] = cols
        grid["spacingDp"] = max(0, int(self.panel_spacing_var.get() or 0))
        self.panel["clickAction"] = bool(self.panel_click_var.get())
        self.panel["background"] = bool(self.panel_bg_var.get())
        self.panel["showTboxDisconnectIndicator"] = bool(self.panel_tbox_var.get())
        if self.is_main_screen:
            self.panel["pageNumber"] = max(1, int(self.panel_page_var.get() or 1))
        widgets = normalize_widgets(rows, cols, self.panel.get("widgets"))
        # Write current cell before normalize already applied length
        idx = min(self.cell_index, len(widgets) - 1)
        widgets[idx] = serialize_widget(self._collect_widget_from_form())
        self.panel["widgets"] = widgets

    def _ok(self) -> None:
        self._collect_widget_from_form()
        self._apply_panel_fields()
        self.result = self.panel
        self.destroy()

    def _cancel(self) -> None:
        self.result = None
        self.destroy()


def open_widget_editor(
    master: tk.Misc,
    panel: dict[str, Any],
    *,
    cell_index: int = 0,
    is_main_screen: bool = True,
    tile_backgrounds: MutableMapping[str, bytes] | None = None,
    color_presets: list[str] | None = None,
) -> dict[str, Any] | None:
    dlg = WidgetEditDialog(
        master,
        panel=panel,
        cell_index=cell_index,
        is_main_screen=is_main_screen,
        tile_backgrounds=tile_backgrounds,
        color_presets=color_presets,
    )
    master.wait_window(dlg)
    return dlg.result


def pick_cell_then_edit(
    master: tk.Misc,
    panel: dict[str, Any],
    *,
    is_main_screen: bool,
    tile_backgrounds: MutableMapping[str, bytes] | None = None,
    color_presets: list[str] | None = None,
) -> dict[str, Any] | None:
    grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
    try:
        rows = max(1, int(grid.get("rows", 1)))
        cols = max(1, int(grid.get("cols", 1)))
    except (TypeError, ValueError):
        rows, cols = 1, 1
    count = rows * cols
    if count <= 1:
        return open_widget_editor(
            master,
            panel,
            cell_index=0,
            is_main_screen=is_main_screen,
            tile_backgrounds=tile_backgrounds,
            color_presets=color_presets,
        )

    chooser = tk.Toplevel(master)
    chooser.title("Ячейка панели")
    chooser.transient(master)
    chooser.grab_set()
    selected: dict[str, int | None] = {"index": None}
    ttk.Label(chooser, text="Выберите ячейку для редактирования:").pack(padx=12, pady=8)

    def on_click(i: int) -> None:
        selected["index"] = i
        chooser.destroy()

    grid_editor = PanelGridEditor(chooser, on_cell_click=on_click, cell_width=100, cell_height=70)
    grid_editor.pack(fill="both", expand=True, padx=8, pady=8)
    grid_editor.set_panel(panel)
    ttk.Button(chooser, text="Отмена", command=chooser.destroy).pack(pady=8)
    master.wait_window(chooser)
    if selected["index"] is None:
        return None
    return open_widget_editor(
        master,
        panel,
        cell_index=int(selected["index"]),
        is_main_screen=is_main_screen,
        tile_backgrounds=tile_backgrounds,
        color_presets=color_presets,
    )
