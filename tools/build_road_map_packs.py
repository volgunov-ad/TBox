#!/usr/bin/env python3
"""Build pilot .tboxroads packs (Overpass) and refresh assets/catalog.json.

Stdlib only. Needs network for --fetch.
See docs/ROAD_MAPS_HOSTING_RU.md.
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
TOOL = ROOT / "tools" / "osm_to_tboxroads.py"
ASSETS = ROOT / "app" / "src" / "main" / "assets" / "road_maps"
STUBS = ASSETS / "stubs"
CATALOG = ASSETS / "catalog.json"
OUT_DIR = ROOT / "tools" / "out" / "road_maps"

# Pilot city-scale extracts (west,south,east,north). Keep modest for APK assets.
PILOT = [
    {
        "id": "ru-moscow",
        "country": "RU",
        "title_ru": "Москва (центр)",
        "title_en": "Moscow (center)",
        "bbox": [37.55, 55.72, 37.70, 55.80],
        "asset": True,
    },
    {
        "id": "ru-nizhny",
        "country": "RU",
        "title_ru": "Нижегородская область (Н.Новгород)",
        "title_en": "Nizhny Novgorod (city)",
        "bbox": [43.90, 56.28, 44.05, 56.36],
        "asset": True,
    },
    {
        "id": "ru-crimea",
        "country": "RU",
        "title_ru": "Крым (Симферополь)",
        "title_en": "Crimea (Simferopol)",
        "bbox": [34.05, 44.90, 34.20, 45.02],
        "asset": True,
    },
    {
        "id": "ru-dnr",
        "country": "RU",
        "title_ru": "ДНР (Донецк)",
        "title_en": "DNR (Donetsk)",
        "bbox": [37.70, 47.95, 37.90, 48.10],
        "asset": True,
    },
    {
        "id": "ru-lnr",
        "country": "RU",
        "title_ru": "ЛНР (Луганск)",
        "title_en": "LNR (Luhansk)",
        "bbox": [39.25, 48.52, 39.40, 48.62],
        "asset": True,
    },
    {
        "id": "by-minsk",
        "country": "BY",
        "title_ru": "Минск (центр)",
        "title_en": "Minsk (center)",
        "bbox": [27.48, 53.86, 27.65, 53.95],
        "asset": True,
    },
    {
        "id": "kz-almaty",
        "country": "KZ",
        "title_ru": "Алматы (центр)",
        "title_en": "Almaty (center)",
        "bbox": [76.88, 43.20, 77.00, 43.30],
        "asset": True,
    },
    {
        "id": "am-yerevan",
        "country": "AM",
        "title_ru": "Ереван (центр)",
        "title_en": "Yerevan (center)",
        "bbox": [44.48, 40.16, 44.55, 40.22],
        "asset": True,
    },
    {
        "id": "az-baku",
        "country": "AZ",
        "title_ru": "Баку (центр)",
        "title_en": "Baku (center)",
        "bbox": [49.80, 40.36, 49.90, 40.42],
        "asset": True,
    },
    {
        "id": "uz-tashkent",
        "country": "UZ",
        "title_ru": "Ташкент (центр)",
        "title_en": "Tashkent (center)",
        "bbox": [69.22, 41.28, 69.32, 41.35],
        "asset": True,
    },
]


def run_fetch(region: dict, out: Path, graph_version: int) -> int:
    bbox = ",".join(str(x) for x in region["bbox"])
    endpoints = [
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
    ]
    last_err: Exception | None = None
    for endpoint in endpoints:
        cmd = [
            sys.executable,
            str(TOOL),
            "--fetch-overpass",
            "--region-id",
            region["id"],
            "--graph-version",
            str(graph_version),
            "--bbox",
            bbox,
            "--overpass-endpoint",
            endpoint,
            "--out",
            str(out),
        ]
        print("+", " ".join(cmd), flush=True)
        try:
            subprocess.check_call(cmd)
            return out.stat().st_size
        except subprocess.CalledProcessError as e:
            last_err = e
            print(f"warn: fetch failed via {endpoint}", file=sys.stderr, flush=True)
    raise SystemExit(f"fetch failed for {region['id']}: {last_err}")


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--fetch", action="store_true", help="Fetch Overpass and rebuild packs")
    ap.add_argument("--graph-version", type=int, default=2)
    ap.add_argument(
        "--release-base",
        default="",
        help="If set, catalog urls become {base}/{id}-v{graphVersion}.tboxroads "
        "instead of asset:// for packs larger than --asset-max-bytes",
    )
    ap.add_argument("--asset-max-bytes", type=int, default=1_500_000)
    ap.add_argument(
        "--continue-on-error",
        action="store_true",
        help="Skip regions that fail Overpass and leave url empty",
    )
    args = ap.parse_args()

    OUT_DIR.mkdir(parents=True, exist_ok=True)
    STUBS.mkdir(parents=True, exist_ok=True)

    regions_out = []
    for region in PILOT:
        out_name = f"{region['id']}-v{args.graph_version}.tboxroads"
        out_path = OUT_DIR / out_name
        asset_name = f"{region['id']}-v{args.graph_version}.tboxroads"
        asset_path = STUBS / asset_name

        if args.fetch:
            try:
                size = run_fetch(region, out_path, args.graph_version)
            except SystemExit as e:
                if not args.continue_on_error:
                    raise
                print(f"skip {region['id']}: {e}", file=sys.stderr)
                regions_out.append(
                    {
                        "id": region["id"],
                        "country": region["country"],
                        "title_ru": region["title_ru"],
                        "title_en": region["title_en"],
                        "bbox": region["bbox"],
                        "url": "",
                        "bytes": 0,
                        "graphVersion": args.graph_version,
                    }
                )
                continue
        elif out_path.is_file():
            size = out_path.stat().st_size
        elif asset_path.is_file():
            size = asset_path.stat().st_size
            out_path = asset_path
        else:
            print(f"skip {region['id']}: no pack (pass --fetch)", file=sys.stderr)
            regions_out.append(
                {
                    "id": region["id"],
                    "country": region["country"],
                    "title_ru": region["title_ru"],
                    "title_en": region["title_en"],
                    "bbox": region["bbox"],
                    "url": "",
                    "bytes": 0,
                    "graphVersion": args.graph_version,
                }
            )
            continue

        use_asset = region.get("asset", True) and size <= args.asset_max_bytes
        if use_asset:
            asset_path.write_bytes(out_path.read_bytes())
            url = f"asset://road_maps/stubs/{asset_name}"
            size = asset_path.stat().st_size
        elif args.release_base:
            url = f"{args.release_base.rstrip('/')}/{out_name}"
        else:
            url = ""
            print(
                f"warn {region['id']}: {size} bytes > asset max; "
                f"left in {out_path} (set --release-base to publish URL)",
                file=sys.stderr,
            )

        regions_out.append(
            {
                "id": region["id"],
                "country": region["country"],
                "title_ru": region["title_ru"],
                "title_en": region["title_en"],
                "bbox": region["bbox"],
                "url": url,
                "bytes": size if url else 0,
                "graphVersion": args.graph_version,
            }
        )

    # Keep Belarus full-country slot as optional unpublished (large).
    by_idx = next((i for i, r in enumerate(regions_out) if r["country"] == "BY"), None)
    if by_idx is not None and not any(r["id"] == "by-all" for r in regions_out):
        regions_out.insert(
            by_idx + 1,
            {
                "id": "by-all",
                "country": "BY",
                "title_ru": "Беларусь (вся)",
                "title_en": "Belarus (all)",
                "bbox": [23.1, 51.2, 32.8, 56.2],
                "url": "",
                "bytes": 0,
                "graphVersion": args.graph_version,
            },
        )

    catalog = {"version": args.graph_version, "regions": regions_out}
    CATALOG.write_text(json.dumps(catalog, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {CATALOG} ({len(regions_out)} regions)", flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
