#!/usr/bin/env python3
"""Unit tests for tools/skdf_speed_limits.py. Stdlib only, no network."""

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

import skdf_speed_limits as skdf


def east_line(length_m: float, lat: float = 0.0) -> list[list[float]]:
    # 1 deg lon at equator ≈ 111320 m
    return [[0.0, lat], [length_m / 111320.0, lat]]


class ParseTest(unittest.TestCase):
    def test_chainage(self) -> None:
        self.assertEqual(skdf.parse_chainage_m("0+000"), 0.0)
        self.assertEqual(skdf.parse_chainage_m("12+345"), 12345.0)
        self.assertEqual(skdf.parse_chainage_m(305), 305000.0)
        self.assertEqual(skdf.parse_chainage_m(340905), 340905.0)
        self.assertIsNone(skdf.parse_chainage_m("nope"))

    def test_speed(self) -> None:
        self.assertEqual(skdf.parse_speed_kmh(90), 90)
        self.assertEqual(skdf.parse_speed_kmh({"name": 110}), 110)
        self.assertIsNone(skdf.parse_speed_kmh(600))
        self.assertIsNone(skdf.parse_speed_kmh(None))

    def test_refs(self) -> None:
        self.assertEqual(skdf.normalize_ref("М-7"), "M-7")
        self.assertEqual(skdf.normalize_ref("Р-158"), "R-158")
        self.assertEqual(skdf.extract_head_ref('М-7 "Волга" Москва'), "M-7")
        self.assertIsNone(skdf.extract_head_ref('Подъезд к с. Криуши от а/д М-7 "Волга"'))
        self.assertTrue(skdf.ref_keys("M-7; E22") & skdf.ref_keys("М-7"))


class CrsInterpTest(unittest.TestCase):
    def test_mercator_moscow(self) -> None:
        lon, lat = skdf.mercator_to_wgs84(4187590.0, 7514065.0)
        self.assertAlmostEqual(lon, 37.62, delta=0.05)
        self.assertAlmostEqual(lat, 55.75, delta=0.05)

    def test_interpolate_and_reverse(self) -> None:
        line = east_line(1000)
        mid = skdf.point_along(line, 500)
        self.assertAlmostEqual(skdf.haversine_m(line[0][0], line[0][1], mid[0], mid[1]), 500.0, delta=2.0)
        rev = skdf.reverse_line(line)
        self.assertEqual(rev[0], line[-1])
        self.assertEqual(rev[-1], line[0])
        sl = skdf.slice_line(line, 200, 800)
        self.assertGreaterEqual(len(sl), 2)
        self.assertAlmostEqual(skdf.polyline_length_m(sl), 600.0, delta=2.0)


class Split200Test(unittest.TestCase):
    def test_both_pieces_at_least_200m(self) -> None:
        coords = east_line(1000)
        pieces, rejected = skdf.split_edge_by_zones(coords, [(0.0, 400.0, 90), (400.0, 1000.0, 60)])
        self.assertEqual(rejected, 0)
        self.assertEqual(len(pieces), 2)
        self.assertEqual([p[1] for p in pieces], [90, 60])
        self.assertGreaterEqual(skdf.polyline_length_m(pieces[0][0]), 199.0)
        self.assertGreaterEqual(skdf.polyline_length_m(pieces[1][0]), 199.0)

    def test_reject_short_piece(self) -> None:
        coords = east_line(1000)
        pieces, rejected = skdf.split_edge_by_zones(coords, [(0.0, 80.0, 40), (80.0, 1000.0, 90)])
        self.assertGreaterEqual(rejected, 1)
        self.assertEqual(len(pieces), 1)
        self.assertEqual(pieces[0][1], 90)

    def test_absorb_short_tail(self) -> None:
        coords = east_line(1000)
        pieces, rejected = skdf.split_edge_by_zones(coords, [(0.0, 850.0, 90), (850.0, 1000.0, 40)])
        self.assertGreaterEqual(rejected, 1)
        self.assertEqual(len(pieces), 1)
        self.assertEqual(pieces[0][1], 90)

    def test_short_edge_not_split_picks_min_on_tie(self) -> None:
        coords = east_line(150)
        pieces, rejected = skdf.split_edge_by_zones(
            coords, [(0.0, 75.0, 90), (75.0, 150.0, 60)]
        )
        self.assertEqual(len(pieces), 1)
        self.assertEqual(pieces[0][1], 60)
        self.assertGreaterEqual(rejected, 1)


class OverlayMatchTest(unittest.TestCase):
    def test_long_official_line_matches_short_osm_subsegment(self) -> None:
        official = east_line(5000)
        piece = skdf.slice_line(official, 2000, 2600)
        edges = [
            {
                "id": 1,
                "class": "trunk",
                "coords": piece,
                "ref": "M-7",
                "wayId": 7,
                "lengthM": skdf.polyline_length_m(piece),
            }
        ]
        snapshot = {
            "intervals": [{"ref": "M-7", "speed": 110, "coords": official}],
            "quality": {"nonmonotonic_parts": [], "max_km_post_error_m": 1.0},
        }
        out, report = skdf.apply_overlay(edges, snapshot)
        self.assertEqual(report["matched_intervals"], 1)
        self.assertEqual(out[0]["maxspeed"], 110)

    def test_skdf_overrides_osm_and_keeps_way_id(self) -> None:
        line = east_line(800)
        edges = [
            {
                "id": 1,
                "class": "trunk",
                "lengthM": 800.0,
                "from": 0,
                "to": 1,
                "coords": line,
                "maxspeed": 90,
                "ref": "M-7",
                "wayId": 42,
            }
        ]
        snapshot = {
            "intervals": [
                {"ref": "M-7", "speed": 110, "coords": east_line(800), "road_name": "М-7"}
            ],
            "quality": {"nonmonotonic_parts": [], "max_km_post_error_m": 5.0},
        }
        out, report = skdf.apply_overlay(edges, snapshot)
        self.assertEqual(len(out), 1)
        self.assertEqual(out[0]["maxspeed"], 110)
        self.assertEqual(out[0]["maxspeedForward"], 110)
        self.assertEqual(out[0]["maxspeedBackward"], 110)
        self.assertEqual(out[0]["wayId"], 42)
        self.assertEqual(report["matched_intervals"], 1)
        self.assertGreaterEqual(report["disagreements_osm_skdf"], 1)

    def test_ambiguous_same_heading_parallel_skipped(self) -> None:
        a = east_line(800)
        b = [[p[0], p[1] + 0.00015] for p in a]  # ~17 m north
        edges = [
            {"id": 1, "class": "trunk", "coords": a, "ref": "M-7", "wayId": 1, "lengthM": 800},
            {"id": 2, "class": "trunk", "coords": b, "ref": "M-7", "wayId": 2, "lengthM": 800},
        ]
        snapshot = {
            "intervals": [{"ref": "M-7", "speed": 110, "coords": a}],
            "quality": {"nonmonotonic_parts": [], "max_km_post_error_m": 1.0},
        }
        out, report = skdf.apply_overlay(edges, snapshot)
        self.assertEqual(report["matched_intervals"], 1)
        speeds = {e["wayId"]: e.get("maxspeed") for e in out}
        self.assertEqual(speeds[1], 110)
        self.assertIsNone(speeds[2])

    def test_dual_carriageway_opposite_heading_both_match(self) -> None:
        fwd = east_line(800)
        rev = skdf.reverse_line(fwd)
        rev = [[p[0], p[1] + 0.00012] for p in rev]
        edges = [
            {"id": 1, "class": "trunk", "coords": fwd, "ref": "M-7", "wayId": 10, "lengthM": 800, "oneway": 1},
            {"id": 2, "class": "trunk", "coords": rev, "ref": "M-7", "wayId": 11, "lengthM": 800, "oneway": 1},
        ]
        snapshot = {
            "intervals": [{"ref": "M-7", "speed": 110, "coords": fwd}],
            "quality": {"nonmonotonic_parts": [], "max_km_post_error_m": 1.0},
        }
        out, report = skdf.apply_overlay(edges, snapshot)
        self.assertEqual(report["matched_intervals"], 1)
        speeds = {e["wayId"]: e.get("maxspeed") for e in out}
        self.assertEqual(speeds[10], 110)
        self.assertEqual(speeds[11], 110)

    def test_snapshot_without_network_and_unmatched_keeps_osm(self) -> None:
        edges = [
            {
                "id": 1,
                "class": "residential",
                "coords": east_line(400),
                "maxspeed": 60,
                "ref": "22K-9999",
                "wayId": 9,
                "lengthM": 400,
            }
        ]
        snapshot = {
            "intervals": [{"ref": "M-7", "speed": 110, "coords": east_line(800)}],
            "quality": {"nonmonotonic_parts": [], "max_km_post_error_m": 1.0},
        }
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "snap.json"
            skdf.save_snapshot(path, snapshot)
            loaded = skdf.load_snapshot(path)
        out, report = skdf.apply_overlay(edges, loaded)
        self.assertEqual(out[0]["maxspeed"], 60)
        self.assertEqual(report["matched_intervals"], 0)

    def test_quality_fail_km_post_error(self) -> None:
        report = {
            "nonmonotonic_parts": [],
            "max_km_post_error_m": 500.0,
            "ref_intervals": 0,
            "ref_match_ratio": 1.0,
        }
        with self.assertRaises(skdf.SkdfError):
            skdf.raise_if_quality_failed(report)

    def test_token_file(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / "token"
            path.write_text("abc123\n", encoding="utf-8")
            tok = skdf.load_token(path)
            self.assertEqual(tok, "abc123")


class FilterCutsTest(unittest.TestCase):
    def test_cuts_must_leave_200m_ends(self) -> None:
        self.assertEqual(skdf.filter_cuts(1000, [200, 500, 800]), [200, 500, 800])
        self.assertEqual(skdf.filter_cuts(1000, [50, 500]), [500])
        self.assertEqual(skdf.filter_cuts(150, [75]), [])


if __name__ == "__main__":
    unittest.main()
