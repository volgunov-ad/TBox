#!/usr/bin/env python3
"""Release task names and APK file names, without invoking Gradle."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import build_app_release
from app_releases import apk_file_name, app_by_key, gradle_tasks


class ReleaseNamesTest(unittest.TestCase):
    def test_monitor_keeps_prefix_and_flavor_in_the_file_name(self) -> None:
        app = app_by_key("app")
        self.assertEqual(
            "tbox-monitor-1.2.1-ru-release.apk",
            apk_file_name(app, "1.2.1", "release", "ru"),
        )
        self.assertEqual(
            (":app:assembleRuRelease", ":app:assembleEnRelease"),
            gradle_tasks(app, "release", ("ru", "en")),
        )

    def test_other_apps_have_no_tbox_prefix(self) -> None:
        self.assertEqual(
            "store-0.1.1-en-release.apk",
            apk_file_name(app_by_key("store"), "0.1.1", "release", "en"),
        )
        self.assertEqual(
            "camd-0.9.0-release.apk",
            apk_file_name(app_by_key("camd"), "0.9.0", "release"),
        )
        self.assertEqual(
            (":fwunlock:assembleRelease",),
            gradle_tasks(app_by_key("fwunlock"), "release", ()),
        )

    def test_output_dir_inside_the_repo_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            build_app_release.output_dir_outside_repo(build_app_release.ROOT / "tools")

    def test_output_dir_outside_the_repo_is_accepted(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            resolved = build_app_release.output_dir_outside_repo(Path(tmp))
            self.assertTrue(resolved.is_absolute())

    def test_unflavored_app_rejects_a_flavor(self) -> None:
        with self.assertRaises(ValueError):
            build_app_release.resolve_flavors((), "ru")

    def test_store_defaults_to_both_flavors(self) -> None:
        self.assertEqual(
            ("ru", "en"),
            build_app_release.resolve_flavors(("ru", "en"), None),
        )


if __name__ == "__main__":
    unittest.main()
