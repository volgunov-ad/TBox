#!/usr/bin/env python3
"""Linux/macOS HU smoke test for TBox Monitor (ADB TCP).

Mirrors Invoke-HuFullTest.ps1 so the same scenarios can run from Cursor Cloud.
Default device: 192.168.1.128:5555.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import os
import re
import shutil
import subprocess
import sys
import time
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
PKG = "vad.dashing.tbox"
DEVICE_DIR = "/storage/emulated/0/Download/hu_test"
DEFAULT_DEVICE = "192.168.1.128:5555"

# API 28 logcat --pid is almost empty; dump the full buffer and filter locally.
FATAL_PATTERNS = ("FATAL EXCEPTION", "OutOfMemoryError")
INTERESTING = (
    "FATAL EXCEPTION",
    "AndroidRuntime",
    "OutOfMemoryError",
    "HUTEST",
    "Automation",
    "Theme Service",
    "Floating Dashboard",
    "MbCanEngineFacade",
    "Background Service",
    "toast_theme_apply",
)


def log(report: list[str], message: str, level: str = "INFO") -> None:
    line = f"[{time.strftime('%H:%M:%S')}] {level} {message}"
    print(line, flush=True)
    report.append(line)


def find_adb(explicit: str | None) -> str:
    if explicit:
        return explicit
    home = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or ""
    candidates = [
        shutil.which("adb") or "",
        str(Path(home) / "platform-tools" / "adb") if home else "",
        "/opt/android-sdk/platform-tools/adb",
        str(Path.home() / "android-sdk" / "platform-tools" / "adb"),
    ]
    for path in candidates:
        if path and Path(path).is_file() and os.access(path, os.X_OK):
            return path
    raise SystemExit("adb not found; install platform-tools or pass --adb")


def default_apk() -> Path | None:
    outputs = REPO_ROOT / "app" / "build" / "outputs" / "apk"
    names = (
        "ru/debug/app-ru-debug.apk",
        "ru/release/app-ru-release.apk",
        "en/debug/app-en-debug.apk",
    )
    for rel in names:
        path = outputs / rel
        if path.is_file():
            return path
    found = sorted(outputs.rglob("*.apk")) if outputs.is_dir() else []
    return found[0] if found else None


def run_adb(adb: str, device: str, args: list[str], check: bool = False) -> subprocess.CompletedProcess[str]:
    cmd = [adb, "-s", device, *args]
    result = subprocess.run(cmd, text=True, capture_output=True)
    if check and result.returncode != 0:
        raise RuntimeError(
            f"adb {' '.join(args)} failed ({result.returncode}): "
            f"{(result.stderr or result.stdout).strip()}",
        )
    return result


def adb_shell(adb: str, device: str, command: str) -> str:
    result = run_adb(adb, device, ["shell", command])
    return (result.stdout or "") + (result.stderr or "")


def connect_device(adb: str, device: str, report: list[str]) -> None:
    log(report, f"adb connect {device}")
    subprocess.run([adb, "connect", device], text=True, capture_output=True)
    time.sleep(1)
    listed = subprocess.run([adb, "devices", "-l"], text=True, capture_output=True)
    log(report, listed.stdout.strip() or listed.stderr.strip())
    devices = subprocess.run([adb, "devices"], text=True, capture_output=True).stdout
    if device not in devices:
        raise RuntimeError(f"Device {device} is not connected")
    if f"{device}\tunauthorized" in devices.replace(" ", "\t") or f"{device} unauthorized" in devices:
        raise RuntimeError(
            f"Device {device} is unauthorized. Accept the RSA dialog on the HU.",
        )


def grant_permissions(adb: str, device: str, report: list[str]) -> None:
    log(report, "Granting permissions / appops")
    runtime = [
        "android.permission.ACCESS_FINE_LOCATION",
        "android.permission.ACCESS_COARSE_LOCATION",
        "android.permission.ACCESS_BACKGROUND_LOCATION",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
        "android.permission.WRITE_SECURE_SETTINGS",
        "android.permission.WRITE_SETTINGS",
    ]
    for perm in runtime:
        out = adb_shell(adb, device, f"pm grant {PKG} {perm}").strip()
        if out:
            log(report, f"pm grant {perm} : {out}", "WARN")
        else:
            log(report, f"pm grant {perm} ok")
    for op, mode in (
        ("SYSTEM_ALERT_WINDOW", "allow"),
        ("GET_USAGE_STATS", "allow"),
        ("WRITE_SETTINGS", "allow"),
        ("MOCK_LOCATION", "allow"),
    ):
        out = adb_shell(adb, device, f"appops set {PKG} {op} {mode}").strip()
        log(report, f"appops {op}={mode} {out}")
    adb_shell(adb, device, f"dumpsys deviceidle whitelist +{PKG}")
    listener = f"{PKG}/{PKG}.MediaControlNotificationListenerService"
    current = adb_shell(adb, device, "settings get secure enabled_notification_listeners").strip()
    if listener not in current:
        value = f"{current}:{listener}" if current and current != "null" else listener
        adb_shell(adb, device, f"settings put secure enabled_notification_listeners {value}")
        log(report, "enabled notification listener")
    adb_shell(adb, device, f"settings put secure mock_location {PKG}")


def install_apk(adb: str, device: str, apk: Path, report: list[str]) -> None:
    log(report, f"Installing {apk}")
    result = run_adb(adb, device, ["install", "-r", "-g", str(apk)])
    text = (result.stdout + result.stderr).strip()
    log(report, text)
    if "Success" not in text:
        raise RuntimeError("apk install failed")


def push_fixtures(adb: str, device: str, fixtures: Path, report: list[str]) -> None:
    log(report, f"Pushing fixtures to {DEVICE_DIR}")
    adb_shell(adb, device, f"mkdir -p {DEVICE_DIR}")
    for path in sorted(fixtures.iterdir()):
        if not path.is_file() or path.name.startswith("."):
            continue
        if path.name.startswith("_") or path.suffix == ".py":
            continue
        result = run_adb(adb, device, ["push", str(path), f"{DEVICE_DIR}/{path.name}"])
        log(report, (result.stdout or result.stderr).strip())


def dump_ui(adb: str, device: str, out_dir: Path, name: str) -> str:
    remote = "/sdcard/hu_uidump.xml"
    adb_shell(adb, device, f"uiautomator dump {remote}")
    local = out_dir / f"{name}.xml"
    run_adb(adb, device, ["pull", remote, str(local)])
    if local.is_file():
        return local.read_text(encoding="utf-8", errors="replace")
    return ""


def save_screenshot(adb: str, device: str, out_dir: Path, name: str, report: list[str]) -> None:
    remote = "/sdcard/hu_shot.png"
    adb_shell(adb, device, f"screencap -p {remote}")
    local = out_dir / f"{name}.png"
    run_adb(adb, device, ["pull", remote, str(local)])
    log(report, f"screenshot {name}")


def tap(adb: str, device: str, x: int, y: int) -> None:
    adb_shell(adb, device, f"input tap {x} {y}")
    time.sleep(0.7)


def swipe_up(adb: str, device: str) -> None:
    adb_shell(adb, device, "input swipe 960 800 960 280 400")
    time.sleep(0.6)


def click_text(
    adb: str,
    device: str,
    out_dir: Path,
    ui: dict,
    report: list[str],
    text: str,
    swipes: int = 4,
    dump_name: str = "click",
) -> bool:
    helper = SCRIPT_DIR / "click_from_dump.py"
    for index in range(swipes + 1):
        dump_ui(adb, device, out_dir, f"{dump_name}-{index}")
        xml_path = out_dir / f"{dump_name}-{index}.xml"
        if xml_path.is_file():
            result = subprocess.run(
                [sys.executable, str(helper), str(xml_path), text],
                text=True,
                capture_output=True,
            )
            if result.returncode == 0:
                parts = result.stdout.strip().split()
                if len(parts) == 2:
                    x, y = int(parts[0]), int(parts[1])
                    log(report, f"tap '{text}' at {x},{y} via dump")
                    tap(adb, device, x, y)
                    return True
        if index < swipes:
            swipe_up(adb, device)
    log(report, f"UI text not found: {text}", "WARN")
    save_screenshot(adb, device, out_dir, "missing-" + re.sub(r"[^\w\-]", "_", text), report)
    return False


def dismiss_first_run(
    adb: str,
    device: str,
    out_dir: Path,
    ui: dict,
    report: list[str],
) -> None:
    labels = ["Allow", "ALLOW", ui["allow"], "OK", ui["ok_ru"], ui["close"], "While using the app", "Yes", ui["yes"], ui["yes_cap"]]
    xml = dump_ui(adb, device, out_dir, "dismiss")
    helper = SCRIPT_DIR / "click_from_dump.py"
    xml_path = out_dir / "dismiss.xml"
    for label in labels:
        if not xml_path.is_file():
            break
        result = subprocess.run(
            [sys.executable, str(helper), str(xml_path), label],
            text=True,
            capture_output=True,
        )
        if result.returncode == 0:
            parts = result.stdout.strip().split()
            if len(parts) == 2:
                log(report, f"dismiss '{label}'")
                tap(adb, device, int(parts[0]), int(parts[1]))
                xml = dump_ui(adb, device, out_dir, "dismiss")


def analyze_logcat(text: str) -> tuple[list[str], bool]:
    lines = [f"log size={len(text)}"]
    failed = False
    for name in INTERESTING:
        count = text.count(name)
        lines.append(f"{name}: {count}")
        if name in FATAL_PATTERNS and count > 0:
            failed = True
    lines.append(
        "gc/oom-ish="
        + str(len(re.findall(r"OutOfMemoryError|GC_FOR_ALLOC|WaitForGcToComplete", text))),
    )
    fatals = [line for line in text.splitlines() if any(p in line for p in FATAL_PATTERNS)]
    if fatals:
        lines.append("")
        lines.append("---- fatals ----")
        lines.extend(fatals[:40])
    hutes = [line for line in text.splitlines() if "HUTEST" in line or "Automation" in line]
    if hutes:
        lines.append("")
        lines.append("---- automation / HUTEST ----")
        lines.extend(hutes[:80])
    return lines, failed


def collect_logs(adb: str, device: str, out_dir: Path, report: list[str]) -> Path:
    log(report, "Collecting logcat / dumpsys")
    pid = adb_shell(adb, device, f"pidof {PKG}").strip().split()
    pid = pid[0] if pid else ""
    log(report, f"pid={pid}")
    log_file = out_dir / "logcat.txt"
    # Full buffer: --pid on API 28 often returns almost nothing.
    result = run_adb(adb, device, ["logcat", "-d"])
    log_file.write_text(result.stdout or result.stderr, encoding="utf-8")
    (out_dir / "windows.txt").write_text(
        adb_shell(adb, device, "dumpsys window windows"), encoding="utf-8",
    )
    (out_dir / "meminfo.txt").write_text(
        adb_shell(adb, device, f"dumpsys meminfo {PKG}"), encoding="utf-8",
    )
    (out_dir / "services.txt").write_text(
        adb_shell(adb, device, f"dumpsys activity services {PKG}"), encoding="utf-8",
    )
    (out_dir / "appops.txt").write_text(
        adb_shell(adb, device, f"appops get {PKG}"), encoding="utf-8",
    )
    return log_file


def run_self_test() -> int:
    click = SCRIPT_DIR / "click_from_dump.py"
    gen = SCRIPT_DIR / "generate_hu_test_fixtures.py"
    for script, extra in ((click, ["--self-test"]), (gen, ["--self-test"])):
        result = subprocess.run([sys.executable, str(script), *extra], text=True)
        if result.returncode != 0:
            return result.returncode
    sample = "FATAL EXCEPTION: main\nHUTEST hide overlays (Settings)\n"
    lines, failed = analyze_logcat(sample)
    assert failed
    assert any("HUTEST: 1" in line for line in lines)
    print("run_hu_full_test self-test ok")
    return 0


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="HU ADB smoke test for TBox Monitor")
    parser.add_argument("--device", default=os.environ.get("ANDROID_SERIAL", DEFAULT_DEVICE))
    parser.add_argument("--adb", default=None)
    parser.add_argument("--apk", default=None)
    parser.add_argument("--skip-install", action="store_true")
    parser.add_argument("--skip-ui", action="store_true", help="Install/permissions/logs only")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--connect-only", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.self_test:
        return run_self_test()

    report: list[str] = []
    stamp = dt.datetime.now().strftime("%Y%m%d-%H%M%S")
    out_dir = SCRIPT_DIR / "results" / stamp
    out_dir.mkdir(parents=True, exist_ok=True)
    ui_json = SCRIPT_DIR / "ui-strings.json"
    failed = False
    try:
        adb = find_adb(args.adb)
        connect_device(adb, args.device, report)
        if args.connect_only:
            log(report, "connect-only ok")
            return 0
        if not args.skip_install:
            apk = Path(args.apk) if args.apk else default_apk()
            if apk is None or not apk.is_file():
                raise RuntimeError(
                    "APK not found. Build with ./gradlew assembleRuDebug or pass --apk",
                )
            install_apk(adb, args.device, apk, report)
        grant_permissions(adb, args.device, report)
        subprocess.run([sys.executable, str(SCRIPT_DIR / "generate_hu_test_fixtures.py")], check=True)
        if not ui_json.is_file():
            raise RuntimeError("Missing ui-strings.json after fixture generation")
        ui = json.loads(ui_json.read_text(encoding="utf-8"))
        push_fixtures(adb, args.device, SCRIPT_DIR / "fixtures", report)
        run_adb(adb, args.device, ["logcat", "-c"])
        adb_shell(adb, args.device, f"am force-stop {PKG}")
        time.sleep(0.5)
        log(report, "Starting MainActivity")
        log(report, adb_shell(adb, args.device, f"am start -W -n {PKG}/.MainActivity").strip())
        time.sleep(4)
        dismiss_first_run(adb, args.device, out_dir, ui, report)
        save_screenshot(adb, args.device, out_dir, "01-first-launch", report)
        if not args.skip_ui:
            log(report, "Import backup via Settings UI")
            click_text(adb, args.device, out_dir, ui, report, ui["settings"], dump_name="menu-settings")
            time.sleep(1)
            imported = click_text(
                adb, args.device, out_dir, ui, report, ui["import_json"], swipes=8, dump_name="backup-import",
            )
            if imported:
                time.sleep(1)
                click_text(
                    adb, args.device, out_dir, ui, report, ui["choose_file"], swipes=1, dump_name="backup-choose",
                )
                time.sleep(2)
                picked = click_text(
                    adb, args.device, out_dir, ui, report, "hu_test_backup.json", swipes=6, dump_name="backup-file",
                )
                if not picked:
                    picked = click_text(
                        adb, args.device, out_dir, ui, report, "hu_test_backup", swipes=2, dump_name="backup-file2",
                    )
                time.sleep(4)
                save_screenshot(adb, args.device, out_dir, "after-backup-import", report)
                log(report, f"backup imported={picked}")
            for theme in ("hu_test_eco.tboxtheme", "hu_test_nor.tboxtheme", "hu_test_spt.tboxtheme"):
                log(report, f"VIEW theme {theme}")
                uri = f"file://{DEVICE_DIR}/{theme}"
                adb_shell(
                    adb,
                    args.device,
                    f'am start -a android.intent.action.VIEW -d "{uri}" -n {PKG}/.MainActivity -t application/octet-stream',
                )
                time.sleep(2)
                save_screenshot(adb, args.device, out_dir, "theme-dialog-" + theme.replace(".", "_"), report)
                ok = click_text(adb, args.device, out_dir, ui, report, ui["yes"], swipes=0, dump_name="theme-yes")
                if not ok:
                    click_text(adb, args.device, out_dir, ui, report, ui["yes_cap"], swipes=0, dump_name="theme-yes2")
                time.sleep(5)
                save_screenshot(adb, args.device, out_dir, "theme-applied-" + theme.replace(".", "_"), report)
            log(report, "Tapping ECO/NOR/SPT widgets")
            adb_shell(adb, args.device, f"am start -n {PKG}/.MainActivity")
            time.sleep(2)
            for label in ("ECO", "NOR", "SPT", "ECO"):
                hit = click_text(adb, args.device, out_dir, ui, report, label, swipes=2, dump_name=f"mode-{label}")
                log(report, f"drive widget {label} hit={hit}")
                time.sleep(3)
                save_screenshot(adb, args.device, out_dir, f"mode-{label}", report)
            log(report, "Foreground-app automations: Settings then back to Monitor")
            save_screenshot(adb, args.device, out_dir, "before-settings", report)
            adb_shell(adb, args.device, "am start -a android.settings.SETTINGS")
            time.sleep(4)
            save_screenshot(adb, args.device, out_dir, "on-settings", report)
            adb_shell(adb, args.device, f"am start -n {PKG}/.MainActivity")
            time.sleep(4)
            save_screenshot(adb, args.device, out_dir, "back-monitor", report)
            navi = adb_shell(adb, args.device, "pm path ru.yandex.yandexnavi")
            if "package:" in navi:
                log(report, "Launching Yandex Navi")
                adb_shell(
                    adb,
                    args.device,
                    "monkey -p ru.yandex.yandexnavi -c android.intent.category.LAUNCHER 1",
                )
                time.sleep(4)
                save_screenshot(adb, args.device, out_dir, "on-navi", report)
                adb_shell(adb, args.device, f"am start -n {PKG}/.MainActivity")
                time.sleep(4)
                save_screenshot(adb, args.device, out_dir, "after-navi", report)
        log_file = collect_logs(adb, args.device, out_dir, report)
        text = log_file.read_text(encoding="utf-8", errors="replace")
        analysis, fatal = analyze_logcat(text)
        (out_dir / "analysis.txt").write_text("\n".join(analysis) + "\n", encoding="utf-8")
        for line in analysis[:20]:
            log(report, line, "ERROR" if fatal and "FATAL" in line else "INFO")
        save_screenshot(adb, args.device, out_dir, "99-final", report)
        log(report, f"DONE results={out_dir}")
        failed = fatal
        return 1 if failed else 0
    except Exception as error:
        log(report, str(error), "ERROR")
        try:
            adb = find_adb(args.adb)
            save_screenshot(adb, args.device, out_dir, "error", report)
        except Exception:
            pass
        return 1
    finally:
        report_path = out_dir / "report.txt"
        report_path.write_text("\n".join(report) + "\n", encoding="utf-8")
        print(f"REPORT {report_path}", flush=True)


if __name__ == "__main__":
    raise SystemExit(main())
