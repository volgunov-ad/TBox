#!/usr/bin/env python3
"""Unit tests for tools/appstore_manager/appstore_catalog.py (stdlib)."""

from __future__ import annotations

import json
import re
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "appstore_manager"))

from apk_manifest import extract_apk_icon, read_apk_info  # noqa: E402
from appstore_catalog import (  # noqa: E402
    AppMeta,
    CertPinMismatchError,
    _meta_from_dict,
    add_app,
    add_download_files,
    add_section,
    build_catalog,
    delete_app,
    delete_section,
    detect_flavor_from_filename,
    find_changelog_file,
    hu_from_label,
    hu_label,
    install_mode_from_label,
    install_mode_label,
    load_meta_map,
    load_sections,
    migrate_legacy_catalog,
    move_section,
    normalize_hu,
    parse_all_releases,
    read_changelog,
    remove_download_files,
    resolve_apps_dir,
    save_meta_map,
    scan_apps,
    set_apk,
    set_icon,
    update_section,
    upsert_meta,
    validate_download_file_name,
    validate_downloads_folder,
    write_catalog,
    write_changelog,
)

DCL_APK = ROOT / "tools" / "dcl_install" / "DclInstall.apk"
_DCL_MANIFEST = (ROOT / "tools" / "dcl_install" / "AndroidManifest.xml").read_text(encoding="utf-8")
DCL_CODE = int(re.search(r'android:versionCode="(\d+)"', _DCL_MANIFEST).group(1))
DCL_NAME = re.search(r'android:versionName="([^"]+)"', _DCL_MANIFEST).group(1)

REMOVED_KEYS = (
    "origin",
    "verified",
    "reviewNotes",
    "category",
    "copyApkToDownloadsAppId",
    "downloadsSidecarAppId",
    "changelog",
)


class AppStoreCatalogTest(unittest.TestCase):
    def test_resolve_and_add_replace_apk(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp) / "AppStore"
            (root / "release").mkdir(parents=True)
            (root / "dev").mkdir(parents=True)
            apps = resolve_apps_dir(root)
            self.assertTrue(str(apps).endswith(str(Path("release") / "apps")))

            apk = Path(tmp) / "demo.apk"
            apk.write_bytes(b"apk-bytes-1")

            meta = AppMeta(
                id="demo",
                packageName="vad.dashing.demo",
                title="Demo",
                summary="test",
                hu=["a9"],
            )
            add_app(
                apps,
                meta,
                apk_source=apk,
                version_code=1,
                version_name="0.1.0",
            )

            records = scan_apps(apps)
            self.assertEqual(1, len(records))
            self.assertEqual("demo", records[0].meta.id)
            self.assertIsNotNone(records[0].release)
            assert records[0].release is not None
            self.assertEqual(1, records[0].release.versionCode)
            self.assertTrue((apps / "demo" / "demo.apk").is_file())

            catalog = build_catalog(apps)
            self.assertEqual(1, len(catalog["apps"]))
            self.assertEqual(
                "yandex-disk:/apps/demo/version.json",
                catalog["apps"][0]["manifest"],
            )

            apk2 = Path(tmp) / "demo-v2.apk"
            apk2.write_bytes(b"apk-bytes-2-longer")
            release = set_apk(
                apps,
                "demo",
                apk2,
                version_code=2,
                version_name="0.2.0",
            )
            self.assertEqual("demo-v2.apk", release.apkFileName)
            self.assertFalse((apps / "demo" / "demo.apk").exists())
            self.assertTrue((apps / "demo" / "demo-v2.apk").is_file())

            out = write_catalog(apps)
            data = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual(1, len(data["apps"]))

            upsert_meta(
                apps,
                AppMeta(
                    id="demo",
                    packageName="vad.dashing.demo",
                    title="Demo Renamed",
                    hu=["a9", "a10"],
                ),
            )
            write_catalog(apps)
            self.assertEqual(
                "Demo Renamed",
                json.loads((apps / "catalog.json").read_text(encoding="utf-8"))["apps"][0]["title"],
            )

            icon = Path(tmp) / "logo.png"
            icon.write_bytes(b"\x89PNG\r\n\x1a\nfake")
            set_icon(apps, "demo", icon)
            catalog2 = json.loads((apps / "catalog.json").read_text(encoding="utf-8"))
            self.assertEqual(
                "yandex-disk:/apps/demo/icon.png",
                catalog2["apps"][0]["icon"],
            )
            self.assertTrue((apps / "demo" / "icon.png").is_file())

            delete_app(apps, "demo")
            self.assertEqual([], scan_apps(apps))
            self.assertFalse((apps / "demo").exists())

    def test_detect_flavor_from_filename(self) -> None:
        self.assertEqual("ru", detect_flavor_from_filename("tbox-monitor-1.0-ru-release.apk"))
        self.assertEqual("ru", detect_flavor_from_filename("store-0.1.1-ru-release.apk"))
        self.assertEqual("en", detect_flavor_from_filename("app-en.apk"))
        self.assertEqual("ru", detect_flavor_from_filename("store_0.1.0_ru_debug.apk"))
        self.assertIsNone(detect_flavor_from_filename("camd.apk"))

    def test_set_apk_keeps_other_flavor(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            upsert_meta(
                apps,
                AppMeta(
                    id="demo",
                    packageName="vad.dashing.dclinstall",
                    title="Demo",
                    hu=["a9"],
                ),
            )
            ru_src = Path(tmp) / "demo-ru.apk"
            en_src = Path(tmp) / "demo-en.apk"
            ru_src.write_bytes(DCL_APK.read_bytes())
            en_src.write_bytes(DCL_APK.read_bytes())
            set_apk(apps, "demo", ru_src, flavor="ru", allow_cert_change=True)
            set_apk(apps, "demo", en_src, flavor="en", allow_cert_change=True)
            releases = parse_all_releases(apps / "demo" / "version.json")
            flavors = sorted(r.flavor for r in releases)
            self.assertEqual(["en", "ru"], flavors)
            self.assertTrue((apps / "demo" / "demo-ru.apk").is_file())
            self.assertTrue((apps / "demo" / "demo-en.apk").is_file())

    def test_read_apk_info_from_manifest(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        info = read_apk_info(DCL_APK)
        self.assertEqual("vad.dashing.dclinstall", info.packageName)
        self.assertEqual(DCL_CODE, info.versionCode)
        self.assertEqual(DCL_NAME, info.versionName)
        self.assertTrue(info.source in ("aapt", "axml"))

    def test_set_apk_reads_version_from_apk(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            meta = AppMeta(
                id="dclinstall",
                packageName="vad.dashing.dclinstall",
                title="DCL Install",
                hu=["a9"],
            )
            upsert_meta(apps, meta)
            release = set_apk(apps, "dclinstall", DCL_APK)
            self.assertEqual(DCL_CODE, release.versionCode)
            self.assertEqual(DCL_NAME, release.versionName)
            self.assertTrue((apps / "dclinstall" / DCL_APK.name).is_file())

    def test_extract_icon_and_cert_pin(self) -> None:
        factory = ROOT / "mbcan" / "MB_FactoryMode.apk"
        if not factory.is_file():
            self.skipTest("MB_FactoryMode.apk missing")
        info = read_apk_info(factory)
        self.assertEqual(64, len(info.signingCertSha256))
        with tempfile.TemporaryDirectory() as tmp:
            icon = extract_apk_icon(factory, tmp)
            self.assertIsNotNone(icon)
            assert icon is not None
            self.assertTrue(icon.is_file())
            self.assertGreater(icon.stat().st_size, 0)

            apps = Path(tmp) / "apps"
            apps.mkdir()
            upsert_meta(
                apps,
                AppMeta(
                    id="factory",
                    packageName="com.mengbo.factory",
                    title="Factory",
                    hu=["a9"],
                ),
            )
            set_apk(apps, "factory", factory)
            meta = scan_apps(apps)[0].meta
            self.assertEqual([info.signingCertSha256], meta.signingCertSha256)
            self.assertTrue((apps / "factory" / "icon.png").is_file())

    def test_cert_pin_mismatch_requires_allow(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            upsert_meta(
                apps,
                AppMeta(
                    id="dclinstall",
                    packageName="vad.dashing.dclinstall",
                    title="DCL Install",
                    hu=["a9"],
                    signingCertSha256=["a" * 64],
                ),
            )
            with self.assertRaises(CertPinMismatchError):
                set_apk(apps, "dclinstall", DCL_APK)
            release = set_apk(
                apps,
                "dclinstall",
                DCL_APK,
                allow_cert_change=True,
            )
            self.assertEqual(DCL_CODE, release.versionCode)
            meta = scan_apps(apps)[0].meta
            self.assertEqual(64, len(meta.signingCertSha256[0]))

    def test_set_apk_rejects_package_mismatch(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            upsert_meta(
                apps,
                AppMeta(
                    id="dclinstall",
                    packageName="com.example.other",
                    title="Wrong",
                    hu=["a9"],
                ),
            )
            with self.assertRaises(ValueError):
                set_apk(apps, "dclinstall", DCL_APK)

    def test_changelog_ru_en_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp) / "mqtt"
            folder.mkdir()
            write_changelog(folder, "ru", "0.1.0\n1. Первая версия\n")
            write_changelog(folder, "en", "0.1.0\n1. First release\n")
            self.assertEqual(
                "0.1.0\n1. Первая версия",
                read_changelog(folder, "ru").rstrip(),
            )
            self.assertEqual(
                "0.1.0\n1. First release",
                read_changelog(folder, "en").rstrip(),
            )
            self.assertIsNotNone(find_changelog_file(folder, "ru"))
            self.assertTrue(find_changelog_file(folder, "en").name.endswith("en.txt"))

    def test_missing_package_excluded_from_catalog(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            folder = apps / "orphan"
            folder.mkdir()
            (folder / "version.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "releases": [
                            {
                                "versionCode": 1,
                                "versionName": "1",
                                "flavor": "default",
                                "apkFileName": "x.apk",
                                "sha256": "abc",
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )
            (folder / "x.apk").write_bytes(b"x")
            catalog = build_catalog(apps)
            self.assertEqual([], catalog["apps"])

    def test_removed_keys_absent_from_written_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            apk = Path(tmp) / "demo.apk"
            apk.write_bytes(b"apk")
            add_app(
                apps,
                AppMeta(id="demo", packageName="vad.dashing.demo", title="Demo", hu=["a9"]),
                apk_source=apk,
                version_code=1,
                version_name="0.1.0",
            )
            meta_raw = json.loads((apps / "catalog.meta.json").read_text(encoding="utf-8"))
            catalog = build_catalog(apps)
            version = json.loads((apps / "demo" / "version.json").read_text(encoding="utf-8"))
            for blob in (meta_raw["apps"][0], catalog["apps"][0], version["releases"][0]):
                for key in REMOVED_KEYS:
                    self.assertNotIn(key, blob)

    def test_meta_from_dict_ignores_legacy_and_normalize_hu(self) -> None:
        meta = _meta_from_dict(
            {
                "id": "x",
                "packageName": "p",
                "title": "T",
                "origin": "third-party",
                "verified": False,
                "reviewNotes": "n",
                "category": "tool",
                "copyApkToDownloadsAppId": "other",
                "hu": "A10, junk, a9",
            },
            "x",
        )
        self.assertEqual(["a9", "a10"], meta.hu)
        self.assertEqual([], meta.sections)
        self.assertEqual("", meta.downloadsFolder)
        self.assertEqual(normalize_hu(""), ["a9", "a10"])
        self.assertEqual(normalize_hu(["A9"]), ["a9"])
        self.assertEqual(normalize_hu("a10"), ["a10"])
        self.assertEqual(normalize_hu(["x", "a10", "a9", "a9"]), ["a9", "a10"])

    def test_hu_and_install_mode_labels(self) -> None:
        self.assertEqual("A9 и A10", hu_label(["a9", "a10"]))
        self.assertEqual("Только A9", hu_label(["a9"]))
        self.assertEqual("Только A10", hu_label(["a10"]))
        self.assertEqual(["a9", "a10"], hu_from_label("A9 и A10"))
        self.assertEqual(["a9"], hu_from_label("Только A9"))
        self.assertEqual(["a10"], hu_from_label("Только A10"))
        self.assertEqual(["a9", "a10"], hu_from_label("неизвестно"))
        self.assertEqual("На ГУ", install_mode_label("hu"))
        self.assertEqual("На телефон (QR и Загрузки)", install_mode_label("phone"))
        self.assertEqual("hu", install_mode_from_label("На ГУ"))
        self.assertEqual("phone", install_mode_from_label("На телефон (QR и Загрузки)"))
        self.assertEqual("hu", install_mode_from_label("???") )

        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            apk = Path(tmp) / "p.apk"
            apk.write_bytes(b"p")
            add_app(
                apps,
                AppMeta(
                    id="phone-app",
                    packageName="vad.dashing.phone",
                    title="Phone",
                    installMode="phone",
                ),
                apk_source=apk,
                version_code=1,
                version_name="1",
            )
            add_app(
                apps,
                AppMeta(
                    id="hu-app",
                    packageName="vad.dashing.hu",
                    title="HU",
                    installMode="hu",
                ),
                apk_source=apk,
                version_code=1,
                version_name="1",
            )
            catalog = build_catalog(apps)
            by_id = {a["id"]: a for a in catalog["apps"]}
            self.assertEqual("phone", by_id["phone-app"]["installMode"])
            self.assertNotIn("installMode", by_id["hu-app"])

    def test_sections_crud_and_catalog(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            s1 = add_section(apps, "Приложения", "Apps")
            self.assertEqual("s1", s1.id)
            s2 = add_section(apps, "Инструменты", "Tools")
            self.assertEqual("s2", s2.id)
            update_section(apps, s1.id, "Apps RU", "Apps")
            move_section(apps, s2.id, -1)
            sections = load_sections(apps)
            self.assertEqual(["s2", "s1"], [s.id for s in sections])

            apk = Path(tmp) / "a.apk"
            apk.write_bytes(b"a")
            meta = AppMeta(
                id="demo",
                packageName="vad.dashing.demo",
                title="Demo",
                sections=["s1", "missing", "s2"],
            )
            add_app(apps, meta, apk_source=apk, version_code=1, version_name="1")
            # upsert drops unknown section ids
            loaded = load_meta_map(apps)["demo"]
            self.assertEqual(["s1", "s2"], loaded.sections)

            catalog = build_catalog(apps)
            self.assertEqual(
                ["schemaVersion", "publishedAt", "sections", "apps"],
                list(catalog.keys()),
            )
            meta_raw = json.loads((apps / "catalog.meta.json").read_text(encoding="utf-8"))
            self.assertEqual(["sections", "apps"], list(meta_raw.keys()))
            self.assertEqual(["s2", "s1"], [s["id"] for s in catalog["sections"]])
            self.assertEqual(["s1", "s2"], catalog["apps"][0]["sections"])

            # save_meta_map(None) keeps sections
            metas = load_meta_map(apps)
            save_meta_map(apps, metas)
            self.assertEqual(2, len(load_sections(apps)))

            detached = delete_section(apps, "s1")
            self.assertEqual(1, detached)
            self.assertEqual(["s2"], load_meta_map(apps)["demo"].sections)
            self.assertEqual(["s2"], [s.id for s in load_sections(apps)])

    def test_card_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            apk = Path(tmp) / "demo.apk"
            apk.write_bytes(b"apk")
            add_app(
                apps,
                AppMeta(id="demo", packageName="vad.dashing.demo", title="Demo App"),
                apk_source=apk,
                version_code=1,
                version_name="1",
            )
            f1 = Path(tmp) / "readme.txt"
            f1.write_text("hello", encoding="utf-8")
            f2 = Path(tmp) / "extra.bin"
            f2.write_bytes(b"\x00\x01")
            added = add_download_files(apps, "demo", [f1, f2])
            self.assertEqual(2, len(added))
            self.assertTrue((apps / "demo" / "files" / "readme.txt").is_file())
            meta = load_meta_map(apps)["demo"]
            self.assertEqual("Demo App", meta.downloadsFolder)
            self.assertEqual(2, len(meta.downloadFiles))

            with self.assertRaises(FileExistsError):
                add_download_files(apps, "demo", [f1], replace=False)
            upper_dir = Path(tmp) / "upper"
            upper_dir.mkdir()
            f1_upper = upper_dir / "README.TXT"
            f1_upper.write_text("other", encoding="utf-8")
            with self.assertRaises(FileExistsError) as clash:
                add_download_files(apps, "demo", [f1_upper], replace=False)
            self.assertIn("README.TXT", str(clash.exception))
            old_sha = next(f.sha256 for f in meta.downloadFiles if f.name == "readme.txt")
            f1.write_text("hello2", encoding="utf-8")
            replaced = add_download_files(apps, "demo", [f1], replace=True)
            self.assertEqual(1, len(replaced))
            meta = load_meta_map(apps)["demo"]
            self.assertEqual(2, len(meta.downloadFiles))
            readme_entry = next(f for f in meta.downloadFiles if f.name == "readme.txt")
            self.assertNotEqual(old_sha, readme_entry.sha256)
            self.assertEqual(6, readme_entry.sizeBytes)
            with self.assertRaises(ValueError):
                validate_downloads_folder("a:b")
            with self.assertRaises(ValueError):
                validate_downloads_folder("name.")

            with self.assertRaises(ValueError):
                validate_download_file_name("../x")
            with self.assertRaises(ValueError):
                validate_download_file_name(".hidden")
            with self.assertRaises(ValueError):
                validate_download_file_name("x.part")
            with self.assertRaises(ValueError):
                validate_downloads_folder("../bad")
            with self.assertRaises(ValueError):
                validate_downloads_folder(".")

            # Missing / size-changed → excluded from catalog + download_problems
            (apps / "demo" / "files" / "extra.bin").unlink()
            readme = apps / "demo" / "files" / "readme.txt"
            readme.write_text("changed-size!!!!", encoding="utf-8")
            records = scan_apps(apps)
            problems = records[0].download_problems
            self.assertTrue(any("нет файла extra.bin" in p for p in problems))
            self.assertTrue(any("readme.txt" in p and "изменён" in p for p in problems))
            catalog = build_catalog(apps)
            self.assertNotIn("downloads", catalog["apps"][0])

            # Restore and remove
            add_download_files(apps, "demo", [f1, f2], replace=True)
            remove_download_files(apps, "demo", ["readme.txt"])
            meta = load_meta_map(apps)["demo"]
            self.assertEqual(["extra.bin"], [f.name for f in meta.downloadFiles])
            self.assertFalse((apps / "demo" / "files" / "readme.txt").exists())

            # Folder required when files exist
            meta.downloadsFolder = ""
            with self.assertRaises(ValueError):
                upsert_meta(apps, meta)

    def test_migration_legacy_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            # Build a legacy meta from the old shape (reintroduce legacy keys).
            legacy_apps = [
                {
                    "id": "demo-app",
                    "packageName": "vad.dashing.demo",
                    "title": "Demo",
                    "publisher": "TBox",
                    "origin": "first-party",
                    "hu": ["a9"],
                    "category": "app",
                    "verified": True,
                },
                {
                    "id": "dclinstall",
                    "packageName": "vad.dashing.dclinstall",
                    "title": "DCL Install",
                    "publisher": "TBox",
                    "origin": "first-party",
                    "hu": ["a9"],
                    "category": "tool",
                    "verified": True,
                    "copyApkToDownloadsAppId": "dcl-launcher",
                },
                {
                    "id": "hidden",
                    "packageName": "vad.dashing.hidden",
                    "title": "Hidden",
                    "publisher": "TBox",
                    "origin": "third-party",
                    "hu": ["a9"],
                    "category": "app",
                    "verified": False,
                    "reviewNotes": "skip",
                },
                {
                    "id": "dcl-launcher",
                    "packageName": "ras.dashing.tbox.launcher",
                    "title": "DCL Launcher",
                    "publisher": "DCL",
                    "origin": "third-party",
                    "hu": ["a9"],
                    "category": "app",
                    "verified": True,
                },
            ]
            (apps / "catalog.meta.json").write_text(
                json.dumps({"apps": legacy_apps}, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            # Target APKs for sidecar
            launcher = apps / "dcl-launcher"
            launcher.mkdir()
            ru = launcher / "dcl-launcher-0.0.1-ru.apk"
            en = launcher / "dcl-launcher-0.0.1-en.apk"
            ru.write_bytes(b"ru-apk")
            en.write_bytes(b"en-apk")
            (launcher / "version.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "releases": [
                            {
                                "versionCode": 1,
                                "versionName": "0.0.1",
                                "flavor": "ru",
                                "apkFileName": ru.name,
                                "sha256": "a" * 64,
                                "changelog": "старый текст ru",
                            },
                            {
                                "versionCode": 1,
                                "versionName": "0.0.1",
                                "flavor": "en",
                                "apkFileName": en.name,
                                "sha256": "b" * 64,
                                "changelog": "old en text",
                            },
                        ],
                    },
                    ensure_ascii=False,
                    indent=2,
                )
                + "\n",
                encoding="utf-8",
            )
            # Stub version.json for other apps so they appear in catalog
            for app_id in ("demo-app", "dclinstall", "hidden"):
                folder = apps / app_id
                folder.mkdir(exist_ok=True)
                apk = folder / f"{app_id}.apk"
                apk.write_bytes(b"x")
                (folder / "version.json").write_text(
                    json.dumps(
                        {
                            "schemaVersion": 1,
                            "releases": [
                                {
                                    "versionCode": 1,
                                    "versionName": "1",
                                    "flavor": "default",
                                    "apkFileName": apk.name,
                                    "sha256": "c" * 64,
                                }
                            ],
                        }
                    ),
                    encoding="utf-8",
                )

            report = migrate_legacy_catalog(apps)
            self.assertTrue(report.changed)
            self.assertIn("hidden", report.unverified_ids)
            self.assertIn("app", report.category_sections)
            self.assertIn("tool", report.category_sections)
            self.assertIn("dclinstall", report.sidecar_files)
            self.assertTrue(any("changelog.ru.txt" in p for p in report.changelog_files))
            self.assertIn("dcl-launcher", report.version_json_stripped)
            summary = report.summary_lines()
            self.assertTrue(summary[0].startswith("Были скрыты (Проверено = нет)"))
            self.assertIn("hidden", summary[0])
            self.assertTrue(any("после обновления dcl-launcher" in line for line in summary))
            self.assertTrue((apps / "dcl-launcher" / "changelog.en.txt").is_file())

            meta_raw = json.loads((apps / "catalog.meta.json").read_text(encoding="utf-8"))
            self.assertEqual(
                ["app", "tool"],
                [s["id"] for s in meta_raw["sections"]],
            )
            for app in meta_raw["apps"]:
                for key in REMOVED_KEYS:
                    self.assertNotIn(key, app)
            dcl = next(a for a in meta_raw["apps"] if a["id"] == "dclinstall")
            self.assertEqual(["tool"], dcl["sections"])
            self.assertEqual("DCL Launcher", dcl["downloads"]["folder"])
            self.assertEqual(2, len(dcl["downloads"]["files"]))
            self.assertTrue((apps / "dclinstall" / "files" / ru.name).is_file())
            self.assertTrue((apps / "dclinstall" / "files" / en.name).is_file())

            changelog_ru = (apps / "dcl-launcher" / "changelog.ru.txt").read_text(encoding="utf-8")
            self.assertIn("старый текст", changelog_ru)
            version = json.loads((apps / "dcl-launcher" / "version.json").read_text(encoding="utf-8"))
            for rel in version["releases"]:
                self.assertNotIn("changelog", rel)

            # Existing changelog file is not overwritten
            write_changelog(apps / "dcl-launcher", "ru", "keep me")
            # Re-add changelog key to force strip path again — migration should not overwrite file
            version["releases"][0]["changelog"] = "ignored"
            (apps / "dcl-launcher" / "version.json").write_text(
                json.dumps(version, ensure_ascii=False, indent=2) + "\n",
                encoding="utf-8",
            )
            # Second full run on already-migrated meta (no legacy keys) → changed may be True
            # only due to the re-added changelog key strip; then third run is clean.
            report2 = migrate_legacy_catalog(apps)
            self.assertIn("dcl-launcher", report2.version_json_stripped)
            self.assertEqual("keep me", read_changelog(apps / "dcl-launcher", "ru").strip())

            meta_before = (apps / "catalog.meta.json").read_bytes()
            report3 = migrate_legacy_catalog(apps)
            self.assertFalse(report3.changed)
            self.assertEqual(meta_before, (apps / "catalog.meta.json").read_bytes())

    def test_upsert_meta_keeps_download_files(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            apk = Path(tmp) / "demo.apk"
            apk.write_bytes(b"apk")
            add_app(
                apps,
                AppMeta(id="demo", packageName="vad.dashing.demo", title="Demo"),
                apk_source=apk,
                version_code=1,
                version_name="1",
            )
            f1 = Path(tmp) / "a.txt"
            f1.write_text("a", encoding="utf-8")
            add_download_files(apps, "demo", [f1])
            meta = load_meta_map(apps)["demo"]
            self.assertEqual(1, len(meta.downloadFiles))
            # Simulate form save that deepcopy's and only overrides form fields.
            meta.title = "Demo 2"
            upsert_meta(apps, meta)
            again = load_meta_map(apps)["demo"]
            self.assertEqual("Demo 2", again.title)
            self.assertEqual(1, len(again.downloadFiles))
            self.assertEqual("a.txt", again.downloadFiles[0].name)


if __name__ == "__main__":
    unittest.main()
