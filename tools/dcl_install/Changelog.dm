# Changelog — Установка DCL (`tools/dcl_install`, `vad.dashing.dclinstall`)

Версии APK: `android:versionName` / `android:versionCode` в `tools/dcl_install/AndroidManifest.xml`.
При публикации поднять **versionCode** на 1 и править **versionName**; сюда — блок с тем же номером сверху.
Сборка: `tools/dcl_install/build.ps1`, не Gradle. Готовый файл — `tools/dcl_install/DclInstall.apk` (версия только в манифесте).

1.20
1. Установщик Dashing Club Launcher на головное устройство Android 9: выбранный APK лаунчера, его разрешения, скрытие штатного лаунчера и `android.car.usb.handler`, по желанию System UI и лишних штатных приложений.
2. Обои (картинка из установщика, чёрный фон или своя) ставятся без livepicker. Для лаунчера старше 0.8.2 ставится программа флешек; у 0.8.2 и новее этот шаг пропускается.
3. Переключатель визита к дилеру возвращает штатный вид и не трогает загрузку, пока есть `/sdcard/dcl_dealer.flag`. В конце установка перезагружает головное устройство.
