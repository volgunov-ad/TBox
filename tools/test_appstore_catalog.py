#!/usr/bin/env python3
"""Unit tests for tools/appstore_manager/appstore_catalog.py (stdlib)."""

from __future__ import annotations

import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools" / "appstore_manager"))

from apk_manifest import read_apk_info  # noqa: E402
from appstore_catalog import (  # noqa: E402
    AppMeta,
    add_app,
    build_catalog,
    delete_app,
    resolve_apps_dir,
    scan_apps,
    set_apk,
    set_icon,
    upsert_meta,
    write_catalog,
)

DCL_APK = ROOT / "tools" / "dcl_install" / "DclInstall.apk"


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
                changelog="first",
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
                changelog="second",
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

    def test_read_apk_info_from_manifest(self) -> None:
        if not DCL_APK.is_file():
            self.skipTest("DclInstall.apk missing")
        info = read_apk_info(DCL_APK)
        self.assertEqual("vad.dashing.dclinstall", info.packageName)
        self.assertEqual(21, info.versionCode)
        self.assertEqual("1.20", info.versionName)
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
                category="tool",
            )
            upsert_meta(apps, meta)
            release = set_apk(apps, "dclinstall", DCL_APK)
            self.assertEqual(21, release.versionCode)
            self.assertEqual("1.20", release.versionName)
            self.assertTrue((apps / "dclinstall" / DCL_APK.name).is_file())

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

    def test_copy_apk_to_downloads_field(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            apps = Path(tmp) / "apps"
            apps.mkdir()
            apk = Path(tmp) / "install.apk"
            apk.write_bytes(b"dclinstall")
            meta = AppMeta(
                id="dclinstall",
                packageName="vad.dashing.dclinstall",
                title="DCL Install",
                hu=["a9"],
                category="tool",
                copyApkToDownloadsAppId="dcl-launcher",
            )
            add_app(
                apps,
                meta,
                apk_source=apk,
                version_code=1,
                version_name="1.0.0",
            )
            catalog = build_catalog(apps)
            self.assertEqual(
                "dcl-launcher",
                catalog["apps"][0]["copyApkToDownloadsAppId"],
            )

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


if __name__ == "__main__":
    unittest.main()
