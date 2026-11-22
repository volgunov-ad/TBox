# Changelog — TBox MQTT (`:mqtt`, `vad.dashing.mqtt`)

Версии APK: `versionName` / `versionCode` в `mqtt/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `mqtt-<versionName>-<debug|release>.apk`.

0.1.0
1. Мост External API TBox Monitor → MQTT-брокер пользователя на головном устройстве. Сущности можно отдать в Home Assistant через MQTT Discovery.
2. Мастер WQTT.ru создаёт устройства для Алисы по токену кабинета. Токен API Monitor запрашивается кнопкой на ГУ, ручная вставка остаётся запасным способом.
