#!/usr/bin/env python3
"""Build whole-region packs and prepare Yandex Disk release/maps.

The user's synchronized OTA root already contains release/. This script writes:
  <output-base>/release/maps/catalog.json
  <output-base>/release/maps/{region-id}-vN.tboxroads

The APK keeps a fallback catalog with all RU/BY regions but no download URLs.
The app refreshes /maps/catalog.json from the same public Yandex Disk share.
"""

from __future__ import annotations

import argparse
import gzip
import json
import subprocess
import sys
from pathlib import Path

from road_map_regions import REGIONS

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "osm_to_tboxroads.py"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "road_maps"
CATALOG = ASSETS / "catalog.json"
DEFAULT_OUTPUT_BASE = Path(r"C:\Users\volgu\AndroidStudioProjects\TBM")


def run_fetch(region: dict, out: Path, graph_version: int) -> None:
    endpoints = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    ]
    last_err: Exception | None = None
    for endpoint in endpoints:
        cmd = [
            sys.executable,
            str(TOOL),
            "--region-id",
            region["id"],
            "--graph-version",
            str(graph_version),
            "--fetch-overpass-area",
            region["osm_name"],
            "--country-code",
            region["country"],
            "--overpass-endpoint",
            endpoint,
            "--out",
            str(out),
        ]
        print("+", " ".join(cmd), flush=True)
        try:
            subprocess.check_call(cmd)
            return
        except subprocess.CalledProcessError as e:
            last_err = e
            print(f"warn: fetch failed via {endpoint}", file=sys.stderr, flush=True)
    raise SystemExit(f"fetch failed for {region['id']}: {last_err}")


def read_pack_metadata(path: Path) -> tuple[list[float], int]:
    data = path.read_bytes()
    if data[:8] != b"TBOXRDS1":
        raise ValueError(f"Bad .tboxroads magic: {path}")
    root = json.loads(gzip.decompress(data[8:]).decode("utf-8"))
    bbox = root.get("bbox")
    if not isinstance(bbox, list) or len(bbox) < 4:
        raise ValueError(f"Missing bbox: {path}")
    return [float(x) for x in bbox[:4]], int(root.get("graphVersion", 1))


def catalog_entry(region: dict, maps_dir: Path, graph_version: int, remote: bool) -> dict:
    file_name = f"{region['id']}-v{graph_version}.tboxroads"
    pack = maps_dir / file_name
    bbox = [0.0, 0.0, 0.0, 0.0]
    version = graph_version
    size = 0
    url = ""
    if pack.is_file():
        bbox, version = read_pack_metadata(pack)
        size = pack.stat().st_size
        if remote:
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


def main() -> int:
    ap = argparse.ArgumentParser(description="Prepare whole-region road maps for Yandex Disk")
    ap.add_argument("--graph-version", type=int, default=3)
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
    ap.add_argument("--list", action="store_true", help="List supported region IDs")
    args = ap.parse_args()

    if args.list:
        for region in REGIONS:
            print(f"{region['id']}\t{region['title_ru']}")
        return 0

    maps_dir = args.output_base / "release" / "maps"
    maps_dir.mkdir(parents=True, exist_ok=True)
    by_id = {region["id"]: region for region in REGIONS}
    unknown = sorted(set(args.fetch_region) - set(by_id))
    if unknown:
        raise SystemExit(f"Unknown region id(s): {', '.join(unknown)}")

    for region_id in args.fetch_region:
        region = by_id[region_id]
        out = maps_dir / f"{region_id}-v{args.graph_version}.tboxroads"
        run_fetch(region, out, args.graph_version)

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

    published = sum(1 for entry in remote_entries if entry["url"])
    print(f"wrote {maps_dir / 'catalog.json'} ({published}/{len(REGIONS)} published)")
    print(f"wrote {CATALOG} (fallback list, no URLs)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
