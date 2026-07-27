"""
TBox Theme Editor — desktop GUI for creating and editing `.tboxtheme` files.

Designed for Windows (Python 3.10+ with tkinter). Also runs on Linux/macOS.
"""

from __future__ import annotations

import sys
import tkinter as tk
from pathlib import Path
from tkinter import colorchooser, filedialog, messagebox, ttk
from theme_bundle import (
    ALL_SECTIONS,
    FONT_SLUGS,
    IMAGE_EXTENSIONS,
    MAX_ASSET_BYTES,
    SECTION_APP_ICONS,
    SECTION_FLOATING_PANELS,
    SECTION_MAIN_SCREEN,
    THEME_FILE_EXTENSION,
    ThemeBundle,
    get_main_screen,
    get_visual_theme,
    normalize_hex_color,
    sanitize_theme_export_base_name,
    theme_file_name_from_base_name,
)

try:
    from PIL import Image, ImageTk

    HAS_PIL = True
except ImportError:  # pragma: no cover
    HAS_PIL = False


APP_TITLE = "TBox Theme Editor"
APP_VERSION = "1.0.0"


class ThemeEditorApp(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title(f"{APP_TITLE} {APP_VERSION}")
        self.geometry("1100x720")
        self.minsize(900, 600)

        self.bundle = ThemeBundle.new_empty()
        self.dirty = False
        self._preview_image = None
        self._suppress_trace = False

        self._build_style()
        self._build_menu()
        self._build_toolbar()
        self._build_body()
        self._build_status()
        self.protocol("WM_DELETE_WINDOW", self._on_close)
        self._reload_from_bundle()
        self._set_dirty(False)

    def _build_style(self) -> None:
        style = ttk.Style(self)
        if sys.platform.startswith("win"):
            try:
                style.theme_use("vista")
            except tk.TclError:
                pass
        style.configure("Header.TLabel", font=("Segoe UI", 11, "bold"))
        style.configure("Muted.TLabel", foreground="#555555")

    def _build_menu(self) -> None:
        menubar = tk.Menu(self)
        file_menu = tk.Menu(menubar, tearoff=0)
        file_menu.add_command(label="Новая тема…", accelerator="Ctrl+N", command=self.new_theme)
        file_menu.add_command(label="Открыть…", accelerator="Ctrl+O", command=self.open_theme)
        file_menu.add_separator()
        file_menu.add_command(label="Сохранить", accelerator="Ctrl+S", command=self.save_theme)
        file_menu.add_command(
            label="Сохранить как…",
            accelerator="Ctrl+Shift+S",
            command=self.save_theme_as,
        )
        file_menu.add_separator()
        file_menu.add_command(label="Выход", command=self._on_close)
        menubar.add_cascade(label="Файл", menu=file_menu)

        help_menu = tk.Menu(menubar, tearoff=0)
        help_menu.add_command(label="О программе", command=self._show_about)
        menubar.add_cascade(label="Справка", menu=help_menu)
        self.config(menu=menubar)

        self.bind_all("<Control-n>", lambda _e: self.new_theme())
        self.bind_all("<Control-o>", lambda _e: self.open_theme())
        self.bind_all("<Control-s>", lambda _e: self.save_theme())
        self.bind_all("<Control-S>", lambda _e: self.save_theme_as())

    def _build_toolbar(self) -> None:
        bar = ttk.Frame(self, padding=(8, 6))
        bar.pack(fill="x")
        ttk.Button(bar, text="Новая", command=self.new_theme).pack(side="left", padx=2)
        ttk.Button(bar, text="Открыть", command=self.open_theme).pack(side="left", padx=2)
        ttk.Button(bar, text="Сохранить", command=self.save_theme).pack(side="left", padx=2)
        ttk.Button(bar, text="Сохранить как", command=self.save_theme_as).pack(side="left", padx=2)
        ttk.Separator(bar, orient="vertical").pack(side="left", fill="y", padx=8)
        ttk.Button(bar, text="Проверить", command=self.validate_theme).pack(side="left", padx=2)
        self.path_var = tk.StringVar(value="(новая тема)")
        ttk.Label(bar, textvariable=self.path_var, style="Muted.TLabel").pack(
            side="right", padx=4
        )

    def _build_body(self) -> None:
        notebook = ttk.Notebook(self)
        notebook.pack(fill="both", expand=True, padx=8, pady=(0, 4))
        self.notebook = notebook

        self.general_tab = ttk.Frame(notebook, padding=10)
        self.colors_tab = ttk.Frame(notebook, padding=10)
        self.wallpapers_tab = ttk.Frame(notebook, padding=10)
        self.icons_tab = ttk.Frame(notebook, padding=10)
        self.http_tab = ttk.Frame(notebook, padding=10)
        self.tiles_tab = ttk.Frame(notebook, padding=10)
        self.panels_tab = ttk.Frame(notebook, padding=10)
        self.json_tab = ttk.Frame(notebook, padding=10)

        notebook.add(self.general_tab, text="Общие")
        notebook.add(self.colors_tab, text="Цвета")
        notebook.add(self.wallpapers_tab, text="Обои")
        notebook.add(self.icons_tab, text="Иконки приложений")
        notebook.add(self.http_tab, text="Иконки HTTP")
        notebook.add(self.tiles_tab, text="Фоны плиток")
        notebook.add(self.panels_tab, text="Панели")
        notebook.add(self.json_tab, text="JSON")

        self._build_general_tab()
        self._build_colors_tab()
        self._build_wallpapers_tab()
        self._build_asset_tab(
            self.icons_tab,
            title="Иконки ярлыков приложений (PNG)",
            hint="Имя файла: package.name.png — как в assets/icons/",
            list_attr="icons",
            filetypes=[("PNG", "*.png"), ("Все файлы", "*.*")],
            rename_to_png=True,
        )
        self._build_asset_tab(
            self.http_tab,
            title="Иконки виджетов HTTP-запрос",
            hint="Имя файла: {panelId}-{widgetIndex}.png — как в assets/http_request_icons/",
            list_attr="http_request_icons",
            filetypes=[("PNG", "*.png"), ("Все файлы", "*.*")],
            rename_to_png=True,
        )
        self._build_asset_tab(
            self.tiles_tab,
            title="Фоны плиток",
            hint="Относительный путь внутри assets/tile_backgrounds/, напр. panel_id/0_light",
            list_attr="tile_backgrounds",
            filetypes=[("Изображения", "*.png *.jpg *.jpeg *.webp *.gif *.bmp"), ("Все файлы", "*.*")],
            rename_to_png=False,
            ask_rel_path=True,
        )
        self._build_panels_tab()
        self._build_json_tab()

    def _build_status(self) -> None:
        self.status_var = tk.StringVar(value="Готово")
        ttk.Label(self, textvariable=self.status_var, relief="sunken", anchor="w", padding=4).pack(
            fill="x", side="bottom"
        )

    def _build_general_tab(self) -> None:
        frame = self.general_tab
        ttk.Label(frame, text="Разделы темы", style="Header.TLabel").grid(
            row=0, column=0, sticky="w", pady=(0, 6)
        )

        self.section_vars = {
            SECTION_MAIN_SCREEN: tk.BooleanVar(value=True),
            SECTION_FLOATING_PANELS: tk.BooleanVar(value=False),
            SECTION_APP_ICONS: tk.BooleanVar(value=False),
        }
        labels = {
            SECTION_MAIN_SCREEN: "Главный экран (mainScreen)",
            SECTION_FLOATING_PANELS: "Плавающие панели (floatingPanels)",
            SECTION_APP_ICONS: "Иконки приложений / HTTP (appIcons)",
        }
        for i, key in enumerate(ALL_SECTIONS):
            ttk.Checkbutton(
                frame,
                text=labels[key],
                variable=self.section_vars[key],
                command=self._on_sections_changed,
            ).grid(row=1 + i, column=0, sticky="w", pady=2)

        opts = ttk.LabelFrame(frame, text="Главный экран", padding=10)
        opts.grid(row=5, column=0, sticky="ew", pady=(16, 0))
        frame.columnconfigure(0, weight=1)

        self.page_count_var = tk.IntVar(value=1)
        self.current_page_var = tk.IntVar(value=1)
        self.wallpaper_crop_var = tk.BooleanVar(value=True)
        self.font_var = tk.StringVar(value="default")
        self.corner_size_var = tk.IntVar(value=50)

        ttk.Label(opts, text="Число страниц").grid(row=0, column=0, sticky="w")
        ttk.Spinbox(
            opts,
            from_=1,
            to=20,
            textvariable=self.page_count_var,
            width=6,
            command=self._apply_general_fields,
        ).grid(row=0, column=1, sticky="w", padx=8)
        ttk.Label(opts, text="Текущая страница").grid(row=1, column=0, sticky="w", pady=4)
        ttk.Spinbox(
            opts,
            from_=1,
            to=20,
            textvariable=self.current_page_var,
            width=6,
            command=self._apply_general_fields,
        ).grid(row=1, column=1, sticky="w", padx=8)
        ttk.Checkbutton(
            opts,
            text="Обрезка обоев (wallpaperCrop)",
            variable=self.wallpaper_crop_var,
            command=self._apply_general_fields,
        ).grid(row=2, column=0, columnspan=2, sticky="w", pady=4)
        ttk.Label(opts, text="Шрифт").grid(row=3, column=0, sticky="w")
        ttk.Combobox(
            opts,
            textvariable=self.font_var,
            values=list(FONT_SLUGS),
            state="readonly",
            width=18,
        ).grid(row=3, column=1, sticky="w", padx=8, pady=4)
        self.font_var.trace_add("write", lambda *_: self._apply_general_fields())
        ttk.Label(opts, text="Размер угловых кнопок, dp").grid(row=4, column=0, sticky="w")
        ttk.Spinbox(
            opts,
            from_=24,
            to=120,
            textvariable=self.corner_size_var,
            width=6,
            command=self._apply_general_fields,
        ).grid(row=4, column=1, sticky="w", padx=8, pady=4)

        ttk.Label(
            frame,
            text="Панели и плитки редактируются на вкладках «Панели» и «JSON». "
            "Обои, иконки и фоны — на соответствующих вкладках.",
            style="Muted.TLabel",
            wraplength=720,
        ).grid(row=6, column=0, sticky="w", pady=(18, 0))

        for var in (
            self.page_count_var,
            self.current_page_var,
            self.corner_size_var,
        ):
            var.trace_add("write", lambda *_: self._apply_general_fields())
        self.wallpaper_crop_var.trace_add("write", lambda *_: self._apply_general_fields())

    def _build_colors_tab(self) -> None:
        frame = self.colors_tab
        self.color_vars: dict[str, tk.StringVar] = {}
        groups = [
            ("Фон холста", [("canvas.light", "Светлая"), ("canvas.dark", "Тёмная")]),
            (
                "Фон угловых кнопок",
                [("corner_bg.light", "Светлая"), ("corner_bg.dark", "Тёмная")],
            ),
            (
                "Иконки угловых кнопок",
                [("corner_icon.light", "Светлая"), ("corner_icon.dark", "Тёмная")],
            ),
        ]
        row = 0
        for title, items in groups:
            box = ttk.LabelFrame(frame, text=title, padding=8)
            box.grid(row=row, column=0, sticky="ew", pady=6)
            for i, (key, label) in enumerate(items):
                var = tk.StringVar(value="#FFFFFFFF")
                self.color_vars[key] = var
                ttk.Label(box, text=label, width=12).grid(row=i, column=0, sticky="w")
                entry = ttk.Entry(box, textvariable=var, width=14)
                entry.grid(row=i, column=1, padx=6, pady=2)
                ttk.Button(
                    box,
                    text="…",
                    width=3,
                    command=lambda k=key: self._pick_color(k),
                ).grid(row=i, column=2, padx=2)
                var.trace_add("write", lambda *_: self._apply_colors())
            row += 1

        presets = ttk.LabelFrame(frame, text="Пресеты цветов виджетов (8 слотов)", padding=8)
        presets.grid(row=row, column=0, sticky="ew", pady=6)
        self.preset_vars: list[tk.StringVar] = []
        for i in range(8):
            var = tk.StringVar(value="#FFFFFFFF")
            self.preset_vars.append(var)
            ttk.Label(presets, text=f"#{i + 1}").grid(row=i // 4, column=(i % 4) * 3, sticky="w")
            ttk.Entry(presets, textvariable=var, width=12).grid(
                row=i // 4, column=(i % 4) * 3 + 1, padx=4, pady=2
            )
            ttk.Button(
                presets,
                text="…",
                width=3,
                command=lambda idx=i: self._pick_preset(idx),
            ).grid(row=i // 4, column=(i % 4) * 3 + 2, padx=2)
            var.trace_add("write", lambda *_: self._apply_colors())
        frame.columnconfigure(0, weight=1)

    def _build_wallpapers_tab(self) -> None:
        frame = self.wallpapers_tab
        frame.columnconfigure(0, weight=1)
        frame.columnconfigure(1, weight=1)
        frame.rowconfigure(1, weight=1)

        self.wallpaper_side = tk.StringVar(value="light")
        side_bar = ttk.Frame(frame)
        side_bar.grid(row=0, column=0, columnspan=2, sticky="ew", pady=(0, 6))
        ttk.Label(side_bar, text="Тема ГУ:").pack(side="left")
        ttk.Radiobutton(
            side_bar,
            text="Светлая (light)",
            value="light",
            variable=self.wallpaper_side,
            command=self._refresh_wallpaper_list,
        ).pack(side="left", padx=6)
        ttk.Radiobutton(
            side_bar,
            text="Тёмная (dark)",
            value="dark",
            variable=self.wallpaper_side,
            command=self._refresh_wallpaper_list,
        ).pack(side="left", padx=6)

        left = ttk.Frame(frame)
        left.grid(row=1, column=0, sticky="nsew", padx=(0, 8))
        left.rowconfigure(0, weight=1)
        left.columnconfigure(0, weight=1)
        self.wallpaper_list = tk.Listbox(left, exportselection=False)
        self.wallpaper_list.grid(row=0, column=0, sticky="nsew")
        scroll = ttk.Scrollbar(left, orient="vertical", command=self.wallpaper_list.yview)
        scroll.grid(row=0, column=1, sticky="ns")
        self.wallpaper_list.configure(yscrollcommand=scroll.set)
        self.wallpaper_list.bind("<<ListboxSelect>>", lambda _e: self._on_wallpaper_selected())

        btns = ttk.Frame(left)
        btns.grid(row=1, column=0, columnspan=2, sticky="ew", pady=6)
        ttk.Button(btns, text="Добавить…", command=self._add_wallpapers).pack(side="left", padx=2)
        ttk.Button(btns, text="Удалить", command=self._remove_wallpaper).pack(side="left", padx=2)
        ttk.Button(btns, text="Назначить на страницу…", command=self._assign_wallpaper_page).pack(
            side="left", padx=2
        )

        right = ttk.LabelFrame(frame, text="Превью / назначения", padding=8)
        right.grid(row=1, column=1, sticky="nsew")
        right.rowconfigure(1, weight=1)
        right.columnconfigure(0, weight=1)
        self.wallpaper_preview = ttk.Label(right, text="Нет изображения", anchor="center")
        self.wallpaper_preview.grid(row=0, column=0, sticky="nsew", pady=(0, 8))
        self.assignment_text = tk.Text(right, height=10, wrap="word")
        self.assignment_text.grid(row=1, column=0, sticky="nsew")
        self.assignment_text.configure(state="disabled")

    def _build_asset_tab(
        self,
        parent: ttk.Frame,
        *,
        title: str,
        hint: str,
        list_attr: str,
        filetypes: list[tuple[str, str]],
        rename_to_png: bool,
        ask_rel_path: bool = False,
    ) -> None:
        parent.columnconfigure(0, weight=1)
        parent.rowconfigure(1, weight=1)
        ttk.Label(parent, text=title, style="Header.TLabel").grid(row=0, column=0, sticky="w")
        ttk.Label(parent, text=hint, style="Muted.TLabel").grid(row=0, column=0, sticky="e")

        listbox = tk.Listbox(parent, exportselection=False)
        listbox.grid(row=1, column=0, sticky="nsew", pady=6)
        setattr(self, f"{list_attr}_listbox", listbox)

        btns = ttk.Frame(parent)
        btns.grid(row=2, column=0, sticky="ew")

        def add_files() -> None:
            paths = filedialog.askopenfilenames(title="Выберите файлы", filetypes=filetypes)
            if not paths:
                return
            store: dict = getattr(self.bundle, list_attr)
            for path in paths:
                p = Path(path)
                data = p.read_bytes()
                if len(data) > MAX_ASSET_BYTES:
                    messagebox.showwarning(
                        APP_TITLE,
                        f"Файл {p.name} больше 10 МБ и будет пропущен.",
                    )
                    continue
                name = p.name
                if ask_rel_path:
                    name = simple_prompt(
                        self,
                        "Относительный путь",
                        "Путь внутри tile_backgrounds:",
                        initial=name,
                    )
                    if not name:
                        continue
                    name = name.replace("\\", "/").lstrip("/")
                elif rename_to_png and not name.lower().endswith(".png"):
                    name = f"{p.stem}.png"
                store[name] = data
            self._set_dirty(True)
            self._refresh_asset_lists()
            self._sync_json_editor()

        def remove_selected() -> None:
            sel = listbox.curselection()
            if not sel:
                return
            name = listbox.get(sel[0])
            store: dict = getattr(self.bundle, list_attr)
            store.pop(name, None)
            self._set_dirty(True)
            self._refresh_asset_lists()
            self._sync_json_editor()

        ttk.Button(btns, text="Добавить…", command=add_files).pack(side="left", padx=2)
        ttk.Button(btns, text="Удалить", command=remove_selected).pack(side="left", padx=2)

    def _build_panels_tab(self) -> None:
        frame = self.panels_tab
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(1, weight=1)
        frame.rowconfigure(3, weight=1)
        ttk.Label(frame, text="Панели главного экрана", style="Header.TLabel").grid(
            row=0, column=0, sticky="w"
        )
        self.main_panels_tree = ttk.Treeview(
            frame,
            columns=("id", "name", "page", "grid", "enabled"),
            show="headings",
            height=8,
        )
        for col, title, width in (
            ("id", "ID", 140),
            ("name", "Имя", 160),
            ("page", "Стр.", 50),
            ("grid", "Сетка", 70),
            ("enabled", "Вкл.", 50),
        ):
            self.main_panels_tree.heading(col, text=title)
            self.main_panels_tree.column(col, width=width, anchor="w")
        self.main_panels_tree.grid(row=1, column=0, sticky="nsew", pady=4)

        ttk.Label(frame, text="Плавающие панели", style="Header.TLabel").grid(
            row=2, column=0, sticky="w", pady=(12, 0)
        )
        self.floating_panels_tree = ttk.Treeview(
            frame,
            columns=("id", "name", "grid", "size", "enabled"),
            show="headings",
            height=8,
        )
        for col, title, width in (
            ("id", "ID", 140),
            ("name", "Имя", 160),
            ("grid", "Сетка", 70),
            ("size", "Размер", 100),
            ("enabled", "Вкл.", 50),
        ):
            self.floating_panels_tree.heading(col, text=title)
            self.floating_panels_tree.column(col, width=width, anchor="w")
        self.floating_panels_tree.grid(row=3, column=0, sticky="nsew", pady=4)
        ttk.Label(
            frame,
            text="Для изменения состава/плиток используйте вкладку JSON "
            "(структура совместима с экспортом из приложения).",
            style="Muted.TLabel",
        ).grid(row=4, column=0, sticky="w", pady=8)

    def _build_json_tab(self) -> None:
        frame = self.json_tab
        frame.columnconfigure(0, weight=1)
        frame.rowconfigure(1, weight=1)
        bar = ttk.Frame(frame)
        bar.grid(row=0, column=0, sticky="ew", pady=(0, 4))
        ttk.Button(bar, text="Применить JSON к теме", command=self._apply_json_editor).pack(
            side="left", padx=2
        )
        ttk.Button(bar, text="Перечитать из темы", command=self._sync_json_editor).pack(
            side="left", padx=2
        )
        self.json_text = tk.Text(frame, wrap="none", font=("Consolas", 10))
        self.json_text.grid(row=1, column=0, sticky="nsew")
        yscroll = ttk.Scrollbar(frame, orient="vertical", command=self.json_text.yview)
        yscroll.grid(row=1, column=1, sticky="ns")
        xscroll = ttk.Scrollbar(frame, orient="horizontal", command=self.json_text.xview)
        xscroll.grid(row=2, column=0, sticky="ew")
        self.json_text.configure(yscrollcommand=yscroll.set, xscrollcommand=xscroll.set)

    # --- state helpers ---

    def _set_dirty(self, value: bool) -> None:
        self.dirty = value
        mark = " *" if value else ""
        path = self.bundle.source_path or "(новая тема)"
        self.path_var.set(f"{path}{mark}")
        title_path = Path(path).name if self.bundle.source_path else "новая тема"
        self.title(f"{APP_TITLE} — {title_path}{mark}")

    def _set_status(self, text: str) -> None:
        self.status_var.set(text)

    def _confirm_discard(self) -> bool:
        if not self.dirty:
            return True
        return messagebox.askyesno(
            APP_TITLE,
            "Есть несохранённые изменения. Продолжить без сохранения?",
        )

    def _reload_from_bundle(self) -> None:
        self._suppress_trace = True
        try:
            sections = set(self.bundle.theme.get("sections", []))
            for key, var in self.section_vars.items():
                var.set(key in sections)
            main = get_main_screen(self.bundle.theme)
            visual = get_visual_theme(self.bundle.theme)
            self.page_count_var.set(int(main.get("pageCount", 1) or 1))
            self.current_page_var.set(int(main.get("currentPage", 1) or 1))
            self.wallpaper_crop_var.set(bool(visual.get("wallpaperCrop", True)))
            typography = visual.get("typography") if isinstance(visual.get("typography"), dict) else {}
            font = typography.get("fontFamily", "default")
            self.font_var.set(font if font in FONT_SLUGS else "default")
            corner = visual.get("cornerButtons") if isinstance(visual.get("cornerButtons"), dict) else {}
            self.corner_size_var.set(int(corner.get("sizeDp", 50) or 50))

            canvas = visual.get("canvasBackground") if isinstance(visual.get("canvasBackground"), dict) else {}
            corner_bg = corner.get("background") if isinstance(corner.get("background"), dict) else {}
            corner_icon = corner.get("icon") if isinstance(corner.get("icon"), dict) else {}
            self.color_vars["canvas.light"].set(normalize_hex_color(str(canvas.get("light", "#FFF8F9FA"))))
            self.color_vars["canvas.dark"].set(normalize_hex_color(str(canvas.get("dark", "#FF292F3B"))))
            self.color_vars["corner_bg.light"].set(
                normalize_hex_color(str(corner_bg.get("light", "#00000000")))
            )
            self.color_vars["corner_bg.dark"].set(
                normalize_hex_color(str(corner_bg.get("dark", "#00000000")))
            )
            self.color_vars["corner_icon.light"].set(
                normalize_hex_color(str(corner_icon.get("light", "#FF1A1C1E")))
            )
            self.color_vars["corner_icon.dark"].set(
                normalize_hex_color(str(corner_icon.get("dark", "#FFE2E2E6")))
            )
            presets = visual.get("colorPresets")
            if not isinstance(presets, list):
                presets = []
            for i, var in enumerate(self.preset_vars):
                value = presets[i] if i < len(presets) else "#FFFFFFFF"
                var.set(normalize_hex_color(str(value)))
        finally:
            self._suppress_trace = False

        self._refresh_wallpaper_list()
        self._refresh_asset_lists()
        self._refresh_panels()
        self._sync_json_editor()
        self._update_assignment_text()
        summary = self.bundle.summary()
        self._set_status(
            "Разделы: {sections}; панели: {mainPanels}/{floatingPanels}; "
            "обои L/D: {lightWallpapers}/{darkWallpapers}; иконки: {icons}".format(**summary)
        )

    def _on_sections_changed(self) -> None:
        if self._suppress_trace:
            return
        selected = [key for key, var in self.section_vars.items() if var.get()]
        if not selected:
            messagebox.showwarning(APP_TITLE, "Нужен хотя бы один раздел.")
            self.section_vars[SECTION_MAIN_SCREEN].set(True)
            selected = [SECTION_MAIN_SCREEN]
        self.bundle.theme["sections"] = selected
        if SECTION_MAIN_SCREEN in selected:
            get_main_screen(self.bundle.theme)
        else:
            self.bundle.theme.pop(SECTION_MAIN_SCREEN, None)
        if SECTION_FLOATING_PANELS in selected:
            self.bundle.theme.setdefault(SECTION_FLOATING_PANELS, {"panels": []})
        else:
            self.bundle.theme.pop(SECTION_FLOATING_PANELS, None)
        if SECTION_APP_ICONS in selected:
            self.bundle.theme.setdefault(
                SECTION_APP_ICONS, {"packages": [], "httpRequestIconKeys": []}
            )
        else:
            self.bundle.theme.pop(SECTION_APP_ICONS, None)
        self._set_dirty(True)
        self._sync_json_editor()

    def _apply_general_fields(self) -> None:
        if self._suppress_trace:
            return
        if SECTION_MAIN_SCREEN not in self.bundle.theme.get("sections", []):
            return
        main = get_main_screen(self.bundle.theme)
        visual = get_visual_theme(self.bundle.theme)
        try:
            page_count = max(1, int(self.page_count_var.get()))
            current_page = max(1, min(page_count, int(self.current_page_var.get())))
            corner_size = max(1, int(self.corner_size_var.get()))
        except (tk.TclError, ValueError, TypeError):
            return
        main["pageCount"] = page_count
        main["currentPage"] = current_page
        visual["wallpaperCrop"] = bool(self.wallpaper_crop_var.get())
        visual.setdefault("typography", {})["fontFamily"] = self.font_var.get()
        visual.setdefault("cornerButtons", {})["sizeDp"] = corner_size
        self._set_dirty(True)
        self._update_assignment_text()
        self._sync_json_editor()

    def _apply_colors(self) -> None:
        if self._suppress_trace:
            return
        if SECTION_MAIN_SCREEN not in self.bundle.theme.get("sections", []):
            return
        visual = get_visual_theme(self.bundle.theme)
        visual["canvasBackground"] = {
            "light": normalize_hex_color(self.color_vars["canvas.light"].get()),
            "dark": normalize_hex_color(self.color_vars["canvas.dark"].get()),
        }
        corner = visual.setdefault("cornerButtons", {})
        corner["background"] = {
            "light": normalize_hex_color(self.color_vars["corner_bg.light"].get()),
            "dark": normalize_hex_color(self.color_vars["corner_bg.dark"].get()),
        }
        corner["icon"] = {
            "light": normalize_hex_color(self.color_vars["corner_icon.light"].get()),
            "dark": normalize_hex_color(self.color_vars["corner_icon.dark"].get()),
        }
        visual["colorPresets"] = [
            normalize_hex_color(var.get()) for var in self.preset_vars
        ]
        self._set_dirty(True)
        self._sync_json_editor()

    def _pick_color(self, key: str) -> None:
        current = normalize_hex_color(self.color_vars[key].get())
        rgb = colorchooser.askcolor(color=f"#{current[3:]}", title="Цвет")
        if not rgb or not rgb[1]:
            return
        self.color_vars[key].set(normalize_hex_color(rgb[1]))

    def _pick_preset(self, index: int) -> None:
        current = normalize_hex_color(self.preset_vars[index].get())
        rgb = colorchooser.askcolor(color=f"#{current[3:]}", title=f"Пресет #{index + 1}")
        if not rgb or not rgb[1]:
            return
        self.preset_vars[index].set(normalize_hex_color(rgb[1]))

    def _wallpaper_store(self) -> dict[str, bytes]:
        return (
            self.bundle.light_wallpapers
            if self.wallpaper_side.get() == "light"
            else self.bundle.dark_wallpapers
        )

    def _refresh_wallpaper_list(self) -> None:
        self.wallpaper_list.delete(0, "end")
        for name in sorted(self._wallpaper_store()):
            self.wallpaper_list.insert("end", name)
        self.wallpaper_preview.configure(image="", text="Нет изображения")
        self._preview_image = None
        self._update_assignment_text()

    def _on_wallpaper_selected(self) -> None:
        sel = self.wallpaper_list.curselection()
        if not sel:
            return
        name = self.wallpaper_list.get(sel[0])
        data = self._wallpaper_store().get(name)
        if not data:
            return
        if HAS_PIL:
            try:
                from io import BytesIO

                img = Image.open(BytesIO(data))
                img.thumbnail((420, 280))
                photo = ImageTk.PhotoImage(img)
                self._preview_image = photo
                self.wallpaper_preview.configure(image=photo, text="")
            except Exception as exc:  # noqa: BLE001
                self.wallpaper_preview.configure(image="", text=f"Не удалось показать: {exc}")
        else:
            self.wallpaper_preview.configure(
                image="",
                text=f"{name}\n({len(data)} байт)\nУстановите Pillow для превью",
            )

    def _add_wallpapers(self) -> None:
        paths = filedialog.askopenfilenames(
            title="Добавить обои",
            filetypes=[
                ("Изображения", " ".join(f"*.{ext}" for ext in sorted(IMAGE_EXTENSIONS))),
                ("Все файлы", "*.*"),
            ],
        )
        if not paths:
            return
        store = self._wallpaper_store()
        added = 0
        for path in paths:
            p = Path(path)
            data = p.read_bytes()
            if len(data) > MAX_ASSET_BYTES:
                messagebox.showwarning(APP_TITLE, f"{p.name}: больше 10 МБ, пропуск.")
                continue
            store[p.name] = data
            added += 1
        if added:
            if SECTION_MAIN_SCREEN not in self.bundle.theme.get("sections", []):
                self.section_vars[SECTION_MAIN_SCREEN].set(True)
                self._on_sections_changed()
            self._set_dirty(True)
            self._refresh_wallpaper_list()
            self._sync_json_editor()
            self._set_status(f"Добавлено обоев: {added}")

    def _remove_wallpaper(self) -> None:
        sel = self.wallpaper_list.curselection()
        if not sel:
            return
        name = self.wallpaper_list.get(sel[0])
        self._wallpaper_store().pop(name, None)
        main = get_main_screen(self.bundle.theme)
        selection = main.get("wallpaperSelectionByPage")
        if isinstance(selection, dict):
            side = self.wallpaper_side.get()
            side_map = selection.get(side)
            if isinstance(side_map, dict):
                for page in list(side_map):
                    if side_map.get(page) == name:
                        del side_map[page]
        self._set_dirty(True)
        self._refresh_wallpaper_list()
        self._sync_json_editor()

    def _assign_wallpaper_page(self) -> None:
        sel = self.wallpaper_list.curselection()
        if not sel:
            messagebox.showinfo(APP_TITLE, "Сначала выберите файл обоев.")
            return
        name = self.wallpaper_list.get(sel[0])
        page_raw = simple_prompt(self, "Страница", "Номер страницы (1…):", initial="1")
        if page_raw is None:
            return
        try:
            page = int(page_raw)
            if page < 1:
                raise ValueError
        except ValueError:
            messagebox.showerror(APP_TITLE, "Нужен целый номер страницы >= 1.")
            return
        main = get_main_screen(self.bundle.theme)
        selection = main.setdefault("wallpaperSelectionByPage", {})
        if not isinstance(selection, dict):
            selection = {}
            main["wallpaperSelectionByPage"] = selection
        side = self.wallpaper_side.get()
        side_map = selection.setdefault(side, {})
        if not isinstance(side_map, dict):
            side_map = {}
            selection[side] = side_map
        side_map[str(page)] = name
        page_count = int(main.get("pageCount", 1) or 1)
        if page > page_count:
            main["pageCount"] = page
            self.page_count_var.set(page)
        self._set_dirty(True)
        self._update_assignment_text()
        self._sync_json_editor()

    def _update_assignment_text(self) -> None:
        main = self.bundle.theme.get(SECTION_MAIN_SCREEN)
        lines = ["Назначения обоев по страницам:", ""]
        if isinstance(main, dict):
            selection = main.get("wallpaperSelectionByPage")
            if isinstance(selection, dict):
                for side in ("light", "dark"):
                    side_map = selection.get(side)
                    lines.append(f"[{side}]")
                    if isinstance(side_map, dict) and side_map:
                        for page in sorted(side_map, key=lambda x: int(x) if str(x).isdigit() else 0):
                            lines.append(f"  страница {page}: {side_map[page]}")
                    else:
                        lines.append("  (нет)")
                    lines.append("")
            else:
                lines.append("(нет назначений)")
        else:
            lines.append("Раздел mainScreen не включён.")
        self.assignment_text.configure(state="normal")
        self.assignment_text.delete("1.0", "end")
        self.assignment_text.insert("1.0", "\n".join(lines))
        self.assignment_text.configure(state="disabled")

    def _refresh_asset_lists(self) -> None:
        for attr in ("icons", "http_request_icons", "tile_backgrounds"):
            listbox: tk.Listbox = getattr(self, f"{attr}_listbox")
            listbox.delete(0, "end")
            store: dict = getattr(self.bundle, attr)
            for name in sorted(store):
                listbox.insert("end", name)

    def _refresh_panels(self) -> None:
        for tree in (self.main_panels_tree, self.floating_panels_tree):
            for item in tree.get_children():
                tree.delete(item)
        main = self.bundle.theme.get(SECTION_MAIN_SCREEN)
        if isinstance(main, dict) and isinstance(main.get("panels"), list):
            for panel in main["panels"]:
                if not isinstance(panel, dict):
                    continue
                grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
                self.main_panels_tree.insert(
                    "",
                    "end",
                    values=(
                        panel.get("id", ""),
                        panel.get("name", ""),
                        panel.get("pageNumber", 1),
                        f"{grid.get('rows', '?')}×{grid.get('cols', '?')}",
                        "да" if panel.get("enabled", True) else "нет",
                    ),
                )
        floating = self.bundle.theme.get(SECTION_FLOATING_PANELS)
        if isinstance(floating, dict) and isinstance(floating.get("panels"), list):
            for panel in floating["panels"]:
                if not isinstance(panel, dict):
                    continue
                grid = panel.get("grid") if isinstance(panel.get("grid"), dict) else {}
                self.floating_panels_tree.insert(
                    "",
                    "end",
                    values=(
                        panel.get("id", ""),
                        panel.get("name", ""),
                        f"{grid.get('rows', '?')}×{grid.get('cols', '?')}",
                        f"{panel.get('width', '?')}×{panel.get('height', '?')}",
                        "да" if panel.get("enabled", True) else "нет",
                    ),
                )

    def _sync_json_editor(self) -> None:
        text = self.bundle.theme_json_text()
        self.json_text.delete("1.0", "end")
        self.json_text.insert("1.0", text)

    def _apply_json_editor(self) -> None:
        raw = self.json_text.get("1.0", "end-1c")
        try:
            self.bundle.set_theme_from_json_text(raw)
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror(APP_TITLE, f"Некорректный JSON:\n{exc}")
            return
        self._set_dirty(True)
        self._reload_from_bundle()
        self._set_status("JSON применён")

    # --- file actions ---

    def new_theme(self) -> None:
        if not self._confirm_discard():
            return
        dialog = NewThemeDialog(self)
        self.wait_window(dialog)
        if not dialog.result:
            return
        self.bundle = ThemeBundle.new_empty(dialog.result)
        self._reload_from_bundle()
        self._set_dirty(True)
        self._set_status("Создана новая тема")

    def open_theme(self) -> None:
        if not self._confirm_discard():
            return
        path = filedialog.askopenfilename(
            title="Открыть тему",
            filetypes=[
                ("TBox theme", f"*.{THEME_FILE_EXTENSION}"),
                ("ZIP / theme", "*.tboxtheme *.zip"),
                ("Все файлы", "*.*"),
            ],
        )
        if not path:
            return
        try:
            self.bundle = ThemeBundle.load_path(path)
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror(APP_TITLE, f"Не удалось открыть файл:\n{exc}")
            return
        self._reload_from_bundle()
        self._set_dirty(False)
        self._set_status(f"Открыто: {path}")

    def save_theme(self) -> None:
        if self.bundle.source_path:
            self._save_to(self.bundle.source_path)
        else:
            self.save_theme_as()

    def save_theme_as(self) -> None:
        initial = "theme"
        if self.bundle.source_path:
            initial = Path(self.bundle.source_path).stem
        path = filedialog.asksaveasfilename(
            title="Сохранить тему",
            defaultextension=f".{THEME_FILE_EXTENSION}",
            initialfile=theme_file_name_from_base_name(
                sanitize_theme_export_base_name(initial) or "theme"
            ),
            filetypes=[("TBox theme", f"*.{THEME_FILE_EXTENSION}")],
        )
        if not path:
            return
        self._save_to(path)

    def _save_to(self, path: str) -> None:
        self._apply_general_fields()
        self._apply_colors()
        try:
            # Prefer JSON editor content if user edited it without applying.
            raw = self.json_text.get("1.0", "end-1c").strip()
            if raw:
                try:
                    self.bundle.set_theme_from_json_text(raw)
                except Exception:
                    pass
            saved = self.bundle.save_path(path)
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror(APP_TITLE, f"Ошибка сохранения:\n{exc}")
            return
        self._reload_from_bundle()
        self._set_dirty(False)
        self._set_status(f"Сохранено: {saved}")

    def validate_theme(self) -> None:
        try:
            raw = self.json_text.get("1.0", "end-1c")
            if raw.strip():
                self.bundle.set_theme_from_json_text(raw)
            self.bundle.ensure_sections_consistency()
            errors = self.bundle.validate()
        except Exception as exc:  # noqa: BLE001
            messagebox.showerror(APP_TITLE, f"Ошибка проверки:\n{exc}")
            return
        if errors:
            messagebox.showwarning(
                APP_TITLE,
                "Найдены проблемы:\n\n- " + "\n- ".join(errors),
            )
        else:
            messagebox.showinfo(APP_TITLE, "Тема корректна и совместима с форматом приложения.")
        self._reload_from_bundle()

    def _show_about(self) -> None:
        messagebox.showinfo(
            APP_TITLE,
            f"{APP_TITLE} {APP_VERSION}\n\n"
            "Редактор файлов .tboxtheme для TBox Monitor (Jetour Dashing).\n"
            "Формат: ZIP + theme.json (formatVersion 1, type tbox_theme).\n\n"
            "Созданную тему скопируйте на ГУ и примените во вкладке «Темы».",
        )

    def _on_close(self) -> None:
        if not self._confirm_discard():
            return
        self.destroy()


class NewThemeDialog(tk.Toplevel):
    def __init__(self, master: tk.Tk) -> None:
        super().__init__(master)
        self.title("Новая тема")
        self.resizable(False, False)
        self.result: list[str] | None = None
        self.transient(master)
        self.grab_set()

        ttk.Label(self, text="Выберите разделы для новой темы:").pack(
            anchor="w", padx=12, pady=(12, 6)
        )
        self.vars = {
            SECTION_MAIN_SCREEN: tk.BooleanVar(value=True),
            SECTION_FLOATING_PANELS: tk.BooleanVar(value=False),
            SECTION_APP_ICONS: tk.BooleanVar(value=False),
        }
        labels = {
            SECTION_MAIN_SCREEN: "Главный экран",
            SECTION_FLOATING_PANELS: "Плавающие панели",
            SECTION_APP_ICONS: "Иконки приложений / HTTP",
        }
        for key in ALL_SECTIONS:
            ttk.Checkbutton(self, text=labels[key], variable=self.vars[key]).pack(
                anchor="w", padx=20, pady=2
            )
        btns = ttk.Frame(self)
        btns.pack(fill="x", padx=12, pady=12)
        ttk.Button(btns, text="Создать", command=self._ok).pack(side="right", padx=4)
        ttk.Button(btns, text="Отмена", command=self.destroy).pack(side="right")
        self.bind("<Return>", lambda _e: self._ok())
        self.bind("<Escape>", lambda _e: self.destroy())

    def _ok(self) -> None:
        selected = [key for key, var in self.vars.items() if var.get()]
        if not selected:
            messagebox.showwarning(APP_TITLE, "Выберите хотя бы один раздел.", parent=self)
            return
        self.result = selected
        self.destroy()


def simple_prompt(
    master: tk.Misc,
    title: str,
    prompt: str,
    *,
    initial: str = "",
) -> str | None:
    dialog = tk.Toplevel(master)
    dialog.title(title)
    dialog.transient(master)
    dialog.grab_set()
    dialog.resizable(False, False)
    ttk.Label(dialog, text=prompt).pack(anchor="w", padx=12, pady=(12, 4))
    var = tk.StringVar(value=initial)
    entry = ttk.Entry(dialog, textvariable=var, width=42)
    entry.pack(padx=12, pady=4)
    entry.focus_set()
    entry.selection_range(0, "end")
    result: dict[str, str | None] = {"value": None}

    def ok() -> None:
        result["value"] = var.get().strip()
        dialog.destroy()

    def cancel() -> None:
        dialog.destroy()

    btns = ttk.Frame(dialog)
    btns.pack(fill="x", padx=12, pady=12)
    ttk.Button(btns, text="OK", command=ok).pack(side="right", padx=4)
    ttk.Button(btns, text="Отмена", command=cancel).pack(side="right")
    dialog.bind("<Return>", lambda _e: ok())
    dialog.bind("<Escape>", lambda _e: cancel())
    master.wait_window(dialog)
    return result["value"]


def main(argv: list[str] | None = None) -> int:
    args = list(sys.argv[1:] if argv is None else argv)
    app = ThemeEditorApp()
    if args:
        path = Path(args[0])
        if path.is_file():
            try:
                app.bundle = ThemeBundle.load_path(path)
                app._reload_from_bundle()
                app._set_dirty(False)
                app._set_status(f"Открыто: {path}")
            except Exception as exc:  # noqa: BLE001
                messagebox.showerror(APP_TITLE, f"Не удалось открыть {path}:\n{exc}")
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
