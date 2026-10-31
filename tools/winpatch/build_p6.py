#!/usr/bin/env python3
"""
Reproducible builder for the patched A10 windowed AndroidAuto_p6.apk and CarPlayView_p6.apk
bundled in :winpatch (winpatch/src/main/assets/).

Inputs (all tracked in this repository):
  - Stock APKs: winpatch/src/main/assets/stock/{AndroidAuto_v0712.apk, CarPlayView_stock.apk}
  - Java patch helpers: tools/winpatch/src/
  - Smali/manifest/resource overlays: tools/winpatch/patches/{androidauto_p6, carplayview_p6}/
  - AOSP platform test-key: tools/winpatch/certs/{platform.pk8, aosp_platform_cert.pem}
"""

import argparse
import hashlib
import os
import shutil
import subprocess
import tempfile
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
REPO_ROOT = HERE.parent.parent
STOCK_DIR = REPO_ROOT / "winpatch" / "src" / "main" / "assets" / "stock"
ASSETS_DIR = REPO_ROOT / "winpatch" / "src" / "main" / "assets"
CERTS_DIR = HERE / "certs"
PATCHES_DIR = HERE / "patches"
SRC_DIR = HERE / "src"


def find_sdk_root() -> Path:
    for env_key in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        val = os.environ.get(env_key)
        if val and Path(val).is_dir():
            return Path(val)
    local_props = REPO_ROOT / "local.properties"
    if local_props.is_file():
        for line in local_props.read_text(encoding="utf-8").splitlines():
            if line.startswith("sdk.dir="):
                raw = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                if Path(raw).is_dir():
                    return Path(raw)
    default_win = Path.home() / "AppData" / "Local" / "Android" / "Sdk"
    if default_win.is_dir():
        return default_win
    raise FileNotFoundError("Android SDK not found (set ANDROID_HOME or local.properties)")


def find_build_tools(sdk: Path) -> Path:
    bt_root = sdk / "build-tools"
    versions = sorted([d for d in bt_root.iterdir() if d.is_dir()], reverse=True)
    if not versions:
        raise FileNotFoundError(f"No build-tools found under {bt_root}")
    return versions[0]


def copy_tree_overlay(src: Path, dst: Path) -> None:
    for root, _, files in os.walk(src):
        rel = Path(root).relative_to(src)
        target_dir = dst / rel
        target_dir.mkdir(parents=True, exist_ok=True)
        for f in files:
            shutil.copy2(Path(root) / f, target_dir / f)


def compile_java_helpers(apktool_jar: Path, sdk: Path, bt: Path, work: Path) -> None:
    android_jar = sdk / "platforms" / "android-28" / "android.jar"
    d8 = bt / ("d8.bat" if os.name == "nt" else "d8")
    classes_dir = work / "classes"
    classes_dir.mkdir(parents=True, exist_ok=True)

    java_files = [
        SRC_DIR / "com" / "adayo" / "androidauto" / "patch" / "AAWindowPatch.java",
        SRC_DIR / "com" / "adayo" / "carplay" / "view" / "patch" / "WindowSurface.java",
        SRC_DIR / "com" / "adayo" / "carplay" / "view" / "ui" / "activity" / "MainActivity.java",
    ]
    subprocess.check_call([
        "javac", "-source", "1.8", "-target", "1.8",
        "-cp", str(android_jar),
        "-d", str(classes_dir),
        *[str(p) for p in java_files],
    ])

    class_files = [str(p) for p in classes_dir.rglob("*.class") if "MainActivity" not in p.name]
    subprocess.check_call([
        str(d8), "--min-api", "28", "--lib", str(android_jar), "--output", str(work), *class_files
    ])

    dummy_apk = work / "helpers.apk"
    with zipfile.ZipFile(dummy_apk, "w") as zf:
        zf.write(work / "classes.dex", "classes.dex")

    dec_dir = work / "helpers_dec"
    subprocess.check_call(["java", "-jar", str(apktool_jar), "d", "-f", str(dummy_apk), "-o", str(dec_dir)])

    shutil.copy2(
        dec_dir / "smali" / "com" / "adayo" / "androidauto" / "patch" / "AAWindowPatch.smali",
        PATCHES_DIR / "androidauto_p6" / "smali" / "com" / "adayo" / "androidauto" / "patch" / "AAWindowPatch.smali",
    )
    shutil.copy2(
        dec_dir / "smali" / "com" / "adayo" / "carplay" / "view" / "patch" / "WindowSurface.smali",
        PATCHES_DIR / "carplayview_p6" / "smali" / "com" / "adayo" / "carplay" / "view" / "patch" / "WindowSurface.smali",
    )


def build_one(
    name: str,
    stock_apk: Path,
    overlay_dir: Path,
    out_apk: Path,
    apktool_jar: Path,
    bt: Path,
    work: Path,
) -> str:
    dec_dir = work / f"{name}_dec"
    uns_apk = work / f"{name}_unsigned.apk"
    al_apk = work / f"{name}_aligned.apk"
    zipalign = bt / ("zipalign.exe" if os.name == "nt" else "zipalign")
    apksigner = bt / ("apksigner.bat" if os.name == "nt" else "apksigner")
    pk8 = CERTS_DIR / "platform.pk8"
    pem = CERTS_DIR / "aosp_platform_cert.pem"

    out_apk.parent.mkdir(parents=True, exist_ok=True)
    subprocess.check_call(["java", "-jar", str(apktool_jar), "d", "-f", str(stock_apk), "-o", str(dec_dir)])
    copy_tree_overlay(overlay_dir, dec_dir)
    subprocess.check_call(["java", "-jar", str(apktool_jar), "b", str(dec_dir), "-o", str(uns_apk)])
    subprocess.check_call([str(zipalign), "-f", "4", str(uns_apk), str(al_apk)])
    subprocess.check_call([
        str(apksigner), "sign",
        "--key", str(pk8),
        "--cert", str(pem),
        "--out", str(out_apk),
        str(al_apk),
    ])
    subprocess.check_call([str(apksigner), "verify", "-v", str(out_apk)])
    idsig = Path(str(out_apk) + ".idsig")
    if idsig.is_file():
        idsig.unlink()
    md5 = hashlib.md5(out_apk.read_bytes()).hexdigest()
    print(f"{out_apk.name}: md5={md5} size={out_apk.stat().st_size}")
    return md5


def main() -> None:
    parser = argparse.ArgumentParser(description="Build patched p6 APKs for :winpatch")
    parser.add_argument(
        "--apktool",
        type=Path,
        default=Path(os.environ.get("APKTOOL_JAR", str(HERE / "apktool.jar"))),
        help="Path to apktool.jar (3.0.x)",
    )
    parser.add_argument(
        "--recompile-java",
        action="store_true",
        help="Recompile Java helper sources in tools/winpatch/src before building APKs",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=ASSETS_DIR,
        help="Output directory for AndroidAuto_p6.apk and CarPlayView_p6.apk",
    )
    args = parser.parse_args()

    if not args.apktool.is_file():
        raise FileNotFoundError(f"apktool.jar not found at {args.apktool} (pass --apktool <path>)")

    sdk = find_sdk_root()
    bt = find_build_tools(sdk)

    with tempfile.TemporaryDirectory(prefix="winpatch_build_") as tmp:
        work = Path(tmp)
        if args.recompile_java:
            compile_java_helpers(args.apktool, sdk, bt, work)
        build_one(
            name="CarPlayView_p6",
            stock_apk=STOCK_DIR / "CarPlayView_stock.apk",
            overlay_dir=PATCHES_DIR / "carplayview_p6",
            out_apk=args.out_dir / "CarPlayView_p6.apk",
            apktool_jar=args.apktool,
            bt=bt,
            work=work,
        )
        build_one(
            name="AndroidAuto_p6",
            stock_apk=STOCK_DIR / "AndroidAuto_v0712.apk",
            overlay_dir=PATCHES_DIR / "androidauto_p6",
            out_apk=args.out_dir / "AndroidAuto_p6.apk",
            apktool_jar=args.apktool,
            bt=bt,
            work=work,
        )


if __name__ == "__main__":
    main()
