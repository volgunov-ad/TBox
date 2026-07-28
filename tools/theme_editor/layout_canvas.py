"""Tkinter dual-coordinate layout canvas for TBox themes (HU quasi-emulator)."""

from __future__ import annotations

import tkinter as tk
from pathlib import Path
from tkinter import ttk
from typing import Any, Callable

from hu_profiles import DEFAULT_PROFILE_ID, PROFILES, DisplayProfile, get_profile
from layout_geometry import (
    Rect,
    apply_floating_physical_to_panel,
    apply_main_rel_to_panel,
    fit_scale,
    floating_panel_physical_rect,
    main_panel_physical_rect,
    physical_to_main_rel,
)
from theme_bundle import (
    DEFAULT_CANVAS_DARK,
    DEFAULT_CANVAS_LIGHT,
    SECTION_FLOATING_PANELS,
    SECTION_MAIN_SCREEN,
    get_visual_theme,
)
from tile_preview import draw_panel_tiles_on_canvas

try:
    from PIL import Image, ImageTk

    HAS_PIL = True
except ImportError:  # pragma: no cover
    HAS_PIL = False


def _tk_rgb_from_hex(value: str, *, default: str = "#F8F9FA") -> str:
    raw = (value or "").strip()
    if raw.startswith("#"):
        raw = raw[1:]
    if len(raw) == 8:
        raw = raw[2:]
    if len(raw) != 6:
        d = default[1:] if default.startswith("#") else default
        return f"#{d[-6:]}"
    return f"#{raw.upper()}"

HANDLE_SIZE = 10
MAIN_FILL = "#2F80ED"
MAIN_OUTLINE = "#4DA3FF"
FLOAT_FILL = "#E67E22"
FLOAT_OUTLINE = "#FFB060"
VD_OUTLINE = "#00BCD4"
SELECTED_WIDTH = 2


class HuLayoutCanvas(ttk.Frame):
    """Visual editor: physical panel underlay + app VD inset + panels."""

    def __init__(
        self,
        master: tk.Misc,
        *,
        on_changed: Callable[[], None] | None = None,
        on_add_main: Callable[[], None] | None = None,
        on_add_floating: Callable[[], None] | None = None,
        on_delete_selected: Callable[[], None] | None = None,
        on_edit_panel: Callable[[], None] | None = None,
    ) -> None:
        super().__init__(master)
        self.on_changed = on_changed
        self.on_add_main = on_add_main
        self.on_add_floating = on_add_floating
        self.on_delete_selected = on_delete_selected
        self.on_edit_panel = on_edit_panel
        self.theme: dict[str, Any] = {}
        self.light_wallpapers: dict[str, bytes] = {}
        self.dark_wallpapers: dict[str, bytes] = {}
        self.profile: DisplayProfile = get_profile(DEFAULT_PROFILE_ID)
        self.underlay_path: Path | None = self.profile.underlay_path
        self.page_var = tk.IntVar(value=1)
        self.show_main_var = tk.BooleanVar(value=True)
        self.show_floating_var = tk.BooleanVar(value=True)
        self.show_vd_var = tk.BooleanVar(value=True)
        self.show_tile_preview_var = tk.BooleanVar(value=True)
        self.theme_mode_var = tk.StringVar(value="light")  # light | dark
        self.underlay_opacity_var = tk.DoubleVar(value=0.85)

        self._photo: Any = None
        self._wallpaper_photo: Any = None
        self._underlay_cache_key: tuple[Any, ...] | None = None
        self._wallpaper_cache_key: tuple[Any, ...] | None = None
        self._scale = 1.0
        self._ox = 0.0
        self._oy = 0.0
        self._selected: tuple[str, str] | None = None  # ("main"|"float", id)
        self._drag_mode: str | None = None  # move | resize
        self._drag_origin_canvas: tuple[float, float] | None = None
        self._drag_last_canvas: tuple[float, float] | None = None
        self._drag_start_rect: Rect | None = None
        self._redraw_after_id: str | None = None
        self._skip_tiles_while_dragging = False

        self._build_toolbar()
        self.canvas = tk.Canvas(self, background="#1e1e1e", highlightthickness=0)
        self.canvas.pack(fill="both", expand=True, padx=4, pady=4)
        self.hint = ttk.Label(
            self,
            text=(
                "Тонкая рамка: синяя — ГЭ (внутри VD), оранжевая — плавающая. "
                "Тема Светлая/Тёмная меняет плитки, цвет холста и обои страницы. "
                "Тяните за тело — перемещение, за угол — размер. Двойной клик — плитки."
            ),
            style="Muted.TLabel",
            wraplength=900,
        )
        self.hint.pack(fill="x", padx=6, pady=(0, 4))

        self.canvas.bind("<Configure>", self._on_canvas_configure)
        self.canvas.bind("<ButtonPress-1>", self._on_press)
        self.canvas.bind("<B1-Motion>", self._on_drag)
        self.canvas.bind("<ButtonRelease-1>", self._on_release)
        self.canvas.bind("<Double-Button-1>", self._on_double_click)
        self.canvas.bind("<Delete>", lambda _e: self._request_delete())
        self.canvas.bind("<KeyPress-Delete>", lambda _e: self._request_delete())

    def _on_canvas_configure(self, _event: tk.Event) -> None:  # type: ignore[type-arg]
        # Debounce Configure storms (window resize) — avoids LANCZOS on every pixel.
        self._schedule_redraw(delay_ms=40)

    def _request_delete(self) -> None:
        if self.on_delete_selected is not None:
            self.on_delete_selected()

    def _build_toolbar(self) -> None:
        bar = ttk.Frame(self)
        bar.pack(fill="x", padx=4, pady=4)

        ttk.Label(bar, text="Профиль ГУ:").pack(side="left")
        self.profile_var = tk.StringVar(value=self.profile.id)
        combo = ttk.Combobox(
            bar,
            textvariable=self.profile_var,
            values=[p.id for p in PROFILES.values()],
            state="readonly",
            width=22,
        )
        combo.pack(side="left", padx=4)
        combo.bind("<<ComboboxSelected>>", self._on_profile_changed)

        ttk.Label(bar, text="Стр.:").pack(side="left", padx=(12, 0))
        ttk.Spinbox(
            bar,
            from_=1,
            to=20,
            width=4,
            textvariable=self.page_var,
            command=self.redraw,
        ).pack(side="left", padx=4)
        self.page_var.trace_add("write", lambda *_: self.redraw())

        ttk.Checkbutton(
            bar, text="ГЭ", variable=self.show_main_var, command=self.redraw
        ).pack(side="left", padx=4)
        ttk.Checkbutton(
            bar, text="Плавающие", variable=self.show_floating_var, command=self.redraw
        ).pack(side="left", padx=4)
        ttk.Checkbutton(
            bar, text="Рамка VD", variable=self.show_vd_var, command=self.redraw
        ).pack(side="left", padx=4)
        ttk.Checkbutton(
            bar, text="Превью плиток", variable=self.show_tile_preview_var, command=self.redraw
        ).pack(side="left", padx=4)
        ttk.Label(bar, text="Тема:").pack(side="left", padx=(8, 2))
        ttk.Radiobutton(
            bar,
            text="Светлая",
            value="light",
            variable=self.theme_mode_var,
            command=self.redraw,
        ).pack(side="left")
        ttk.Radiobutton(
            bar,
            text="Тёмная",
            value="dark",
            variable=self.theme_mode_var,
            command=self.redraw,
        ).pack(side="left", padx=(0, 4))

        ttk.Button(bar, text="+ ГЭ", command=self._request_add_main).pack(
            side="left", padx=(12, 2)
        )
        ttk.Button(bar, text="+ Плавающая", command=self._request_add_floating).pack(
            side="left", padx=2
        )
        ttk.Button(bar, text="Плитки…", command=self._request_edit).pack(side="left", padx=2)
        ttk.Button(bar, text="Удалить", command=self._request_delete).pack(
            side="left", padx=2
        )

        ttk.Button(bar, text="Подложка…", command=self._ask_underlay).pack(
            side="right", padx=2
        )
        ttk.Button(bar, text="Сброс подложки", command=self._reset_underlay).pack(
            side="right", padx=2
        )

    def _request_add_main(self) -> None:
        if self.on_add_main is not None:
            self.on_add_main()

    def _request_add_floating(self) -> None:
        if self.on_add_floating is not None:
            self.on_add_floating()

    def _request_edit(self) -> None:
        if self.on_edit_panel is not None:
            self.on_edit_panel()

    def _on_double_click(self, event: tk.Event) -> None:  # type: ignore[type-arg]
        hit = self._find_panel_at(event.x, event.y)
        if hit is None:
            return
        kind, panel_id, _mode = hit
        self._selected = (kind, panel_id)
        self.redraw()
        self._request_edit()

    def set_theme(self, theme: dict[str, Any]) -> None:
        self.theme = theme
        main = theme.get(SECTION_MAIN_SCREEN)
        if isinstance(main, dict):
            try:
                page = int(main.get("currentPage", 1) or 1)
            except (TypeError, ValueError):
                page = 1
            self.page_var.set(max(1, page))
        self._wallpaper_cache_key = None
        self.redraw()

    def set_wallpaper_stores(
        self,
        light_wallpapers: dict[str, bytes] | None = None,
        dark_wallpapers: dict[str, bytes] | None = None,
        *,
        redraw: bool = True,
    ) -> None:
        if light_wallpapers is not None:
            self.light_wallpapers = light_wallpapers
        if dark_wallpapers is not None:
            self.dark_wallpapers = dark_wallpapers
        self._wallpaper_cache_key = None
        if redraw:
            self.redraw()

    def is_dark_theme(self) -> bool:
        return self.theme_mode_var.get() == "dark"

    def _on_profile_changed(self, _event: object | None = None) -> None:
        self.profile = get_profile(self.profile_var.get())
        if self.underlay_path is None or self._is_bundled_underlay(self.underlay_path):
            self.underlay_path = self.profile.underlay_path
        self.redraw()

    def _is_bundled_underlay(self, path: Path) -> bool:
        try:
            return path.resolve() == self.profile.underlay_path.resolve()
        except OSError:
            return False

    def _ask_underlay(self) -> None:
        from tkinter import filedialog

        path = filedialog.askopenfilename(
            title="Подложка экрана ГУ",
            filetypes=[
                ("Изображения", "*.png *.jpg *.jpeg *.webp *.bmp"),
                ("Все файлы", "*.*"),
            ],
        )
        if not path:
            return
        self.underlay_path = Path(path)
        self._underlay_cache_key = None
        self.redraw()

    def _reset_underlay(self) -> None:
        self.underlay_path = self.profile.underlay_path
        self._underlay_cache_key = None
        self.redraw()

    # --- coordinate transforms ---

    def _phys_to_canvas(self, x: float, y: float) -> tuple[float, float]:
        return self._ox + x * self._scale, self._oy + y * self._scale

    def _canvas_to_phys(self, cx: float, cy: float) -> tuple[float, float]:
        if self._scale <= 0:
            return 0.0, 0.0
        return (cx - self._ox) / self._scale, (cy - self._oy) / self._scale

    def _rect_to_canvas(self, rect: Rect) -> tuple[float, float, float, float]:
        x1, y1 = self._phys_to_canvas(rect.x, rect.y)
        x2, y2 = self._phys_to_canvas(rect.right, rect.bottom)
        return x1, y1, x2, y2

    # --- drawing ---

    def _schedule_redraw(self, *, delay_ms: int = 0) -> None:
        if self._redraw_after_id is not None:
            try:
                self.after_cancel(self._redraw_after_id)
            except tk.TclError:
                pass
            self._redraw_after_id = None

        def _run() -> None:
            self._redraw_after_id = None
            self.redraw()

        if delay_ms <= 0:
            self._redraw_after_id = self.after_idle(_run)
        else:
            self._redraw_after_id = self.after(delay_ms, _run)

    def redraw(self) -> None:
        c = self.canvas
        c.delete("all")
        vw = max(c.winfo_width(), 100)
        vh = max(c.winfo_height(), 100)
        pw, ph = self.profile.physical_width, self.profile.physical_height
        self._scale = fit_scale(pw, ph, vw, vh, padding=12)
        drawn_w = pw * self._scale
        drawn_h = ph * self._scale
        self._ox = (vw - drawn_w) / 2
        self._oy = (vh - drawn_h) / 2

        # Physical frame
        x1, y1 = self._phys_to_canvas(0, 0)
        x2, y2 = self._phys_to_canvas(pw, ph)
        c.create_rectangle(x1, y1, x2, y2, fill="#111111", outline="#888888", width=1)

        self._draw_underlay(x1, y1, x2, y2)

        vd = Rect(
            self.profile.app_vd_x,
            self.profile.app_vd_y,
            self.profile.app_vd_width,
            self.profile.app_vd_height,
        )
        vx1, vy1, vx2, vy2 = self._rect_to_canvas(vd)
        self._draw_main_screen_background(vx1, vy1, vx2, vy2)

        if self.show_vd_var.get():
            c.create_rectangle(
                vx1,
                vy1,
                vx2,
                vy2,
                outline=VD_OUTLINE,
                width=1,
                dash=(6, 4),
            )

        if self.show_main_var.get():
            self._draw_main_panels()
        if self.show_floating_var.get():
            self._draw_floating_panels()

    def _draw_underlay(self, x1: float, y1: float, x2: float, y2: float) -> None:
        path = self.underlay_path
        if path is None or not path.is_file():
            self.canvas.create_text(
                (x1 + x2) / 2,
                (y1 + y2) / 2,
                fill="#777777",
                font=("Segoe UI", 12),
                text="Нет подложки — загрузите скриншот ГУ (adb screencap)",
            )
            return
        if not HAS_PIL:
            self.canvas.create_text(
                (x1 + x2) / 2,
                (y1 + y2) / 2,
                fill="#777777",
                font=("Segoe UI", 11),
                text="Для подложки нужен Pillow: pip install Pillow",
            )
            return
        target_w = max(1, int(round(x2 - x1)))
        target_h = max(1, int(round(y2 - y1)))
        alpha = max(0.15, min(1.0, float(self.underlay_opacity_var.get())))
        try:
            cache_key = (str(path.resolve()), path.stat().st_mtime_ns, target_w, target_h, round(alpha, 3))
        except OSError:
            cache_key = (str(path), target_w, target_h, round(alpha, 3))
        if self._underlay_cache_key == cache_key and self._photo is not None:
            self.canvas.create_image(x1, y1, anchor="nw", image=self._photo)
            return
        try:
            img = Image.open(path).convert("RGBA")
            img = img.resize((target_w, target_h), Image.Resampling.BILINEAR)
            if alpha < 0.999:
                overlay = Image.new("RGBA", img.size, (0, 0, 0, int(255 * (1.0 - alpha))))
                img = Image.alpha_composite(img, overlay)
            self._photo = ImageTk.PhotoImage(img)
            self._underlay_cache_key = cache_key
            self.canvas.create_image(x1, y1, anchor="nw", image=self._photo)
        except Exception as exc:  # noqa: BLE001
            self._photo = None
            self._underlay_cache_key = None
            self.canvas.create_text(
                (x1 + x2) / 2,
                (y1 + y2) / 2,
                fill="#aa6666",
                font=("Segoe UI", 11),
                text=f"Не удалось загрузить подложку:\n{exc}",
            )

    def _canvas_fill_color(self) -> str:
        dark = self.is_dark_theme()
        default = DEFAULT_CANVAS_DARK if dark else DEFAULT_CANVAS_LIGHT
        try:
            visual = get_visual_theme(self.theme) if self.theme else {}
        except Exception:  # noqa: BLE001
            visual = {}
        canvas = (
            visual.get("canvasBackground")
            if isinstance(visual.get("canvasBackground"), dict)
            else {}
        )
        key = "dark" if dark else "light"
        return _tk_rgb_from_hex(str(canvas.get(key, default)), default=default)

    def _wallpaper_bytes_for_current_page(self) -> tuple[str | None, bytes | None]:
        side = "dark" if self.is_dark_theme() else "light"
        try:
            page = str(int(self.page_var.get()))
        except (tk.TclError, TypeError, ValueError):
            page = "1"
        main = self.theme.get(SECTION_MAIN_SCREEN) if isinstance(self.theme, dict) else None
        if not isinstance(main, dict):
            return None, None
        selection = main.get("wallpaperSelectionByPage")
        if not isinstance(selection, dict):
            return None, None
        side_map = selection.get(side)
        if not isinstance(side_map, dict):
            return None, None
        name = side_map.get(page) or side_map.get(int(page) if page.isdigit() else page)
        if not name:
            return None, None
        name_s = str(name)
        store = self.dark_wallpapers if side == "dark" else self.light_wallpapers
        data = store.get(name_s)
        return name_s, data

    def _draw_main_screen_background(
        self, x1: float, y1: float, x2: float, y2: float
    ) -> None:
        """Paint App VD: theme canvas color + page wallpaper for light/dark mode."""
        fill = self._canvas_fill_color()
        self.canvas.create_rectangle(x1, y1, x2, y2, fill=fill, outline="")
        name, data = self._wallpaper_bytes_for_current_page()
        if not data or not HAS_PIL:
            return
        target_w = max(1, int(round(x2 - x1)))
        target_h = max(1, int(round(y2 - y1)))
        crop = True
        try:
            visual = get_visual_theme(self.theme)
            crop = bool(visual.get("wallpaperCrop", True))
        except Exception:  # noqa: BLE001
            crop = True
        cache_key = (
            "wp",
            name,
            "dark" if self.is_dark_theme() else "light",
            target_w,
            target_h,
            crop,
            len(data),
        )
        if self._wallpaper_cache_key == cache_key and self._wallpaper_photo is not None:
            self.canvas.create_image(x1, y1, anchor="nw", image=self._wallpaper_photo)
            return
        try:
            import io

            img = Image.open(io.BytesIO(data)).convert("RGBA")
            if crop:
                # Cover: scale to fill VD, center-crop.
                scale = max(target_w / max(1, img.width), target_h / max(1, img.height))
                nw = max(1, int(round(img.width * scale)))
                nh = max(1, int(round(img.height * scale)))
                img = img.resize((nw, nh), Image.Resampling.BILINEAR)
                left = max(0, (nw - target_w) // 2)
                top = max(0, (nh - target_h) // 2)
                img = img.crop((left, top, left + target_w, top + target_h))
            else:
                img = img.resize((target_w, target_h), Image.Resampling.BILINEAR)
            self._wallpaper_photo = ImageTk.PhotoImage(img)
            self._wallpaper_cache_key = cache_key
            self.canvas.create_image(x1, y1, anchor="nw", image=self._wallpaper_photo)
        except Exception:  # noqa: BLE001
            self._wallpaper_photo = None
            self._wallpaper_cache_key = None

    def _draw_main_panels(self) -> None:
        main = self.theme.get(SECTION_MAIN_SCREEN)
        if not isinstance(main, dict):
            return
        panels = main.get("panels")
        if not isinstance(panels, list):
            return
        try:
            page = int(self.page_var.get())
        except (tk.TclError, TypeError, ValueError):
            page = 1
        for panel in panels:
            if not isinstance(panel, dict):
                continue
            try:
                panel_page = int(panel.get("pageNumber", 1) or 1)
            except (TypeError, ValueError):
                panel_page = 1
            if panel_page != page:
                continue
            if panel.get("enabled", True) is False:
                continue
            panel_id = str(panel.get("id", ""))
            if not panel_id:
                continue
            rect = main_panel_physical_rect(panel, self.profile)
            self._draw_panel_rect(
                kind="main",
                panel_id=panel_id,
                rect=rect,
                outline=MAIN_OUTLINE,
                panel=panel,
            )

    def _draw_floating_panels(self) -> None:
        floating = self.theme.get(SECTION_FLOATING_PANELS)
        if not isinstance(floating, dict):
            return
        panels = floating.get("panels")
        if not isinstance(panels, list):
            return
        for panel in panels:
            if not isinstance(panel, dict):
                continue
            if panel.get("enabled", True) is False:
                continue
            panel_id = str(panel.get("id", ""))
            if not panel_id:
                continue
            rect = floating_panel_physical_rect(panel)
            self._draw_panel_rect(
                kind="float",
                panel_id=panel_id,
                rect=rect,
                outline=FLOAT_OUTLINE,
                panel=panel,
            )

    def _draw_panel_rect(
        self,
        *,
        kind: str,
        panel_id: str,
        rect: Rect,
        outline: str,
        panel: dict[str, Any] | None = None,
    ) -> None:
        x1, y1, x2, y2 = self._rect_to_canvas(rect)
        selected = self._selected == (kind, panel_id)
        width = SELECTED_WIDTH if selected else 1
        tag = f"{kind}:{panel_id}"
        # Transparent body so tile backgrounds (incl. alpha=0) match the real HU.
        # Fallback translucent fill only when tile preview is off (edit affordance).
        if self.show_tile_preview_var.get():
            fill = ""
            stipple = ""
        else:
            fill = MAIN_FILL if kind == "main" else FLOAT_FILL
            stipple = "gray50"
        self.canvas.create_rectangle(
            x1,
            y1,
            x2,
            y2,
            fill=fill,
            stipple=stipple,
            outline=outline,
            width=width,
            tags=(tag, "panel"),
        )
        draw_tiles = (
            self.show_tile_preview_var.get()
            and isinstance(panel, dict)
            and not self._skip_tiles_while_dragging
        )
        if draw_tiles:
            draw_panel_tiles_on_canvas(
                self.canvas,
                x1,
                y1,
                x2,
                y2,
                panel,
                dark=self.is_dark_theme(),
                tags=(tag, "panel", "tile-preview"),
            )
        # Resize handle (SE)
        hs = HANDLE_SIZE
        self.canvas.create_rectangle(
            x2 - hs,
            y2 - hs,
            x2,
            y2,
            fill="#ffffff",
            outline=outline,
            width=1,
            tags=(tag, "handle", f"handle:{kind}:{panel_id}"),
        )

    # --- interaction ---

    def _find_panel_at(self, cx: float, cy: float) -> tuple[str, str, str] | None:
        """Return (kind, id, mode) where mode is move|resize."""
        items = self.canvas.find_overlapping(cx, cy, cx, cy)
        handle_hit: tuple[str, str] | None = None
        panel_hit: tuple[str, str] | None = None
        for item in reversed(items):
            tags = self.canvas.gettags(item)
            for tag in tags:
                if tag.startswith("handle:"):
                    parts = tag.split(":", 2)
                    if len(parts) == 3:
                        handle_hit = (parts[1], parts[2])
                elif ":" in tag and tag.split(":", 1)[0] in {"main", "float"}:
                    kind, pid = tag.split(":", 1)
                    if kind in {"main", "float"} and pid:
                        panel_hit = (kind, pid)
        if handle_hit:
            return handle_hit[0], handle_hit[1], "resize"
        if panel_hit:
            return panel_hit[0], panel_hit[1], "move"
        return None

    def _panel_dict(self, kind: str, panel_id: str) -> dict[str, Any] | None:
        section_key = SECTION_MAIN_SCREEN if kind == "main" else SECTION_FLOATING_PANELS
        section = self.theme.get(section_key)
        if not isinstance(section, dict):
            return None
        panels = section.get("panels")
        if not isinstance(panels, list):
            return None
        for panel in panels:
            if isinstance(panel, dict) and str(panel.get("id", "")) == panel_id:
                return panel
        return None

    def _current_rect(self, kind: str, panel: dict[str, Any]) -> Rect:
        if kind == "main":
            return main_panel_physical_rect(panel, self.profile)
        return floating_panel_physical_rect(panel)

    def _on_press(self, event: tk.Event) -> None:  # type: ignore[type-arg]
        hit = self._find_panel_at(event.x, event.y)
        if hit is None:
            self._selected = None
            self._drag_mode = None
            self.redraw()
            return
        kind, panel_id, mode = hit
        panel = self._panel_dict(kind, panel_id)
        if panel is None:
            return
        self._selected = (kind, panel_id)
        self._drag_mode = mode
        self._drag_origin_canvas = (event.x, event.y)
        self._drag_last_canvas = (event.x, event.y)
        self._drag_start_rect = self._current_rect(kind, panel)
        self.canvas.focus_set()
        self.redraw()

    def _on_drag(self, event: tk.Event) -> None:  # type: ignore[type-arg]
        if (
            self._selected is None
            or self._drag_mode is None
            or self._drag_origin_canvas is None
            or self._drag_start_rect is None
        ):
            return
        kind, panel_id = self._selected
        panel = self._panel_dict(kind, panel_id)
        if panel is None:
            return
        dx_c = event.x - self._drag_origin_canvas[0]
        dy_c = event.y - self._drag_origin_canvas[1]
        dx = dx_c / self._scale if self._scale else 0.0
        dy = dy_c / self._scale if self._scale else 0.0
        start = self._drag_start_rect
        if self._drag_mode == "move":
            new_rect = Rect(start.x + dx, start.y + dy, start.w, start.h)
            self._apply_rect(kind, panel, new_rect)
            # Move existing canvas items — avoids full redraw (underlay + all tiles).
            if self._drag_last_canvas is not None:
                self.canvas.move(
                    f"{kind}:{panel_id}",
                    event.x - self._drag_last_canvas[0],
                    event.y - self._drag_last_canvas[1],
                )
            self._drag_last_canvas = (event.x, event.y)
            return

        new_rect = Rect(start.x, start.y, max(8.0, start.w + dx), max(8.0, start.h + dy))
        self._apply_rect(kind, panel, new_rect)
        # Resize needs geometry rebuild; skip tile glyphs while dragging for speed.
        self._skip_tiles_while_dragging = True
        self._schedule_redraw(delay_ms=16)

    def _on_release(self, _event: tk.Event) -> None:  # type: ignore[type-arg]
        was_dragging = self._drag_mode is not None
        self._drag_mode = None
        self._drag_origin_canvas = None
        self._drag_last_canvas = None
        self._drag_start_rect = None
        self._skip_tiles_while_dragging = False
        if was_dragging:
            self.redraw()
            if self.on_changed is not None:
                self.on_changed()

    def _apply_rect(self, kind: str, panel: dict[str, Any], rect: Rect) -> None:
        if kind == "main":
            rel = physical_to_main_rel(rect, self.profile)
            apply_main_rel_to_panel(panel, *rel)
        else:
            apply_floating_physical_to_panel(panel, rect, self.profile)
