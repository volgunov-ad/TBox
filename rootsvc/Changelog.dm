# Changelog — Root-служба ГУ (`:rootsvc`, `vad.dashing.rootsvc`)

Версии APK: `versionName` / `versionCode` в `rootsvc/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `rootsvc-<versionName>-<debug|release>.apk`.

1.0.0
1. Первая поставка общей root-службы для ГУ Android 9 (Mengbo): APK-сторож со спецвозможностью и `BOOT_COMPLETED`, супервизор и `app_process`-демон на абстрактном сокете `vad.dashing.rootsvc`, JSON-протокол v1, клиентская библиотека `shared/rootsvc` / модуль `:rootclient`, протокол захвата канала `mb_daemon`. A10 и миграция потребителей — отдельными PR.
