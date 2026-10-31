#!/usr/bin/env python3
"""Summarize hu_perf_run.ps1 result folders side by side (see docs/HU_PERF_TESTS_RU.md).

usage: python hu_perf_analyze.py --pkg=<package> <run_dir> [<run_dir> ...]
"""
import argparse
import re
from pathlib import Path

HZ = 100


def read(p):
    for enc in ("utf-16", "utf-8"):
        try:
            return Path(p).read_text(encoding=enc)
        except (UnicodeError, UnicodeDecodeError):
            continue
    return Path(p).read_text(encoding="utf-8", errors="replace")


def ts(line):
    return float(line.split()[0])


def parse_stat_line(line):
    t = line.split()
    i = t.index("cpu")
    v = list(map(int, t[i + 1:i + 11]))
    li = t.index("L")
    return float(t[1]), sum(v), v[3] + v[4], int(t[li + 1]) + int(t[li + 2])


def window(a, b):
    dt_tot = b[1] - a[1]
    busy = dt_tot - (b[2] - a[2])
    return {
        "sys_pct": 100.0 * busy / dt_tot,
        "proc_core_pct": 100.0 * (b[3] - a[3]) / HZ / (b[0] - a[0]),
    }


def analyze(run, pkg):
    run = Path(run)
    r = {"run": run.name}
    ev = read(run / "events.txt").splitlines()
    start = next(l for l in ev if "boot_progress_start:" in l)
    # Log timestamps before the MCU sets the clock are shifted; boot_progress_* values are true uptime ms.
    off = int(start.split(":")[-1]) / 1000.0 - ts(start)

    def up(line):
        t = ts(line)
        return t + off if t < 0 or t > 10000 else t

    for l in ev:
        if "boot_progress_enable_screen" in l:
            r["enable_screen_s"] = int(l.split(":")[-1]) / 1000
        if "wm_boot_animation_done" in l:
            r["bootanim_done_s"] = int(l.split(":")[-1]) / 1000
    ps = next(l for l in ev if "am_proc_start" in l and "," + pkg + "," in l)
    pid = ps.split("[")[1].split(",")[1]
    r["proc_start_s"] = round(up(ps), 2)

    main = read(run / "main2.txt").splitlines()
    mine = [l for l in main if len(l.split()) > 2 and l.split()[1] == pid]
    davey = [l for l in mine if "Davey!" in l]
    if davey:
        m = re.search(r"FrameCompleted=(\d+)", davey[0])
        r["first_frame_s"] = round(int(m.group(1)) / 1e9, 2)
        r["first_frame_dur_ms"] = int(re.search(r"duration=(\d+)ms", davey[0]).group(1))
        r["start_to_first_frame_s"] = round(r["first_frame_s"] - r["proc_start_s"], 2)
    skipped = [int(re.search(r"Skipped (\d+) frames", l).group(1)) for l in mine if "Skipped" in l and "frames" in l]
    r["skipped_frames_total"] = sum(skipped)
    r["skipped_max"] = max(skipped) if skipped else 0
    r["davey_count"] = len(davey)
    r["jit_compiles_logged"] = sum(1 for l in mine if "Compiler allocated" in l)

    s = [l for l in read(run / "sample.txt").splitlines() if l.startswith("S ")]
    pts = [parse_stat_line(l) for l in s]
    r["adb_uptime_s"] = round(pts[0][0], 1)
    r["proc_cpu_at_adb_s"] = round(pts[0][3] / HZ, 1)
    r["end_uptime_s"] = round(pts[-1][0], 1)
    r["proc_cpu_at_end_s"] = round(pts[-1][3] / HZ, 1)
    w = window(pts[0], pts[-1])
    r["boot_window_sys_pct"] = round(w["sys_pct"], 1)
    r["boot_window_proc_core_pct"] = round(w["proc_core_pct"], 1)
    late = [p for p in pts if p[0] >= pts[-1][0] - 60]
    w2 = window(late[0], late[-1])
    r["idle60_sys_pct"] = round(w2["sys_pct"], 1)
    r["idle60_proc_core_pct"] = round(w2["proc_core_pct"], 1)
    r["loadavg_end"] = float(s[-1].split()[-1])

    sw = read(run / "switch.txt").splitlines()
    waits = [int(re.search(r"WaitTime: (\d+)", l).group(1)) for l in sw if l.startswith("OPEN") and "WaitTime" in l]
    r["open_ms_avg"] = round(sum(waits) / len(waits))
    r["open_ms_first"] = waits[0]
    r["open_ms_min"] = min(waits)
    r["open_ms_max"] = max(waits)
    a = parse_stat_line(next(l for l in sw if l.startswith("S0")))
    b = parse_stat_line(next(l for l in sw if l.startswith("S1")))
    w3 = window(a, b)
    r["switch_sys_pct"] = round(w3["sys_pct"], 1)
    r["switch_proc_core_pct"] = round(w3["proc_core_pct"], 1)
    for l in sw:
        if l.startswith("GFX "):
            k, v = l[4:].split(":", 1)
            r["gfx_" + k.strip().replace(" ", "_")] = v.strip()
    m = re.search(r"\[status=[^\]]+\] \[reason=[^\]]+\]", read(run / "dexopt_after.txt"))
    r["dexopt_after_boot"] = m.group(0) if m else "-"
    return r


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pkg", required=True)
    ap.add_argument("runs", nargs="+")
    args = ap.parse_args()
    rows = [analyze(p, args.pkg) for p in args.runs]
    keys = list(dict.fromkeys(k for r in rows for k in r))
    for k in keys:
        print(f"{k:28s}" + "".join(f"{str(r.get(k, '-')):>36s}" for r in rows))


if __name__ == "__main__":
    main()
