#!/usr/bin/env python3
"""Smoke tests for the OTA release Gradle invocation."""

from __future__ import annotations

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


if __name__ == "__main__":
    unittest.main()
