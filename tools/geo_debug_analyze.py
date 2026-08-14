#!/usr/bin/env python3
"""
Analyze TBox Monitor geo-debug logs (tbox_geo_debug_*.txt).

Parses 1 Hz blocks from GeoDebugLogRecorder and prints a trip summary:
  mode/source, truth-loss windows, shadow peaks, hardResync, reverse gear,
  online yaw calib (if present), session integrals (integ.*), left/right turn
  scale estimates, rough k_speed from CAN integ vs GNSS path, bitrate gaps,
  HU turn signals (turn.left/right/hazard/side) when the log has them.

Stdlib only (no openpyxl). Optional CSV of per-tick fields.

Examples:
  python3 tools/geo_debug_analyze.py ~/Downloads/tbox_geo_debug_20260806_072827.txt
  python3 tools/geo_debug_analyze.py log1.txt log2.txt --csv /tmp/ticks.csv
  python3 tools/geo_debug_analyze.py log.txt --json-summary /tmp/summary.json
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import re
import statistics
import sys
from collections import Counter
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Optional


KV_RE = re.compile(r"([A-Za-z0-9_.]+)=([^\s]+)")
HEADER_RE = re.compile(
    r"(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}\.\d+)\s+elapsedMs=(\d+)",
)

# Earth radius for GNSS path length (WGS84 mean).
_EARTH_R_M = 6_371_000.0


def _f(x: Any, default: Optional[float] = None) -> Optional[float]:
    if x is None or x in ("-", "", "N/A"):
        return default
    try:
        return float(x)
    except (TypeError, ValueError):
        return default


def _wrap_delta(from_deg: float, to_deg: float) -> float:
    d = to_deg - from_deg
    while d > 180.0:
        d -= 360.0
    while d < -180.0:
        d += 360.0
    return d


def _ang_diff(a: float, b: float) -> float:
    return _wrap_delta(b, a)


def _haversine_m(
    lat1: float,
    lon1: float,
    lat2: float,
    lon2: float,
) -> Optional[float]:
    if not all(math.isfinite(x) for x in (lat1, lon1, lat2, lon2)):
        return None
    if lat1 == 0.0 and lon1 == 0.0:
        return None
    if lat2 == 0.0 and lon2 == 0.0:
        return None
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlmb = math.radians(lon2 - lon1)
    a = (
        math.sin(dphi / 2) ** 2
        + math.cos(p1) * math.cos(p2) * math.sin(dlmb / 2) ** 2
    )
    return 2 * _EARTH_R_M * math.asin(min(1.0, math.sqrt(a)))


@dataclass
class Tick:
    ts: str
    elapsed_ms: int
    fields: dict[str, str] = field(default_factory=dict)

    def get(self, key: str, default: Optional[str] = None) -> Optional[str]:
        if key in self.fields:
            return self.fields[key]
        # Common aliases from "calib.biasYaw=…" / "drive.speedScale=…" / "online.phase=…"
        aliases = {
            "biasYaw": ("calib.biasYaw",),
            "speedScale": ("drive.speedScale",),
            "yawScale": ("yawScale", "drive.yawScale"),
            "phase": ("online.phase",),
            "lastBiasStep": ("lastBiasStep", "online.lastBiasStep"),
            "lastScaleCand": ("lastScaleCand", "online.lastScaleCand"),
            "distM": ("integ.distM",),
            "yawDebDeg": ("integ.yawDebDeg",),
            "yawRawDeg": ("integ.yawRawDeg",),
            "pitchDeg": ("integ.pitchDeg",),
            "rollDeg": ("integ.rollDeg",),
            "steerPathDeg": ("integ.steerPathDeg",),
            "dDistM": ("integ.dDistM",),
            "dYawDebDeg": ("integ.dYawDebDeg",),
            "dYawRawDeg": ("integ.dYawRawDeg",),
            "dPitchDeg": ("integ.dPitchDeg",),
            "dRollDeg": ("integ.dRollDeg",),
            "dSteerPathDeg": ("integ.dSteerPathDeg",),
        }
        for alt in aliases.get(key, ()):
            if alt in self.fields:
                return self.fields[alt]
        return default

    def num(self, key: str, default: Optional[float] = None) -> Optional[float]:
        return _f(self.get(key), default)


def parse_log(path: Path) -> list[Tick]:
    text = path.read_text(encoding="utf-8", errors="replace")
    # Split on tick headers; keep header line inside each chunk.
    parts = re.split(r"\n(?=--- )", text)
    ticks: list[Tick] = []
    for part in parts:
        part = part.strip()
        if not part.startswith("--- "):
            continue
        lines = part.split("\n")
        header = lines[0][4:].strip()  # drop leading "--- "
        m = HEADER_RE.match(header)
        if not m:
            continue
        fields: dict[str, str] = {}
        for line in lines[1:]:
            if line.startswith("nmea|") or line.startswith("#"):
                continue
            for k, v in KV_RE.findall(line):
                # Normalize compound prefixes used in the log line.
                if k.startswith("constant."):
                    k = k[len("constant.") :]
                fields[k] = v
                # Also store short aliases for calib./online./integ. keys.
                if k.startswith("calib."):
                    fields[k[len("calib.") :]] = v
                if k.startswith("drive."):
                    fields[k[len("drive.") :]] = v
                if k.startswith("online."):
                    fields[k[len("online.") :]] = v
                if k.startswith("integ."):
                    fields[k[len("integ.") :]] = v
                if k.startswith("steering."):
                    fields[k[len("steering.") :]] = v
        ticks.append(Tick(ts=m.group(1), elapsed_ms=int(m.group(2)), fields=fields))
    return ticks


def _contiguous_groups(
    ticks: list[Tick],
    pred,
    gap_ms: int = 2500,
) -> list[list[Tick]]:
    matched = [t for t in ticks if pred(t)]
    if not matched:
        return []
    groups: list[list[Tick]] = [[matched[0]]]
    for t in matched[1:]:
        if t.elapsed_ms - groups[-1][-1].elapsed_ms < gap_ms:
            groups[-1].append(t)
        else:
            groups.append([t])
    return groups


def _median(xs: list[float]) -> Optional[float]:
    if not xs:
        return None
    return float(statistics.median(xs))


def _round_opt(v: Optional[float], nd: int = 3) -> Optional[float]:
    if v is None or not math.isfinite(v):
        return None
    return round(v, nd)


def _last_integ_tick(ticks: list[Tick]) -> Optional[Tick]:
    for t in reversed(ticks):
        if t.num("distM") is not None or t.num("integ.distM") is not None:
            return t
    return None


def summarize_session_integrals(ticks: list[Tick]) -> dict[str, Any]:
    """End-of-session integ.* totals + rough GNSS path / k_speed."""
    last = _last_integ_tick(ticks)
    if last is None:
        return {"present": False}

    def end(key: str) -> Optional[float]:
        return last.num(key)

    gnss_path = 0.0
    gnss_segments = 0
    can_path_truth = 0.0
    for a, b in zip(ticks, ticks[1:]):
        if a.get("truth") != "true" or b.get("truth") != "true":
            continue
        lat0, lon0 = a.num("lat"), a.num("lon")
        lat1, lon1 = b.num("lat"), b.num("lon")
        if lat0 is None or lon0 is None or lat1 is None or lon1 is None:
            continue
        step = _haversine_m(lat0, lon0, lat1, lon1)
        if step is None or step > 80.0:
            # Skip teleport / first fix jumps.
            continue
        gnss_path += step
        gnss_segments += 1
        dd = b.num("dDistM")
        if dd is not None and dd >= 0:
            can_path_truth += dd
        else:
            d0, d1 = a.num("distM"), b.num("distM")
            if d0 is not None and d1 is not None and d1 >= d0:
                can_path_truth += d1 - d0

    can_total = end("distM")
    k_speed = None
    if can_path_truth > 50.0 and gnss_path > 50.0:
        k_speed = round(gnss_path / can_path_truth, 4)
    elif can_total is not None and can_total > 50.0 and gnss_path > 50.0:
        k_speed = round(gnss_path / can_total, 4)

    return {
        "present": True,
        "distM": _round_opt(end("distM")),
        "yawRawDeg": _round_opt(end("yawRawDeg")),
        "yawDebDeg": _round_opt(end("yawDebDeg")),
        "pitchDeg": _round_opt(end("pitchDeg")),
        "rollDeg": _round_opt(end("rollDeg")),
        "steerPathDeg": _round_opt(end("steerPathDeg")),
        "nSpeed": last.get("nSpeed") or last.get("integ.nSpeed"),
        "nGyro": last.get("nGyro") or last.get("integ.nGyro"),
        "nSteer": last.get("nSteer") or last.get("integ.nSteer"),
        "gnssPathM": round(gnss_path, 2) if gnss_segments else None,
        "gnssPathSegments": gnss_segments,
        "canPathTruthM": round(can_path_truth, 2) if can_path_truth > 0 else None,
        "kSpeedEstimate": k_speed,
    }


def summarize(ticks: list[Tick]) -> dict[str, Any]:
    if not ticks:
        return {"error": "no ticks"}

    def mode_count(key: str) -> dict[str, int]:
        return dict(Counter(t.get(key) or "?" for t in ticks))

    shadows = [s for s in (t.num("shadowDistM") for t in ticks) if s is not None]
    bitrates = [b for b in (t.num("bitrate_bps") for t in ticks) if b is not None]

    truth_false = _contiguous_groups(ticks, lambda t: t.get("truth") == "false")
    truth_loss = []
    for g in truth_false:
        sh = [s for s in (t.num("shadowDistM") for t in g) if s is not None]
        cans = [c or 0.0 for c in (t.num("can.accountingKmh") for t in g)]
        truth_loss.append(
            {
                "start": g[0].ts,
                "end": g[-1].ts,
                "n": len(g),
                "durSec": round((g[-1].elapsed_ms - g[0].elapsed_ms) / 1000.0, 1),
                "canMax": round(max(cans), 1) if cans else None,
                "shadowMin": round(min(sh), 2) if sh else None,
                "shadowMax": round(max(sh), 2) if sh else None,
                "bitrateMean": round(
                    statistics.mean(
                        [b for b in (t.num("bitrate_bps") for t in g) if b is not None]
                        or [0.0]
                    ),
                    0,
                ),
                "bearingSrc": dict(Counter(t.get("bearingSrc") or "?" for t in g)),
            }
        )

    hard = []
    for t in ticks:
        if t.get("hardResync") == "true":
            hard.append(
                {
                    "ts": t.ts,
                    "shadowDistM": t.num("shadowDistM"),
                    "thresholdM": t.num("thresholdM"),
                    "bearing": t.num("bearing"),
                    "course": t.num("course"),
                    "can": t.num("can.accountingKmh"),
                }
            )

    # Shadow peaks (top absolute)
    peak_ticks = sorted(
        [t for t in ticks if t.num("shadowDistM") is not None],
        key=lambda t: t.num("shadowDistM") or 0.0,
        reverse=True,
    )[:10]
    shadow_peaks = [
        {
            "ts": t.ts,
            "shadowDistM": round(t.num("shadowDistM") or 0.0, 2),
            "truth": t.get("truth"),
            "liveUsable": t.get("liveUsable"),
            "retaining": t.get("retaining"),
            "hardResync": t.get("hardResync"),
            "can": t.num("can.accountingKmh"),
        }
        for t in peak_ticks
        if (t.num("shadowDistM") or 0.0) >= 5.0
    ]

    # Reverse transitions
    reverse_events = []
    prev_key = None
    for t in ticks:
        key = (t.get("huPrnd"), t.get("tboxPrnd"), t.get("huSwitch"))
        if key != prev_key:
            reverse_events.append(
                {
                    "ts": t.ts,
                    "huPrnd": t.get("huPrnd"),
                    "tboxPrnd": t.get("tboxPrnd"),
                    "huSwitch": t.get("huSwitch"),
                    "bearing": t.num("bearing"),
                    "bearingSrc": t.get("bearingSrc"),
                    "course": t.num("course"),
                    "can": t.num("can.accountingKmh"),
                }
            )
            prev_key = key

    # Online calib
    online_phases = Counter(t.get("phase") for t in ticks if t.get("phase"))
    online_bias_steps = [
        t.num("lastBiasStep")
        for t in ticks
        if t.num("lastBiasStep") is not None
    ]
    online_scale_cands = [
        t.num("lastScaleCand")
        for t in ticks
        if t.num("lastScaleCand") is not None
    ]
    bias_series = [t.num("biasYaw") for t in ticks if t.num("biasYaw") is not None]
    scale_series = [t.num("yawScale") for t in ticks if t.num("yawScale") is not None]

    # Left/right turn scale: prefer high-rate integ.dYawDebDeg when present.
    turn_left, turn_right = estimate_turn_scales(ticks)
    integ = summarize_session_integrals(ticks)

    low_br = _contiguous_groups(
        ticks,
        lambda t: (t.num("bitrate_bps") is not None and (t.num("bitrate_bps") or 0) < 1000)
        or t.get("nmea.tick") == "(none)",
        gap_ms=3000,
    )
    bitrate_gaps = []
    for g in low_br:
        if len(g) < 5:
            continue
        bitrate_gaps.append(
            {
                "start": g[0].ts,
                "end": g[-1].ts,
                "durSec": round((g[-1].elapsed_ms - g[0].elapsed_ms) / 1000.0, 1),
                "n": len(g),
                "truthFalse": sum(1 for t in g if t.get("truth") == "false"),
            }
        )

    return {
        "ticks": len(ticks),
        "start": ticks[0].ts,
        "end": ticks[-1].ts,
        "spanMin": round((ticks[-1].elapsed_ms - ticks[0].elapsed_ms) / 60000.0, 2),
        "source": mode_count("source"),
        "mockMode": mode_count("mockMode"),
        "mockOn": mode_count("mockOn"),
        "truth": mode_count("truth"),
        "liveUsable": mode_count("liveUsable"),
        "retaining": mode_count("retaining"),
        "indicator": mode_count("indicator"),
        "bearingSrc": mode_count("bearingSrc"),
        "hardResyncCount": sum(1 for t in ticks if t.get("hardResync") == "true"),
        "shadow": {
            "n": len(shadows),
            "min": round(min(shadows), 3) if shadows else None,
            "max": round(max(shadows), 3) if shadows else None,
            "mean": round(statistics.mean(shadows), 3) if shadows else None,
            "p95": round(sorted(shadows)[int(0.95 * (len(shadows) - 1))], 3)
            if len(shadows) >= 2
            else (round(shadows[0], 3) if shadows else None),
        },
        "bitrate": {
            "mean": round(statistics.mean(bitrates), 0) if bitrates else None,
            "min": round(min(bitrates), 0) if bitrates else None,
            "zeroShare": round(
                sum(1 for b in bitrates if b <= 0) / len(bitrates), 3
            )
            if bitrates
            else None,
        },
        "truthLossWindows": truth_loss,
        "hardResyncEvents": hard,
        "shadowPeaks": shadow_peaks,
        "prndTransitions": reverse_events,
        "online": {
            "phases": {str(k): v for k, v in online_phases.items() if k},
            "biasStepCount": len(online_bias_steps),
            "scaleCandCount": len(online_scale_cands),
            "biasYawStart": bias_series[0] if bias_series else None,
            "biasYawEnd": bias_series[-1] if bias_series else None,
            "yawScaleStart": scale_series[0] if scale_series else None,
            "yawScaleEnd": scale_series[-1] if scale_series else None,
            "lastScaleCandMedian": _median(online_scale_cands),
        },
        "integrals": integ,
        "turnScale": {
            "left": turn_left,
            "right": turn_right,
            "leftRightMedianRatio": (
                round(turn_left["median"] / turn_right["median"], 3)
                if turn_left.get("median") and turn_right.get("median")
                else None
            ),
        },
        "bitrateGaps": bitrate_gaps,
        "calib": {
            "biasYaw": ticks[0].num("biasYaw"),
            "yawScale": ticks[0].num("yawScale"),
            "yawSign": ticks[0].get("yawSign"),
            "speedScale": ticks[0].num("speedScale") or ticks[0].num("drive.speedScale"),
            "lagMs": ticks[0].get("lagMs"),
        },
    }


def estimate_turn_scales(ticks: list[Tick]) -> tuple[dict[str, Any], dict[str, Any]]:
    """Compare ∫yaw (integ.dYawDebDeg or yawDebiased·dt) vs GNSS course Δ (≥25°)."""
    work = [t for t in ticks if t.get("truth") == "true"]
    left: list[float] = []
    right: list[float] = []
    left_meta: list[dict[str, Any]] = []
    right_meta: list[dict[str, Any]] = []
    i = 0
    while i < len(work) - 2:
        can0 = work[i].num("can.accountingKmh") or 0.0
        d_yaw0 = work[i].num("dYawDebDeg")
        yaw0 = work[i].num("yawDebiased")
        # Prefer session-integrator tick delta; fall back to rate sample.
        has_integ = d_yaw0 is not None
        rate_ok = yaw0 is not None and abs(yaw0) >= 1.5
        if can0 < 18 or (not has_integ and not rate_ok):
            i += 1
            continue
        if has_integ and abs(d_yaw0 or 0.0) < 0.3 and not rate_ok:
            i += 1
            continue
        gyro = 0.0
        j = i
        end_limit = work[i].elapsed_ms + 10_000
        used_integ = False
        while j + 1 < len(work) and work[j + 1].elapsed_ms <= end_limit:
            a, b = work[j], work[j + 1]
            dt = (b.elapsed_ms - a.elapsed_ms) / 1000.0
            if dt <= 0 or dt > 1.5:
                break
            if (b.num("can.accountingKmh") or 0.0) < 14:
                break
            dy = b.num("dYawDebDeg")
            if dy is not None:
                gyro += dy
                used_integ = True
            else:
                ya = a.num("yawDebiased")
                if ya is None:
                    break
                gyro += ya * dt
            j += 1
            if abs(gyro) >= 25:
                break
        if j <= i or abs(gyro) < 25:
            i += 1
            continue
        c0 = work[i].num("course")
        c1 = work[j].num("course")
        if c0 is None or c1 is None or c0 == 0 or c1 == 0:
            i = j + 1
            continue
        gd = _wrap_delta(c0, c1)
        if abs(gd) < 25 * 0.45:
            i = j + 1
            continue
        scale = -gd / gyro
        if not (0.4 <= scale <= 2.5):
            i = j + 1
            continue
        meta = {
            "ts": work[i].ts,
            "gyroDeg": round(gyro, 2),
            "gnssDeg": round(gd, 2),
            "scale": round(scale, 3),
            "durSec": round((work[j].elapsed_ms - work[i].elapsed_ms) / 1000.0, 1),
            "source": "integ" if used_integ else "rate",
        }
        if gyro > 0:
            left.append(scale)
            left_meta.append(meta)
        else:
            right.append(scale)
            right_meta.append(meta)
        i = j + 1

    def pack(scales: list[float], meta: list[dict[str, Any]]) -> dict[str, Any]:
        if not scales:
            return {"n": 0, "median": None, "mean": None, "segments": []}
        return {
            "n": len(scales),
            "median": round(_median(scales) or 0.0, 3),
            "mean": round(statistics.mean(scales), 3),
            "min": round(min(scales), 3),
            "max": round(max(scales), 3),
            "segments": meta[:12],
        }

    return pack(left, left_meta), pack(right, right_meta)


def print_summary(path: Path, summary: dict[str, Any]) -> None:
    print(f"\n======== {path.name} ========")
    if "error" in summary:
        print(summary["error"])
        return
    print(
        f"ticks={summary['ticks']}  {summary['start']} .. {summary['end']}  "
        f"({summary['spanMin']} min)"
    )
    print(f"source={summary['source']}  mockMode={summary['mockMode']}  mockOn={summary['mockOn']}")
    print(
        f"truth={summary['truth']}  liveUsable={summary['liveUsable']}  "
        f"retaining={summary['retaining']}"
    )
    print(f"bearingSrc={summary['bearingSrc']}  indicator={summary['indicator']}")
    cal = summary.get("calib") or {}
    print(
        f"calib start: biasYaw={cal.get('biasYaw')} yawScale={cal.get('yawScale')} "
        f"yawSign={cal.get('yawSign')} speedScale={cal.get('speedScale')} lagMs={cal.get('lagMs')}"
    )
    integ = summary.get("integrals") or {}
    if integ.get("present"):
        print(
            f"integ end: distM={integ.get('distM')} yawDeb={integ.get('yawDebDeg')} "
            f"yawRaw={integ.get('yawRawDeg')} pitch={integ.get('pitchDeg')} "
            f"rollZ={integ.get('rollDeg')} steerPath={integ.get('steerPathDeg')} "
            f"nSpeed={integ.get('nSpeed')} nGyro={integ.get('nGyro')} nSteer={integ.get('nSteer')}"
        )
        print(
            f"integ vs GNSS: gnssPathM={integ.get('gnssPathM')} "
            f"canPathTruthM={integ.get('canPathTruthM')} "
            f"kSpeed≈{integ.get('kSpeedEstimate')} "
            f"(segments={integ.get('gnssPathSegments')})"
        )
    else:
        print("integ: (no integ.* fields — older log or recording without accumulators)")
    sh = summary.get("shadow") or {}
    print(
        f"shadowDist: n={sh.get('n')} min={sh.get('min')} mean={sh.get('mean')} "
        f"p95={sh.get('p95')} max={sh.get('max')}"
    )
    br = summary.get("bitrate") or {}
    print(
        f"bitrate_bps: mean={br.get('mean')} min={br.get('min')} zeroShare={br.get('zeroShare')}"
    )

    print(f"\n--- truth=false windows ({len(summary['truthLossWindows'])}) ---")
    for w in summary["truthLossWindows"]:
        print(
            f"  {w['start']} .. {w['end']}  {w['durSec']}s  n={w['n']}  "
            f"canMax={w['canMax']}  shadow={w['shadowMin']}..{w['shadowMax']}  "
            f"bitrate~{w['bitrateMean']}  {w['bearingSrc']}"
        )

    print(f"\n--- hardResync ({summary['hardResyncCount']}) ---")
    for e in summary["hardResyncEvents"]:
        print(
            f"  {e['ts']}  shadow={e['shadowDistM']}  thr={e['thresholdM']}  "
            f"bearing={e['bearing']} course={e['course']} can={e['can']}"
        )

    if summary["shadowPeaks"]:
        print(f"\n--- shadow peaks (≥5 m, top {len(summary['shadowPeaks'])}) ---")
        for p in summary["shadowPeaks"]:
            print(
                f"  {p['ts']}  {p['shadowDistM']}m  truth={p['truth']}  "
                f"live={p['liveUsable']} ret={p['retaining']} hard={p['hardResync']} "
                f"can={p['can']}"
            )

    ol = summary.get("online") or {}
    print(
        f"\n--- online yaw calib --- phases={ol.get('phases')}  "
        f"biasSteps={ol.get('biasStepCount')} scaleCands={ol.get('scaleCandCount')}"
    )
    print(
        f"  biasYaw {ol.get('biasYawStart')} → {ol.get('biasYawEnd')}  "
        f"yawScale {ol.get('yawScaleStart')} → {ol.get('yawScaleEnd')}  "
        f"lastScaleCandMedian={ol.get('lastScaleCandMedian')}"
    )

    ts = summary.get("turnScale") or {}
    print(
        f"\n--- turn scale (∫yaw vs GNSS course) --- "
        f"L n={ts.get('left', {}).get('n')} med={ts.get('left', {}).get('median')}  "
        f"R n={ts.get('right', {}).get('n')} med={ts.get('right', {}).get('median')}"
    )
    for side, label in (("left", "L"), ("right", "R")):
        side_d = ts.get(side) or {}
        for seg in side_d.get("segments") or []:
            src = seg.get("source", "?")
            print(
                f"    {label} {seg['ts']} gyro={seg['gyroDeg']} gnss={seg['gnssDeg']} "
                f"scale={seg['scale']} {seg['durSec']}s src={src}"
            )
    if ts.get("leftRightMedianRatio") is not None:
        print(f"  left/right median ratio={ts['leftRightMedianRatio']} (1.0 = symmetric)")

    if summary.get("bitrateGaps"):
        print("\n--- low bitrate / no NMEA gaps ---")
        for g in summary["bitrateGaps"]:
            print(
                f"  {g['start']} .. {g['end']}  {g['durSec']}s  n={g['n']}  "
                f"truthFalse={g['truthFalse']}"
            )


CSV_COLUMNS = [
    "ts",
    "elapsedMs",
    "source",
    "mockMode",
    "bitrate_bps",
    "truth",
    "liveUsable",
    "retaining",
    "indicator",
    "lat",
    "lon",
    "course",
    "gnssSpeed",
    "can.accountingKmh",
    "steering.angleDeg",
    "mock.lat",
    "mock.lon",
    "bearing",
    "bearingSrc",
    "shadowDistM",
    "thresholdM",
    "posW",
    "hardResync",
    "blendLive",
    "yawRaw",
    "yawDebiased",
    "yawCal",
    "gyro.z",
    "biasYaw",
    "yawScale",
    "yawSign",
    "integ.distM",
    "integ.dDistM",
    "integ.yawRawDeg",
    "integ.dYawRawDeg",
    "integ.yawDebDeg",
    "integ.dYawDebDeg",
    "integ.pitchDeg",
    "integ.dPitchDeg",
    "integ.rollDeg",
    "integ.dRollDeg",
    "integ.steerPathDeg",
    "integ.dSteerPathDeg",
    "integ.nSpeed",
    "integ.nGyro",
    "integ.nSteer",
    "huPrnd",
    "tboxPrnd",
    "huSwitch",
    "turn.left",
    "turn.right",
    "turn.hazard",
    "turn.side",
    "turn.latched",
    "mapMatch.turnHint",
    "online.phase",
    "straightHoldMs",
    "turnGyroAbsDeg",
    "lastBiasStep",
    "lastScaleCand",
]


def write_csv(ticks: list[Tick], path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS, extrasaction="ignore")
        w.writeheader()
        for t in ticks:
            row = {"ts": t.ts, "elapsedMs": t.elapsed_ms}
            for col in CSV_COLUMNS:
                if col in ("ts", "elapsedMs"):
                    continue
                if col == "gnssSpeed":
                    row[col] = t.get("speedKmh")
                elif col == "steering.angleDeg":
                    row[col] = t.get("steering.angleDeg") or t.get("angleDeg")
                elif col == "gyro.z":
                    row[col] = t.get("z") or t.get("roll") or t.get("gyro.z")
                elif col.startswith("integ."):
                    short = col[len("integ.") :]
                    row[col] = t.get(col) or t.get(short)
                elif col == "mapMatch.turnHint":
                    row[col] = t.get("mapMatch.turnHint") or t.get("turnHint")
                elif col.startswith("turn."):
                    row[col] = t.get(col)
                elif col == "mock.lat":
                    row[col] = t.get("mock.lat") or t.fields.get("mock.lat")
                elif col == "mock.lon":
                    row[col] = t.get("mock.lon") or t.fields.get("mock.lon")
                else:
                    row[col] = t.get(col)
            row["lat"] = t.get("lat")
            row["lon"] = t.get("lon")
            row["mock.lat"] = t.fields.get("mock.lat")
            row["mock.lon"] = t.fields.get("mock.lon")
            w.writerow(row)


def main(argv: Optional[Iterable[str]] = None) -> int:
    p = argparse.ArgumentParser(
        description="Analyze TBox Monitor geo-debug logs (tbox_geo_debug_*.txt).",
    )
    p.add_argument(
        "logs",
        nargs="+",
        type=Path,
        help="Path(s) to tbox_geo_debug_*.txt",
    )
    p.add_argument(
        "--csv",
        type=Path,
        help="Write per-tick CSV (only for the first log if multiple)",
    )
    p.add_argument(
        "--json-summary",
        type=Path,
        help="Write JSON summary (object or list if multiple logs)",
    )
    args = p.parse_args(list(argv) if argv is not None else None)

    summaries: list[dict[str, Any]] = []
    for idx, log_path in enumerate(args.logs):
        if not log_path.is_file():
            print(f"missing file: {log_path}", file=sys.stderr)
            return 2
        ticks = parse_log(log_path)
        summary = summarize(ticks)
        summary["file"] = str(log_path)
        summaries.append(summary)
        print_summary(log_path, summary)
        if args.csv and idx == 0:
            write_csv(ticks, args.csv)
            print(f"\nCSV → {args.csv}")

    if args.json_summary:
        args.json_summary.parent.mkdir(parents=True, exist_ok=True)
        payload: Any = summaries[0] if len(summaries) == 1 else summaries
        args.json_summary.write_text(
            json.dumps(payload, ensure_ascii=False, indent=2, default=str),
            encoding="utf-8",
        )
        print(f"JSON summary → {args.json_summary}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
