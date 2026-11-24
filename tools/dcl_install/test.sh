#!/bin/sh
# Compiles InstallPlan, MbDaemon and RootRun with the JDK only and runs InstallPlanTest.
# No Android SDK. From the repo root: bash tools/dcl_install/test.sh
set -eu
cd "$(dirname "$0")"
out=$(mktemp -d)
trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -Xlint:-options -source 8 -target 8 -d "$out" \
  src/vad/dashing/dclinstall/InstallPlan.java \
  src/vad/dashing/dclinstall/MbDaemon.java \
  src/vad/dashing/dclinstall/RootRun.java \
  test/InstallPlanTest.java
java -cp "$out" vad.dashing.dclinstall.InstallPlanTest
