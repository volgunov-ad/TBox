#!/usr/bin/env python3
"""Unit tests for osm_to_tboxroads pack fields and junction splits. Stdlib only."""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parent
TOOL = ROOT / "osm_to_tboxroads.py"
SAMPLE = ROOT / "samples" / "ru_moscow_demo.geojson"


def load_tool():
    spec = importlib.util.spec_from_file_location("osm_to_tboxroads", TOOL)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    sys.modules[spec.name] = mod
    spec.loader.exec_module(mod)
    return mod


class ParseMaxspeedTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.m = load_tool()

    def test_numeric_and_units(self) -> None:
        p = self.m.parse_maxspeed_kmh
        self.assertEqual(p("60"), 60)
        self.assertEqual(p(60), 60)
        self.assertEqual(p("60 km/h"), 60)
        self.assertEqual(p("30 mph"), 48)
        self.assertEqual(p("50 kmh"), 50)

    def test_ru_zone_codes(self) -> None:
        p = self.m.parse_maxspeed_kmh
        self.assertEqual(p("RU:urban"), 60)
        self.assertEqual(p("ru:rural"), 90)
        self.assertEqual(p("RU:motorway"), 110)

    def test_rejects_implicit_and_conditional(self) -> None:
        p = self.m.parse_maxspeed_kmh
        self.assertIsNone(p("signals"))
        self.assertIsNone(p("walk"))
        self.assertIsNone(p("none"))
        self.assertIsNone(p("DE:rural"))
        self.assertIsNone(p("60 @ (22:00-06:00)"))
        self.assertIsNone(p(""))
        self.assertIsNone(p(None))

    def test_motorway_class_defaults_to_110_unless_tagged(self) -> None:
        s = self.m.speed_fields_from_tags
        self.assertEqual(s({}, highway="motorway"), (110, None, None))
        self.assertEqual(s({"maxspeed": "90"}, highway="motorway"), (90, None, None))
        self.assertEqual(s({"maxspeed": "RU:urban"}, highway="motorway"), (60, None, None))
        self.assertEqual(s({}, highway="motorway_link"), (None, None, None))
        self.assertEqual(s({}, highway="trunk"), (None, None, None))


class JunctionSplitTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.m = load_tool()

    def test_demo_geojson_splits_shared_vertex_and_keeps_speed(self) -> None:
        edges = self.m.edges_from_geojson(SAMPLE, self.m.DEFAULT_HIGHWAY_CLASSES)
        self.assertEqual(len(edges), 5)
        primary = [e for e in edges if e["class"] == "primary"]
        secondary = [e for e in edges if e["class"] == "secondary"]
        residential = [e for e in edges if e["class"] == "residential"]
        self.assertEqual(len(primary), 2)
        self.assertEqual(len(secondary), 2)
        self.assertEqual(len(residential), 1)
        for e in primary:
            self.assertEqual(e.get("maxspeed"), 60)
            self.assertEqual(e.get("ref"), "A-101")
            self.assertEqual(e.get("wayId"), 1001)
        junction = [37.615, 55.760]
        primary_ends = {tuple(e["coords"][0]) for e in primary} | {
            tuple(e["coords"][-1]) for e in primary
        }
        self.assertIn(tuple(junction), primary_ends)

    def test_overpass_splits_on_shared_node_ids(self) -> None:
        payload = {
            "elements": [
                {
                    "type": "way",
                    "id": 10,
                    "nodes": [1, 2, 3],
                    "tags": {
                        "highway": "primary",
                        "maxspeed": "80",
                        "maxspeed:backward": "60",
                        "ref": "M-7",
                    },
                    "geometry": [
                        {"lat": 55.75, "lon": 37.60},
                        {"lat": 55.75, "lon": 37.61},
                        {"lat": 55.75, "lon": 37.62},
                    ],
                },
                {
                    "type": "way",
                    "id": 11,
                    "nodes": [4, 2, 5],
                    "tags": {"highway": "residential", "maxspeed": "RU:urban"},
                    "geometry": [
                        {"lat": 55.74, "lon": 37.61},
                        {"lat": 55.75, "lon": 37.61},
                        {"lat": 55.76, "lon": 37.61},
                    ],
                },
            ]
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overpass.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            edges = self.m.edges_from_overpass_json(path, self.m.DEFAULT_HIGHWAY_CLASSES)
        self.assertEqual(len(edges), 4)
        primary = [e for e in edges if e["class"] == "primary"]
        self.assertEqual(len(primary), 2)
        for e in primary:
            self.assertEqual(e.get("maxspeed"), 80)
            self.assertEqual(e.get("maxspeedBackward"), 60)
            self.assertEqual(e.get("ref"), "M-7")
            self.assertEqual(e.get("wayId"), 10)
        residential = [e for e in edges if e["class"] == "residential"]
        self.assertEqual(len(residential), 2)
        self.assertTrue(all(e.get("maxspeed") == 60 for e in residential))

    def test_does_not_split_shape_vertices_of_a_single_way(self) -> None:
        payload = {
            "elements": [
                {
                    "type": "way",
                    "id": 3,
                    "nodes": [1, 2, 3],
                    "tags": {"highway": "secondary"},
                    "geometry": [
                        {"lat": 55.0, "lon": 37.0},
                        {"lat": 55.0, "lon": 37.1},
                        {"lat": 55.0, "lon": 37.2},
                    ],
                }
            ]
        }
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "overpass.json"
            path.write_text(json.dumps(payload), encoding="utf-8")
            edges = self.m.edges_from_overpass_json(path, self.m.DEFAULT_HIGHWAY_CLASSES)
        self.assertEqual(len(edges), 1)
        self.assertEqual(len(edges[0]["coords"]), 3)


if __name__ == "__main__":
    unittest.main()
