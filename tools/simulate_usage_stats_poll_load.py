#!/usr/bin/env python3
"""Estimate UsageStats poll load before vs after the shared foreground-app sampler.

Automations (#276) switched panels+automations to a shared 1 s / 10 s UsageStats sampler
(de6f52e2). Previously panels polled every 3 s with a 5-minute queryEvents lookback and a
queryUsageStats fallback.

This script does not talk to Android; it only prints the binder/event-span model used in
the investigation. Stdlib only.

  python tools/simulate_usage_stats_poll_load.py
"""

from __future__ import annotations

import json
import sys


def model(poll_ms: int, window_ms: int, label: str) -> dict:
    polls_per_min = 60_000 / poll_ms
    # Upper bound: each poll rescans the whole window of wall-clock history.
    event_seconds_scanned_per_min = polls_per_min * (window_ms / 1000.0)
    return {
        "label": label,
        "poll_ms": poll_ms,
        "window_ms": window_ms,
        "polls_per_minute": polls_per_min,
        "event_seconds_scanned_per_minute": event_seconds_scanned_per_min,
        "query_usage_stats_fallback": label.startswith("legacy"),
    }


def main() -> int:
    legacy = model(3_000, 300_000, "legacy_panels_pre_de6f52e2")
    current = model(1_000, 10_000, "shared_sampler_automations")
    ratio = (
        legacy["event_seconds_scanned_per_minute"]
        / current["event_seconds_scanned_per_minute"]
    )
    report = {
        "legacy": legacy,
        "current": current,
        "event_span_reduction_factor": ratio,
        "notes": [
            "Current design queries UsageStats only when panel hide/show rules exist "
            "OR a foreground_app automation is watching (automationWatching).",
            "Automations receive undebounced sticky package; panels still use 2-poll debounce.",
            "1 Hz IPC is more frequent; per-poll event span is much smaller than 5 min lookback.",
            "Not a road-map tile OOM path; at most binder/CPU pressure and panel remount thrash "
            "if Yandex packages are in hide/show watch lists.",
        ],
    }
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if ratio < 5:
        print("FAIL: expected large reduction vs legacy 5-minute lookback", file=sys.stderr)
        return 1
    print(
        f"OK: event-span load ~{ratio:.1f}× lower than legacy "
        f"({current['polls_per_minute']:.0f} polls/min vs {legacy['polls_per_minute']:.0f})",
        file=sys.stderr,
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
