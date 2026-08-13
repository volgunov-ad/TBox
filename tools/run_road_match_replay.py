#!/usr/bin/env python3
"""Replay geo-debug field logs through the production Kotlin road matcher.

The script downloads/extracts the published map bundle (or accepts a local map
zip/directory), invokes the optional Robolectric replay test, prints metrics and
optionally checks a committed regression baseline.

Examples:
  python tools/run_road_match_replay.py \
    --region ru-moscow \
    --logs ~/logs/tbox_geo_debug_*.txt \
    --report /tmp/road_match_replay.json

  python tools/run_road_match_replay.py \
    --maps-dir /tmp/maps \
    --logs /tmp/logs/*.txt \
    --baseline tools/road_match_replay_baseline.json
"""

from __future__ import annotations

import argparse
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
BUILD_GRADLE = ROOT / "app" / "build.gradle.kts"
DOWNLOAD_API = "https://cloud-api.yandex.net/v1/disk/public/resources/download"


def release_public_key() -> str:
    text = BUILD_GRADLE.read_text(encoding="utf-8")
    match = re.search(
        r'"UPDATE_RELEASE_PUBLIC_KEY"\s*,\s*"\\"([^"]+)\\""',
        text,
        re.MULTILINE,
    )
    if not match:
        raise RuntimeError("UPDATE_RELEASE_PUBLIC_KEY not found")
    return match.group(1)


def public_download_url(public_key: str, path: str) -> str:
    query = urllib.parse.urlencode({"public_key": public_key, "path": path})
    with urllib.request.urlopen(f"{DOWNLOAD_API}?{query}", timeout=30) as response:
        return json.load(response)["href"]


def download_region(region: str, cache_dir: Path) -> Path:
    cache_dir.mkdir(parents=True, exist_ok=True)
    key = release_public_key()
    catalog_url = public_download_url(key, "/maps/catalog.json")
    with urllib.request.urlopen(catalog_url, timeout=30) as response:
        catalog = json.load(response)
    entry = next((item for item in catalog.get("regions", []) if item.get("id") == region), None)
    if not entry or not str(entry.get("url", "")).startswith("yandex-disk:"):
        raise RuntimeError(f"published map not found for region {region}")
    disk_path = str(entry["url"])[len("yandex-disk:") :]
    target = cache_dir / Path(disk_path).name
    expected_size = int(entry.get("bytes") or 0)
    if not target.is_file() or (expected_size and target.stat().st_size != expected_size):
        print(f"Downloading {region} map ({expected_size} bytes) …", file=sys.stderr)
        urllib.request.urlretrieve(public_download_url(key, disk_path), target)
    return target


def extract_bundle(zip_path: Path, work_dir: Path, region: str) -> Path:
    maps_dir = work_dir / "maps"
    bundle = maps_dir / f"{region}.tboxroads.bundle"
    if bundle.exists():
        shutil.rmtree(bundle)
    bundle.mkdir(parents=True)
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(bundle)
    if not (bundle / "index.json").is_file():
        raise RuntimeError(f"{zip_path} is not a v4 bundle (index.json missing)")
    return maps_dir


def run_replay(maps_dir: Path, logs: list[Path], report: Path) -> None:
    env = os.environ.copy()
    env["TBOX_ROADMATCH_REPLAY_MAPS_DIR"] = str(maps_dir.resolve())
    env["TBOX_ROADMATCH_REPLAY_LOGS"] = os.pathsep.join(str(p.resolve()) for p in logs)
    env["TBOX_ROADMATCH_REPLAY_REPORT"] = str(report.resolve())
    command = [
        str(ROOT / "gradlew"),
        "testRuDebugUnitTest",
        "--tests",
        "vad.dashing.tbox.location.roadmatch.RoadMatchFieldReplayTest",
    ]
    subprocess.run(command, cwd=ROOT, env=env, check=True)
    if not report.is_file():
        raise RuntimeError("replay completed without report")


def print_report(data: dict[str, Any]) -> None:
    print(
        "file".ljust(39),
        "ticks corr rate  high med hold low none switch edges nearRej maxGap",
    )
    for item in data["logs"]:
        print(
            str(item["file"]).ljust(39),
            f"{item['ticks']:5d} {item['corrections']:4d} "
            f"{item['correctionRate'] * 100:4.1f}% "
            f"{item['high']:4d} {item['medium']:3d} {item['holdEdge']:4d} "
            f"{item['low']:3d} {item['noCandidate']:4d} {item['switches']:6d} "
            f"{item['uniqueEdges']:5d} {item['nearRejected']:7d} "
            f"{item['maxMovingNoCorrectionTicks']:6d}",
        )


def check_baseline(data: dict[str, Any], baseline_path: Path) -> None:
    baseline = json.loads(baseline_path.read_text(encoding="utf-8"))
    rules = baseline.get("logs", {})
    failures: list[str] = []
    for item in data["logs"]:
        rule = rules.get(item["file"])
        if not rule:
            continue
        for metric, minimum in rule.get("min", {}).items():
            if float(item[metric]) < float(minimum):
                failures.append(f"{item['file']}: {metric}={item[metric]} < {minimum}")
        for metric, maximum in rule.get("max", {}).items():
            if float(item[metric]) > float(maximum):
                failures.append(f"{item['file']}: {metric}={item[metric]} > {maximum}")
    if failures:
        raise RuntimeError("road-match replay regression:\n  " + "\n  ".join(failures))
    print(f"Baseline OK: {baseline_path}")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--logs", nargs="+", type=Path, required=True)
    source = parser.add_mutually_exclusive_group()
    source.add_argument("--maps-dir", type=Path)
    source.add_argument("--map-zip", type=Path)
    parser.add_argument("--region", default="ru-moscow")
    parser.add_argument("--cache-dir", type=Path, default=Path.home() / ".cache/tbox-road-replay")
    parser.add_argument("--report", type=Path, default=Path("/tmp/road_match_replay.json"))
    parser.add_argument("--baseline", type=Path)
    args = parser.parse_args()

    logs = [path for path in args.logs if path.is_file()]
    if len(logs) != len(args.logs):
        missing = [str(path) for path in args.logs if not path.is_file()]
        parser.error("missing logs: " + ", ".join(missing))

    with tempfile.TemporaryDirectory(prefix="tbox-road-replay-") as temporary:
        if args.maps_dir:
            maps_dir = args.maps_dir
        else:
            zip_path = args.map_zip or download_region(args.region, args.cache_dir)
            maps_dir = extract_bundle(zip_path, Path(temporary), args.region)
        args.report.parent.mkdir(parents=True, exist_ok=True)
        run_replay(maps_dir, logs, args.report)

    data = json.loads(args.report.read_text(encoding="utf-8"))
    print_report(data)
    if args.baseline:
        check_baseline(data, args.baseline)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
