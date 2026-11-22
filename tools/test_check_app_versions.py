#!/usr/bin/env python3
"""Version headers stay aligned with each app's versionName."""

from __future__ import annotations

import unittest

import check_app_versions
from app_releases import APPS, app_by_key, changelog_error


class RepoVersionsTest(unittest.TestCase):
    def test_every_app_changelog_matches_version_file(self) -> None:
        self.assertEqual([], check_app_versions.check())

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


if __name__ == "__main__":
    unittest.main()
