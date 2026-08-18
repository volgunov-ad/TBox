#!/usr/bin/env python3
"""Smoke tests for build_road_map_packs batch / retry logic (stdlib unittest)."""

from __future__ import annotations

import tempfile
import unittest
from pathlib import Path
from unittest import mock

import build_road_map_packs as bmp


class ResolveIdsTest(unittest.TestCase):
    def test_fetch_all_preserves_catalog_order(self) -> None:
        ids = bmp.resolve_region_ids([], fetch_all=True)
        self.assertEqual(ids[0], "ru-adygea")
        self.assertIn("by-minsk", ids)
        self.assertEqual(len(ids), len(bmp.REGIONS))

    def test_dedupe_and_unknown(self) -> None:
        ids = bmp.resolve_region_ids(
            ["ru-adygea", "ru-adygea", "by-brest"],
            fetch_all=False,
        )
        self.assertEqual(ids, ["ru-adygea", "by-brest"])
        with self.assertRaises(SystemExit):
            bmp.resolve_region_ids(["no-such"], fetch_all=False)


class BuildPassesTest(unittest.TestCase):
    def test_second_pass_retries_failures(self) -> None:
        by_id = {r["id"]: r for r in bmp.REGIONS}
        region_ids = ["ru-adygea", "ru-altai-republic"]
        attempts: list[str] = []

        def fake_build(region, **_kwargs):
            attempts.append(region["id"])
            # Fail adygea on first attempt only.
            if region["id"] == "ru-adygea" and attempts.count("ru-adygea") == 1:
                raise bmp.FetchError("boom")
            return Path("/tmp/fake.zip")

        with tempfile.TemporaryDirectory() as td:
            maps_dir = Path(td)
            with mock.patch.object(bmp, "build_one_region", side_effect=fake_build):
                with mock.patch.object(bmp.time, "sleep"):
                    ok, failed, errors = bmp.run_build_passes(
                        region_ids,
                        by_id=by_id,
                        maps_dir=maps_dir,
                        graph_version=4,
                        tile_deg=0.1,
                        overlap_m=150.0,
                        passes=2,
                        interval_s=1.0,
                        retry_interval_s=2.0,
                        skip_existing=False,
                    )

        self.assertEqual(ok, ["ru-altai-republic", "ru-adygea"])
        self.assertEqual(failed, [])
        self.assertEqual(errors, {})
        self.assertEqual(attempts, ["ru-adygea", "ru-altai-republic", "ru-adygea"])

    def test_skip_existing(self) -> None:
        by_id = {r["id"]: r for r in bmp.REGIONS}
        with tempfile.TemporaryDirectory() as td:
            maps_dir = Path(td)
            existing = maps_dir / "ru-adygea-v4.tboxroads.zip"
            existing.write_bytes(b"x")
            with mock.patch.object(bmp, "build_one_region") as build:
                with mock.patch.object(bmp.time, "sleep"):
                    ok, failed, _errors = bmp.run_build_passes(
                        ["ru-adygea"],
                        by_id=by_id,
                        maps_dir=maps_dir,
                        graph_version=4,
                        tile_deg=0.1,
                        overlap_m=150.0,
                        passes=2,
                        interval_s=0,
                        retry_interval_s=0,
                        skip_existing=True,
                    )
                build.assert_not_called()
            self.assertEqual(ok, ["ru-adygea"])
            self.assertEqual(failed, [])


if __name__ == "__main__":
    unittest.main()
