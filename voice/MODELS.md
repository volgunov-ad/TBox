# Voice offline models

TTS/STT binaries are **not** stored in git.

| Model | Fetch | Asset path |
|-------|-------|------------|
| Piper RU Irina int8 (TTS) | `./gradlew :voice:fetchTtsModel` | `voice/src/main/assets/vits-piper-ru_RU-irina-medium-int8/` |
| Vosk small-ru-0.22 (STT) | `./gradlew :voice:fetchSttModel` | `voice/src/main/assets/vosk-model-small-ru-0.22/` |

`./gradlew :voice:assembleDebug` downloads both automatically when missing.
