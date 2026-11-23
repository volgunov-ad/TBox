# Changelog — USB-аудио ГУ (`:usbaudio`, `vad.dashing.usbaudio`)

Версии APK: `versionName` / `versionCode` в `usbaudio/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `usbaudio-<versionName>-<debug|release>.apk`.

0.1.1
1. Одноразовое включение звука через USB-карту на головном устройстве Android 10: конфиг с модулем usb записывается в `/system/etc/audio_policy_configuration.xml` (снятие защиты записи eMMC, как в «Расширение /data»). Файл `/vendor/etc/audio_policy_configuration.xml` удаляется, чтобы audioserver читал конфиг из `/system`. Временный bind-overlay с автоприменением при загрузке остаётся в TBox Monitor.
