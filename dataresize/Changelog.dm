# Changelog — Расширение /data (`:dataresize`, `vad.dashing.dataresize`)

Версии APK: `versionName` / `versionCode` в `dataresize/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `dataresize-<versionName>-<debug|release>.apk`.

0.2.0
1. Одноразовое приложение для головного устройства Android 10: переносит раздел `userdata` в свободный хвост eMMC и увеличивает его, с живым прогрессом. Root через localhost ADB и `su`.
