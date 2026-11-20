#!/usr/bin/env python3
"""Build whole-region packs and prepare Yandex Disk release/maps.

The user's synchronized OTA root already contains release/. This script writes:
  <output-base>/release/maps/catalog.json
  <output-base>/release/maps/{region-id}-vN.tboxroads.zip

The APK keeps a fallback catalog with all RU/BY regions but no download URLs.
The app refreshes /maps/catalog.json from the same public Yandex Disk share.

Modes:
  * catalog-only (no fetch flags): rebuild catalog.json files from existing packs.
  * fetch: download regions via Overpass, tile them, write bundles and catalogs.

Parallelism:
  --workers N         process up to N regions concurrently (default 1)
  --max-inflight M    cap concurrent Overpass fetches across workers (default 3)

Publication control:
  --build-dir DIR       where bundles are written (default: <output-base>/release/maps)
  --no-remote-catalog   do not write <publish-dir>/catalog.json
  --no-bundled-catalog  do not overwrite app/src/main/assets/road_maps/catalog.json
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import os
import subprocess
import sys
import tempfile
import threading
import time
import zipfile
from concurrent.futures import ThreadPoolExecutor, as_completed
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
DEFAULT_WORKERS = 1
DEFAULT_MAX_INFLIGHT = 3
OVERPASS_ENDPOINTS = [
    "https://overpass-api.de/api/interpreter",
    "https://lz4.overpass-api.de/api/interpreter",
    "https://overpass.private.coffee/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
]


class FetchError(RuntimeError):
    """Overpass / osm_to_tboxroads failed for a region after all endpoints."""


# ---------------------------------------------------------------------------
# Output helpers (thread-safe printing)
# ---------------------------------------------------------------------------

_PRINT_LOCK = threading.Lock()


def _log(msg: str, *, err: bool = False) -> None:
    with _PRINT_LOCK:
        print(msg, file=sys.stderr if err else sys.stdout, flush=True)


# ---------------------------------------------------------------------------
# Fetching
# ---------------------------------------------------------------------------

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
        _log("+ " + " ".join(cmd))
        try:
            subprocess.check_call(cmd)
            return
        except subprocess.CalledProcessError as e:
            last_err = e
            _log(f"warn: fetch failed via {endpoint}", err=True)
    raise FetchError(f"fetch failed for {region['id']}: {last_err}")


# ---------------------------------------------------------------------------
# Pack format
# ---------------------------------------------------------------------------

def read_pack(path: Path) -> dict:
    data = path.read_bytes()
    if data[:8] != PACK_MAGIC:
        raise ValueError(f"Bad .tboxroads magic: {path}")
    return json.loads(gzip.decompress(data[8:]).decode("utf-8"))


def pack_bytes(payload: dict) -> bytes:
    raw = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
    return PACK_MAGIC + gzip.compress(raw, compresslevel=9)


# ---------------------------------------------------------------------------
# Tiling
# ---------------------------------------------------------------------------

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
    tmp = bundle.with_name(bundle.name + ".tmp")
    if tmp.exists():
        try:
            tmp.unlink()
        except OSError:
            pass
    with zipfile.ZipFile(tmp, "w", compression=zipfile.ZIP_STORED) as zf:
        zf.writestr(
            BUNDLE_INDEX,
            json.dumps(index, ensure_ascii=False, separators=(",", ":")).encode("utf-8"),
        )
        for name, data in encoded:
            # Each tile is independently gzip-compressed; avoid double compression.
            zf.writestr(name, data)
    os.replace(tmp, bundle)
    _log(
        f"wrote {bundle} ({bundle.stat().st_size} bytes, "
        f"{len(tiles)} tiles, {len(root.get('edges') or [])} source edges)"
    )


# ---------------------------------------------------------------------------
# Metadata / catalog
# ---------------------------------------------------------------------------

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
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(
        json.dumps({"version": version, "regions": entries}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    os.replace(tmp, path)


def existing_bundle_ok(path: Path) -> bool:
    """True if path looks like a usable tiled pack (non-empty zip with index)."""
    if not path.is_file() or path.stat().st_size <= 0:
        return False
    try:
        with zipfile.ZipFile(path) as zf:
            if BUNDLE_INDEX not in zf.namelist():
                return False
            root = json.loads(zf.read(BUNDLE_INDEX).decode("utf-8"))
        bbox = root.get("bbox")
        return isinstance(bbox, list) and len(bbox) >= 4
    except (OSError, ValueError, KeyError, zipfile.BadZipFile, UnicodeDecodeError, json.JSONDecodeError):
        return False


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


# ---------------------------------------------------------------------------
# Parallel build passes
# ---------------------------------------------------------------------------

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
    workers: int,
    max_inflight: int,
) -> tuple[list[str], list[str], dict[str, str]]:
    """Return (ok_ids, still_failed_ids, errors_by_id)."""
    pending = list(region_ids)
    ok: list[str] = []
    errors: dict[str, str] = {}

    if skip_existing:
        kept: list[str] = []
        for region_id in pending:
            existing = bundle_path(maps_dir, region_id, graph_version)
            if existing_bundle_ok(existing):
                _log(f"skip existing {existing}")
                ok.append(region_id)
            else:
                if existing.is_file():
                    _log(f"warn: ignoring broken/incomplete pack, will rebuild: {existing}", err=True)
                kept.append(region_id)
        pending = kept

    workers = max(1, workers)
    max_inflight = max(1, max_inflight)

    # Global throttle: ensure at least interval_s between *fetch starts*.
    start_lock = threading.Lock()
    last_start = [0.0]
    inflight = threading.Semaphore(max_inflight)

    def throttle() -> None:
        if interval_s <= 0:
            return
        with start_lock:
            now = time.monotonic()
            wait = last_start[0] + interval_s - now
            if wait > 0:
                time.sleep(wait)
            last_start[0] = time.monotonic()

    def worker(region_id: str) -> tuple[str, str | None]:
        region = by_id[region_id]
        try:
            with inflight:
                throttle()
                _log(f"START {region_id} ({region['title_ru']})")
                build_one_region(
                    region,
                    maps_dir=maps_dir,
                    graph_version=graph_version,
                    tile_deg=tile_deg,
                    overlap_m=overlap_m,
                )
            return region_id, None
        except (FetchError, OSError, ValueError, zipfile.BadZipFile) as e:
            return region_id, str(e)

    for pass_no in range(1, max(1, passes) + 1):
        if not pending:
            break
        _log(
            f"=== pass {pass_no}/{passes}: {len(pending)} region(s), "
            f"workers={workers}, max_inflight={max_inflight} ==="
        )
        if pass_no > 1 and retry_interval_s > 0:
            _log(f"waiting {retry_interval_s:.0f}s before retry pass…")
            time.sleep(retry_interval_s)

        still_failed: list[str] = []
        with ThreadPoolExecutor(max_workers=workers) as pool:
            futures = {pool.submit(worker, rid): rid for rid in pending}
            for fut in as_completed(futures):
                rid, err = fut.result()
                if err is None:
                    ok.append(rid)
                    errors.pop(rid, None)
                    _log(f"OK {rid}")
                else:
                    errors[rid] = err
                    still_failed.append(rid)
                    _log(f"FAIL {rid}: {err}", err=True)

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
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_text(json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    os.replace(tmp, path)
    _log(f"wrote {path}")


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="Prepare whole-region road maps for Yandex Disk")
    ap.add_argument("--graph-version", type=int, default=4)
    ap.add_argument("--tile-deg", type=float, default=DEFAULT_TILE_DEG)
    ap.add_argument("--tile-overlap-m", type=float, default=DEFAULT_OVERLAP_M)
    ap.add_argument(
        "--output-base",
        type=Path,
        default=DEFAULT_OUTPUT_BASE,
        help="OTA sync root; published maps live in <root>/release/maps",
    )
    ap.add_argument(
        "--build-dir",
        type=Path,
        default=None,
        help="Where to write region bundles (default: <output-base>/release/maps)",
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
        "--fetch-missing",
        action="store_true",
        help=(
            "Fetch all regions that do not yet have a valid "
            "{id}-vN.tboxroads.zip (implies --fetch-all --skip-existing)"
        ),
    )
    ap.add_argument(
        "--interval",
        type=float,
        default=DEFAULT_INTERVAL_S,
        metavar="SEC",
        help=f"Minimum gap between fetch starts, globally (default {DEFAULT_INTERVAL_S:g})",
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
        "--only-missing",
        dest="skip_existing",
        action="store_true",
        help=(
            "Do not rebuild regions that already have a valid "
            "{id}-vN.tboxroads.zip in build dir (alias: --only-missing)"
        ),
    )
    ap.add_argument(
        "--workers",
        type=int,
        default=DEFAULT_WORKERS,
        metavar="N",
        help=f"Parallel region builders (default {DEFAULT_WORKERS} = sequential)",
    )
    ap.add_argument(
        "--max-inflight",
        type=int,
        default=DEFAULT_MAX_INFLIGHT,
        metavar="M",
        help=f"Cap concurrent Overpass fetches across workers (default {DEFAULT_MAX_INFLIGHT})",
    )
    ap.add_argument(
        "--no-remote-catalog",
        action="store_true",
        help="Do not write <publish-dir>/catalog.json (local/offline build only)",
    )
    ap.add_argument(
        "--no-bundled-catalog",
        action="store_true",
        help="Do not overwrite the app fallback catalog in assets/road_maps",
    )
    ap.add_argument(
        "--report",
        type=Path,
        default=None,
        help="Write JSON ok/failed report (default: <build-dir>/build_report.json when fetching)",
    )
    ap.add_argument("--list", action="store_true", help="List supported region IDs")
    args = ap.parse_args(argv)

    if args.list:
        for region in REGIONS:
            print(f"{region['id']}\t{region['title_ru']}")
        return 0

    publish_dir = args.output_base / "release" / "maps"
    build_dir = args.build_dir or publish_dir

    # --- catalog-only mode ------------------------------------------------
    if not args.fetch_all and not args.fetch_missing and not args.fetch_region:
        build_dir.mkdir(parents=True, exist_ok=True)
        if not args.no_remote_catalog:
            remote_entries = [
                catalog_entry(region, build_dir, args.graph_version, remote=True)
                for region in REGIONS
            ]
            write_catalog(publish_dir / "catalog.json", remote_entries, args.graph_version)
            published = sum(1 for entry in remote_entries if entry["url"])
            _log(f"wrote {publish_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
        else:
            _log("skipped remote catalog (--no-remote-catalog)")

        if not args.no_bundled_catalog:
            bundled_entries = [
                catalog_entry(region, build_dir, args.graph_version, remote=False)
                for region in REGIONS
            ]
            write_catalog(CATALOG, bundled_entries, args.graph_version)
            _log(f"wrote {CATALOG} (fallback list, no URLs)")
        else:
            _log("skipped bundled catalog (--no-bundled-catalog)")
        return 0

    # --- fetch / build mode ----------------------------------------------
    if args.passes < 1:
        raise SystemExit("--passes must be >= 1")
    if args.workers < 1:
        raise SystemExit("--workers must be >= 1")
    if args.max_inflight < 1:
        raise SystemExit("--max-inflight must be >= 1")

    fetch_all = bool(args.fetch_all or args.fetch_missing)
    skip_existing = bool(args.skip_existing or args.fetch_missing)

    build_dir.mkdir(parents=True, exist_ok=True)
    by_id = {region["id"]: region for region in REGIONS}
    region_ids = resolve_region_ids(args.fetch_region, fetch_all)

    ok, failed, errors = run_build_passes(
        region_ids,
        by_id=by_id,
        maps_dir=build_dir,
        graph_version=args.graph_version,
        tile_deg=args.tile_deg,
        overlap_m=args.tile_overlap_m,
        passes=args.passes,
        interval_s=args.interval,
        retry_interval_s=args.retry_interval,
        skip_existing=skip_existing,
        workers=args.workers,
        max_inflight=args.max_inflight,
    )

    # Remote catalog: describe published bundles (build_dir may differ from publish_dir,
    # but URLs always point at <share>/maps/<file>; sizes/bbox are read from build_dir).
    if not args.no_remote_catalog:
        remote_entries = [
            catalog_entry(region, build_dir, args.graph_version, remote=True)
            for region in REGIONS
        ]
        write_catalog(publish_dir / "catalog.json", remote_entries, args.graph_version)
        published = sum(1 for entry in remote_entries if entry["url"])
        _log(f"wrote {publish_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
    else:
        _log("skipped remote catalog (--no-remote-catalog)")

    # Bundled fallback: complete list, but no download URLs.
    if not args.no_bundled_catalog:
        bundled_entries = [
            catalog_entry(region, build_dir, args.graph_version, remote=False)
            for region in REGIONS
        ]
        write_catalog(CATALOG, bundled_entries, args.graph_version)
        _log(f"wrote {CATALOG} (fallback list, no URLs)")
    else:
        _log("skipped bundled catalog (--no-bundled-catalog)")

    report_path = args.report or (build_dir / "build_report.json")
    write_build_report(
        report_path,
        ok=ok,
        failed=failed,
        errors=errors,
        graph_version=args.graph_version,
    )

    _log(f"build summary: ok={len(ok)} failed={len(failed)} requested={len(region_ids)}")
    if failed:
        _log("failed regions: " + ", ".join(failed), err=True)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())