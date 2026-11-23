#!/usr/bin/env python3
"""Version headers stay aligned with each app's versionName."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

import check_app_versions
from app_releases import (
    APPS,
    ROOT,
    app_by_key,
    application_modules,
    changelog_error,
    module_catalog_errors,
    version_bump_error,
)


class RepoVersionsTest(unittest.TestCase):
    def test_every_app_changelog_matches_version_file(self) -> None:
        self.assertEqual([], check_app_versions.check())

    def test_head_is_not_behind_itself(self) -> None:
        self.assertEqual([], check_app_versions.check(base_ref="HEAD"))

    def test_catalog_lists_every_application_module(self) -> None:
        listed = sorted(app.gradle_module for app in APPS if app.gradle_module)
        self.assertEqual(listed, sorted(application_modules(ROOT)))
        self.assertNotIn("adbcore", application_modules(ROOT))

    def test_monitor_keeps_tbox_prefix_and_others_do_not(self) -> None:
        self.assertEqual("tbox-monitor", app_by_key("app").slug)
        for app in APPS:
            if app.key == "app":
                continue
            self.assertFalse(app.slug.startswith("tbox-"), app.key)


class ChangelogHeaderTest(unittest.TestCase):
    def test_top_header_must_be_the_version_name(self) -> None:
        app = app_by_key("fwunlock")
        self.assertIsNone(
            changelog_error(app, 1, "0.1.0", "# Changelog\n\n0.1.0\n1. Первая сборка.\n"),
        )
        self.assertIn(
            "starts with '0.1.0'",
            changelog_error(
                app,
                2,
                "0.2.0",
                "0.1.0\n1. Старое.\n\n0.2.0\n1. Новое.\n",
            )
            or "",
        )

    def test_version_code_must_be_positive(self) -> None:
        app = app_by_key("camview")
        message = changelog_error(app, 0, "0.1.0", "0.1.0\n1. Тест.\n")
        self.assertIn("versionCode", message or "")


class VersionBumpTest(unittest.TestCase):
    app = app_by_key("mqtt")

    def test_new_name_with_higher_code_passes(self) -> None:
        self.assertIsNone(version_bump_error(self.app, (3, "0.1.2"), (4, "0.1.3"), "base"))

    def test_unchanged_version_passes(self) -> None:
        self.assertIsNone(version_bump_error(self.app, (3, "0.1.2"), (3, "0.1.2"), "base"))

    def test_new_name_without_code_bump_fails(self) -> None:
        message = version_bump_error(self.app, (3, "0.1.2"), (3, "0.1.3"), "base")
        self.assertIn("needs versionCode above 3", message or "")

    def test_lower_code_fails(self) -> None:
        message = version_bump_error(self.app, (5, "0.2.0"), (4, "0.2.0"), "base")
        self.assertIn("lower than 5", message or "")


class ModuleCatalogTest(unittest.TestCase):
    def _repo(self, settings: str, modules: dict[str, str]) -> Path:
        root = Path(tempfile.mkdtemp())
        (root / "settings.gradle.kts").write_text(settings, encoding="utf-8")
        for name, text in modules.items():
            (root / name).mkdir()
            (root / name / "build.gradle.kts").write_text(text, encoding="utf-8")
        return root

    def test_unknown_application_module_is_reported(self) -> None:
        root = self._repo(
            'include(":newapp")\ninclude(":lib")\n',
            {
                "newapp": "plugins {\n    alias(libs.plugins.android.application)\n}\n",
                "lib": "plugins {\n    alias(libs.plugins.android.library)\n}\n",
            },
        )
        errors = module_catalog_errors(root)
        self.assertTrue(any(":newapp builds an APK" in e for e in errors), errors)
        self.assertFalse(any(":lib" in e for e in errors), errors)

    def test_wrong_slug_is_reported(self) -> None:
        root = self._repo(
            'include(":fwunlock")\n',
            {
                "fwunlock": (
                    "plugins {\n    alias(libs.plugins.android.application)\n}\n"
                    'extra["apkSlug"] = "tbox-fwunlock"\n'
                    'apply(from = rootProject.file("gradle/apk-release.gradle"))\n'
                ),
            },
        )
        errors = module_catalog_errors(root)
        self.assertTrue(any("apkSlug" in e and "tbox-fwunlock" in e for e in errors), errors)


if __name__ == "__main__":
    unittest.main()
