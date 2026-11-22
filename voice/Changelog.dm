# Changelog — VAD Voice (`:voice`, `vad.dashing.voice`)

Версии APK: `versionName` / `versionCode` в `voice/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `voice-<versionName>-<debug|release>.apk`.

0.1.0
1. Голосовой клиент External API TBox Monitor на головном устройстве: офлайн Vosk (распознавание) и Piper Irina (озвучка), разбор фраз по русским синонимам сигналов и автоматизаций.
2. В APK только `armeabi-v7a` и `arm64-v8a`. Модели при сборке скачивает Gradle, в git их нет.
