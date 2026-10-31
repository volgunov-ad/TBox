#!/bin/bash
# Rebuild mqtt/libs/wgstack.aar. Needs Go 1.26+ (or GOTOOLCHAIN=auto) and Android NDK r26.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
SDK="${ANDROID_HOME:-/opt/android-sdk}"
NDK="${ANDROID_NDK_HOME:-$SDK/ndk/26.3.11579264}"
export ANDROID_HOME="$SDK"
export ANDROID_NDK_HOME="$NDK"
export GOTOOLCHAIN="${GOTOOLCHAIN:-auto}"
cd "$ROOT"
# gomobile from 2026 wants the module's Go toolchain, not the system Go.
GO_BIN="$(go env GOROOT)/bin/go"
"$GO_BIN" install tool
export PATH="$( "$GO_BIN" env GOPATH )/bin:$PATH"
command -v gomobile >/dev/null || "$GO_BIN" install golang.org/x/mobile/cmd/gomobile@latest
gomobile init
mkdir -p "$ROOT/../libs"
cd "$ROOT"
gomobile bind -target=android/arm,android/arm64 -androidapi 28 -o "$ROOT/../libs/wgstack.aar" .
