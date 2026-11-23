# Changelog — Файрвол ГУ (`:fwunlock`, `vad.dashing.fwunlock`)

Версии APK: `versionName` / `versionCode` в `fwunlock/build.gradle.kts`.
При публикации поднять **versionCode** на 1 и править **versionName** (semver); сюда — блок с тем же номером сверху.
Имя APK: `fwunlock-<versionName>-<debug|release>.apk`.

0.1.0
1. Постоянное открытие сети сторонним приложениям на головном устройстве Android 10: в белый список Adayo (`AdayoFirewallAppDef.json`) дописываются установленные пакеты. Запись через localhost ADB и `su`, со снятием защиты eMMC. После записи перезапускается `com.android.car`, перезагрузка не нужна. Приложения, поставленные позже, по-прежнему закрывает временный iptables-фикс TBox Monitor.
