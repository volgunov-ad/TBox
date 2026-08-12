#!/usr/bin/env python3
"""Convert GeoJSON road lines (or a synthetic grid) into a .tboxroads v1 pack.

Format: 8-byte magic TBOXRDS1 + gzip(UTF-8 JSON). See docs/TBOXROADS_FORMAT_RU.md.
Stdlib only (Python 3.9+).
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import struct
import sys
from pathlib import Path
from typing import Any, Iterable, List, Sequence, Tuple

MAGIC = b"TBOXRDS1"
FORMAT = 1

DEFAULT_HIGHWAY_CLASSES = frozenset(
    {
        "motorway",
        "motorway_link",
        "trunk",
        "trunk_link",
        "primary",
        "primary_link",
        "secondary",
        "secondary_link",
        "tertiary",
        "tertiary_link",
        "residential",
        "unclassified",
        "living_street",
    }
)


def haversine_m(lon1: float, lat1: float, lon2: float, lat2: float) -> float:
    r = 6371000.0
    p1 = math.radians(lat1)
    p2 = math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    return 2 * r * math.asin(min(1.0, math.sqrt(a)))


def polyline_length_m(coords: Sequence[Sequence[float]]) -> float:
    total = 0.0
    for i in range(1, len(coords)):
        lon1, lat1 = coords[i - 1][0], coords[i - 1][1]
        lon2, lat2 = coords[i][0], coords[i][1]
        total += haversine_m(lon1, lat1, lon2, lat2)
    return total


def bbox_of(coords_lists: Iterable[Sequence[Sequence[float]]]) -> List[float]:
    west = east = south = north = None
    for coords in coords_lists:
        for lon, lat in coords:
            west = lon if west is None else min(west, lon)
            east = lon if east is None else max(east, lon)
            south = lat if south is None else min(south, lat)
            north = lat if north is None else max(north, lat)
    if west is None:
        return [0.0, 0.0, 0.0, 0.0]
    return [west, south, east, north]


def write_pack(path: Path, payload: dict[str, Any]) -> int:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    compressed = gzip.compress(raw, compresslevel=9)
    data = MAGIC + compressed
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)
    return len(data)


def build_payload(
    region_id: str,
    graph_version: int,
    edges: List[dict[str, Any]],
    bbox: Sequence[float] | None = None,
) -> dict[str, Any]:
    if bbox is None:
        bbox = bbox_of(e["coords"] for e in edges)
    return {
        "format": FORMAT,
        "regionId": region_id,
        "graphVersion": graph_version,
        "bbox": [float(bbox[0]), float(bbox[1]), float(bbox[2]), float(bbox[3])],
        "edges": edges,
    }


def edges_from_geojson(path: Path, allowed: frozenset[str]) -> List[dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    features = root.get("features") or []
    edges: List[dict[str, Any]] = []
    next_node = 0
    edge_id = 1
    for feat in features:
        props = feat.get("properties") or {}
        highway = str(props.get("highway") or props.get("class") or "residential").strip()
        if highway not in allowed:
            continue
        geom = feat.get("geometry") or {}
        gtype = geom.get("type")
        raw_coords = geom.get("coordinates") or []
        lines: List[List[List[float]]] = []
        if gtype == "LineString":
            lines.append([[float(c[0]), float(c[1])] for c in raw_coords])
        elif gtype == "MultiLineString":
            for line in raw_coords:
                lines.append([[float(c[0]), float(c[1])] for c in line])
        else:
            continue
        for coords in lines:
            if len(coords) < 2:
                continue
            frm = next_node
            to = next_node + 1
            next_node += 2
            edges.append(
                {
                    "id": edge_id,
                    "class": highway,
                    "lengthM": round(polyline_length_m(coords), 3),
                    "from": frm,
                    "to": to,
                    "coords": coords,
                }
            )
            edge_id += 1
    return edges


def synthetic_grid(bbox: Sequence[float], step_deg: float = 0.05) -> List[dict[str, Any]]:
    """Small axis-aligned grid inside bbox for demos / tests."""
    west, south, east, north = [float(x) for x in bbox]
    if east <= west or north <= south:
        raise SystemExit("invalid bbox: need west<east and south<north")
    edges: List[dict[str, Any]] = []
    edge_id = 1
    next_node = 0
    # Horizontal lines
    lat = south
    while lat <= north + 1e-9:
        coords = [[west, lat], [east, lat]]
        edges.append(
            {
                "id": edge_id,
                "class": "secondary",
                "lengthM": round(polyline_length_m(coords), 3),
                "from": next_node,
                "to": next_node + 1,
                "coords": coords,
            }
        )
        edge_id += 1
        next_node += 2
        lat += step_deg
    # Vertical lines
    lon = west
    while lon <= east + 1e-9:
        coords = [[lon, south], [lon, north]]
        edges.append(
            {
                "id": edge_id,
                "class": "secondary",
                "lengthM": round(polyline_length_m(coords), 3),
                "from": next_node,
                "to": next_node + 1,
                "coords": coords,
            }
        )
        edge_id += 1
        next_node += 2
        lon += step_deg
    return edges


def parse_bbox(s: str) -> List[float]:
    parts = [p.strip() for p in s.split(",")]
    if len(parts) != 4:
        raise SystemExit("--bbox must be west,south,east,north")
    return [float(p) for p in parts]


def main(argv: Sequence[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Build .tboxroads v1 pack")
    src = p.add_mutually_exclusive_group(required=True)
    src.add_argument("--geojson", type=Path, help="Input GeoJSON with LineString roads")
    src.add_argument("--synthetic", action="store_true", help="Build synthetic grid from --bbox")
    p.add_argument("--region-id", required=True)
    p.add_argument("--graph-version", type=int, default=1)
    p.add_argument("--bbox", help="west,south,east,north (required for --synthetic; optional override)")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument(
        "--step-deg",
        type=float,
        default=0.05,
        help="Synthetic grid step in degrees (default 0.05)",
    )
    args = p.parse_args(argv)

    if args.synthetic:
        if not args.bbox:
            raise SystemExit("--synthetic requires --bbox")
        bbox = parse_bbox(args.bbox)
        edges = synthetic_grid(bbox, step_deg=args.step_deg)
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox)
    else:
        edges = edges_from_geojson(args.geojson, DEFAULT_HIGHWAY_CLASSES)
        if not edges:
            raise SystemExit("no edges extracted from GeoJSON (check highway classes)")
        bbox = parse_bbox(args.bbox) if args.bbox else None
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox)

    nbytes = write_pack(args.out, payload)
    print(
        f"wrote {args.out} ({nbytes} bytes, {len(payload['edges'])} edges, "
        f"bbox={payload['bbox']})",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
