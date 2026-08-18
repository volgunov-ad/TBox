#!/usr/bin/env python3
"""Build whole-region packs and prepare Yandex Disk release/maps.

The user's synchronized OTA root already contains release/. This script writes:
  <output-base>/release/maps/catalog.json
  <output-base>/release/maps/{region-id}-vN.tboxroads.zip

The APK keeps a fallback catalog with all RU/BY regions but no download URLs.
The app refreshes /maps/catalog.json from the same public Yandex Disk share.
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import subprocess
import sys
import tempfile
import time
import zipfile
from pathlib import Path

from road_map_regions import REGIONS

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "osm_to_tboxroads.py"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "road_maps"
CATALOG = ASSETS / "catalog.json"
DEFAULT_OUTPUT_BASE = Path(r"C:\Users\volgu\AndroidStudioProjects\TBM")
PACK_MAGIC = b"TBOXRDS1"
BUNDLE_INDEX = "index.json"
DEFAULT_TILE_DEG = 0.10
DEFAULT_OVERLAP_M = 150.0
DEFAULT_INTERVAL_S = 30.0
DEFAULT_RETRY_INTERVAL_S = 120.0
DEFAULT_PASSES = 2
OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]


class FetchError(RuntimeError):
    """Overpass / osm_to_tboxroads failed for a region after all endpoints."""


def run_fetch(region: dict, out: Path, graph_version: int) -> None:
    last_err: Exception | None = None
    for endpoint in OVERPASS_ENDPOINTS:
        cmd = [
            sys.executable,
            str(TOOL),
            "--region-id",
            region["id"],
            "--graph-version",
            str(graph_version),
        ]
        relation_id = int(region.get("osm_relation_id") or 0)
        if relation_id > 0:
            cmd.extend(["--fetch-overpass-relation", str(relation_id)])
        else:
            cmd.extend(
                [
                    "--fetch-overpass-area",
                    region["osm_name"],
                    "--country-code",
                    region["country"],
                ]
            )
        cmd.extend(["--overpass-endpoint", endpoint, "--out", str(out)])
        print("+", " ".join(cmd), flush=True)
        try:
            subprocess.check_call(cmd)
            return
        except subprocess.CalledProcessError as e:
            last_err = e
            print(f"warn: fetch failed via {endpoint}", file=sys.stderr, flush=True)
    raise FetchError(f"fetch failed for {region['id']}: {last_err}")


def read_pack(path: Path) -> dict:
    data = path.read_bytes()
    if data[:8] != PACK_MAGIC:
        raise ValueError(f"Bad .tboxroads magic: {path}")
    return json.loads(gzip.decompress(data[8:]).decode("utf-8"))


def pack_bytes(payload: dict) -> bytes:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return PACK_MAGIC + gzip.compress(raw, compresslevel=9)


def tile_whole_pack(
    whole_pack: Path,
    bundle: Path,
    *,
    tile_deg: float,
    overlap_m: float,
) -> None:
    root = read_pack(whole_pack)
    bbox = [float(x) for x in root["bbox"][:4]]
    west, south, east, north = bbox
    overlap_lat = overlap_m / 111_320.0
    mid_lat = (south + north) / 2.0
    overlap_lon = overlap_m / max(1.0, 111_320.0 * math.cos(math.radians(mid_lat)))
    tile_count_x = max(1, int(math.ceil((east - west) / tile_deg)))
    tile_count_y = max(1, int(math.ceil((north - south) / tile_deg)))
    buckets: dict[tuple[int, int], list[dict]] = {}

    for edge in root.get("edges") or []:
        coords = edge.get("coords") or []
        if len(coords) < 2:
            continue
        edge_w = min(float(p[0]) for p in coords) - overlap_lon
        edge_e = max(float(p[0]) for p in coords) + overlap_lon
        edge_s = min(float(p[1]) for p in coords) - overlap_lat
        edge_n = max(float(p[1]) for p in coords) + overlap_lat
        x0 = min(tile_count_x - 1, max(0, int(math.floor((edge_w - west) / tile_deg))))
        x1 = min(tile_count_x - 1, max(0, int(math.floor((edge_e - west) / tile_deg))))
        y0 = min(tile_count_y - 1, max(0, int(math.floor((edge_s - south) / tile_deg))))
        y1 = min(tile_count_y - 1, max(0, int(math.floor((edge_n - south) / tile_deg))))
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                buckets.setdefault((x, y), []).append(edge)

    tiles: list[dict] = []
    encoded: list[tuple[str, bytes]] = []
    for (x, y), edges in sorted(buckets.items()):
        core_w = west + x * tile_deg
        core_s = south + y * tile_deg
        core_e = min(east, core_w + tile_deg)
        core_n = min(north, core_s + tile_deg)
        tile_bbox = [
            max(west, core_w - overlap_lon),
            max(south, core_s - overlap_lat),
            min(east, core_e + overlap_lon),
            min(north, core_n + overlap_lat),
        ]
        tile_id = f"{x:04d}_{y:04d}"
        file_name = f"tiles/{tile_id}.tboxroads"
        payload = {
            "format": int(root.get("format", 1)),
            "regionId": str(root["regionId"]),
            "graphVersion": int(root.get("graphVersion", 1)),
            "bbox": tile_bbox,
            "edges": edges,
        }
        data = pack_bytes(payload)
        encoded.append((file_name, data))
        tiles.append(
            {
                "id": tile_id,
                "file": file_name,
                "bbox": tile_bbox,
                "bytes": len(data),
                "edgeCount": len(edges),
            }
        )

    index = {
        "format": 1,
        "regionId": str(root["regionId"]),
        "graphVersion": int(root.get("graphVersion", 1)),
        "bbox": bbox,
        "tileSizeDeg": tile_deg,
        "overlapM": overlap_m,
        "tiles": tiles,
    }
    bundle.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(bundle, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr(
            BUNDLE_INDEX,
            json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
        )
        for name, data in encoded:
            # Each tile is independently gzip-compressed; avoid wasteful double compression.
            zf.writestr(name, data)
    print(
        f"wrote {bundle} ({bundle.stat().st_size} bytes, "
        f"{len(tiles)} tiles, {len(root.get('edges') or [])} source edges)"
    )


def read_pack_metadata(path: Path) -> tuple[list[float], int]:
    if path.suffix == ".zip":
        with zipfile.ZipFile(path) as zf:
            root = json.loads(zf.read(BUNDLE_INDEX).decode("utf-8"))
    else:
        root = read_pack(path)
    bbox = root.get("bbox")
    if not isinstance(bbox, list) or len(bbox) < 4:
        raise ValueError(f"Missing bbox: {path}")
    return [float(x) for x in bbox[:4]], int(root.get("graphVersion", 1))


def catalog_entry(region: dict, maps_dir: Path, graph_version: int, remote: bool) -> dict:
    file_name = f"{region['id']}-v{graph_version}.tboxroads.zip"
    pack = maps_dir / file_name
    bbox = [0.0, 0.0, 0.0, 0.0]
    version = graph_version
    size = 0
    url = ""
    if remote and pack.is_file():
        bbox, version = read_pack_metadata(pack)
        size = pack.stat().st_size
        url = f"yandex-disk:/maps/{file_name}"
    return {
        "id": region["id"],
        "country": region["country"],
        "title_ru": region["title_ru"],
        "title_en": region["title_en"],
        "bbox": bbox,
        "url": url,
        "bytes": size if url else 0,
        "graphVersion": version,
    }


def write_catalog(path: Path, entries: list[dict], version: int) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps({"version": version, "regions": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def bundle_path(maps_dir: Path, region_id: str, graph_version: int) -> Path:
    return maps_dir / f"{region_id}-v{graph_version}.tboxroads.zip"


def build_one_region(
    region: dict,
    *,
    maps_dir: Path,
    graph_version: int,
    tile_deg: float,
    overlap_m: float,
) -> Path:
    region_id = region["id"]
    bundle = bundle_path(maps_dir, region_id, graph_version)
    with tempfile.TemporaryDirectory(prefix=f"{region_id}-") as td:
        whole = Path(td) / f"{region_id}-whole.tboxroads"
        run_fetch(region, whole, graph_version)
        tile_whole_pack(
            whole,
            bundle,
            tile_deg=tile_deg,
            overlap_m=overlap_m,
        )
    return bundle


def resolve_region_ids(fetch_region: list[str], fetch_all: bool) -> list[str]:
    by_id = {region["id"]: region for region in REGIONS}
    if fetch_all:
        ids = [region["id"] for region in REGIONS]
    else:
        ids = list(fetch_region)
    unknown = sorted(set(ids) - set(by_id))
    if unknown:
        raise SystemExit(f"Unknown region id(s): {', '.join(unknown)}")
    # Preserve catalog order; de-dupe while keeping first occurrence.
    seen: set[str] = set()
    ordered: list[str] = []
    for region_id in ids:
        if region_id in seen:
            continue
        seen.add(region_id)
        ordered.append(region_id)
    return ordered


def run_build_passes(
    region_ids: list[str],
    *,
    by_id: dict[str, dict],
    maps_dir: Path,
    graph_version: int,
    tile_deg: float,
    overlap_m: float,
    passes: int,
    interval_s: float,
    retry_interval_s: float,
    skip_existing: bool,
) -> tuple[list[str], list[str], dict[str, str]]:
    """Return (ok_ids, still_failed_ids, errors_by_id)."""
    pending = list(region_ids)
    ok: list[str] = []
    errors: dict[str, str] = {}

    if skip_existing:
        kept: list[str] = []
        for region_id in pending:
            existing = bundle_path(maps_dir, region_id, graph_version)
            if existing.is_file():
                print(f"skip existing {existing}", flush=True)
                ok.append(region_id)
            else:
                kept.append(region_id)
        pending = kept

    for pass_no in range(1, max(1, passes) + 1):
        if not pending:
            break
        print(
            f"=== pass {pass_no}/{passes}: {len(pending)} region(s) ===",
            flush=True,
        )
        if pass_no > 1 and retry_interval_s > 0:
            print(
                f"waiting {retry_interval_s:.0f}s before retry pass…",
                flush=True,
            )
            time.sleep(retry_interval_s)

        still_failed: list[str] = []
        for index, region_id in enumerate(pending):
            region = by_id[region_id]
            print(
                f"[{pass_no}/{passes} {index + 1}/{len(pending)}] {region_id} "
                f"({region['title_ru']})",
                flush=True,
            )
            try:
                build_one_region(
                    region,
                    maps_dir=maps_dir,
                    graph_version=graph_version,
                    tile_deg=tile_deg,
                    overlap_m=overlap_m,
                )
            except (FetchError, OSError, ValueError, zipfile.BadZipFile) as e:
                msg = str(e)
                errors[region_id] = msg
                still_failed.append(region_id)
                print(f"FAIL {region_id}: {msg}", file=sys.stderr, flush=True)
            else:
                ok.append(region_id)
                errors.pop(region_id, None)
                print(f"OK {region_id}", flush=True)

            if interval_s > 0 and index + 1 < len(pending):
                print(f"sleep {interval_s:.0f}s…", flush=True)
                time.sleep(interval_s)

        pending = still_failed

    return ok, pending, errors


def write_build_report(
    path: Path,
    *,
    ok: list[str],
    failed: list[str],
    errors: dict[str, str],
    graph_version: int,
) -> None:
    payload = {
        "graphVersion": graph_version,
        "ok": ok,
        "failed": failed,
        "errors": {rid: errors[rid] for rid in failed if rid in errors},
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {path}", flush=True)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Prepare whole-region road maps for Yandex Disk")
    ap.add_argument("--graph-version", type=int, default=4)
    ap.add_argument("--tile-deg", type=float, default=DEFAULT_TILE_DEG)
    ap.add_argument("--tile-overlap-m", type=float, default=DEFAULT_OVERLAP_M)
    ap.add_argument(
        "--output-base",
        type=Path,
        default=DEFAULT_OUTPUT_BASE,
        help="OTA sync root; maps are written to <root>/release/maps",
    )
    ap.add_argument(
        "--fetch-region",
        action="append",
        default=[],
        metavar="ID",
        help="Fetch exact whole admin region via Overpass; repeat as needed",
    )
    ap.add_argument(
        "--fetch-all",
        action="store_true",
        help="Fetch every region from tools/road_map_regions.py",
    )
    ap.add_argument(
        "--interval",
        type=float,
        default=DEFAULT_INTERVAL_S,
        metavar="SEC",
        help=f"Pause between region attempts within a pass (default {DEFAULT_INTERVAL_S:g})",
    )
    ap.add_argument(
        "--retry-interval",
        type=float,
        default=DEFAULT_RETRY_INTERVAL_S,
        metavar="SEC",
        help=(
            "Extra pause before each retry pass "
            f"(default {DEFAULT_RETRY_INTERVAL_S:g})"
        ),
    )
    ap.add_argument(
        "--passes",
        type=int,
        default=DEFAULT_PASSES,
        help=f"Build passes; failed regions are retried (default {DEFAULT_PASSES})",
    )
    ap.add_argument(
        "--skip-existing",
        action="store_true",
        help="Skip regions that already have {id}-vN.tboxroads.zip in maps/",
    )
    ap.add_argument(
        "--report",
        type=Path,
        default=None,
        help="Write JSON ok/failed report (default: <maps>/build_report.json when fetching)",
    )
    ap.add_argument("--list", action="store_true", help="List supported region IDs")
    args = ap.parse_args(argv)

    if args.list:
        for region in REGIONS:
            print(f"{region['id']}\t{region['title_ru']}")
        return 0

    if not args.fetch_all and not args.fetch_region:
        # Catalog-only refresh from packs already on disk.
        maps_dir = args.output_base / "release" / "maps"
        maps_dir.mkdir(parents=True, exist_ok=True)
        remote_entries = [
            catalog_entry(region, maps_dir, args.graph_version, remote=True)
            for region in REGIONS
        ]
        write_catalog(maps_dir / "catalog.json", remote_entries, args.graph_version)
        bundled_entries = [
            catalog_entry(region, maps_dir, args.graph_version, remote=False)
            for region in REGIONS
        ]
        write_catalog(CATALOG, bundled_entries, args.graph_version)
        published = sum(1 for entry in remote_entries if entry["url"])
        print(f"wrote {maps_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
        print(f"wrote {CATALOG} (fallback list, no URLs)")
        return 0

    if args.passes < 1:
        raise SystemExit("--passes must be >= 1")

    maps_dir = args.output_base / "release" / "maps"
    maps_dir.mkdir(parents=True, exist_ok=True)
    by_id = {region["id"]: region for region in REGIONS}
    region_ids = resolve_region_ids(args.fetch_region, args.fetch_all)

    ok, failed, errors = run_build_passes(
        region_ids,
        by_id=by_id,
        maps_dir=maps_dir,
        graph_version=args.graph_version,
        tile_deg=args.tile_deg,
        overlap_m=args.tile_overlap_m,
        passes=args.passes,
        interval_s=args.interval,
        retry_interval_s=args.retry_interval,
        skip_existing=args.skip_existing,
    )

    remote_entries = [
        catalog_entry(region, maps_dir, args.graph_version, remote=True)
        for region in REGIONS
    ]
    write_catalog(maps_dir / "catalog.json", remote_entries, args.graph_version)

    # Bundled fallback: complete list, but no broken download links. Opening the
    # dialog refreshes the remote catalog from /maps/catalog.json.
    bundled_entries = [
        catalog_entry(region, maps_dir, args.graph_version, remote=False)
        for region in REGIONS
    ]
    write_catalog(CATALOG, bundled_entries, args.graph_version)

    report_path = args.report or (maps_dir / "build_report.json")
    write_build_report(
        report_path,
        ok=ok,
        failed=failed,
        errors=errors,
        graph_version=args.graph_version,
    )

    published = sum(1 for entry in remote_entries if entry["url"])
    print(f"wrote {maps_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
    print(f"wrote {CATALOG} (fallback list, no URLs)")
    print(f"build summary: ok={len(ok)} failed={len(failed)} requested={len(region_ids)}")
    if failed:
        print("failed regions:", ", ".join(failed), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
