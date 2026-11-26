#!/bin/bash
# Linux counterpart of build.ps1: aapt + javac + d8 + apksigner, repo keystore.
# Needs ANDROID_HOME with platforms/android-28 and build-tools. Commits the APK:
# CI compares its versionCode with AndroidManifest.xml.
# Prefer build-tools 35.0.0 (35.0.1 matches). d8 from 34 crashes on these classes;
# 36 changes the dex, so a newer default is not a reproducible rebuild.
set -euo pipefail
here=$(cd "$(dirname "$0")" && pwd)
repo=$(cd "$here/../.." && pwd)
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" ]]; then
  echo "Set ANDROID_HOME to an SDK with platforms/android-28 and build-tools." >&2
  exit 1
fi
if [[ -z "${BUILD_TOOLS:-}" ]]; then
  for name in 35.0.0 35.0.1 35.0.2; do
    if [[ -x "$sdk/build-tools/$name/aapt" ]]; then
      BUILD_TOOLS="$sdk/build-tools/$name"
      break
    fi
  done
fi
if [[ -z "${BUILD_TOOLS:-}" ]]; then
  BUILD_TOOLS=$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 \( -type d -o -type l \) | sort -V | tail -1)
fi
android_jar="$sdk/platforms/android-28/android.jar"
ks="$repo/keystore/debug.keystore"
b="$here/build"
if [[ ! -x "$BUILD_TOOLS/aapt" || ! -f "$android_jar" || ! -f "$ks" ]]; then
  echo "Need $BUILD_TOOLS/aapt, $android_jar and $ks" >&2
  exit 1
fi
rm -rf "$b"
mkdir -p "$b/plan-test" "$b/wallpaper-classes" "$b/unlock-classes" "$b/app-classes" "$b/assets" "$b/dex"

javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -d "$b/plan-test" \
  "$here/src/vad/dashing/dclinstall/InstallPlan.java" \
  "$here/src/vad/dashing/dclinstall/MbDaemon.java" \
  "$here/src/vad/dashing/dclinstall/RootRun.java" \
  "$here/test/InstallPlanTest.java"
java -cp "$b/plan-test" vad.dashing.dclinstall.InstallPlanTest

echo "Building :rootsvc release APK for assets…"
( cd "$repo" && ./gradlew :rootsvc:assembleRelease --quiet )
rootsvc_apk=$(ls -1 "$repo"/rootsvc/build/outputs/apk/release/rootsvc-*-release.apk | head -1)
if [[ ! -f "$rootsvc_apk" ]]; then
  echo "rootsvc release APK missing after assembleRelease" >&2
  exit 1
fi
cp "$rootsvc_apk" "$b/assets/rootsvc.apk"
cp "$here/assets/default_wallpaper.jpg" "$b/assets/default_wallpaper.jpg"

javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -classpath "$android_jar" \
  -d "$b/wallpaper-classes" "$here/src/SetWallpaper.java"
"$BUILD_TOOLS/d8" --min-api 22 --lib "$android_jar" --output "$b/assets" \
  "$b/wallpaper-classes/SetWallpaper.class"
mv "$b/assets/classes.dex" "$b/assets/setwallpaper.dex"

javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -classpath "$android_jar" \
  -d "$b/unlock-classes" "$here/src/UnlockUsb.java"
"$BUILD_TOOLS/d8" --min-api 22 --lib "$android_jar" --output "$b/assets" \
  "$b/unlock-classes/UnlockUsb.class"
mv "$b/assets/classes.dex" "$b/assets/unlockusb.dex"

mapfile -d '' sources < <(find "$here/src/vad" "$repo/shared/rootsvc/java" -name '*.java' -print0)
javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -classpath "$android_jar" \
  -d "$b/app-classes" "${sources[@]}"
mapfile -d '' classes < <(find "$b/app-classes" -name '*.class' -print0)
"$BUILD_TOOLS/d8" --min-api 22 --lib "$android_jar" --output "$b/dex" "${classes[@]}"

"$BUILD_TOOLS/aapt" package -f -M "$here/AndroidManifest.xml" -S "$here/res" -A "$b/assets" \
  -I "$android_jar" -F "$b/unsigned.apk"
( cd "$b/dex" && "$BUILD_TOOLS/aapt" add "$b/unsigned.apk" classes.dex )
"$BUILD_TOOLS/zipalign" -f 4 "$b/unsigned.apk" "$b/aligned.apk"
rm -f "$here/DclInstall.apk"
"$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false \
  --ks "$ks" --ks-pass pass:android --key-pass pass:android \
  --out "$here/DclInstall.apk" "$b/aligned.apk"
"$BUILD_TOOLS/apksigner" verify "$here/DclInstall.apk"
"$BUILD_TOOLS/aapt" dump badging "$here/DclInstall.apk" | grep 'package:'
echo "OK $here/DclInstall.apk"
