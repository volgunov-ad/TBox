# Voice offline models

TTS/STT binaries are **not** stored in git.

| Model | Fetch | Asset path |
|-------|-------|------------|
| Piper RU Irina int8 (TTS) | `python3 tools/fetch_voice_tts_model.py` | `voice/src/main/assets/vits-piper-ru_RU-irina-medium-int8/` |
| Vosk small-ru (STT) | этап 5 | TBD |

`./gradlew :voice:assembleDebug` downloads the Piper bundle automatically when missing.
