#!/usr/bin/env python3
"""
TBox Store — менеджер каталога (Windows / любой Python с tkinter).

Открывает локальную sync-папку AppStore (Яндекс.Диск / Mail.ru),
показывает приложения, правит описания, ставит/меняет APK, пишет
catalog.meta.json + version.json + catalog.json.
"""

from __future__ import annotations

import sys
import traceback
from pathlib import Path
from tkinter import (
    BOTH,
    DISABLED,
    END,
    LEFT,
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

# Allow `python appstore_manager.py` from this folder or repo root.
_HERE = Path(__file__).resolve().parent
if str(_HERE) not in sys.path:
    sys.path.insert(0, str(_HERE))

from appstore_catalog import (  # noqa: E402
    AppMeta,
    AppRecord,
    CertPinMismatchError,
    add_app,
    delete_app,
    ensure_seed_meta,
    find_icon_file,
    list_channel_apps_dirs,
    resolve_apps_dir,
    scan_apps,
    set_apk,
    set_icon,
    upsert_meta,
    write_catalog,
)


class AppStoreManagerApp:
    def __init__(self, root: Tk) -> None:
        self.root = root
        self.root.title("TBox Store — менеджер каталога")
        self.root.geometry("1100x720")
        self.root.minsize(900, 560)

        self.appstore_root: Path | None = None
        self.apps_dir: Path | None = None
        self.channel = StringVar(value="release")
        self.status = StringVar(value="Укажите папку AppStore (sync с облаком)")
        self.records: list[AppRecord] = []
        self.selected_id: str | None = None

        self._build_ui()
        self._set_form_enabled(False)

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
        channel.bind("<<ComboboxSelected>>", lambda _e: self.reload())
        ttk.Button(top, text="Обновить", command=self.reload).pack(side=LEFT, padx=8)
        ttk.Button(top, text="Добавить…", command=self.add_dialog).pack(side=LEFT)
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

        cols = ("id", "title", "version", "apk")
        self.tree = ttk.Treeview(left, columns=cols, show="headings", selectmode="browse")
        self.tree.heading("id", text="id")
        self.tree.heading("title", text="Название")
        self.tree.heading("version", text="Версия")
        self.tree.heading("apk", text="APK")
        self.tree.column("id", width=120, stretch=False)
        self.tree.column("title", width=180)
        self.tree.column("version", width=90, stretch=False)
        self.tree.column("apk", width=80, stretch=False)
        scroll = ttk.Scrollbar(left, orient="vertical", command=self.tree.yview)
        self.tree.configure(yscrollcommand=scroll.set)
        self.tree.pack(side=LEFT, fill=BOTH, expand=True)
        scroll.pack(side=RIGHT, fill=Y)
        self.tree.bind("<<TreeviewSelect>>", self.on_select)

        form = ttk.Frame(right, padding=4)
        form.pack(fill=BOTH, expand=True)

        self.fields: dict[str, StringVar] = {}
        rows = [
            ("id", "id"),
            ("packageName", "packageName"),
            ("title", "Название"),
            ("summary", "Описание"),
            ("publisher", "Издатель"),
            ("origin", "origin (first-party|third-party)"),
            ("hu", "ГУ (a9,a10)"),
            ("category", "category (app|tool|oneshot)"),
            ("verified", "verified (true|false)"),
            ("reviewNotes", "Заметки проверки"),
            ("copyApkToDownloadsAppId", "APK в Загрузки (id, напр. dcl-launcher)"),
            ("signingCertSha256", "cert SHA-256 (из APK, pin)"),
            ("versionCode", "versionCode (из APK)"),
            ("versionName", "versionName (из APK)"),
            ("changelog", "Changelog APK"),
            ("apkInfo", "Текущий APK"),
            ("iconInfo", "Иконка"),
        ]
        readonly = {"id", "versionCode", "versionName", "apkInfo", "iconInfo"}
        for key, label in rows:
            row = ttk.Frame(form)
            row.pack(fill=X, pady=2)
            ttk.Label(row, text=label, width=32).pack(side=LEFT)
            var = StringVar()
            self.fields[key] = var
            state = DISABLED if key in readonly else NORMAL
            entry = ttk.Entry(row, textvariable=var, state=state)
            entry.pack(side=LEFT, fill=X, expand=True)
            setattr(self, f"_entry_{key}", entry)

        btns = ttk.Frame(form)
        btns.pack(fill=X, pady=10)
        ttk.Button(btns, text="Сохранить описание", command=self.save_meta).pack(side=LEFT)
        ttk.Button(btns, text="Заменить APK…", command=self.replace_apk).pack(side=LEFT, padx=8)
        ttk.Button(btns, text="Иконка…", command=self.replace_icon).pack(side=LEFT, padx=8)
        ttk.Button(btns, text="Удалить приложение…", command=self.delete_selected).pack(side=LEFT)

        status = ttk.Label(self.root, textvariable=self.status, relief="sunken", anchor=W, padding=6)
        status.pack(fill=X, side="bottom")

    def _set_form_enabled(self, enabled: bool) -> None:
        state = NORMAL if enabled else DISABLED
        for key, entry_name in (
            ("packageName", "_entry_packageName"),
            ("title", "_entry_title"),
            ("summary", "_entry_summary"),
            ("publisher", "_entry_publisher"),
            ("origin", "_entry_origin"),
            ("hu", "_entry_hu"),
            ("category", "_entry_category"),
            ("verified", "_entry_verified"),
            ("reviewNotes", "_entry_reviewNotes"),
            ("copyApkToDownloadsAppId", "_entry_copyApkToDownloadsAppId"),
            ("signingCertSha256", "_entry_signingCertSha256"),
            ("changelog", "_entry_changelog"),
        ):
            getattr(self, entry_name).configure(state=state)
        # versionCode / versionName всегда из манифеста APK — только просмотр
        self._entry_versionCode.configure(state=DISABLED)
        self._entry_versionName.configure(state=DISABLED)

    def choose_root(self) -> None:
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
            self.apps_dir = apps_dir
            self.path_label.configure(text=f"Папка apps: {apps_dir}")
            self.records = scan_apps(apps_dir)
            self.tree.delete(*self.tree.get_children())
            for record in self.records:
                version = "—"
                if record.release:
                    version = f"{record.release.versionName} ({record.release.versionCode})"
                apk = "да" if record.apk_path else "нет"
                mark = " !" if record.missing_package or not record.has_version_json else ""
                self.tree.insert(
                    "",
                    END,
                    iid=record.meta.id,
                    values=(record.meta.id + mark, record.meta.title, version, apk),
                )
            self.status.set(f"Приложений: {len(self.records)}")
            if self.selected_id and self.tree.exists(self.selected_id):
                self.tree.selection_set(self.selected_id)
                self.on_select()
            else:
                self.selected_id = None
                self._clear_form()
                self._set_form_enabled(False)
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def _clear_form(self) -> None:
        for var in self.fields.values():
            var.set("")

    def on_select(self, _event=None) -> None:
        sel = self.tree.selection()
        if not sel:
            return
        app_id = sel[0]
        record = next((r for r in self.records if r.meta.id == app_id), None)
        if record is None:
            return
        self.selected_id = app_id
        self._set_form_enabled(True)
        m = record.meta
        self.fields["id"].set(m.id)
        self.fields["packageName"].set(m.packageName)
        self.fields["title"].set(m.title)
        self.fields["summary"].set(m.summary)
        self.fields["publisher"].set(m.publisher)
        self.fields["origin"].set(m.origin)
        self.fields["hu"].set(",".join(m.hu))
        self.fields["category"].set(m.category)
        self.fields["verified"].set("true" if m.verified else "false")
        self.fields["reviewNotes"].set(m.reviewNotes)
        self.fields["copyApkToDownloadsAppId"].set(m.copyApkToDownloadsAppId)
        self.fields["signingCertSha256"].set(",".join(m.signingCertSha256))
        if record.release:
            self.fields["versionCode"].set(str(record.release.versionCode))
            self.fields["versionName"].set(record.release.versionName)
            self.fields["changelog"].set(record.release.changelog)
            apk = record.release.apkFileName
            size = record.release.apkSizeBytes or 0
            self.fields["apkInfo"].set(
                f"{apk}  sha256={record.release.sha256[:12]}…  {size} bytes",
            )
        else:
            self.fields["versionCode"].set("1")
            self.fields["versionName"].set("0.1.0")
            self.fields["changelog"].set("")
            self.fields["apkInfo"].set("нет APK / version.json")
        icon = find_icon_file(record.folder)
        if icon is not None:
            self.fields["iconInfo"].set(str(icon.name))
        elif record.meta.icon:
            self.fields["iconInfo"].set(record.meta.icon)
        else:
            self.fields["iconInfo"].set("нет (будет буква / иконка установленного APK)")

    def _meta_from_form(self) -> AppMeta:
        hu = [x.strip() for x in self.fields["hu"].get().split(",") if x.strip()]
        certs = [
            x.strip().lower()
            for x in self.fields["signingCertSha256"].get().replace(";", ",").split(",")
            if x.strip()
        ]
        verified_raw = self.fields["verified"].get().strip().lower()
        verified = verified_raw in ("1", "true", "yes", "да")
        return AppMeta(
            id=self.fields["id"].get().strip(),
            packageName=self.fields["packageName"].get().strip(),
            title=self.fields["title"].get().strip(),
            summary=self.fields["summary"].get().strip(),
            publisher=self.fields["publisher"].get().strip() or "TBox",
            origin=self.fields["origin"].get().strip() or "first-party",
            hu=hu or ["a9", "a10"],
            category=self.fields["category"].get().strip() or "app",
            verified=verified,
            reviewNotes=self.fields["reviewNotes"].get().strip(),
            copyApkToDownloadsAppId=self.fields["copyApkToDownloadsAppId"].get().strip(),
            signingCertSha256=certs,
        )

    def save_meta(self) -> None:
        if not self.selected_id:
            return
        try:
            meta = self._meta_from_form()
            if meta.id != self.selected_id:
                raise ValueError("id менять нельзя (создайте новое приложение)")
            upsert_meta(self._current_apps_dir(), meta)
            write_catalog(self._current_apps_dir())
            self.status.set(f"Сохранено: {meta.id}")
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def replace_apk(self) -> None:
        if not self.selected_id:
            return
        path = filedialog.askopenfilename(
            title="Выберите APK",
            filetypes=[("APK", "*.apk"), ("Все файлы", "*.*")],
        )
        if not path:
            return
        try:
            from apk_manifest import read_apk_info

            info = read_apk_info(path)
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
                        changelog=self.fields["changelog"].get().strip(),
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
                f"APK обновлён: {release.apkFileName} — {info.summary()} [{info.source}]",
            )
            self.reload()
        except Exception as exc:
            messagebox.showerror("Ошибка", str(exc))

    def replace_icon(self) -> None:
        if not self.selected_id:
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
        defaults = {
            "id": "",
            "packageName": "",
            "title": "",
            "summary": "",
            "publisher": "TBox",
            "origin": "first-party",
            "hu": "a9,a10",
            "category": "app",
            "versionCode": "",
            "versionName": "",
            "changelog": "",
        }
        entries: dict[str, ttk.Entry] = {}
        for key, default in defaults.items():
            row = ttk.Frame(dlg, padding=4)
            row.pack(fill=X)
            label = key
            if key in ("versionCode", "versionName"):
                label = f"{key} (из APK)"
            ttk.Label(row, text=label, width=18).pack(side=LEFT)
            var = StringVar(value=default)
            vars_map[key] = var
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
                hu = [x.strip() for x in vars_map["hu"].get().split(",") if x.strip()]
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
                    origin=vars_map["origin"].get().strip() or "first-party",
                    hu=hu or ["a9", "a10"],
                    category=vars_map["category"].get().strip() or "app",
                    verified=True,
                )
                add_app(
                    apps_dir,
                    meta,
                    apk_source=apk,
                    # None → set_apk читает манифест APK
                    version_code=None,
                    version_name=None,
                    changelog=vars_map["changelog"].get().strip(),
                )
                dlg.destroy()
                self.selected_id = meta.id
                self.reload()
            except Exception as exc:
                messagebox.showerror("Ошибка", str(exc), parent=dlg)

        ttk.Button(dlg, text="Создать", command=submit).pack(pady=10)


def main() -> int:
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
        style = ttk.Style()
        if "vista" in style.theme_names():
            style.theme_use("vista")
        AppStoreManagerApp(root)
        root.mainloop()
        return 0
    except Exception:
        traceback.print_exc()
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
