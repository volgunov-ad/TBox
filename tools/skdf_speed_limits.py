#!/usr/bin/env python3
"""ФГИС СКДФ speed-limit overlay for .tboxroads packs. Stdlib only.

Token is never written into the pack. Default local secret:
  D:\\Dashing\\СКДФ\\token  (junction: tools/skdf/token)
or env SKDF_TOKEN / SKDF_TOKEN_FILE.
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import ssl
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence

USER_AGENT = "TBoxMonitor-roadmaps/0.18 (skdf_speed_limits)"
SKDF_ORIGIN = "https://xn--d1aluo.xn--p1ai"
DATASET_CODE = "speed-limits"
NIZHNY_REGION_ID = "ru-nizhny-novgorod"
NIZHNY_REGION_GID = 21379
SKDF_GRAPH_VERSION = 5
MIN_SPLIT_M = 200.0
MAX_MATCH_DISTANCE_M = 45.0
MIN_HEADING_COS = 0.85
MIN_OVERLAP_RATIO = 0.35
FAIL_KM_POST_ERROR_M = 400.0
FAIL_MIN_REF_MATCH_RATIO = 0.02
VALID_SPEED_MIN = 5
VALID_SPEED_MAX = 200
FEDERAL_VALUE_GID = 83717
REGIONAL_VALUE_GID = 83718
LOCAL_VALUE_GID = 83719
MATCH_CLASSES = frozenset(
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
    }
)
NO_REF_CLASSES = frozenset({"motorway", "trunk", "primary", "secondary"})
GRID_CELL_DEG = 0.03
WEB_MERCATOR_SHIFT = 20037508.342789244
SSL_CTX = ssl.create_default_context()

_CYR_LAT = str.maketrans(
    {
        "М": "M",
        "м": "M",
        "Р": "R",
        "р": "R",
        "А": "A",
        "а": "A",
        "К": "K",
        "к": "K",
        "Н": "N",
        "н": "N",
        "Е": "E",
        "е": "E",
        "Ё": "E",
        "ё": "E",
        "Н": "N",
    }
)
REF_HEAD_RE = re.compile(
    r"(?iu)^\s*(?:а/?д\s+)?"
    r"(?P<ref>[МMРRАA]\s*-?\s*\d+[А-ЯA-Z]?|\d{2}\s*[КKНHNРRP]\s*-?\s*\d+)"
)
CHAINAGE_RE = re.compile(r"^\s*(\d+)\s*\+\s*(\d+)\s*$")
DEFAULT_TOKEN_PATHS = (
    Path(r"D:\Dashing\СКДФ\token"),
    Path(__file__).resolve().parent / "skdf" / "token",
)
DEFAULT_SNAPSHOT_DIR = Path(r"D:\Dashing\СКДФ\snapshots")


class SkdfError(RuntimeError):
    """Network / quality failure for an explicit SKDF overlay."""


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
        total += haversine_m(coords[i - 1][0], coords[i - 1][1], coords[i][0], coords[i][1])
    return total


def mercator_to_wgs84(x: float, y: float) -> list[float]:
    lon = x * 180.0 / WEB_MERCATOR_SHIFT
    lat = math.atan(math.exp(y * math.pi / WEB_MERCATOR_SHIFT)) * 360.0 / math.pi - 90.0
    return [lon, lat]


def parse_chainage_m(raw: Any) -> float | None:
    if raw is None or isinstance(raw, bool):
        return None
    if isinstance(raw, (int, float)):
        if not math.isfinite(float(raw)) or float(raw) < 0:
            return None
        # Integers from WFS km posts are whole kilometres.
        val = float(raw)
        return val * 1000.0 if val < 10000 else val
    s = str(raw).strip().replace(",", ".")
    m = CHAINAGE_RE.match(s)
    if m:
        return float(m.group(1)) * 1000.0 + float(m.group(2))
    try:
        val = float(s)
    except ValueError:
        return None
    if not math.isfinite(val) or val < 0:
        return None
    return val * 1000.0 if val < 10000 else val


def parse_speed_kmh(raw: Any) -> int | None:
    if isinstance(raw, dict):
        raw = raw.get("name")
    if raw is None or isinstance(raw, bool):
        return None
    try:
        kmh = int(round(float(raw)))
    except (TypeError, ValueError):
        return None
    if VALID_SPEED_MIN <= kmh <= VALID_SPEED_MAX:
        return kmh
    return None


def normalize_ref(raw: Any) -> str | None:
    if raw is None:
        return None
    s = str(raw).strip().translate(_CYR_LAT)
    s = re.sub(r"\s+", "", s).upper()
    s = s.replace("—", "-").replace("–", "-")
    if not s:
        return None
    s = re.sub(r"^([MRA])(\d)", r"\1-\2", s)
    s = re.sub(r"^(\d{2})([KNRP])(\d)", r"\1\2-\3", s)
    return s[:32]


def ref_keys(raw: Any) -> set[str]:
    keys: set[str] = set()
    if raw is None:
        return keys
    for part in re.split(r"[;,/|]", str(raw)):
        n = normalize_ref(part)
        if n:
            keys.add(n)
            keys.add(n.replace("-", ""))
    return keys


def extract_head_ref(road_name: Any) -> str | None:
    m = REF_HEAD_RE.match(str(road_name or ""))
    if not m:
        return None
    return normalize_ref(m.group("ref"))


def reverse_line(coords: Sequence[Sequence[float]]) -> list[list[float]]:
    return [[float(c[0]), float(c[1])] for c in reversed(coords)]


def cumulative_m(coords: Sequence[Sequence[float]]) -> list[float]:
    acc = [0.0]
    for i in range(1, len(coords)):
        acc.append(acc[-1] + haversine_m(coords[i - 1][0], coords[i - 1][1], coords[i][0], coords[i][1]))
    return acc


def point_along(coords: Sequence[Sequence[float]], dist_m: float) -> list[float]:
    if len(coords) < 2:
        raise ValueError("need a line")
    acc = cumulative_m(coords)
    length = acc[-1]
    target = min(max(0.0, dist_m), length)
    for i in range(1, len(acc)):
        if acc[i] >= target - 1e-9:
            span = acc[i] - acc[i - 1]
            t = 0.0 if span <= 1e-9 else (target - acc[i - 1]) / span
            lon = coords[i - 1][0] + t * (coords[i][0] - coords[i - 1][0])
            lat = coords[i - 1][1] + t * (coords[i][1] - coords[i - 1][1])
            return [lon, lat]
    return [float(coords[-1][0]), float(coords[-1][1])]


def slice_line(coords: Sequence[Sequence[float]], start_m: float, end_m: float) -> list[list[float]]:
    if end_m < start_m:
        start_m, end_m = end_m, start_m
    acc = cumulative_m(coords)
    length = acc[-1]
    a = min(max(0.0, start_m), length)
    b = min(max(0.0, end_m), length)
    if b - a < 1.0:
        p = point_along(coords, a)
        q = point_along(coords, min(length, a + 1.0))
        return [p, q]
    out: list[list[float]] = [point_along(coords, a)]
    for i, d in enumerate(acc):
        if a < d < b:
            out.append([float(coords[i][0]), float(coords[i][1])])
    end_pt = point_along(coords, b)
    if haversine_m(out[-1][0], out[-1][1], end_pt[0], end_pt[1]) > 0.2:
        out.append(end_pt)
    return out if len(out) >= 2 else [out[0], end_pt]


def heading_cos(a: Sequence[Sequence[float]], b: Sequence[Sequence[float]]) -> float:
    def vec(line: Sequence[Sequence[float]]) -> tuple[float, float]:
        lon1, lat1 = line[0][0], line[0][1]
        lon2, lat2 = line[-1][0], line[-1][1]
        x = (lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2.0))
        y = lat2 - lat1
        n = math.hypot(x, y)
        return (0.0, 0.0) if n < 1e-12 else (x / n, y / n)

    ax, ay = vec(a)
    bx, by = vec(b)
    return ax * bx + ay * by


def grid_cell(lon: float, lat: float) -> tuple[int, int]:
    return (int(math.floor(lon / GRID_CELL_DEG)), int(math.floor(lat / GRID_CELL_DEG)))


def build_edge_grid(edges: Sequence[dict[str, Any]]) -> dict[tuple[int, int], list[int]]:
    grid: dict[tuple[int, int], list[int]] = {}
    for i, edge in enumerate(edges):
        coords = edge.get("coords") or []
        if len(coords) < 2:
            continue
        cells: set[tuple[int, int]] = set()
        for lon, lat in coords:
            cells.add(grid_cell(float(lon), float(lat)))
        for cell in cells:
            grid.setdefault(cell, []).append(i)
    return grid


def grid_candidates(
    grid: dict[tuple[int, int], list[int]],
    coords: Sequence[Sequence[float]],
    pad_cells: int = 1,
) -> list[int]:
    if len(coords) < 2:
        return []
    lons = [float(c[0]) for c in coords]
    lats = [float(c[1]) for c in coords]
    x0 = int(math.floor(min(lons) / GRID_CELL_DEG)) - pad_cells
    x1 = int(math.floor(max(lons) / GRID_CELL_DEG)) + pad_cells
    y0 = int(math.floor(min(lats) / GRID_CELL_DEG)) - pad_cells
    y1 = int(math.floor(max(lats) / GRID_CELL_DEG)) + pad_cells
    seen: set[int] = set()
    out: list[int] = []
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            for idx in grid.get((x, y), ()):
                if idx not in seen:
                    seen.add(idx)
                    out.append(idx)
    return out


def dist_point_to_segment_m(
    lon: float,
    lat: float,
    lon1: float,
    lat1: float,
    lon2: float,
    lat2: float,
) -> float:
    vx = (lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2.0))
    vy = lat2 - lat1
    wx = (lon - lon1) * math.cos(math.radians((lat + lat1) / 2.0))
    wy = lat - lat1
    c2 = vx * vx + vy * vy
    if c2 <= 1e-18:
        return haversine_m(lon, lat, lon1, lat1)
    t = max(0.0, min(1.0, (wx * vx + wy * vy) / c2))
    return haversine_m(lon, lat, lon1 + t * (lon2 - lon1), lat1 + t * (lat2 - lat1))


def dist_point_to_line_m(lon: float, lat: float, line: Sequence[Sequence[float]]) -> float:
    best = float("inf")
    for i in range(1, len(line)):
        d = dist_point_to_segment_m(lon, lat, line[i - 1][0], line[i - 1][1], line[i][0], line[i][1])
        if d < best:
            best = d
    return best


def mean_sample_distance_m(src: Sequence[Sequence[float]], dst: Sequence[Sequence[float]], samples: int = 5) -> float:
    if len(src) < 2 or len(dst) < 2:
        return float("inf")
    length = polyline_length_m(src)
    if length <= 0:
        return dist_point_to_line_m(src[0][0], src[0][1], dst)
    total = 0.0
    n = max(2, samples)
    for i in range(n):
        d = length * i / (n - 1)
        pt = point_along(src, d)
        total += dist_point_to_line_m(pt[0], pt[1], dst)
    return total / n


def overlap_along_m(edge: Sequence[Sequence[float]], official: Sequence[Sequence[float]], max_dist: float) -> tuple[float, float] | None:
    acc = cumulative_m(edge)
    hits: list[float] = []
    for i, pt in enumerate(edge):
        if dist_point_to_line_m(pt[0], pt[1], official) <= max_dist:
            hits.append(acc[i])
    if len(hits) < 2:
        return None
    return (hits[0], hits[-1])


def token_paths(explicit: Path | None = None) -> list[Path]:
    paths: list[Path] = []
    if explicit is not None:
        paths.append(explicit)
    env_file = os.environ.get("SKDF_TOKEN_FILE", "").strip()
    if env_file:
        paths.append(Path(env_file))
    paths.extend(DEFAULT_TOKEN_PATHS)
    return paths


def load_token(explicit: Path | None = None) -> str:
    if explicit is not None:
        text = explicit.read_text(encoding="utf-8").strip()
        if text:
            return text.splitlines()[0].strip()
        raise SkdfError(f"empty SKDF token file: {explicit}")
    env = os.environ.get("SKDF_TOKEN", "").strip()
    if env:
        return env
    tried: list[str] = []
    for path in token_paths(explicit):
        tried.append(str(path))
        try:
            text = path.read_text(encoding="utf-8").strip()
        except OSError:
            continue
        if text:
            return text.splitlines()[0].strip()
    raise SkdfError("SKDF read token not found (SKDF_TOKEN / SKDF_TOKEN_FILE / " + ", ".join(tried) + ")")


def _request_json(
    method: str,
    url: str,
    *,
    body: Any | None = None,
    headers: dict[str, str] | None = None,
    timeout: float = 60.0,
) -> Any:
    hdrs = {"User-Agent": USER_AGENT, "Accept": "application/json"}
    if headers:
        hdrs.update(headers)
    data = None
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        hdrs.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout, context=SSL_CTX) as resp:
            raw = resp.read()
    except urllib.error.HTTPError as e:
        detail = e.read(500).decode("utf-8", errors="replace")
        raise SkdfError(f"{method} {url} -> {e.code}: {detail}") from e
    except (urllib.error.URLError, TimeoutError) as e:
        raise SkdfError(f"{method} {url} failed: {e}") from e
    if not raw or raw == b"null":
        return None
    try:
        return json.loads(raw.decode("utf-8"))
    except json.JSONDecodeError as e:
        raise SkdfError(f"invalid JSON from {url}: {e}") from e


def export_speed_limits(
    token: str,
    *,
    region_gids: Sequence[int] | None = None,
    road_ids: Sequence[int] | None = None,
    timeout: float = 180.0,
) -> list[dict[str, Any]]:
    payload: dict[str, Any] = {"dataset_code": DATASET_CODE}
    if region_gids:
        payload["region_gids"] = [int(x) for x in region_gids]
    if road_ids:
        payload["road_ids"] = [int(x) for x in road_ids]
    url = f"{SKDF_ORIGIN}/service-api-go/api/v1/developer/dataset/export"
    data = _request_json(
        "POST",
        url,
        body=payload,
        headers={"X-External-System-Token": token},
        timeout=timeout,
    )
    if not isinstance(data, list):
        raise SkdfError("speed-limits export did not return a JSON array")
    return data


def flatten_mercator_lines(geom: Any) -> list[list[list[float]]]:
    lines: list[list[list[float]]] = []

    def add_line(raw: Any) -> None:
        pts: list[list[float]] = []
        for pt in raw or []:
            if not isinstance(pt, (list, tuple)) or len(pt) < 2:
                continue
            pts.append(mercator_to_wgs84(float(pt[0]), float(pt[1])))
        if len(pts) >= 2:
            lines.append(pts)

    if not isinstance(geom, dict):
        return lines
    gtype = geom.get("type")
    coords = geom.get("coordinates")
    if gtype == "LineString":
        add_line(coords)
    elif gtype == "MultiLineString":
        for line in coords or []:
            add_line(line)
    elif gtype == "Feature":
        lines.extend(flatten_mercator_lines(geom.get("geometry")))
    elif gtype == "FeatureCollection":
        for feat in geom.get("features") or []:
            lines.extend(flatten_mercator_lines(feat))
    elif gtype == "GeometryCollection":
        for g in geom.get("geometries") or []:
            lines.extend(flatten_mercator_lines(g))
    return lines


def longest_line(lines: Sequence[Sequence[Sequence[float]]]) -> list[list[float]]:
    if not lines:
        return []
    return max(([ [float(c[0]), float(c[1])] for c in line] for line in lines), key=polyline_length_m)


def fetch_part_geometry(part_id: int) -> list[list[float]]:
    url = f"{SKDF_ORIGIN}/api-pg/rpc/f_get_object_geom"
    data = _request_json(
        "POST",
        url,
        body={"object_id": int(part_id), "object_type": 901},
        headers={"Content-Profile": "gis_api_public"},
        timeout=40.0,
    )
    return longest_line(flatten_mercator_lines(data))


def fetch_km_posts_wfs(part_ids: Sequence[int]) -> dict[int, list[dict[str, float]]]:
    out: dict[int, list[dict[str, float]]] = {int(p): [] for p in part_ids}
    ids = [int(p) for p in part_ids]
    for i in range(0, len(ids), 40):
        chunk = ids[i : i + 40]
        cql = "road_part_id IN (" + ",".join(str(x) for x in chunk) + ")"
        query = urllib.parse.urlencode(
            {
                "service": "WFS",
                "version": "1.1.0",
                "request": "GetFeature",
                "typeName": "skdf_open:lyr_eng_km_posts",
                "outputFormat": "application/json",
                "maxFeatures": "5000",
                "CQL_FILTER": cql,
            }
        )
        url = f"{SKDF_ORIGIN}/api-geoserver/skdf_open/wfs?{query}"
        data = _request_json("GET", url, timeout=60.0)
        for feat in (data or {}).get("features") or []:
            props = feat.get("properties") or {}
            geom = feat.get("geometry") or {}
            coords = geom.get("coordinates") or [0, 0]
            if not isinstance(coords, (list, tuple)) or len(coords) < 2:
                continue
            if float(coords[0]) == 0.0 and float(coords[1]) == 0.0:
                continue
            lon, lat = mercator_to_wgs84(float(coords[0]), float(coords[1]))
            part_id = int(props.get("road_part_id") or 0)
            chainage = parse_chainage_m(props.get("location"))
            if part_id not in out or chainage is None:
                continue
            out[part_id].append({"chainage_m": chainage, "lon": lon, "lat": lat})
    for part_id, posts in out.items():
        posts.sort(key=lambda p: p["chainage_m"])
        out[part_id] = posts
    return out


def _along_of_point(coords: Sequence[Sequence[float]], lon: float, lat: float) -> float:
    best_d = float("inf")
    best_along = 0.0
    acc = cumulative_m(coords)
    for i in range(1, len(coords)):
        lon1, lat1 = coords[i - 1]
        lon2, lat2 = coords[i]
        vx = (lon2 - lon1) * math.cos(math.radians((lat1 + lat2) / 2.0))
        vy = lat2 - lat1
        wx = (lon - lon1) * math.cos(math.radians((lat + lat1) / 2.0))
        wy = lat - lat1
        c2 = vx * vx + vy * vy
        t = 0.0 if c2 <= 1e-18 else max(0.0, min(1.0, (wx * vx + wy * vy) / c2))
        plon = lon1 + t * (lon2 - lon1)
        plat = lat1 + t * (lat2 - lat1)
        d = haversine_m(lon, lat, plon, plat)
        if d < best_d:
            best_d = d
            span = acc[i] - acc[i - 1]
            best_along = acc[i - 1] + t * span
    return best_along


def orient_line(coords: list[list[float]], posts: Sequence[dict[str, float]]) -> list[list[float]]:
    if len(coords) < 2 or len(posts) < 2:
        return coords
    first, last = posts[0], posts[-1]
    d_fwd = _along_of_point(coords, first["lon"], first["lat"])
    d_rev_line = reverse_line(coords)
    d_rev = _along_of_point(d_rev_line, first["lon"], first["lat"])
    # Prefer orientation where chainage increases along the line.
    along_first = d_fwd
    along_last = _along_of_point(coords, last["lon"], last["lat"])
    if along_last + 20.0 < along_first:
        return d_rev_line
    if d_rev + 50.0 < d_fwd and along_last <= along_first:
        return d_rev_line
    return coords


def km_locator(
    coords: list[list[float]],
    posts: Sequence[dict[str, float]],
    part_start_m: float,
    part_finish_m: float,
) -> tuple[Callable[[float], list[float]], float, bool]:
    """Return (chainage_m → lonlat, max_post_error_m, monotonic)."""
    line = orient_line(coords, posts) if posts else coords
    length = polyline_length_m(line)
    span = max(1.0, part_finish_m - part_start_m)

    def along_fallback(chainage_m: float) -> list[float]:
        t = (chainage_m - part_start_m) / span
        return point_along(line, min(max(0.0, t), 1.0) * length)

    if len(posts) < 2 or length < 1.0:
        return along_fallback, 0.0, True

    samples: list[tuple[float, float]] = []  # chainage_m, along_m
    errors: list[float] = []
    for post in posts:
        along = _along_of_point(line, post["lon"], post["lat"])
        samples.append((post["chainage_m"], along))
        errors.append(abs(along - _along_of_point(line, post["lon"], post["lat"])))
        # Distance from post to line.
        errors[-1] = dist_point_to_line_m(post["lon"], post["lat"], line)

    samples.sort(key=lambda x: x[0])
    alongs = [s[1] for s in samples]
    monotonic = all(alongs[i] <= alongs[i + 1] + 15.0 for i in range(len(alongs) - 1))
    if not monotonic:
        # Keep longest non-decreasing subsequence by along.
        kept: list[tuple[float, float]] = []
        last = -1.0
        for ch, along in samples:
            if along + 15.0 >= last:
                kept.append((ch, along))
                last = along
        samples = kept
        monotonic = len(samples) >= 2 and all(
            samples[i][1] <= samples[i + 1][1] + 15.0 for i in range(len(samples) - 1)
        )

    def locate(chainage_m: float) -> list[float]:
        if len(samples) < 2:
            return along_fallback(chainage_m)
        if chainage_m <= samples[0][0]:
            return point_along(line, samples[0][1])
        if chainage_m >= samples[-1][0]:
            return point_along(line, samples[-1][1])
        for i in range(1, len(samples)):
            ch0, al0 = samples[i - 1]
            ch1, al1 = samples[i]
            if chainage_m <= ch1:
                t = 0.0 if ch1 <= ch0 else (chainage_m - ch0) / (ch1 - ch0)
                return point_along(line, al0 + t * (al1 - al0))
        return along_fallback(chainage_m)

    max_err = max(errors) if errors else 0.0
    return locate, max_err, monotonic


def interval_usable(row: dict[str, Any]) -> bool:
    gid = int((row.get("value_of_the_road") or {}).get("id") or 0)
    if gid == LOCAL_VALUE_GID:
        return False
    if gid not in {FEDERAL_VALUE_GID, REGIONAL_VALUE_GID, 0}:
        # Unknown classification: keep only numbered refs.
        if extract_head_ref(row.get("road_name")) is None:
            return False
    if gid == REGIONAL_VALUE_GID and extract_head_ref(row.get("road_name")) is None:
        # Regional without a stable ref: still usable for geometry match of long parts.
        try:
            length_km = float(row.get("length") or 0.0)
        except (TypeError, ValueError):
            length_km = 0.0
        if length_km < 5.0:
            return False
    return parse_speed_kmh(row.get("speed_limit_name") or row.get("speed_limit")) is not None


def load_snapshot(path: Path) -> dict[str, Any]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict) or "intervals" not in data:
        raise SkdfError(f"bad SKDF snapshot: {path}")
    return data


def save_snapshot(path: Path, snapshot: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(snapshot, ensure_ascii=False), encoding="utf-8")


def _fetch_geoms(part_ids: Sequence[int], workers: int = 12) -> dict[int, list[list[float]]]:
    geoms: dict[int, list[list[float]]] = {}
    ids = list(dict.fromkeys(int(p) for p in part_ids))
    if not ids:
        return geoms
    print(f"skdf: fetching geometry for {len(ids)} parts", flush=True)
    with ThreadPoolExecutor(max_workers=max(1, workers)) as pool:
        futs = {pool.submit(fetch_part_geometry, pid): pid for pid in ids}
        done = 0
        for fut in as_completed(futs):
            pid = futs[fut]
            done += 1
            try:
                geoms[pid] = fut.result()
            except Exception as e:
                print(f"warn: geom {pid}: {e}", flush=True)
                geoms[pid] = []
            if done % 50 == 0 or done == len(ids):
                print(f"skdf: geom {done}/{len(ids)}", flush=True)
    return geoms


def build_snapshot(
    *,
    token: str,
    region_id: str = NIZHNY_REGION_ID,
    region_gid: int = NIZHNY_REGION_GID,
    raw_rows: list[dict[str, Any]] | None = None,
) -> dict[str, Any]:
    rows = raw_rows if raw_rows is not None else export_speed_limits(token, region_gids=[region_gid])
    usable = [row for row in rows if interval_usable(row)]
    part_ids = sorted({int(row["road_part_id"]) for row in usable if row.get("road_part_id")})
    geoms = _fetch_geoms(part_ids)
    print(f"skdf: fetching km posts for {len(part_ids)} parts", flush=True)
    posts_by_part = fetch_km_posts_wfs(part_ids) if part_ids else {}

    intervals: list[dict[str, Any]] = []
    nonmonotonic: list[int] = []
    max_err = 0.0
    parts_failed_geom: list[int] = []

    grouped: dict[int, list[dict[str, Any]]] = {}
    for row in usable:
        grouped.setdefault(int(row["road_part_id"]), []).append(row)

    for part_id, part_rows in grouped.items():
        geom = geoms.get(part_id) or []
        if len(geom) < 2:
            parts_failed_geom.append(part_id)
            continue
        starts: list[float] = []
        finishes: list[float] = []
        for row in part_rows:
            s = parse_chainage_m(row.get("start"))
            f = parse_chainage_m(row.get("finish"))
            if s is not None:
                starts.append(s)
            if f is not None:
                finishes.append(f)
        posts = posts_by_part.get(part_id) or []
        part_start = min(starts) if starts else (posts[0]["chainage_m"] if posts else 0.0)
        part_finish = max(finishes) if finishes else (
            posts[-1]["chainage_m"] if posts else part_start + polyline_length_m(geom)
        )
        chainage_offset = 0.0
        if posts and starts and min(starts) < 5000 and posts[0]["chainage_m"] >= 20000:
            chainage_offset = posts[0]["chainage_m"] - min(starts)
            part_start += chainage_offset
            part_finish += chainage_offset
        locate, err, mono = km_locator(geom, posts, part_start, part_finish)
        max_err = max(max_err, err)
        if not mono:
            nonmonotonic.append(part_id)
            continue
        for row in part_rows:
            speed = parse_speed_kmh(row.get("speed_limit_name") or row.get("speed_limit"))
            start_m = parse_chainage_m(row.get("start"))
            finish_m = parse_chainage_m(row.get("finish"))
            if speed is None or start_m is None or finish_m is None:
                continue
            start_m += chainage_offset
            finish_m += chainage_offset
            p0 = locate(start_m)
            p1 = locate(finish_m)
            # Slice the oriented official line between those points.
            line = orient_line(geom, posts) if posts else geom
            a = _along_of_point(line, p0[0], p0[1])
            b = _along_of_point(line, p1[0], p1[1])
            coords = slice_line(line, a, b)
            intervals.append(
                {
                    "id": row.get("id"),
                    "road_id": row.get("road_id"),
                    "road_part_id": part_id,
                    "ref": extract_head_ref(row.get("road_name")),
                    "road_name": row.get("road_name"),
                    "speed": speed,
                    "start_m": start_m,
                    "finish_m": finish_m,
                    "coords": coords,
                    "km_post_error_m": round(err, 2),
                    "value_gid": int((row.get("value_of_the_road") or {}).get("id") or 0),
                }
            )

    return {
        "schema": 1,
        "region_id": region_id,
        "region_gid": region_gid,
        "source": "fgis-skdf",
        "dataset_code": DATASET_CODE,
        "fetched_at": datetime.now(timezone.utc).isoformat(),
        "raw_rows": len(rows),
        "intervals": intervals,
        "quality": {
            "nonmonotonic_parts": nonmonotonic,
            "max_km_post_error_m": round(max_err, 2),
            "parts_failed_geom": parts_failed_geom,
            "parts_total": len(part_ids),
        },
    }


def filter_cuts(length: float, cuts: Iterable[float], min_m: float = MIN_SPLIT_M) -> list[float]:
    wanted = sorted({round(c, 3) for c in cuts if min_m <= c <= length - min_m})
    kept: list[float] = []
    last = 0.0
    for cut in wanted:
        if cut - last >= min_m and length - cut >= min_m:
            if kept and cut - kept[-1] < min_m:
                continue
            kept.append(cut)
            last = cut
    return kept


def pick_speed_for_range(
    start_m: float,
    end_m: float,
    zones: Sequence[tuple[float, float, int]],
) -> int | None:
    best_overlap = 0.0
    best_speed: int | None = None
    for z0, z1, speed in zones:
        lo = max(start_m, z0)
        hi = min(end_m, z1)
        overlap = hi - lo
        if overlap <= 0:
            continue
        if overlap > best_overlap + 0.5:
            best_overlap = overlap
            best_speed = speed
        elif abs(overlap - best_overlap) <= 0.5 and best_speed is not None:
            best_speed = min(best_speed, speed)
    return best_speed


def split_edge_by_zones(
    coords: Sequence[Sequence[float]],
    zones: Sequence[tuple[float, float, int]],
    min_m: float = MIN_SPLIT_M,
) -> tuple[list[tuple[list[list[float]], int | None]], int]:
    """Return (pieces with optional speed, rejected_cut_count)."""
    length = polyline_length_m(coords)
    raw_cuts: list[float] = []
    for z0, z1, _speed in zones:
        if 0 < z0 < length:
            raw_cuts.append(z0)
        if 0 < z1 < length:
            raw_cuts.append(z1)
    kept = filter_cuts(length, raw_cuts, min_m=min_m)
    rejected = len({round(c, 3) for c in raw_cuts if 0 < c < length}) - len(kept)
    bounds = [0.0, *kept, length]
    pieces: list[tuple[list[list[float]], int | None]] = []
    for i in range(len(bounds) - 1):
        a, b = bounds[i], bounds[i + 1]
        if b - a < 1.0:
            continue
        piece = slice_line(coords, a, b)
        speed = pick_speed_for_range(a, b, zones)
        pieces.append((piece, speed))
    if not pieces:
        pieces = [([[float(c[0]), float(c[1])] for c in coords], pick_speed_for_range(0.0, length, zones))]
    return pieces, max(0, rejected)


def _edge_ref_keys(edge: dict[str, Any]) -> set[str]:
    return ref_keys(edge.get("ref"))


def match_interval_to_edges(
    interval: dict[str, Any],
    edges: Sequence[dict[str, Any]],
    by_ref: dict[str, list[int]],
    grid: dict[tuple[int, int], list[int]],
) -> tuple[list[int], str]:
    """Return (candidate edge indexes, reason)."""
    official = interval.get("coords") or []
    if len(official) < 2:
        return [], "no_geom"
    skdf_ref = interval.get("ref")
    if skdf_ref:
        keys = ref_keys(skdf_ref)
        found: set[int] = set()
        for key in keys:
            found.update(by_ref.get(key, ()))
        idxs = sorted(found)
        if not idxs:
            return [], "no_ref_match"
    else:
        idxs = [
            i
            for i in grid_candidates(grid, official)
            if str(edges[i].get("class") or "") in NO_REF_CLASSES
        ]
    scored: list[tuple[float, int, float]] = []  # dist, idx, heading_cos
    for i in idxs:
        edge = edges[i]
        coords = edge.get("coords") or []
        if len(coords) < 2:
            continue
        if str(edge.get("class") or "") not in MATCH_CLASSES:
            continue
        dist = mean_sample_distance_m(coords, official)
        if dist > MAX_MATCH_DISTANCE_M:
            continue
        cos = heading_cos(official, coords)
        if abs(cos) < MIN_HEADING_COS:
            continue
        ov = overlap_along_m(coords, official, MAX_MATCH_DISTANCE_M)
        if ov is None:
            continue
        cover = (ov[1] - ov[0]) / max(1.0, polyline_length_m(coords))
        if cover < MIN_OVERLAP_RATIO and (ov[1] - ov[0]) < MIN_SPLIT_M:
            continue
        scored.append((dist, i, cos))
    if not scored:
        return [], "far"
    same = [s for s in scored if s[2] >= MIN_HEADING_COS]
    opp = [s for s in scored if s[2] <= -MIN_HEADING_COS]
    same.sort()
    if same:
        best_d = same[0][0]
        # Sequential OSM pieces of one carriageway all sit on the official
        # line. A same-heading frontage is farther — drop it, keep the closest band.
        same = [s for s in same if s[0] <= best_d + 10.0]
    if not skdf_ref:
        same_ways = {edges[s[1]].get("wayId") for s in same}
        if len(same_ways) > 1:
            return [], "ambiguous_parallel"
    return sorted({s[1] for s in same + opp}), "ok"


def apply_overlay(edges: list[dict[str, Any]], snapshot: dict[str, Any]) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    intervals = list(snapshot.get("intervals") or [])
    by_ref: dict[str, list[int]] = {}
    for i, edge in enumerate(edges):
        for key in _edge_ref_keys(edge):
            by_ref.setdefault(key, []).append(i)
    grid = build_edge_grid(edges)

    zones_by_edge: dict[int, list[tuple[float, float, int]]] = {}
    matched_edge_ids: set[int] = set()
    matched_intervals = 0
    skipped = {"no_geom": 0, "no_ref_match": 0, "far": 0, "ambiguous_parallel": 0}
    disagreements = 0
    official_km = 0.0
    covered_km = 0.0
    ref_intervals = 0
    ref_matched = 0

    for n, interval in enumerate(intervals, 1):
        coords = interval.get("coords") or []
        official_km += polyline_length_m(coords) / 1000.0
        if interval.get("ref"):
            ref_intervals += 1
        idxs, reason = match_interval_to_edges(interval, edges, by_ref, grid)
        if n % 50 == 0 or n == len(intervals):
            print(f"skdf overlay {n}/{len(intervals)}", flush=True)
        if reason != "ok":
            skipped[reason] = skipped.get(reason, 0) + 1
            continue
        matched_intervals += 1
        if interval.get("ref"):
            ref_matched += 1
        speed = int(interval["speed"])
        covered_here = 0.0
        for i in idxs:
            edge = edges[i]
            ov = overlap_along_m(edge["coords"], coords, MAX_MATCH_DISTANCE_M)
            if ov is None:
                continue
            zones_by_edge.setdefault(i, []).append((ov[0], ov[1], speed))
            matched_edge_ids.add(i)
            covered_here += max(0.0, ov[1] - ov[0])
            osm_speed = edge.get("maxspeed")
            if osm_speed is not None and int(osm_speed) != speed:
                disagreements += 1
        covered_km += covered_here / 1000.0

    rejected_cuts = 0
    new_edges: list[dict[str, Any]] = []
    next_id = 1
    for i, edge in enumerate(edges):
        zones = zones_by_edge.get(i)
        if not zones:
            cloned = dict(edge)
            cloned["id"] = next_id
            next_id += 1
            new_edges.append(cloned)
            continue
        pieces, rej = split_edge_by_zones(edge["coords"], zones)
        rejected_cuts += rej
        for coords, speed in pieces:
            cloned = dict(edge)
            cloned["id"] = next_id
            cloned["coords"] = coords
            cloned["lengthM"] = round(polyline_length_m(coords), 3)
            if speed is not None:
                cloned["maxspeed"] = int(speed)
                cloned["maxspeedForward"] = int(speed)
                cloned["maxspeedBackward"] = int(speed)
            next_id += 1
            new_edges.append(cloned)

    try:
        from osm_to_tboxroads import assign_shared_nodes

        assign_shared_nodes(new_edges)
    except Exception:
        pass

    quality = dict(snapshot.get("quality") or {})
    ref_ratio = (ref_matched / ref_intervals) if ref_intervals else 1.0
    report = {
        "official_intervals": len(intervals),
        "matched_intervals": matched_intervals,
        "official_km": round(official_km, 3),
        "covered_osm_km": round(covered_km, 3),
        "matched_osm_edges": len(matched_edge_ids),
        "disagreements_osm_skdf": disagreements,
        "skipped": skipped,
        "rejected_splits_200m": rejected_cuts,
        "max_km_post_error_m": quality.get("max_km_post_error_m", 0.0),
        "nonmonotonic_parts": quality.get("nonmonotonic_parts") or [],
        "parts_failed_geom": quality.get("parts_failed_geom") or [],
        "ref_intervals": ref_intervals,
        "ref_matched": ref_matched,
        "ref_match_ratio": round(ref_ratio, 4),
        "output_edges": len(new_edges),
        "input_edges": len(edges),
    }
    return new_edges, report


def raise_if_quality_failed(report: dict[str, Any]) -> None:
    err = float(report.get("max_km_post_error_m") or 0.0)
    if err > FAIL_KM_POST_ERROR_M:
        raise SkdfError(f"km-post bind error {err:.1f} m exceeds {FAIL_KM_POST_ERROR_M:g} m")
    ref_intervals = int(report.get("ref_intervals") or 0)
    ratio = float(report.get("ref_match_ratio") or 0.0)
    if ref_intervals > 0 and ratio < FAIL_MIN_REF_MATCH_RATIO:
        raise SkdfError(
            f"SKDF ref match ratio {ratio:.3f} is below {FAIL_MIN_REF_MATCH_RATIO:g} "
            f"({report.get('ref_matched')}/{ref_intervals})"
        )


def overlay_edges_from_snapshot(
    edges: list[dict[str, Any]],
    snapshot_path: Path,
    report_path: Path | None = None,
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    snapshot = load_snapshot(snapshot_path)
    new_edges, report = apply_overlay(edges, snapshot)
    if report_path is not None:
        write_report(report_path, report)
    raise_if_quality_failed(report)
    return new_edges, report


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"skdf report: {path}", flush=True)


def default_snapshot_path(region_id: str = NIZHNY_REGION_ID) -> Path:
    return DEFAULT_SNAPSHOT_DIR / f"{region_id}-skdf-speed-limits.json"


def main(argv: Sequence[str] | None = None) -> int:
    p = argparse.ArgumentParser(description="Fetch / overlay ФГИС СКДФ speed-limits")
    p.add_argument("--region-id", default=NIZHNY_REGION_ID)
    p.add_argument("--region-gid", type=int, default=NIZHNY_REGION_GID)
    p.add_argument("--token-file", type=Path, default=None)
    p.add_argument("--snapshot-out", type=Path, default=None)
    p.add_argument("--raw-json", type=Path, default=None, help="Reuse a saved export array")
    p.add_argument("--fetch-snapshot", action="store_true")
    args = p.parse_args(argv)
    if not args.fetch_snapshot:
        raise SystemExit("specify --fetch-snapshot")
    token = load_token(args.token_file)
    raw = None
    if args.raw_json:
        raw = json.loads(args.raw_json.read_text(encoding="utf-8"))
        if isinstance(raw, dict):
            raw = raw.get("intervals") or raw.get("data") or raw
    snapshot = build_snapshot(
        token=token,
        region_id=args.region_id,
        region_gid=args.region_gid,
        raw_rows=raw if isinstance(raw, list) else None,
    )
    out = args.snapshot_out or default_snapshot_path(args.region_id)
    save_snapshot(out, snapshot)
    print(
        f"wrote {out} intervals={len(snapshot['intervals'])} "
        f"failed_geom={len(snapshot['quality']['parts_failed_geom'])} "
        f"nonmono={len(snapshot['quality']['nonmonotonic_parts'])}",
        flush=True,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
