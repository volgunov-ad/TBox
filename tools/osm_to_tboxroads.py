#!/usr/bin/env python3
"""Convert GeoJSON / Overpass JSON / synthetic grid into a .tboxroads v1 pack.

Format: 8-byte magic TBOXRDS1 + gzip(UTF-8 JSON). See docs/TBOXROADS_FORMAT_RU.md.
Stdlib only (Python 3.9+).
"""

from __future__ import annotations

import argparse
import gzip
import json
import math
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path
from typing import Any, Iterable, List, Sequence

MAGIC = b"TBOXRDS1"
FORMAT = 1
USER_AGENT = "TBoxMonitor-roadmaps/0.18 (osm_to_tboxroads)"

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

HIGHWAY_REGEX = (
    "^(motorway|trunk|primary|secondary|tertiary|residential|unclassified|living_street)"
    "($|_link)$"
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


def _node_key(lon: float, lat: float) -> tuple[int, int]:
    """~1.1 m quantization so shared OSM endpoints get the same node id."""
    return (round(lat * 100_000), round(lon * 100_000))


def assign_shared_nodes(edges: List[dict[str, Any]]) -> None:
    """Rewrite from/to so geometrically shared endpoints reuse node ids."""
    node_ids: dict[tuple[int, int], int] = {}
    next_node = 0

    def node_for(lon: float, lat: float) -> int:
        nonlocal next_node
        key = _node_key(lon, lat)
        existing = node_ids.get(key)
        if existing is not None:
            return existing
        nid = next_node
        next_node += 1
        node_ids[key] = nid
        return nid

    for edge in edges:
        coords = edge.get("coords") or []
        if len(coords) < 2:
            continue
        lon0, lat0 = float(coords[0][0]), float(coords[0][1])
        lon1, lat1 = float(coords[-1][0]), float(coords[-1][1])
        edge["from"] = node_for(lon0, lat0)
        edge["to"] = node_for(lon1, lat1)


def oneway_from_tags(tags: dict[str, Any] | None) -> int:
    """Map OSM tags to pack oneway: 0 both, +1 along coords, -1 against coords."""
    tags = tags or {}
    junction = str(tags.get("junction") or "").strip().lower()
    ow = str(tags.get("oneway") or "").strip().lower()
    if junction == "roundabout":
        return 1
    if ow in ("yes", "true", "1"):
        return 1
    if ow in ("-1", "reverse"):
        return -1
    if ow in ("no", "false", "0"):
        return 0
    return 0


def _append_line(
    edges: List[dict[str, Any]],
    *,
    edge_id: int,
    highway: str,
    coords: List[List[float]],
    oneway: int = 0,
) -> int:
    edge: dict[str, Any] = {
        "id": edge_id,
        "class": highway,
        "lengthM": round(polyline_length_m(coords), 3),
        "from": 0,
        "to": 0,
        "coords": coords,
    }
    if oneway:
        edge["oneway"] = int(oneway)
    edges.append(edge)
    return edge_id + 1


def edges_from_geojson(path: Path, allowed: frozenset[str]) -> List[dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    features = root.get("features") or []
    edges: List[dict[str, Any]] = []
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
        oneway = oneway_from_tags(props)
        for coords in lines:
            if len(coords) < 2:
                continue
            edge_id = _append_line(
                edges, edge_id=edge_id, highway=highway, coords=coords, oneway=oneway,
            )
    assign_shared_nodes(edges)
    return edges


def edges_from_overpass_json(path: Path, allowed: frozenset[str]) -> List[dict[str, Any]]:
    root = json.loads(path.read_text(encoding="utf-8"))
    elements = root.get("elements") or []
    edges: List[dict[str, Any]] = []
    edge_id = 1
    for el in elements:
        if el.get("type") != "way":
            continue
        tags = el.get("tags") or {}
        highway = str(tags.get("highway") or "").strip()
        if highway not in allowed:
            continue
        geom = el.get("geometry") or []
        coords = [[float(p["lon"]), float(p["lat"])] for p in geom if "lon" in p and "lat" in p]
        if len(coords) < 2:
            continue
        oneway = oneway_from_tags(tags)
        edge_id = _append_line(
            edges, edge_id=edge_id, highway=highway, coords=coords, oneway=oneway,
        )
    assign_shared_nodes(edges)
    return edges


def overpass_query_for_bbox(bbox: Sequence[float]) -> str:
    west, south, east, north = [float(x) for x in bbox]
    # Overpass bbox order: south,west,north,east
    return f"""
[out:json][timeout:90];
(
  way["highway"~"{HIGHWAY_REGEX}"]({south},{west},{north},{east});
);
out geom;
""".strip()


def overpass_query_for_area(area_name: str, country_code: str) -> str:
    escaped_name = area_name.replace("\\", "\\\\").replace('"', '\\"')
    escaped_country = country_code.replace("\\", "\\\\").replace('"', '\\"')
    return f"""
[out:json][timeout:900];
area["ISO3166-1"="{escaped_country}"][admin_level="2"]->.country;
rel(area.country)["boundary"="administrative"]["admin_level"="4"]["name"="{escaped_name}"];
map_to_area->.searchArea;
(
  way(area.searchArea)["highway"~"{HIGHWAY_REGEX}"];
);
out geom;
""".strip()


def overpass_query_for_relation(relation_id: int) -> str:
    return f"""
[out:json][timeout:900];
rel({relation_id});
map_to_area->.searchArea;
(
  way(area.searchArea)["highway"~"{HIGHWAY_REGEX}"];
);
out geom;
""".strip()


def fetch_overpass_query(query: str, endpoint: str, retries: int = 3) -> dict[str, Any]:
    data = urllib.parse.urlencode({"data": query}).encode("utf-8")
    last_err: Exception | None = None
    for attempt in range(retries):
        req = urllib.request.Request(
            endpoint,
            data=data,
            headers={"User-Agent": USER_AGENT, "Accept": "application/json"},
            method="POST",
        )
        try:
            with urllib.request.urlopen(req, timeout=900) as resp:
                raw = resp.read()
            return json.loads(raw.decode("utf-8"))
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, json.JSONDecodeError) as e:
            last_err = e
            time.sleep(2.0 * (attempt + 1))
    raise SystemExit(f"overpass fetch failed: {last_err}")


def synthetic_grid(bbox: Sequence[float], step_deg: float = 0.05) -> List[dict[str, Any]]:
    """Small axis-aligned grid inside bbox for demos / tests."""
    west, south, east, north = [float(x) for x in bbox]
    if east <= west or north <= south:
        raise SystemExit("invalid bbox: need west<east and south<north")
    edges: List[dict[str, Any]] = []
    edge_id = 1
    lat = south
    while lat <= north + 1e-9:
        coords = [[west, lat], [east, lat]]
        edge_id = _append_line(edges, edge_id=edge_id, highway="secondary", coords=coords)
        lat += step_deg
    lon = west
    while lon <= east + 1e-9:
        coords = [[lon, south], [lon, north]]
        edge_id = _append_line(edges, edge_id=edge_id, highway="secondary", coords=coords)
        lon += step_deg
    assign_shared_nodes(edges)
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
    src.add_argument("--overpass-json", type=Path, help="Saved Overpass API JSON (out geom)")
    src.add_argument(
        "--fetch-overpass",
        action="store_true",
        help="Fetch ways via Overpass for --bbox (needs network)",
    )
    src.add_argument(
        "--fetch-overpass-area",
        help="Fetch exact admin_level=4 area by OSM relation name (needs network)",
    )
    src.add_argument(
        "--fetch-overpass-relation",
        type=int,
        help="Fetch exact area by OSM relation id (for special/disputed boundaries)",
    )
    src.add_argument("--synthetic", action="store_true", help="Build synthetic grid from --bbox")
    p.add_argument("--region-id", required=True)
    p.add_argument("--graph-version", type=int, default=1)
    p.add_argument("--bbox", help="west,south,east,north (required for --synthetic/--fetch-overpass)")
    p.add_argument("--country-code", choices=["RU", "BY"], help="Required for --fetch-overpass-area")
    p.add_argument("--out", type=Path, required=True)
    p.add_argument(
        "--step-deg",
        type=float,
        default=0.05,
        help="Synthetic grid step in degrees (default 0.05)",
    )
    p.add_argument(
        "--overpass-endpoint",
        default="https://overpass-api.de/api/interpreter",
        help="Overpass interpreter URL",
    )
    p.add_argument(
        "--save-overpass-json",
        type=Path,
        help="Optional path to save raw Overpass JSON when using --fetch-overpass",
    )
    args = p.parse_args(argv)

    bbox_override = parse_bbox(args.bbox) if args.bbox else None

    if args.synthetic:
        if not bbox_override:
            raise SystemExit("--synthetic requires --bbox")
        edges = synthetic_grid(bbox_override, step_deg=args.step_deg)
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)
    elif args.fetch_overpass:
        if not bbox_override:
            raise SystemExit("--fetch-overpass requires --bbox")
        raw = fetch_overpass_query(
            overpass_query_for_bbox(bbox_override),
            args.overpass_endpoint,
        )
        if args.save_overpass_json:
            args.save_overpass_json.parent.mkdir(parents=True, exist_ok=True)
            args.save_overpass_json.write_text(
                json.dumps(raw, ensure_ascii=False), encoding="utf-8"
            )
        tmp = Path(args.out).with_suffix(".overpass.json")
        # edges_from_overpass_json expects a file — write temp next to out unless saved
        path = args.save_overpass_json or tmp
        if path is tmp:
            path.write_text(json.dumps(raw, ensure_ascii=False), encoding="utf-8")
        edges = edges_from_overpass_json(path, DEFAULT_HIGHWAY_CLASSES)
        if path is tmp:
            tmp.unlink(missing_ok=True)
        if not edges:
            raise SystemExit("no edges from Overpass (empty bbox or filter)")
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)
    elif args.fetch_overpass_area:
        if not args.country_code:
            raise SystemExit("--fetch-overpass-area requires --country-code")
        raw = fetch_overpass_query(
            overpass_query_for_area(args.fetch_overpass_area, args.country_code),
            args.overpass_endpoint,
        )
        if args.save_overpass_json:
            args.save_overpass_json.parent.mkdir(parents=True, exist_ok=True)
            args.save_overpass_json.write_text(
                json.dumps(raw, ensure_ascii=False), encoding="utf-8"
            )
        tmp = Path(args.out).with_suffix(".overpass.json")
        # edges_from_overpass_json expects a file — write temp next to out unless saved
        path = args.save_overpass_json or tmp
        if path is tmp:
            path.write_text(json.dumps(raw, ensure_ascii=False), encoding="utf-8")
        edges = edges_from_overpass_json(path, DEFAULT_HIGHWAY_CLASSES)
        if path is tmp:
            tmp.unlink(missing_ok=True)
        if not edges:
            raise SystemExit("no edges from Overpass area (check relation name/admin_level)")
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)
    elif args.fetch_overpass_relation:
        raw = fetch_overpass_query(
            overpass_query_for_relation(args.fetch_overpass_relation),
            args.overpass_endpoint,
        )
        if args.save_overpass_json:
            args.save_overpass_json.parent.mkdir(parents=True, exist_ok=True)
            args.save_overpass_json.write_text(
                json.dumps(raw, ensure_ascii=False), encoding="utf-8"
            )
        tmp = Path(args.out).with_suffix(".overpass.json")
        path = args.save_overpass_json or tmp
        if path is tmp:
            path.write_text(json.dumps(raw, ensure_ascii=False), encoding="utf-8")
        edges = edges_from_overpass_json(path, DEFAULT_HIGHWAY_CLASSES)
        if path is tmp:
            tmp.unlink(missing_ok=True)
        if not edges:
            raise SystemExit("no edges from Overpass relation")
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)
    elif args.overpass_json:
        edges = edges_from_overpass_json(args.overpass_json, DEFAULT_HIGHWAY_CLASSES)
        if not edges:
            raise SystemExit("no edges extracted from Overpass JSON")
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)
    else:
        edges = edges_from_geojson(args.geojson, DEFAULT_HIGHWAY_CLASSES)
        if not edges:
            raise SystemExit("no edges extracted from GeoJSON (check highway classes)")
        payload = build_payload(args.region_id, args.graph_version, edges, bbox=bbox_override)

    nbytes = write_pack(args.out, payload)
    print(
        f"wrote {args.out} ({nbytes} bytes, {len(payload['edges'])} edges, "
        f"bbox={payload['bbox']})",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
