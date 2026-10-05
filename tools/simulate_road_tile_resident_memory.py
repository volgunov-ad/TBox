#!/usr/bin/env python3
"""Simulate resident road-tile memory for published v4 bundles.

Mirrors app invariants from RoadMatchRuntime.loadInstalledGraphs /
RoadMapBundle.covering: only tiles whose bbox contains the pose enter RAM;
at most one covering set per installed region (typically 1–4 tiles with
0.1° cores and 150 m overlap).

Does not modify production code. Uses stdlib only.

Examples:
  python tools/simulate_road_tile_resident_memory.py \\
    --zip /tmp/map_packs/ru-moscow-v4.tboxroads.zip

  python tools/simulate_road_tile_resident_memory.py \\
    --zip /tmp/map_packs/ru-moscow-v4.tboxroads.zip \\
    --zip /tmp/map_packs/ru-moscow-oblast-v4.tboxroads.zip \\
    --drive-lat 55.75 --drive-lon 37.62 --drive-km 30
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any


MAGIC = b"TBOXRDS1"


@dataclass
class Tile:
    region_id: str
    tile_id: str
    file: str
    bbox: list[float]
    packed_bytes: int
    edge_count: int


@dataclass
class Bundle:
    path: Path
    region_id: str
    bbox: list[float]
    tile_size_deg: float | None
    overlap_m: float | None
    tiles: list[Tile]
    index_bytes: int


def load_bundle(path: Path) -> Bundle:
    with zipfile.ZipFile(path) as zf:
        raw = zf.read("index.json")
        index = json.loads(raw)
        tiles = [
            Tile(
                region_id=str(index["regionId"]),
                tile_id=str(t["id"]),
                file=str(t["file"]),
                bbox=[float(x) for x in t["bbox"][:4]],
                packed_bytes=int(t.get("bytes") or 0),
                edge_count=int(t.get("edgeCount") or 0),
            )
            for t in index.get("tiles") or []
        ]
        return Bundle(
            path=path,
            region_id=str(index["regionId"]),
            bbox=[float(x) for x in index["bbox"][:4]],
            tile_size_deg=float(index["tileSizeDeg"]) if "tileSizeDeg" in index else None,
            overlap_m=float(index["overlapM"]) if "overlapM" in index else None,
            tiles=tiles,
            index_bytes=len(raw),
        )


def contains(bbox: list[float], lat: float, lon: float) -> bool:
    return bbox[0] <= lon <= bbox[2] and bbox[1] <= lat <= bbox[3]


def covering(bundle: Bundle, lat: float, lon: float) -> list[Tile]:
    if not contains(bundle.bbox, lat, lon):
        return []
    return [t for t in bundle.tiles if contains(t.bbox, lat, lon)]


def decode_stats(zf: zipfile.ZipFile, tile: Tile) -> dict[str, float | int]:
    data = zf.read(tile.file)
    if data[:8] != MAGIC:
        raise ValueError(f"bad magic in {tile.file}")
    raw = gzip.decompress(data[8:])
    payload = json.loads(raw)
    edges = payload.get("edges") or []
    n_pts = sum(len(e.get("coords") or []) for e in edges)
    # Rough Dalvik/ART heap proxy for RoadEdge + DoubleArray + maps/adjacency.
    est_java_mb = (len(edges) * 450 + n_pts * 20 + len(raw) * 0.6) / 1e6
    return {
        "json_mb": len(raw) / 1e6,
        "edges": len(edges),
        "points": n_pts,
        "est_java_mb": est_java_mb,
    }


def grid_cover_stats(bundle: Bundle, step_deg: float) -> dict[str, Any]:
    west, south, east, north = bundle.bbox
    hist: dict[int, int] = {}
    max_n = 0
    worst_packed = 0
    worst_pt: tuple[float, float] | None = None
    samples = 0
    lat = south
    while lat <= north + 1e-12:
        lon = west
        while lon <= east + 1e-12:
            cov = covering(bundle, lat, lon)
            n = len(cov)
            hist[n] = hist.get(n, 0) + 1
            max_n = max(max_n, n)
            packed = sum(t.packed_bytes for t in cov)
            if packed > worst_packed:
                worst_packed = packed
                worst_pt = (lat, lon)
            samples += 1
            lon += step_deg
        lat += step_deg
    return {
        "samples": samples,
        "max_covering": max_n,
        "hist": dict(sorted(hist.items())),
        "worst_packed_mb": worst_packed / 1e6,
        "worst_point": worst_pt,
    }


def drive_path(
    bundles: list[Bundle],
    lat0: float,
    lon0: float,
    heading_deg: float,
    distance_km: float,
    step_m: float,
) -> dict[str, Any]:
    """Walk a straight DR path; report max resident tiles / estimated heap."""
    m_per_deg_lat = 111_320.0
    m_per_deg_lon = 111_320.0 * max(0.2, math.cos(math.radians(lat0)))
    rad = math.radians(heading_deg)
    dlat = (math.cos(rad) * step_m) / m_per_deg_lat
    dlon = (math.sin(rad) * step_m) / m_per_deg_lon
    steps = max(1, int(distance_km * 1000 / step_m))
    lat, lon = lat0, lon0
    max_tiles = 0
    max_packed = 0
    max_est = 0.0
    open_zips = {b.path: zipfile.ZipFile(b.path) for b in bundles}
    try:
        decode_cache: dict[tuple[str, str], dict[str, float | int]] = {}
        for _ in range(steps):
            resident: list[Tile] = []
            for b in bundles:
                resident.extend(covering(b, lat, lon))
            max_tiles = max(max_tiles, len(resident))
            packed = sum(t.packed_bytes for t in resident)
            max_packed = max(max_packed, packed)
            est = 0.0
            for t in resident:
                key = (t.region_id, t.tile_id)
                if key not in decode_cache:
                    zf = open_zips[next(b.path for b in bundles if b.region_id == t.region_id)]
                    decode_cache[key] = decode_stats(zf, t)
                est += float(decode_cache[key]["est_java_mb"])
            max_est = max(max_est, est)
            lat += dlat
            lon += dlon
    finally:
        for zf in open_zips.values():
            zf.close()
    return {
        "steps": steps,
        "max_resident_tiles": max_tiles,
        "max_packed_mb": max_packed / 1e6,
        "max_est_java_mb": max_est,
    }


def summarize_bundle(bundle: Bundle, step_deg: float, top_n: int) -> dict[str, Any]:
    with zipfile.ZipFile(bundle.path) as zf:
        ranked = sorted(bundle.tiles, key=lambda t: t.packed_bytes, reverse=True)
        top = []
        for t in ranked[:top_n]:
            stats = decode_stats(zf, t)
            top.append(
                {
                    "id": t.tile_id,
                    "packed_mb": t.packed_bytes / 1e6,
                    "edge_count": t.edge_count,
                    **stats,
                }
            )
    cover = grid_cover_stats(bundle, step_deg)
    return {
        "path": str(bundle.path),
        "regionId": bundle.region_id,
        "tiles": len(bundle.tiles),
        "index_mb": bundle.index_bytes / 1e6,
        "tileSizeDeg": bundle.tile_size_deg,
        "overlapM": bundle.overlap_m,
        "bbox": bundle.bbox,
        "top_tiles": top,
        "cover": cover,
        "design_ok": cover["max_covering"] <= 4 and bundle.index_bytes <= 4 * 1024 * 1024,
    }


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--zip", action="append", type=Path, required=True, help="v4 .tboxroads.zip")
    ap.add_argument("--step-deg", type=float, default=0.05, help="grid step for covering scan")
    ap.add_argument("--top", type=int, default=5, help="decode this many densest tiles")
    ap.add_argument("--drive-lat", type=float, default=None)
    ap.add_argument("--drive-lon", type=float, default=None)
    ap.add_argument("--drive-heading", type=float, default=90.0)
    ap.add_argument("--drive-km", type=float, default=20.0)
    ap.add_argument("--drive-step-m", type=float, default=50.0)
    ap.add_argument("--json-out", type=Path, default=None)
    args = ap.parse_args(argv)

    bundles = [load_bundle(p) for p in args.zip]
    report: dict[str, Any] = {"bundles": [], "multi_region": None, "drive": None}
    all_ok = True
    for b in bundles:
        summary = summarize_bundle(b, args.step_deg, args.top)
        report["bundles"].append(summary)
        all_ok = all_ok and bool(summary["design_ok"])
        print(f"\n=== {b.path.name} ({b.region_id}) ===")
        print(
            f"tiles={summary['tiles']} index={summary['index_mb']:.2f}MB "
            f"tileSizeDeg={summary['tileSizeDeg']} overlapM={summary['overlapM']}"
        )
        print(f"covering hist={summary['cover']['hist']} max={summary['cover']['max_covering']}")
        print(
            f"worst packed covering={summary['cover']['worst_packed_mb']:.2f}MB "
            f"at {summary['cover']['worst_point']}"
        )
        for t in summary["top_tiles"]:
            print(
                f"  top {t['id']}: packed={t['packed_mb']:.2f}MB json={t['json_mb']:.2f}MB "
                f"edges={t['edges']} estJava~{t['est_java_mb']:.1f}MB"
            )
        print("design_ok", summary["design_ok"])

    if len(bundles) > 1:
        # Sample intersection of region bboxes.
        west = max(b.bbox[0] for b in bundles)
        south = max(b.bbox[1] for b in bundles)
        east = min(b.bbox[2] for b in bundles)
        north = min(b.bbox[3] for b in bundles)
        multi = {"overlap_bbox": [west, south, east, north], "max_resident": 0, "samples": 0}
        if west < east and south < north:
            lat = south
            max_n = 0
            samples = 0
            while lat <= north:
                lon = west
                while lon <= east:
                    n = sum(len(covering(b, lat, lon)) for b in bundles)
                    max_n = max(max_n, n)
                    samples += 1
                    lon += args.step_deg
                lat += args.step_deg
            multi["max_resident"] = max_n
            multi["samples"] = samples
            print(f"\n=== multi-region overlap === max_resident_tiles={max_n} samples={samples}")
            # Soft warning only: two regions can legitimately stack up to ~8.
            if max_n > 8:
                all_ok = False
                print("FAIL: multi-region resident tiles > 8")
        report["multi_region"] = multi

    if args.drive_lat is not None and args.drive_lon is not None:
        drive = drive_path(
            bundles,
            args.drive_lat,
            args.drive_lon,
            args.drive_heading,
            args.drive_km,
            args.drive_step_m,
        )
        report["drive"] = drive
        print(
            f"\n=== drive {args.drive_km} km @ {args.drive_heading}° from "
            f"{args.drive_lat},{args.drive_lon} ==="
        )
        print(
            f"max_resident_tiles={drive['max_resident_tiles']} "
            f"max_packed={drive['max_packed_mb']:.2f}MB "
            f"max_est_java={drive['max_est_java_mb']:.1f}MB"
        )
        if drive["max_resident_tiles"] > 8:
            all_ok = False
            print("FAIL: drive path resident tiles > 8")
        if drive["max_est_java_mb"] > 64:
            # Soft fail: single-process HU heap for graphs alone looks unsafe.
            all_ok = False
            print("FAIL: estimated graph heap > 64 MB along drive")

    if args.json_out:
        args.json_out.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
        print("wrote", args.json_out)

    print("\nOVERALL", "OK" if all_ok else "FAIL")
    return 0 if all_ok else 1


if __name__ == "__main__":
    sys.exit(main())
