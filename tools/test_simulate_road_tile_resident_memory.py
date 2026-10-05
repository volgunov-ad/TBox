#!/usr/bin/env python3
"""Unit tests for tools/simulate_road_tile_resident_memory.py (stdlib)."""

from __future__ import annotations

import gzip
import importlib.util
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

_TOOL = Path(__file__).resolve().parent / "simulate_road_tile_resident_memory.py"
_SPEC = importlib.util.spec_from_file_location("simulate_road_tile_resident_memory", _TOOL)
assert _SPEC is not None and _SPEC.loader is not None
sim = importlib.util.module_from_spec(_SPEC)
sys.modules[_SPEC.name] = sim
_SPEC.loader.exec_module(sim)


MAGIC = b"TBOXRDS1"


def pack_tile(region: str, edge_id: int, bbox: list[float]) -> bytes:
    lon = (bbox[0] + bbox[2]) / 2.0
    lat0 = (bbox[1] + bbox[3]) / 2.0 - 0.01
    lat1 = lat0 + 0.02
    payload = {
        "format": 1,
        "regionId": region,
        "graphVersion": 4,
        "bbox": bbox,
        "edges": [
            {
                "id": edge_id,
                "class": "primary",
                "lengthM": 100.0,
                "from": 0,
                "to": 1,
                "coords": [[lon, lat0], [lon, lat1]],
            }
        ],
    }
    raw = json.dumps(payload, separators=(",", ":")).encode("utf-8")
    return MAGIC + gzip.compress(raw)


class SimulateResidentMemoryTest(unittest.TestCase):
    def test_covering_and_design_ok(self) -> None:
        with tempfile.TemporaryDirectory() as td:
            zip_path = Path(td) / "ru-test-v4.tboxroads.zip"
            tiles = []
            encoded = []
            # 2x2 with overlap so the shared corner covers 4 tiles.
            for x in range(2):
                for y in range(2):
                    tid = f"{x:04d}_{y:04d}"
                    core_w, core_s = 37.0 + x * 0.1, 55.0 + y * 0.1
                    bbox = [core_w - 0.01, core_s - 0.01, core_w + 0.11, core_s + 0.11]
                    data = pack_tile("ru-test", x * 10 + y + 1, bbox)
                    file_name = f"tiles/{tid}.tboxroads"
                    encoded.append((file_name, data))
                    tiles.append(
                        {
                            "id": tid,
                            "file": file_name,
                            "bbox": bbox,
                            "bytes": len(data),
                            "edgeCount": 1,
                        }
                    )
            index = {
                "format": 1,
                "regionId": "ru-test",
                "graphVersion": 4,
                "bbox": [37.0, 55.0, 37.2, 55.2],
                "tileSizeDeg": 0.1,
                "overlapM": 150.0,
                "tiles": tiles,
            }
            with zipfile.ZipFile(zip_path, "w", compression=zipfile.ZIP_STORED) as zf:
                zf.writestr("index.json", json.dumps(index).encode("utf-8"))
                for name, data in encoded:
                    zf.writestr(name, data)

            summary = sim.summarize_bundle(sim.load_bundle(zip_path), step_deg=0.05, top_n=2)
            self.assertTrue(summary["design_ok"])
            self.assertLessEqual(summary["cover"]["max_covering"], 4)
            self.assertEqual(sim.main(["--zip", str(zip_path)]), 0)


if __name__ == "__main__":
    unittest.main()
