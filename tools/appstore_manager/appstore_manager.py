#!/usr/bin/env python3
"""
Dashing Store — менеджер каталога (Windows / любой Python с tkinter).

Открывает локальную sync-папку AppStore (Яндекс.Диск / Mail.ru),
показывает приложения, правит описания, ставит/меняет APK, пишет
catalog.meta.json + version.json + catalog.json.
"""

from __future__ import annotations

import copy
import os
import subprocess
import sys
import traceback
from pathlib import Path
from tkinter import (
    BOTH,
    DISABLED,
    END,
    LEFT,
    MULTIPLE,
    NORMAL,
    RIGHT,
    StringVar,
    Tk,
    Toplevel,
    W,
    X,
    Y,
    filedialog,
    messagebox,
    ttk,
)
from tkinter.scrolledtext import ScrolledText

# Allow `python appstore_manager.py` from this folder or repo root.
_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from appstore_catalog import (  # noqa: E402
    AppMeta,
    AppRecord,
    CertPinMismatchError,
    HU_CHOICES,
    INSTALL_MODE_CHOICES,
    add_app,
    add_download_files,
    add_section,
    delete_app,
    delete_section,
    ensure_seed_meta,
    files_dir,
    find_changelog_file,
    find_icon_file,
    hu_from_label,
    hu_label,
    install_mode_from_label,
    install_mode_label,
    list_channel_apps_dirs,
    load_sections,
    migrate_legacy_catalog,
    move_section,
    read_changelog,
    remove_download_files,
    resolve_apps_dir,
    scan_apps,
    set_apk,
    set_icon,
    update_section,
    upsert_meta,
    validate_downloads_folder,
    write_catalog,
    write_changelog,
)


class AppStoreManagerApp:
    def __init__(self, root: Tk) -> None:
        self.root = root
        self.root.title("Dashing Store — менеджер каталога")
        self.root.geometry("1100x780")
        self.root.minsize(900, 620)

        self.appstore_root: Path | None = None
        self.apps_dir: Path | None = None
        self.channel = StringVar(value="release")
        self.status = StringVar(value="Укажите папку AppStore (sync с облаком)")
        self.records: list[AppRecord] = []
        self.selected_id: str | None = None
        self._loaded_snapshot: tuple | None = None
        self._current_channel = self.channel.get()
        self._section_ids_order: list[str] = []

        self._build_ui()
        self._set_form_enabled(False)
        self.root.bind_all("<Control-s>", lambda _e: self.save_meta())
        self.root.bind_all("<Control-S>", lambda _e: self.save_meta())
        self.root.protocol("WM_DELETE_WINDOW", self.on_close)

    def _build_ui(self) -> None:
        top = ttk.Frame(self.root, padding=8)
        top.pack(fill=X)

        ttk.Button(top, text="Папка AppStore…", command=self.choose_root).pack(side=LEFT)
        ttk.Label(top, text="Канал:").pack(side=LEFT, padx=(12, 4))
        channel = ttk.Combobox(
            top,
            textvariable=self.channel,
            values=("release", "dev"),
            width=10,
            state="readonly",
        )
        channel.pack(side=LEFT)
        channel.bind("<<ComboboxSelected>>", lambda _e: self.on_channel_changed())
        ttk.Button(top, text="Обновить", command=self.user_reload).pack(side=LEFT, padx=8)
        ttk.Button(top, text="Добавить…", command=self.add_dialog).pack(side=LEFT)
        ttk.Button(top, text="Разделы…", command=self.sections_dialog).pack(side=LEFT, padx=8)
        ttk.Button(top, text="Пересобрать catalog.json", command=self.rebuild_catalog).pack(
            side=LEFT,
            padx=8,
        )

        self.path_label = ttk.Label(self.root, text="Папка apps: —", padding=(8, 0))
        self.path_label.pack(fill=X)

        body = ttk.Panedwindow(self.root, orient="horizontal")
        body.pack(fill=BOTH, expand=True, padx=8, pady=8)

        left = ttk.Frame(body)
        right = ttk.Frame(body)
        body.add(left, weight=1)
        body.add(right, weight=2)

        cols = ("id", "title", "version", "state")
        self.tree = ttk.Treeview(left, columns=cols, show="headings", selectmode="browse")
        self.tree.heading("id", text="id")
        self.tree.heading("title", text="Название")
        self.tree.heading("version", text="Версия")
        self.tree.heading("state", text="Состояние")
        self.tree.column("id", width=120, stretch=False)
        self.tree.column("title", width=180)
        self.tree.column("version", width=90, stretch=False)
        self.tree.column("state", width=160, stretch=False)
        self.tree.tag_configure("problem", foreground="#B3261E")
        scroll = ttk.Scrollbar(left, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.pack(side=LEFT, fill=BOTH, expand=True)
        scroll.pack(side=RIGHT, fill=Y)
        self.tree.bind("<<TreeviewSelect>>", self.on_select)

        notebook = ttk.Notebook(right)
        notebook.pack(fill=BOTH, expand=True)
        form = ttk.Frame(notebook, padding=4)
        files_tab = ttk.Frame(notebook, padding=4)
        changelog_tab = ttk.Frame(notebook, padding=4)
        notebook.add(form, text="Описание")
        notebook.add(files_tab, text="Файлы в Загрузки")
        notebook.add(changelog_tab, text="Changelog (карточка)")

        self.fields: dict[str, StringVar] = {}
        self._editable_widgets: list = []
        rows = [
            ("id", "id"),
            ("packageName", "packageName"),
            ("title", "Название"),
            ("publisher", "Издатель"),
            ("hu", "ГУ"),
            ("installMode", "Куда ставится"),
            ("signingCertSha256", "cert SHA-256 (из APK, pin)"),
            ("versionCode", "versionCode (из APK)"),
            ("versionName", "versionName (из APK)"),
            ("apkInfo", "Текущий APK"),
            ("iconInfo", "Иконка"),
        ]
        readonly = {"id", "versionCode", "versionName", "apkInfo", "iconInfo"}
        hu_labels = [label for _, label in HU_CHOICES]
        install_labels = [label for _, label in INSTALL_MODE_CHOICES]
        for key, label in rows:
            row = ttk.Frame(form)
            row.pack(fill=X, pady=2)
            ttk.Label(row, text=label, width=36).pack(side=LEFT)
            var = StringVar()
            self.fields[key] = var
            if key == "hu":
                entry = ttk.Combobox(row, textvariable=var, values=hu_labels, state="readonly")
            elif key == "installMode":
                entry = ttk.Combobox(
                    row,
                    textvariable=var,
                    values=install_labels,
                    state="readonly",
                )
            else:
                entry = ttk.Entry(row, textvariable=var, state=DISABLED if key in readonly else NORMAL)
            entry.pack(side=LEFT, fill=X, expand=True)
            if key not in readonly:
                self._editable_widgets.append(entry)

        # Hidden field for dirty tracking of section multi-select.
        self.fields["sections"] = StringVar()
        self.fields["downloadsFolder"] = StringVar()

        sections_row = ttk.Frame(form)
        sections_row.pack(fill=X, pady=4)
        ttk.Label(sections_row, text="Разделы магазина", width=36).pack(side=LEFT, anchor="n")
        sections_col = ttk.Frame(sections_row)
        sections_col.pack(side=LEFT, fill=X, expand=True)
        from tkinter import Listbox

        self.sections_list = Listbox(
            sections_col,
            selectmode=MULTIPLE,
            exportselection=False,
            height=3,
            activestyle="dotbox",
        )
        self.sections_list.pack(side=LEFT, fill=X, expand=True)
        self.sections_list.bind("<<ListboxSelect>>", self._on_sections_select)
        ttk.Button(sections_col, text="Разделы…", command=self.sections_dialog).pack(
            side=LEFT,
            padx=6,
            anchor="n",
        )
        self.sections_hint = ttk.Label(
            form,
            text="",
            foreground="#666",
        )
        self.sections_hint.pack(fill=X, padx=(0, 0))

        summary_box = ttk.LabelFrame(form, text="Описание (блок «О приложении» в магазине)", padding=4)
        summary_box.pack(fill=BOTH, expand=True, pady=(6, 2))
        self.summary_text = ScrolledText(summary_box, height=5, wrap="word", state=DISABLED)
        self.summary_text.pack(fill=BOTH, expand=True)

        btns = ttk.Frame(form)
        btns.pack(fill=X, pady=10)
        ttk.Button(btns, text="Сохранить (Ctrl+S)", command=self.save_meta).pack(side=LEFT)
        ttk.Button(btns, text="Заменить APK…", command=self.replace_apk).pack(side=LEFT, padx=8)
        ttk.Button(btns, text="Иконка…", command=self.replace_icon).pack(side=LEFT)
        ttk.Button(btns, text="Открыть папку", command=self.open_folder).pack(side=LEFT, padx=8)
        ttk.Button(btns, text="Удалить приложение…", command=self.delete_selected).pack(side=RIGHT)

        # --- Files tab ---
        ttk.Label(
            files_tab,
            text=(
                "Store сохраняет эти файлы при скачивании приложения в "
                "«Загрузки/<папка>/» (ГУ и телефон). Лежат в apps\\<id>\\files\\."
            ),
            wraplength=520,
        ).pack(fill=X, pady=(0, 8))
        folder_row = ttk.Frame(files_tab)
        folder_row.pack(fill=X, pady=2)
        ttk.Label(folder_row, text="Папка в Загрузках", width=24).pack(side=LEFT)
        folder_entry = ttk.Entry(folder_row, textvariable=self.fields["downloadsFolder"])
        folder_entry.pack(side=LEFT, fill=X, expand=True)
        self._editable_widgets.append(folder_entry)

        files_cols = ("name", "size", "sha", "state")
        self.files_tree = ttk.Treeview(
            files_tab,
            columns=files_cols,
            show="headings",
            selectmode="extended",
            height=10,
        )
        self.files_tree.heading("name", text="Файл")
        self.files_tree.heading("size", text="Размер")
        self.files_tree.heading("sha", text="SHA-256 (12 симв.)")
        self.files_tree.heading("state", text="Состояние")
        self.files_tree.column("name", width=220)
        self.files_tree.column("size", width=90, stretch=False)
        self.files_tree.column("sha", width=120, stretch=False)
        self.files_tree.column("state", width=120, stretch=False)
        self.files_tree.tag_configure("problem", foreground="#B3261E")
        self.files_tree.pack(fill=BOTH, expand=True, pady=4)

        files_btns = ttk.Frame(files_tab)
        files_btns.pack(fill=X, pady=6)
        ttk.Button(files_btns, text="Добавить файлы…", command=self.add_download_files_ui).pack(
            side=LEFT,
        )
        ttk.Button(
            files_btns,
            text="Удалить выбранные…",
            command=self.remove_download_files_ui,
        ).pack(side=LEFT, padx=8)
        ttk.Button(
            files_btns,
            text="Открыть папку файлов",
            command=self.open_files_folder,
        ).pack(side=LEFT)

        # --- Changelog tab ---
        ttk.Label(
            changelog_tab,
            text=(
                "Файлы changelog.ru.txt / changelog.en.txt в папке приложения. "
                "Магазин на ГУ подтягивает их при открытии карточки (язык UI Store)."
            ),
            wraplength=520,
        ).pack(fill=X, pady=(0, 8))

        self.changelog_texts: dict[str, ScrolledText] = {}
        for lang, title in (("ru", "changelog.ru.txt"), ("en", "changelog.en.txt")):
            box = ttk.LabelFrame(changelog_tab, text=title, padding=4)
            box.pack(fill=BOTH, expand=True, pady=4)
            text = ScrolledText(box, height=12, wrap="word", state=DISABLED)
            text.pack(fill=BOTH, expand=True)
            self.changelog_texts[lang] = text

        ch_btns = ttk.Frame(changelog_tab)
        ch_btns.pack(fill=X, pady=8)
        ttk.Button(
            ch_btns,
            text="Сохранить changelog.ru / .en",
            command=self.save_changelogs,
        ).pack(side=LEFT)
        ttk.Button(
            ch_btns,
            text="Сохранить всё (описание + changelog)",
            command=self.save_meta,
        ).pack(side=LEFT, padx=8)

        status = ttk.Label(self.root, textvariable=self.status, relief="sunken", anchor=W, padding=6)
        status.pack(fill=X, side="bottom")

    def _on_sections_select(self, _event=None) -> None:
        selected = [
            self._section_ids_order[i]
            for i in self.sections_list.curselection()
            if 0 <= i < len(self._section_ids_order)
        ]
        self.fields["sections"].set(",".join(selected))

    def _refresh_sections_listbox(self, selected_ids: list[str] | None = None) -> None:
        selected = set(selected_ids or [])
        self.sections_list.delete(0, END)
        self._section_ids_order = []
        try:
            sections = load_sections(self._current_apps_dir()) if self.appstore_root or self.apps_dir else []
        except Exception:
            sections = []
        if not sections:
            self.sections_list.configure(state=DISABLED)
            self.sections_hint.configure(text="Нет разделов — кнопка «Разделы…»")
            self.fields["sections"].set("")
            return
        self.sections_list.configure(state=NORMAL)
        self.sections_hint.configure(text="")
        height = min(5, max(1, len(sections)))
        self.sections_list.configure(height=height)
        for idx, section in enumerate(sections):
            self.sections_list.insert(END, section.title)
            self._section_ids_order.append(section.id)
            if section.id in selected:
                self.sections_list.selection_set(idx)
        self._on_sections_select()

    def _set_form_enabled(self, enabled: bool) -> None:
        state = NORMAL if enabled else DISABLED
        for widget in self._editable_widgets:
            if isinstance(widget, ttk.Combobox):
                widget.configure(state="readonly" if enabled else DISABLED)
            else:
                widget.configure(state=state)
        for widget in (self.summary_text, *self.changelog_texts.values()):
            widget.configure(state=state)
        try:
            if enabled and self._section_ids_order:
                self.sections_list.configure(state=NORMAL)
            else:
                self.sections_list.configure(state=DISABLED)
        except Exception:
            pass

    @staticmethod
    def _set_text(widget: ScrolledText, body: str) -> None:
        was = str(widget.cget("state"))
        widget.configure(state=NORMAL)
        widget.delete("1.0", END)
        if body:
            widget.insert("1.0", body)
        widget.configure(state=was)
        widget.edit_reset()

    def _set_changelog_text(self, lang: str, body: str) -> None:
        self._set_text(self.changelog_texts[lang], body)

    def _get_changelog_text(self, lang: str) -> str:
        return self.changelog_texts[lang].get("1.0", "end-1c")

    def _form_snapshot(self) -> tuple:
        return (
            tuple(var.get() for var in self.fields.values()),
            self.summary_text.get("1.0", "end-1c"),
            tuple(self._get_changelog_text(lang) for lang in ("ru", "en")),
        )

    def _is_dirty(self) -> bool:
        return self.selected_id is not None and self._form_snapshot() != self._loaded_snapshot

    def _confirm_discard(self) -> bool:
        """True when it is safe to leave the current form (saved, discarded, or clean)."""
        if not self._is_dirty():
            return True
        answer = messagebox.askyesnocancel(
            "Несохранённые изменения",
            f"Сохранить изменения в «{self.selected_id}»?",
        )
        if answer is None:
            return False
        if answer:
            return self.save_meta(reload_after=False)
        return True

    def on_close(self) -> None:
        if self._confirm_discard():
            self.root.destroy()

    def open_folder(self) -> None:
        folder = self._selected_folder()
        if folder is None:
            try:
                folder = self._current_apps_dir()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc))
                return
        self._open_path(folder)

    def open_files_folder(self) -> None:
        if not self.selected_id:
            return
        try:
            folder = files_dir(self._current_apps_dir(), self.selected_id)
            folder.mkdir(parents=True, exist_ok=True)
            self._open_path(folder)
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def _open_path(self, folder: Path) -> None:
        try:
            if sys.platform.startswith("win"):
                os.startfile(folder)  # type: ignore[attr-defined]
            elif sys.platform == "darwin":
                subprocess.Popen(["open", str(folder)])
            else:
                subprocess.Popen(["xdg-open", str(folder)])
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def choose_root(self) -> None:
        if not self._confirm_discard():
            return
        path = filedialog.askdirectory(title="Папка AppStore (или release/apps)")
        if not path:
            return
        try:
            chosen = Path(path)
            # If user picked apps/ or release/, climb to AppStore when possible
            root = chosen
            if chosen.name == "apps" and chosen.parent.name in ("release", "dev"):
                root = chosen.parent.parent
                self.channel.set(chosen.parent.name)
            elif chosen.name in ("release", "dev"):
                root = chosen.parent
                self.channel.set(chosen.name)
            self.appstore_root = root
            self._current_channel = self.channel.get()
            self.selected_id = None
            channels = list_channel_apps_dirs(root)
            if not channels:
                # Single apps dir
                self.apps_dir = resolve_apps_dir(chosen)
                self.appstore_root = self.apps_dir.parent.parent if self.apps_dir.name == "apps" else root
            ensure_seed_meta(channels.get(self.channel.get()) or resolve_apps_dir(chosen))
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def _current_apps_dir(self) -> Path:
        if self.appstore_root is not None:
            channels = list_channel_apps_dirs(self.appstore_root)
            channel = self.channel.get()
            if channel in channels:
                return channels[channel]
        if self.apps_dir is not None:
            return self.apps_dir
        raise RuntimeError("Сначала выберите папку AppStore")

    def reload(self) -> None:
        try:
            apps_dir = self._current_apps_dir()
            apps_dir.mkdir(parents=True, exist_ok=True)
            ensure_seed_meta(apps_dir)
            report = migrate_legacy_catalog(apps_dir)
            if report.changed:
                channel = self.channel.get()
                body = "\n".join(report.summary_lines())
                messagebox.showinfo(
                    "Миграция каталога",
                    f"Каталог {channel} переведён на новый формат\n\n{body}",
                )
            self.apps_dir = apps_dir
            self.path_label.configure(text=f"Папка apps: {apps_dir}")
            self.records = scan_apps(apps_dir)
            self.tree.delete(*self.tree.get_children())
            for record in self.records:
                version = "—"
                if record.releases:
                    parts = [
                        f"{r.flavor}:{r.versionName}({r.versionCode})"
                        for r in record.releases
                    ]
                    version = ", ".join(parts)
                elif record.release:
                    version = f"{record.release.versionName} ({record.release.versionCode})"
                problems = []
                if not record.apk_path:
                    problems.append("нет APK")
                if not record.has_version_json:
                    problems.append("нет version.json")
                if record.missing_package:
                    problems.append("нет packageName")
                problems.extend(record.download_problems)
                if problems:
                    state = ", ".join(problems)
                elif (record.meta.installMode or "hu") == "phone":
                    state = "телефон"
                else:
                    state = "ок"
                self.tree.insert(
                    "",
                    END,
                    iid=record.meta.id,
                    values=(record.meta.id, record.meta.title, version, state),
                    tags=("problem",) if problems else (),
                )
            self.status.set(f"Приложений: {len(self.records)}")
            if self.selected_id and self.tree.exists(self.selected_id):
                self.tree.selection_set(self.selected_id)
                self.tree.see(self.selected_id)
                self._fill_form(self.selected_id)
            else:
                self.selected_id = None
                self._clear_form()
                self._set_form_enabled(False)
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def user_reload(self) -> None:
        if self._confirm_discard():
            self.reload()

    def on_channel_changed(self) -> None:
        if not self._confirm_discard():
            self.channel.set(self._current_channel)
            return
        self._current_channel = self.channel.get()
        self.selected_id = None
        self.reload()

    def _clear_form(self) -> None:
        for var in self.fields.values():
            var.set("")
        self._set_text(self.summary_text, "")
        for lang in self.changelog_texts:
            self._set_changelog_text(lang, "")
        self.sections_list.delete(0, END)
        self._section_ids_order = []
        self.files_tree.delete(*self.files_tree.get_children())
        self._loaded_snapshot = None

    def on_select(self, _event=None) -> None:
        sel = self.tree.selection()
        if not sel or sel[0] == self.selected_id:
            return
        if not self._confirm_discard():
            # Put the selection back on the app whose edits are still in the form.
            if self.selected_id and self.tree.exists(self.selected_id):
                self.tree.selection_set(self.selected_id)
            return
        self._fill_form(sel[0])

    def _fill_files_table(self, record: AppRecord) -> None:
        self.files_tree.delete(*self.files_tree.get_children())
        problem_by_name = {}
        for msg in record.download_problems:
            # «нет файла X» / «файл X изменён»
            if msg.startswith("нет файла "):
                problem_by_name[msg[len("нет файла "):]] = "нет файла"
            elif msg.startswith("файл ") and msg.endswith(" изменён"):
                problem_by_name[msg[len("файл "):-len(" изменён")]] = "изменён"
        for entry in record.meta.downloadFiles:
            state = problem_by_name.get(entry.name, "ок")
            self.files_tree.insert(
                "",
                END,
                iid=entry.name,
                values=(
                    entry.name,
                    str(entry.sizeBytes),
                    entry.sha256[:12],
                    state,
                ),
                tags=("problem",) if state != "ок" else (),
            )

    def _fill_form(self, app_id: str) -> None:
        record = next((r for r in self.records if r.meta.id == app_id), None)
        if record is None:
            return
        self.selected_id = app_id
        self._set_form_enabled(True)
        m = record.meta
        self.fields["id"].set(m.id)
        self.fields["packageName"].set(m.packageName)
        self.fields["title"].set(m.title)
        self._set_text(self.summary_text, m.summary)
        self.fields["publisher"].set(m.publisher)
        self.fields["hu"].set(hu_label(m.hu))
        self.fields["installMode"].set(install_mode_label(m.installMode))
        self.fields["signingCertSha256"].set(",".join(m.signingCertSha256))
        self.fields["downloadsFolder"].set(m.downloadsFolder)
        self._refresh_sections_listbox(m.sections)
        if record.releases:
            primary = record.release or record.releases[0]
            self.fields["versionCode"].set(str(primary.versionCode))
            self.fields["versionName"].set(primary.versionName)
            lines = []
            for rel in record.releases:
                size = rel.apkSizeBytes or 0
                lines.append(
                    f"{rel.flavor}: {rel.apkFileName}  "
                    f"v{rel.versionName}({rel.versionCode})  "
                    f"sha256={rel.sha256[:12]}…  {size} B",
                )
            self.fields["apkInfo"].set(" | ".join(lines))
        else:
            self.fields["versionCode"].set("")
            self.fields["versionName"].set("")
            self.fields["apkInfo"].set("нет APK / version.json")
        icon = find_icon_file(record.folder)
        if icon is not None:
            self.fields["iconInfo"].set(str(icon.name))
        elif record.meta.icon:
            self.fields["iconInfo"].set(record.meta.icon)
        else:
            self.fields["iconInfo"].set("нет (будет буква / иконка установленного APK)")
        self._fill_files_table(record)
        for lang in ("ru", "en"):
            self._set_changelog_text(lang, read_changelog(record.folder, lang))
        self._loaded_snapshot = self._form_snapshot()

    def _meta_from_form(self) -> AppMeta:
        record = next((r for r in self.records if r.meta.id == self.selected_id), None)
        if record is not None:
            meta = copy.deepcopy(record.meta)
        else:
            meta = AppMeta(id=self.fields["id"].get().strip(), packageName="", title="")
        certs = [
            x.strip().lower()
            for x in self.fields["signingCertSha256"].get().replace(";", ",").split(",")
            if x.strip()
        ]
        sections = [
            s.strip() for s in self.fields["sections"].get().split(",") if s.strip()
        ]
        meta.packageName = self.fields["packageName"].get().strip()
        meta.title = self.fields["title"].get().strip()
        meta.summary = self.summary_text.get("1.0", "end-1c").strip()
        meta.publisher = self.fields["publisher"].get().strip() or "TBox"
        meta.hu = hu_from_label(self.fields["hu"].get())
        meta.installMode = install_mode_from_label(self.fields["installMode"].get())
        meta.signingCertSha256 = certs
        meta.sections = sections
        meta.downloadsFolder = self.fields["downloadsFolder"].get().strip()
        return meta

    def _selected_folder(self) -> Path | None:
        if not self.selected_id:
            return None
        record = next((r for r in self.records if r.meta.id == self.selected_id), None)
        return None if record is None else record.folder

    def save_changelogs(self) -> None:
        folder = self._selected_folder()
        if folder is None:
            return
        try:
            for lang in ("ru", "en"):
                write_changelog(folder, lang, self._get_changelog_text(lang))
            flags = []
            for lang in ("ru", "en"):
                path = find_changelog_file(folder, lang)
                if path is not None and path.stat().st_size > 0:
                    flags.append(path.name)
            self.status.set(
                f"Changelog сохранён ({', '.join(flags) if flags else 'пустые файлы'}): "
                f"{self.selected_id}",
            )
            if self._loaded_snapshot is not None:
                current = self._form_snapshot()
                self._loaded_snapshot = (*self._loaded_snapshot[:2], current[2])
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def save_meta(self, reload_after: bool = True) -> bool:
        if not self.selected_id:
            return False
        try:
            meta = self._meta_from_form()
            if meta.id != self.selected_id:
                raise ValueError("id менять нельзя (создайте новое приложение)")
            upsert_meta(self._current_apps_dir(), meta)
            folder = self._selected_folder()
            if folder is not None:
                for lang in ("ru", "en"):
                    write_changelog(folder, lang, self._get_changelog_text(lang))
            write_catalog(self._current_apps_dir())
            self.status.set(f"Сохранено: {meta.id} (описание + changelog.ru/en)")
            self._loaded_snapshot = self._form_snapshot()
            if reload_after:
                self.reload()
            return True
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))
            return False

    def add_download_files_ui(self) -> None:
        if not self.selected_id or not self._confirm_discard():
            return
        paths = filedialog.askopenfilenames(title="Файлы для Загрузок")
        if not paths:
            return
        # askopenfilenames may return a string on some platforms when empty/cancelled.
        if isinstance(paths, str):
            if not paths.strip():
                return
            paths = (paths,)
        try:
            apps_dir = self._current_apps_dir()
            record = next((r for r in self.records if r.meta.id == self.selected_id), None)
            if record is not None and not record.meta.downloadsFolder.strip():
                title = record.meta.title or self.selected_id
                try:
                    folder = validate_downloads_folder(title)
                except ValueError:
                    folder = self.selected_id
                meta = copy.deepcopy(record.meta)
                meta.downloadsFolder = folder
                upsert_meta(apps_dir, meta)
            try:
                added = add_download_files(apps_dir, self.selected_id, list(paths), replace=False)
            except FileExistsError as exc:
                names = str(exc)
                if not messagebox.askyesno("Заменить файлы", f"Заменить: {names}?"):
                    return
                added = add_download_files(apps_dir, self.selected_id, list(paths), replace=True)
            self.status.set(f"Файлов добавлено: {len(added)}")
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def remove_download_files_ui(self) -> None:
        if not self.selected_id:
            return
        selected = list(self.files_tree.selection())
        if not selected:
            messagebox.showinfo("Файлы", "Выберите файлы в таблице")
            return
        if not messagebox.askyesno(
            "Удалить файлы",
            f"Удалить выбранные файлы ({len(selected)}) из карточки и с диска?",
        ):
            return
        try:
            remove_download_files(self._current_apps_dir(), self.selected_id, selected)
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def sections_dialog(self) -> None:
        if not self._confirm_discard():
            return
        try:
            apps_dir = self._current_apps_dir()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))
            return

        dlg = Toplevel(self.root)
        dlg.title(f"Разделы магазина — канал {self.channel.get()}")
        dlg.transient(self.root)
        dlg.grab_set()
        dlg.geometry("560x360")

        cols = ("title", "titleEn", "count")
        tree = ttk.Treeview(dlg, columns=cols, show="headings", selectmode="browse")
        tree.heading("title", text="Название")
        tree.heading("titleEn", text="Название EN")
        tree.heading("count", text="Приложений")
        tree.column("title", width=200)
        tree.column("titleEn", width=160)
        tree.column("count", width=90, stretch=False)
        tree.pack(fill=BOTH, expand=True, padx=8, pady=8)

        def refresh() -> None:
            tree.delete(*tree.get_children())
            metas = {r.meta.id: r.meta for r in scan_apps(apps_dir)}
            for section in load_sections(apps_dir):
                count = sum(1 for m in metas.values() if section.id in m.sections)
                tree.insert(
                    "",
                    END,
                    iid=section.id,
                    values=(section.title, section.titleEn, count),
                )

        def selected_id() -> str | None:
            sel = tree.selection()
            return sel[0] if sel else None

        def add() -> None:
            title, title_en = self._ask_section_fields(dlg)
            if title is None:
                return
            try:
                add_section(apps_dir, title, title_en or "")
                refresh()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        def edit() -> None:
            sid = selected_id()
            if not sid:
                return
            section = next((s for s in load_sections(apps_dir) if s.id == sid), None)
            if section is None:
                return
            title, title_en = self._ask_section_fields(
                dlg,
                initial_title=section.title,
                initial_title_en=section.titleEn,
            )
            if title is None:
                return
            try:
                update_section(apps_dir, sid, title, title_en or "")
                refresh()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        def delete() -> None:
            sid = selected_id()
            if not sid:
                return
            item = tree.item(sid)
            count = int(item["values"][2]) if item and item.get("values") else 0
            if not messagebox.askyesno(
                "Удалить раздел",
                f"Удалить раздел «{item['values'][0]}»? "
                f"Он будет снят с {count} приложений.",
                parent=dlg,
            ):
                return
            try:
                delete_section(apps_dir, sid)
                refresh()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        def move(delta: int) -> None:
            sid = selected_id()
            if not sid:
                return
            try:
                move_section(apps_dir, sid, delta)
                refresh()
                if tree.exists(sid):
                    tree.selection_set(sid)
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        btns = ttk.Frame(dlg, padding=8)
        btns.pack(fill=X)
        ttk.Button(btns, text="Добавить…", command=add).pack(side=LEFT)
        ttk.Button(btns, text="Изменить…", command=edit).pack(side=LEFT, padx=4)
        ttk.Button(btns, text="Удалить…", command=delete).pack(side=LEFT)
        ttk.Button(btns, text="Выше", command=lambda: move(-1)).pack(side=LEFT, padx=8)
        ttk.Button(btns, text="Ниже", command=lambda: move(1)).pack(side=LEFT)

        def close() -> None:
            dlg.destroy()
            self.reload()

        ttk.Button(btns, text="Закрыть", command=close).pack(side=RIGHT)
        dlg.protocol("WM_DELETE_WINDOW", close)
        refresh()
        dlg.wait_window()

    def _ask_section_fields(
        self,
        parent,
        initial_title: str = "",
        initial_title_en: str = "",
    ) -> tuple[str | None, str | None]:
        dlg = Toplevel(parent)
        dlg.title("Раздел")
        dlg.transient(parent)
        dlg.grab_set()
        title_var = StringVar(value=initial_title)
        title_en_var = StringVar(value=initial_title_en)
        row1 = ttk.Frame(dlg, padding=8)
        row1.pack(fill=X)
        ttk.Label(row1, text="Название", width=16).pack(side=LEFT)
        ttk.Entry(row1, textvariable=title_var, width=40).pack(side=LEFT, fill=X, expand=True)
        row2 = ttk.Frame(dlg, padding=8)
        row2.pack(fill=X)
        ttk.Label(row2, text="Название EN", width=16).pack(side=LEFT)
        ttk.Entry(row2, textvariable=title_en_var, width=40).pack(side=LEFT, fill=X, expand=True)
        result: dict[str, str | None] = {"title": None, "titleEn": None}

        def ok() -> None:
            result["title"] = title_var.get().strip()
            result["titleEn"] = title_en_var.get().strip()
            dlg.destroy()

        def cancel() -> None:
            result["title"] = None
            result["titleEn"] = None
            dlg.destroy()

        row = ttk.Frame(dlg, padding=8)
        row.pack(fill=X)
        ttk.Button(row, text="OK", command=ok).pack(side=LEFT, padx=4)
        ttk.Button(row, text="Отмена", command=cancel).pack(side=LEFT)
        dlg.wait_window()
        return result["title"], result["titleEn"]

    def replace_apk(self) -> None:
        if not self.selected_id or not self._confirm_discard():
            return
        path = filedialog.askopenfilename(
            title="Выберите APK",
            filetypes=[("APK", "*.apk"), ("Все файлы", "*.*")],
        )
        if not path:
            return
        try:
            from apk_manifest import read_apk_info
            from appstore_catalog import detect_flavor_from_filename

            info = read_apk_info(path)
            guessed = detect_flavor_from_filename(Path(path).name) or "default"
            flavor = self._ask_flavor(guessed)
            if flavor is None:
                return
            # Preserve catalog description edits; packageName must match APK.
            form_meta = self._meta_from_form()
            if form_meta.packageName and form_meta.packageName != info.packageName:
                raise ValueError(
                    f"packageName в форме ({form_meta.packageName}) ≠ APK "
                    f"({info.packageName})",
                )
            if not form_meta.packageName:
                form_meta.packageName = info.packageName
            upsert_meta(self._current_apps_dir(), form_meta)
            allow_cert_change = False
            while True:
                try:
                    release = set_apk(
                        self._current_apps_dir(),
                        self.selected_id,
                        Path(path),
                        # versionCode / versionName / cert / icon — из APK
                        version_code=None,
                        version_name=None,
                        flavor=flavor,
                        remove_other_apks=True,
                        allow_cert_change=allow_cert_change,
                    )
                    break
                except CertPinMismatchError as exc:
                    if not messagebox.askyesno(
                        "Смена подписи",
                        f"{exc}\n\nЗаменить pin на сертификат этого APK?",
                    ):
                        return
                    allow_cert_change = True
            write_catalog(self._current_apps_dir())
            self.status.set(
                f"APK [{release.flavor}]: {release.apkFileName} — "
                f"{info.summary()} [{info.source}]",
            )
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def _ask_flavor(self, default: str = "default") -> str | None:
        """Ask ru / en / default for the APK being added."""
        dlg = Toplevel(self.root)
        dlg.title("Язык APK (flavor)")
        dlg.transient(self.root)
        dlg.grab_set()
        choice = StringVar(value=default if default in ("ru", "en", "default") else "default")
        ttk.Label(
            dlg,
            text="Какой flavor записать в version.json?\n"
            "Для Monitor/Store обычно ru и en (два APK).",
            padding=8,
        ).pack(fill=X)
        for value, label in (
            ("ru", "ru — русская сборка"),
            ("en", "en — English build"),
            ("default", "default — один APK без языков"),
        ):
            ttk.Radiobutton(dlg, text=label, variable=choice, value=value).pack(
                anchor=W,
                padx=12,
            )
        result: dict[str, str | None] = {"value": None}

        def ok() -> None:
            result["value"] = choice.get().strip() or "default"
            dlg.destroy()

        def cancel() -> None:
            result["value"] = None
            dlg.destroy()

        row = ttk.Frame(dlg, padding=8)
        row.pack(fill=X)
        ttk.Button(row, text="OK", command=ok).pack(side=LEFT, padx=4)
        ttk.Button(row, text="Отмена", command=cancel).pack(side=LEFT)
        dlg.wait_window()
        return result["value"]

    def replace_icon(self) -> None:
        if not self.selected_id or not self._confirm_discard():
            return
        path = filedialog.askopenfilename(
            title="Иконка (PNG / WebP / JPEG)",
            filetypes=[
                ("Images", "*.png *.webp *.jpg *.jpeg"),
                ("Все файлы", "*.*"),
            ],
        )
        if not path:
            return
        try:
            dest = set_icon(self._current_apps_dir(), self.selected_id, Path(path))
            self.status.set(f"Иконка: {dest.name}")
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def delete_selected(self) -> None:
        if not self.selected_id:
            return
        if not messagebox.askyesno(
            "Удалить",
            f"Удалить {self.selected_id} из каталога и папку с APK?",
        ):
            return
        try:
            delete_app(self._current_apps_dir(), self.selected_id, remove_files=True)
            self.selected_id = None
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def rebuild_catalog(self) -> None:
        try:
            out = write_catalog(self._current_apps_dir())
            self.status.set(f"Записан {out}")
            messagebox.showinfo("catalog.json", f"Обновлён:\n{out}")
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def add_dialog(self) -> None:
        if not self._confirm_discard():
            return
        try:
            apps_dir = self._current_apps_dir()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))
            return

        dlg = Toplevel(self.root)
        dlg.title("Добавить приложение")
        dlg.transient(self.root)
        dlg.grab_set()
        vars_map: dict[str, StringVar] = {}
        hu_labels = [label for _, label in HU_CHOICES]
        defaults = {
            "id": "",
            "packageName": "",
            "title": "",
            "summary": "",
            "publisher": "TBox",
            "hu": HU_CHOICES[0][1],
            "versionCode": "",
            "versionName": "",
        }
        entries: dict[str, ttk.Entry] = {}
        for key, default in defaults.items():
            row = ttk.Frame(dlg, padding=4)
            row.pack(fill=X)
            label = key
            if key in ("versionCode", "versionName"):
                label = f"{key} (из APK)"
            elif key == "hu":
                label = "ГУ"
            ttk.Label(row, text=label, width=18).pack(side=LEFT)
            var = StringVar(value=default)
            vars_map[key] = var
            if key == "hu":
                entry = ttk.Combobox(row, textvariable=var, values=hu_labels, state="readonly", width=45)
            else:
                state = DISABLED if key in ("versionCode", "versionName") else NORMAL
                entry = ttk.Entry(row, textvariable=var, width=48, state=state)
            entry.pack(side=LEFT, fill=X, expand=True)
            entries[key] = entry

        apk_var = StringVar(value="")
        apk_row = ttk.Frame(dlg, padding=4)
        apk_row.pack(fill=X)
        apk_hint = StringVar(value="Выберите APK — version/package подставятся сами")

        def pick_apk() -> None:
            path = filedialog.askopenfilename(
                parent=dlg,
                title="APK",
                filetypes=[("APK", "*.apk")],
            )
            if not path:
                return
            try:
                from apk_manifest import read_apk_info

                info = read_apk_info(path)
            except Exception as exc:
                messagebox.showerror("APK", str(exc), parent=dlg)
                return
            apk_var.set(path)
            vars_map["packageName"].set(info.packageName)
            vars_map["versionCode"].set(str(info.versionCode))
            vars_map["versionName"].set(info.versionName)
            if not vars_map["title"].get().strip() and info.label:
                vars_map["title"].set(info.label)
            if not vars_map["id"].get().strip():
                # Suggest id from package last segment
                suggested = info.packageName.rsplit(".", 1)[-1].lower()
                vars_map["id"].set(suggested)
            apk_hint.set(info.summary() + f" [{info.source}]")

        ttk.Label(apk_row, text="APK", width=18).pack(side=LEFT)
        ttk.Entry(apk_row, textvariable=apk_var).pack(side=LEFT, fill=X, expand=True)
        ttk.Button(apk_row, text="…", width=3, command=pick_apk).pack(side=LEFT)
        ttk.Label(dlg, textvariable=apk_hint, padding=4).pack(fill=X)

        def submit() -> None:
            try:
                apk = Path(apk_var.get()) if apk_var.get().strip() else None
                package = vars_map["packageName"].get().strip()
                if apk is not None:
                    from apk_manifest import read_apk_info

                    info = read_apk_info(apk)
                    package = info.packageName
                    vars_map["packageName"].set(package)
                    vars_map["versionCode"].set(str(info.versionCode))
                    vars_map["versionName"].set(info.versionName)
                meta = AppMeta(
                    id=vars_map["id"].get().strip(),
                    packageName=package,
                    title=vars_map["title"].get().strip(),
                    summary=vars_map["summary"].get().strip(),
                    publisher=vars_map["publisher"].get().strip() or "TBox",
                    hu=hu_from_label(vars_map["hu"].get()),
                )
                flavor = None
                if apk is not None:
                    from appstore_catalog import detect_flavor_from_filename

                    guessed = detect_flavor_from_filename(apk.name) or "default"
                    flavor = self._ask_flavor(guessed)
                    if flavor is None:
                        return
                add_app(
                    apps_dir,
                    meta,
                    apk_source=apk,
                    # None → set_apk читает манифест APK
                    version_code=None,
                    version_name=None,
                    flavor=flavor,
                )
                dlg.destroy()
                self.selected_id = meta.id
                self.reload()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        ttk.Button(dlg, text="Создать", command=submit).pack(pady=10)


def _enable_windows_dpi_awareness() -> None:
    """Without this Windows bitmap-scales the window on 125–200 % displays (blurry text)."""
    if not sys.platform.startswith("win"):
        return
    try:
        import ctypes

        ctypes.windll.shcore.SetProcessDpiAwareness(1)
    except Exception:
        try:
            ctypes.windll.user32.SetProcessDPIAware()
        except Exception:
            pass


def _apply_style(root: Tk) -> None:
    style = ttk.Style(root)
    if "vista" in style.theme_names():
        style.theme_use("vista")
    from tkinter import font as tkfont

    base = tkfont.nametofont("TkDefaultFont")
    if base.cget("size") < 10:
        for name in ("TkDefaultFont", "TkTextFont", "TkHeadingFont", "TkMenuFont"):
            tkfont.nametofont(name).configure(size=10)
    # Row height follows the font, otherwise rows overlap on HiDPI.
    line = tkfont.nametofont("TkDefaultFont").metrics("linespace")
    style.configure("Treeview", rowheight=int(line * 1.5))


def main() -> int:
    _enable_windows_dpi_awareness()
    try:
        root = Tk()
    except Exception as exc:
        print(
            "Не удалось запустить GUI (нужен Python с tkinter).\n"
            "На Windows ставьте python.org и отметьте tcl/tk.\n"
            f"Детали: {exc}",
            file=sys.stderr,
        )
        return 1
    try:
        _apply_style(root)
        AppStoreManagerApp(root)
        root.mainloop()
        return 0
    except Exception:
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
