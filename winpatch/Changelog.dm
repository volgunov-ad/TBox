# Changelog — AA/CarPlay окна ГУ (`:winpatch`, `vad.dashing.winpatch`)

Версии APK: `versionName` / `versionCode` в `winpatch/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `winpatch-<versionName>-<debug|release>.apk`.

0.1.0
1. Постоянная установка и откат патча Android Auto p6 и CarPlayView p6 в `/system/app` на известных прошивках Android 10. Два режима, настройка переживает перезагрузку: окно лаунчера 1327×865 и почти весь экран между барами 1920×864. Тот же экран правит `CarPlayManagerConfig.json`. Запасные стоковые APK лежат в assets.
