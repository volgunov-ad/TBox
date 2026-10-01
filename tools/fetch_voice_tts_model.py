#!/usr/bin/env python3
"""Optional: download Piper RU (Irina int8) TTS model into :voice assets.

Prefer Gradle (no Python needed on Windows):

  gradlew :voice:fetchTtsModel
  gradlew :voice:assembleDebug

This script remains for manual/CI use when Python is available:

  python3 tools/fetch_voice_tts_model.py
"""

from __future__ import annotations

import hashlib
import shutil
import sys
import tarfile
import tempfile
import urllib.request
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
ASSETS_DIR = REPO_ROOT / "voice" / "src" / "main" / "assets"
MODEL_DIR_NAME = "vits-piper-ru_RU-irina-medium-int8"
MARKER = ASSETS_DIR / MODEL_DIR_NAME / "tokens.txt"

# Official sherpa-onnx release asset (Piper RU Irina, int8).
URL = (
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
    "vits-piper-ru_RU-irina-medium-int8.tar.bz2"
)
# Optional integrity check; update if upstream re-packs the archive.
EXPECTED_SHA256 = None  # set to hex digest to enforce


def _sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    if MARKER.is_file():
        print(f"TTS model already present: {MARKER.parent}")
        return 0

    ASSETS_DIR.mkdir(parents=True, exist_ok=True)
    target = ASSETS_DIR / MODEL_DIR_NAME
    if target.exists():
        shutil.rmtree(target)

    with tempfile.TemporaryDirectory(prefix="voice-tts-") as tmp:
        tmp_path = Path(tmp)
        archive = tmp_path / "model.tar.bz2"
        print(f"Downloading {URL}")
        urllib.request.urlretrieve(URL, archive)
        if EXPECTED_SHA256:
            digest = _sha256(archive)
            if digest != EXPECTED_SHA256:
                print(f"SHA256 mismatch: got {digest}, expected {EXPECTED_SHA256}", file=sys.stderr)
                return 1
        print(f"Extracting into {ASSETS_DIR}")
        with tarfile.open(archive, "r:bz2") as tar:
            tar.extractall(ASSETS_DIR)

    if not MARKER.is_file():
        print(f"Extract failed: missing {MARKER}", file=sys.stderr)
        return 1

    onnx = MARKER.parent / "ru_RU-irina-medium.onnx"
    espeak = MARKER.parent / "espeak-ng-data"
    if not onnx.is_file() or not espeak.is_dir():
        print("Extract incomplete: onnx or espeak-ng-data missing", file=sys.stderr)
        return 1

    print(f"OK: {MARKER.parent}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
