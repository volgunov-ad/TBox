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
import re
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


def run_fetch(
    region: dict,
    out: Path,
    graph_version: int,
    extra_args: list[str] | None = None,
) -> None:
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
        if extra_args:
            cmd.extend(extra_args)
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


def detect_bundle_version(maps_dir: Path, region_id: str, fallback: int) -> int:
    """Highest valid {id}-vN zip on disk; fallback if none."""
    versions: list[int] = []
    pattern = re.compile(rf"^{re.escape(region_id)}-v(\d+)\.tboxroads\.zip$")
    if not maps_dir.is_dir():
        return fallback
    for path in maps_dir.glob(f"{region_id}-v*.tboxroads.zip"):
        matched = pattern.match(path.name)
        if matched and existing_bundle_ok(path):
            versions.append(int(matched.group(1)))
    return max(versions) if versions else fallback


def catalog_entry(region: dict, maps_dir: Path, graph_version: int, remote: bool) -> dict:
    version = detect_bundle_version(maps_dir, region["id"], graph_version)
    file_name = f"{region['id']}-v{version}.tboxroads.zip"
    pack = maps_dir / file_name
    bbox = [0.0, 0.0, 0.0, 0.0]
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
    skdf_snapshot: Path | None = None,
    skdf_report: Path | None = None,
) -> Path:
    region_id = region["id"]
    bundle = bundle_path(maps_dir, region_id, graph_version)
    extra: list[str] = []
    if skdf_snapshot is not None:
        extra.extend(["--skdf-snapshot", str(skdf_snapshot)])
        if skdf_report is not None:
            extra.extend(["--skdf-report", str(skdf_report)])
        cache = maps_dir.parent.parent / "snapshots" / f"{region_id}-overpass.json"
        extra.extend(["--save-overpass-json", str(cache)])
    with tempfile.TemporaryDirectory(prefix=f"{region_id}-") as td:
        whole = Path(td) / f"{region_id}-whole.tboxroads"
        run_fetch(region, whole, graph_version, extra_args=extra or None)
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
    skdf_snapshot: Path | None = None,
    skdf_report: Path | None = None,
    skdf_region_id: str | None = None,
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
                print(f"skip existing {existing}", flush=True)
                ok.append(region_id)
            else:
                if existing.is_file():
                    print(
                        f"warn: ignoring broken/incomplete pack, will rebuild: {existing}",
                        file=sys.stderr,
                        flush=True,
                    )
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
                use_skdf = bool(skdf_snapshot) and (
                    skdf_region_id is None or region_id == skdf_region_id
                )
                build_one_region(
                    region,
                    maps_dir=maps_dir,
                    graph_version=graph_version,
                    tile_deg=tile_deg,
                    overlap_m=overlap_m,
                    skdf_snapshot=skdf_snapshot if use_skdf else None,
                    skdf_report=skdf_report if use_skdf else None,
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
        "--only-missing",
        dest="skip_existing",
        action="store_true",
        help=(
            "Do not rebuild regions that already have a valid "
            "{id}-vN.tboxroads.zip in maps/ (alias: --only-missing)"
        ),
    )
    ap.add_argument(
        "--report",
        type=Path,
        default=None,
        help="Write JSON ok/failed report (default: <maps>/build_report.json when fetching)",
    )
    ap.add_argument("--list", action="store_true", help="List supported region IDs")
    ap.add_argument(
        "--skdf-overlay",
        action="store_true",
        help=(
            "Enrich ru-nizhny-novgorod with ФГИС СКДФ speed-limits before tiling "
            "(graphVersion 5). Token from env/local file, never written into the pack"
        ),
    )
    ap.add_argument(
        "--skdf-snapshot",
        type=Path,
        default=None,
        help="Existing SKDF snapshot JSON (skip live export if present)",
    )
    ap.add_argument(
        "--skdf-token-file",
        type=Path,
        default=None,
        help="Read-token file (default D:\\Dashing\\СКДФ\\token or tools/skdf/token)",
    )
    ap.add_argument(
        "--skdf-report",
        type=Path,
        default=None,
        help="Overlay quality report JSON (default: next to the bundle)",
    )
    ap.add_argument(
        "--update-bundled-catalog",
        action="store_true",
        help="Also rewrite app/src/main/assets/road_maps/catalog.json (APK fallback)",
    )
    args = ap.parse_args(argv)

    if args.list:
        for region in REGIONS:
            print(f"{region['id']}\t{region['title_ru']}")
        return 0

    if not args.fetch_all and not args.fetch_missing and not args.fetch_region:
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

    fetch_all = bool(args.fetch_all or args.fetch_missing)
    skip_existing = bool(args.skip_existing or args.fetch_missing)

    maps_dir = args.output_base / "release" / "maps"
    maps_dir.mkdir(parents=True, exist_ok=True)
    by_id = {region["id"]: region for region in REGIONS}
    region_ids = resolve_region_ids(args.fetch_region, fetch_all)

    graph_version = args.graph_version
    skdf_snapshot: Path | None = None
    skdf_report: Path | None = args.skdf_report
    skdf_region_id: str | None = None
    if args.skdf_overlay:
        import skdf_speed_limits as skdf

        skdf_region_id = skdf.NIZHNY_REGION_ID
        if skdf_region_id not in region_ids:
            raise SystemExit("--skdf-overlay requires --fetch-region ru-nizhny-novgorod")
        graph_version = max(graph_version, skdf.SKDF_GRAPH_VERSION)
        skdf_snapshot = args.skdf_snapshot
        if skdf_snapshot is None or not skdf_snapshot.is_file():
            token = skdf.load_token(args.skdf_token_file)
            raw_path = skdf.DEFAULT_SNAPSHOT_DIR / "ru-nizhny-novgorod-speed-limits-raw.json"
            raw_rows = None
            if raw_path.is_file():
                print(f"skdf: reusing raw export {raw_path}", flush=True)
                raw_rows = json.loads(raw_path.read_text(encoding="utf-8"))
            snapshot = skdf.build_snapshot(token=token, raw_rows=raw_rows)
            skdf_snapshot = args.skdf_snapshot or skdf.default_snapshot_path()
            skdf.save_snapshot(skdf_snapshot, snapshot)
            print(
                f"skdf: snapshot {skdf_snapshot} "
                f"intervals={len(snapshot['intervals'])}",
                flush=True,
            )
        if skdf_report is None:
            skdf_report = maps_dir / f"{skdf_region_id}-v{graph_version}-skdf-report.json"

    ok, failed, errors = run_build_passes(
        region_ids,
        by_id=by_id,
        maps_dir=maps_dir,
        graph_version=graph_version,
        tile_deg=args.tile_deg,
        overlap_m=args.tile_overlap_m,
        passes=args.passes,
        interval_s=args.interval,
        retry_interval_s=args.retry_interval,
        skip_existing=skip_existing,
        skdf_snapshot=skdf_snapshot,
        skdf_report=skdf_report,
        skdf_region_id=skdf_region_id,
    )

    remote_entries = [
        catalog_entry(region, maps_dir, graph_version, remote=True)
        for region in REGIONS
    ]
    write_catalog(maps_dir / "catalog.json", remote_entries, graph_version)

    write_bundled = (not args.skdf_overlay) or args.update_bundled_catalog
    if write_bundled:
        bundled_entries = [
            catalog_entry(region, maps_dir, graph_version, remote=False)
            for region in REGIONS
        ]
        write_catalog(CATALOG, bundled_entries, graph_version)

    report_path = args.report or (maps_dir / "build_report.json")
    write_build_report(
        report_path,
        ok=ok,
        failed=failed,
        errors=errors,
        graph_version=graph_version,
    )

    published = sum(1 for entry in remote_entries if entry["url"])
    print(f"wrote {maps_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
    if write_bundled:
        print(f"wrote {CATALOG} (fallback list, no URLs)")
    else:
        print("skipped bundled APK catalog (use --update-bundled-catalog to write it)")
    print(f"build summary: ok={len(ok)} failed={len(failed)} requested={len(region_ids)}")
    if failed:
        print("failed regions:", ", ".join(failed), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
