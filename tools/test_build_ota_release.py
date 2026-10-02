#!/usr/bin/env python3
"""Smoke tests for the OTA release Gradle invocation."""

from __future__ import annotations

import argparse
import json
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import build_ota_release as ota


class GradleJvmArgsTest(unittest.TestCase):
    def test_release_heap_is_large_enough_for_r8(self) -> None:
        self.assertIn("-Xmx8192m", ota.DEFAULT_GRADLE_JVM_ARGS)
        self.assertNotIn("-Xmx4096m", ota.DEFAULT_GRADLE_JVM_ARGS)

    def test_jvm_args_stay_one_gradle_argument(self) -> None:
        with mock.patch("build_ota_release.subprocess.run") as run, mock.patch(
            "build_ota_release.gradle_wrapper", return_value=Path("gradlew")
        ):
            ota.run_gradle(
                Path("."),
                ("assembleRuRelease", "assembleEnRelease"),
                ota.DEFAULT_GRADLE_JVM_ARGS,
            )
        command = run.call_args.args[0]
        self.assertEqual(
            command[1],
            "-Dorg.gradle.jvmargs=-Xmx8192m -Dfile.encoding=UTF-8",
        )
        self.assertEqual(
            command[2:],
            ["assembleRuRelease", "assembleEnRelease"],
        )


class FlavorSelectionTest(unittest.TestCase):
    def test_interactive_choices(self) -> None:
        with mock.patch("builtins.input", side_effect=["9", "1"]):
            self.assertEqual(ota.choose_flavors_interactive(), ("ru", "en"))
        with mock.patch("builtins.input", return_value="2"):
            self.assertEqual(ota.choose_flavors_interactive(), ("ru",))
        with mock.patch("builtins.input", return_value="3"):
            self.assertEqual(ota.choose_flavors_interactive(), ("en",))

    def test_cli_flavors(self) -> None:
        self.assertEqual(
            ota.resolve_flavors(argparse.Namespace(flavors="ru")),
            ("ru",),
        )
        self.assertEqual(
            ota.resolve_flavors(argparse.Namespace(flavors="en")),
            ("en",),
        )
        self.assertEqual(
            ota.resolve_flavors(argparse.Namespace(flavors="ru+en")),
            ("ru", "en"),
        )

    def test_gradle_tasks_follow_selected_flavors(self) -> None:
        self.assertEqual(
            ota.gradle_tasks_for("release", ("ru",)),
            ("assembleRuRelease",),
        )
        self.assertEqual(
            ota.gradle_tasks_for("release", ("en",)),
            ("assembleEnRelease",),
        )
        self.assertEqual(
            ota.gradle_tasks_for("debug", ("ru", "en")),
            ("assembleRuDebug", "assembleEnDebug"),
        )

    def test_partial_build_keeps_other_flavor_in_version_json(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            destination = Path(tmp)
            (destination / "version.json").write_text(
                json.dumps(
                    {
                        "schemaVersion": 1,
                        "releases": [
                            {"flavor": "ru", "apkFileName": "old-ru.apk"},
                            {"flavor": "en", "apkFileName": "old-en.apk"},
                        ],
                    }
                ),
                encoding="utf-8",
            )
            merged = ota.merge_version_manifest(
                destination,
                {
                    "schemaVersion": 1,
                    "releases": [{"flavor": "ru", "apkFileName": "new-ru.apk"}],
                },
                ("ru",),
            )
            self.assertEqual(
                [item["apkFileName"] for item in merged["releases"]],
                ["new-ru.apk", "old-en.apk"],
            )


if __name__ == "__main__":
    unittest.main()
