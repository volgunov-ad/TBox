#!/usr/bin/env sh
set -eu
DIR="$(CDPATH= cd -- "$(dirname "$0")" && pwd)"
SCRIPT="$DIR/align_elf_load_16k.py"
LIB="$DIR/../app/src/main/jniLibs/arm64-v8a/libmbCan.so"
for py in python3 python; do
  if command -v "$py" >/dev/null 2>&1; then
    exec "$py" "$SCRIPT" "$LIB" -o "$LIB"
  fi
done
echo "error: need python3 or python on PATH to align libmbCan.so" >&2
exit 1
