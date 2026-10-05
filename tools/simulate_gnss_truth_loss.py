#!/usr/bin/env python3
"""Model GNSS truth-loss effects on mock injection and retention accuracy.

Field note: crashes sometimes coincide with GNSS loss while DR is running.
Road-tile RAM does not grow on truth-loss; WHEN_NO_FIX flips system mock on/off,
and published Location.accuracy grows while retaining.

  python tools/simulate_gnss_truth_loss.py
  python tools/simulate_gnss_truth_loss.py --flap-sec 2 --duration-sec 120 --ceiling-m 1
"""

from __future__ import annotations

import argparse
import json
import sys


DEFAULT_CEILING_M = 75.0
BASE_FLOOR_M = 5.0
GROWTH_DURATION_SEC = 210.0


def retention_accuracy_m(base_m: float, age_sec: float, ceiling_m: float) -> float:
    ceiling = max(1.0, min(100.0, ceiling_m))
    base = BASE_FLOOR_M if not (base_m > 0) else min(base_m, ceiling)
    if age_sec <= 0:
        return base
    growth = max(0.0, (ceiling - BASE_FLOOR_M)) / GROWTH_DURATION_SEC
    return min(ceiling, base + growth * age_sec)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("--flap-sec", type=float, default=3.0, help="truthful/not half-period")
    ap.add_argument("--duration-sec", type=float, default=180.0)
    ap.add_argument("--ceiling-m", type=float, default=DEFAULT_CEILING_M)
    ap.add_argument("--base-m", type=float, default=5.0)
    args = ap.parse_args(argv)

    # WHEN_NO_FIX: inject == not truthful
    steps = max(1, int(args.duration_sec / max(0.1, args.flap_sec)))
    transitions = 0
    inject_on_sec = 0.0
    truthful = True
    last_inject = None
    for i in range(steps):
        truthful = not truthful
        inject = not truthful
        if last_inject is not None and last_inject != inject:
            transitions += 1
        if inject:
            inject_on_sec += args.flap_sec
        last_inject = inject

    # Continuous tunnel: accuracy growth curve samples
    ages = [0, 30, 60, 120, 210, 600]
    curve = [
        {"age_sec": a, "accuracy_m": retention_accuracy_m(args.base_m, a, args.ceiling_m)}
        for a in ages
    ]

    report = {
        "when_no_fix_flap": {
            "flap_sec": args.flap_sec,
            "duration_sec": args.duration_sec,
            "provider_transitions": transitions,
            "inject_on_sec": inject_on_sec,
            "transitions_per_minute": transitions / max(args.duration_sec / 60.0, 1e-6),
        },
        "retention_accuracy_curve_m": curve,
        "notes": [
            "Truth-loss does not increase resident .tboxroads tiles; matcher keeps warm graphs.",
            "WHEN_NO_FIX starts/stops the system mock provider on each truthful edge — "
            "Yandex Navigator may flap providers in urban canyons (Moscow).",
            "accuracy ceiling 1 m keeps DR looking like a live fix; default 75 m loosens nav matching.",
            "OOM during Moscow DR is still cumulative heap, not a GNSS-loss-specific tile load.",
        ],
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if transitions < 1:
        print("FAIL: expected provider transitions under flap", file=sys.stderr)
        return 1
    print(
        f"OK: {transitions} mock provider transitions in {args.duration_sec:.0f}s "
        f"({transitions / max(args.duration_sec / 60.0, 1e-6):.1f}/min), "
        f"ceiling={args.ceiling_m:g} m → {curve[-1]['accuracy_m']:.1f} m at 600 s",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
