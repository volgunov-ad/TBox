# Changelog — Тест камер ГУ (`:camview`, `vad.dashing.camview`)

Версии APK: `versionName` / `versionCode` в `camview/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `camview-<versionName>-<debug|release>.apk`.
Диагностическая сборка, в магазин не выкладывается.

0.1.0
1. Просмотр обработанных кадров камер Mengbo на головном устройстве Android 9: SDK `MBAvm` / `MBOms` / `MBDms` и файлы `*_sharecamera`. Форматы `ARGB_8888`, `RGB_565`, `UYVY`, `YUYV`, `NV12`. Окно можно таскать и менять в размере, внутри приложения или поверх экрана. Только `arm64-v8a`.
