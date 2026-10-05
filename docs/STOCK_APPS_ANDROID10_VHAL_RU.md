# Штатные приложения прошивки Android 10 (VHAL / Adayo)

Каталог пакетов штатной прошивки ГУ **Adayo** (линейка **Android 10 + VHAL** в терминах TBox Monitor; платформенный API у APK — **28**).

Источник: локальная выгрузка `D:\Dashing\Android10-VHAL\firmware_analysis\extracted\system\` — каталоги `app` и `priv-app`.  
Package name и label сняты через `aapt dump badging`.

Связанные доки: [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [STOCK_PUSH_SUBSCRIPTIONS_RU.md](STOCK_PUSH_SUBSCRIPTIONS_RU.md), [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md).

ISO в той же папке (для ориентира версии образа): `QR_8015_T1K_MY1_IHU_ADAYO_V00.05.09.iso`, `QR_8015_T1K_MY1_IHU_ADAYO_V00.07.12.iso`.

---

## 1) OEM / IVI (Adayo, Autochips, Haoke, iFlytek, партнёры)

| Каталог в образе | Package | Кратко |
|------------------|---------|--------|
| Launcher | `com.adayo.launcher` | Главный лаунчер (`Launcher_HMA214A`); окна приложений через `LAUNCH_APP` |
| AtcSystemUI | `com.android.systemui` | System UI |
| CarSetting | `com.adayo.app.carsettings` | Настройки автомобиля (VHAL) |
| AirConditioning | `com.adayo.app.hvac` | Климат / кондиционер |
| SystemSettings | `com.hk.systemsettings` | Системные настройки ГУ (яркость ICM, свет, аудио и т.п.) |
| AdayoAVM | `com.adayo.app.avm` | Круговой обзор 360° |
| SpeechHMI | `com.iflytek.cutefly.speechclient.hmi` | Голосовой ассистент iFlytek |
| T1KIflyPlugin | `com.adayo.iflyplugin` | Плагин iFlytek для T1K |
| IflytekAvatar | `com.iflytek.avatarService` | Виртуальный образ ассистента |
| Iflyteksceneengine | `com.iflytek.autofly.sceneengine` | Scene engine iFlytek |
| MediaService | `com.haoke.media` | Медиасервис Haoke |
| AtcCarMediaApp | `com.android.car.media` | Car Media App |
| AtcLocalMediaPlayer | `com.android.car.media.localmediaplayer` | Local media player |
| RadioService | `com.adayo.service.radio` | Радио |
| AdayoDAB | `com.adayo.service.dab` | DAB |
| BTService | `com.adayo.bt` | Bluetooth Adayo |
| nFore_Hades | `com.nforetek.bt` | Стек Bluetooth nFore |
| PhoneLink | `com.adayo.phonelink` | PhoneLink adapter |
| AndroidAuto | `com.adayo.androidauto` | Android Auto |
| CarPlay / CarPlayView | `com.adayo.carplay` / `com.adayo.carplay.view` | Apple CarPlay |
| MFiService | `com.adayo.mfiservice` | MFi / CarPlay support |
| iPodServer | `com.adayo.ipodserver` | iPod server |
| PhoneDetect | `com.adayo.phonedetect.service` | Детект подключённого телефона |
| TBoxService | `com.adayo.service.tboxservice` | Сервис связи с TBox |
| A2_BcmService | `com.adayo.aaopbcm` | BCM / кузовной сервис |
| BcmServiceDemo | `com.adayo.app.bcmservicedemo` | Demo BCM |
| keyeventservice | `com.adayo.service.keyevent` | Клавиши / key events (руль и т.п.) |
| SourceMngService | `com.adayo.service.sourcemngservice` | Менеджер аудиоисточников |
| DaemonService | `com.adayo.service.daemonservice` | Daemon Adayo |
| ShareService | `com.adayo.service.shareinfo` | ShareInfo |
| AdayoSettingsService | `com.adayo.midware.settings` | Midware settings |
| ProjectAdapter | `com.adayo.localadapter.service` | Project adapter |
| servicecenter | `com.adayo.module.servicecenter` | Service center |
| mediascannerservice | `com.adayo.service.mediascanner` | Медиасканер |
| AdayoLog | `com.adayo.service.log` | Логи Adayo |
| ATCLogger | `com.autochips.atclogger` | Логгер Autochips |
| DiagnosticSystemApp | `com.adayo.diagnostic` | Диагностические коды |
| FactoryMode | `com.adayo.factorymode` | Заводской режим |
| AtcEngineerMode | `com.autochips.engineermode` | Engineer mode Autochips |
| AutoTest | `com.adayo.app.autotest` | Автотесты |
| autotools | `com.adayo.service.autotools` | Autotools service |
| Upgrade | `com.adayo.app.upgrade` | Обновление приложений |
| upgradeservice / tspservice | `com.adayo.fota.upgradeservice` / `com.adayo.fota.tspservice` | FOTA |
| HkUpdater | `com.hk.updater` | Updater Haoke |
| SystemUpdater | `com.android.car.systemupdater` | Local system update |
| InstructionBook | `com.haoke.instruction` | Электронное руководство |
| InputMethod | `com.iqqijni.car.keyboard` | Клавиатура Kika |
| theme0201 | `com.adayo.theme0201` | Тема Theme0201 |
| AtcBrowser | `com.android.browser` | Браузер |
| AtcGallery2 / OP01Gallery | `com.android.gallery3d` / `com.autochips.gallery.op01` | Галерея |
| Camera2 | `com.android.camera2` | Камера |
| RearCamera | `com.autochips.rearcamera` | Камера заднего вида |
| RearMusic / VideoPlayer2 | `com.autochips.rearmusic` / `com.autochips.videoplayer2` | Задний ряд: музыка / видео |
| CarRearLauncher | `com.autochips.rearlauncher` | Лаунчер заднего экрана |
| DualScreenDemo | `com.autochips.dualscreen` | Demo dual screen |
| AudioIn | `com.autochips.audioin` | AUX |
| AtcWatermark | `com.autochips.watermarkservice` | Watermark service |
| YGPS | `com.autochips.ygps` | YGPS (тестовый GPS UI Autochips) |
| android.car.input.service | `android.car.input.service` | Car input service |

---

## 2) AOSP / Android Automotive / прочее

| Каталог в образе | Package | Кратко |
|------------------|---------|--------|
| ATCSettings | `com.android.settings` | Android Settings |
| AtcCarSettings | `com.android.car.settings` | AOSP Car Settings |
| CarService | `com.android.car` | Car framework service |
| CarTrustAgentService | `com.android.car.trust` | Car trust agent |
| Atcwebview | `com.google.android.webview` | System WebView |
| Bluetooth | `com.android.bluetooth` | Bluetooth |
| Telecom / TeleService | `com.android.server.telecom` / `com.android.phone` | Телефония |
| Contacts / ContactsProvider | `com.android.contacts` / `com.android.providers.contacts` | Контакты |
| MediaProvider | `com.android.providers.media` | Медиахранилище |
| DownloadProvider | `com.android.providers.downloads` | Загрузки |
| TelephonyProvider | `com.android.providers.telephony` | SMS / telephony storage |
| SettingsProvider | `com.android.providers.settings` | Settings storage |
| PackageInstaller | `com.android.packageinstaller` | Установщик |
| Shell | `com.android.shell` | Shell |
| AtcDocumentsUI | `com.android.documentsui` | Files |
| ExtShared / ExtServices | `android.ext.shared` / `android.ext.services` | Extension libs |
| FusedLocation | `com.android.location.fused` | Fused Location |
| ExternalStorageProvider | `com.android.externalstorage` | External storage |
| DefaultContainerService | `com.android.defcontainer` | Package access helper |
| StorageManager | `com.android.storagemanager` | Storage Manager |
| SettingsIntelligence | `com.android.settings.intelligence` | Settings suggestions |
| StatementService | `com.android.statementservice` | Intent filter verification |
| UserDictionaryProvider | `com.android.providers.userdictionary` | User dictionary |
| WallpaperCropper | `com.android.wallpapercropper` | Wallpaper cropper |
| CertInstaller / KeyChain | `com.android.certinstaller` / `com.android.keychain` | Сертификаты |
| HTMLViewer | `com.android.htmlviewer` | HTML Viewer |
| SoundRecorder | `com.android.soundrecorder` | Диктофон |
| messaging | `com.android.messaging` | Messaging |
| PhotoTable | `com.android.dreams.phototable` | Photo screensaver |
| SecureElement | `com.android.se` | Secure Element |
| WAPPushManager | `com.android.smspush` | WAP Push |
| MusicFX | `com.android.musicfx` | MusicFX |

---

## Итого

- **~65** OEM/IVI-пакетов Adayo/Autochips/Haoke/iFlytek  
- **~34** AOSP / Car  
- **99** APK в `system/app` + `system/priv-app` этой выгрузки  

Часто нужные для TBox Monitor package name:

| Назначение | Package |
|------------|---------|
| Лаунчер / stock window | `com.adayo.launcher` |
| CarSettings | `com.adayo.app.carsettings` |
| Климат | `com.adayo.app.hvac` |
| SystemSettings | `com.hk.systemsettings` |
| Камера 360 | `com.adayo.app.avm` |
| Голос | `com.iflytek.cutefly.speechclient.hmi` |
| Медиа | `com.haoke.media` |
| Key events | `com.adayo.service.keyevent` |
| TBox service | `com.adayo.service.tboxservice` |
| CarPlay | `com.adayo.carplay` / `com.adayo.carplay.view` |
| Android Auto | `com.adayo.androidauto` |

### Где смотреть decode/encode VHAL (не package list)

Для ID и семантики VHAL полезнее декомпилы приложений, а не этот каталог:

- `CarSetting` / `AirConditioning` / `SystemSettings` / `Launcher` / `MediaService` / `SpeechHMI`  
- уже сведено в [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md) и [STOCK_PUSH_SUBSCRIPTIONS_RU.md](STOCK_PUSH_SUBSCRIPTIONS_RU.md)
