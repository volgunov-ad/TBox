# Штатные приложения прошивки Android 9 (mbCAN)

Каталог пакетов штатной прошивки ГУ **TINNOVE / Wutong X50** (линейка **Android 9 + mbCAN**, Mengbo).

Источник: локальный образ `D:\Dashing\Android9-mbCan\system.img` (ext4), каталоги `system/app` и `system/priv-app`.  
Package name и label сняты через `aapt dump badging` со всех APK образа.

## Прошивка

| Поле | Значение |
|------|----------|
| `ro.product.model` | `X50` |
| `ro.product.brand` | `TINNOVE` |
| `ro.product.manufacturer` | `WUTONG` |
| `ro.build.version.release` / SDK | `9` / **28** |
| `ro.build.fingerprint` | `TINNOVE/sm6150_au/sm6150_au:9/PQ3B.190801.002/272:user/test-keys` |
| `ro.wt.version` | `X50-QC6155-MB-3.0R-QR-V000000279-202312122019` |
| `ro.wt.mini.version` | `V000000279` |
| `ro.wt.solution.provider` | `MB` |
| `ro.wt.channel` | `qiruiX50` |
| `vendor.vehicle.version` | `V30R.X50.85` |

В этом `system.img` отдельных `vendor/app` / `product/app` нет — все 110 APK лежат в `system/app` + `system/priv-app`.

Связанные доки: [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [STOCK_APPS_ANDROID10_VHAL_RU.md](STOCK_APPS_ANDROID10_VHAL_RU.md).

### VirtualCar и mbCAN decode/encode

`com.wt.virtualcar` (`virtual_data.xml`) — **не** каталог сырых mbCAN cfg id. Там ~230 hex property (`0x314…`, `0x354…`, …) в стиле vendor VirtualCar; они **не пересекаются** с известными VHAL int A10 (`2894…` / `5578…`) и не равны mbCAN id вроде `141` / `143`.

Для добавления функций mbCAN в TBox Monitor смотреть:

1. **A9:** `CarSettings` / `ACSettings` / `MB_AIService` → `MBVehicleProperty` + `MBCanRepository` / виджеты (`setModularItem*`, listener’ы) — id, 1/2 on-off, массивы значений в `res/values`.
2. **A10:** `CarSetting` / `AirConditioning` / `SystemSettings` / `Launcher` → VHAL read/write id и encode (уже частично в [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md)).
3. **VirtualCar** — только как подсказка по **семантике UI** (диапазоны, 1/2), если рядом нет прямого UI в CarSettings; маппинг на mbCAN всё равно искать в Mengbo-коде или нативном демоне.

---

## 1) OEM / IVI (Mengbo, Wutong, Tinnove, партнёры)

Пользовательские и платформенные приложения головного устройства.

| Каталог в образе | Package | Кратко |
|------------------|---------|--------|
| WT_Launcher3 | `com.wt.launcher3` | Главный лаунчер ГУ |
| MB_NegativeScreen | `com.android.launcherWT` | Список приложений (AppList) |
| WT_WtSystemUI | `com.android.systemui` | System UI (статусбар / шторка) |
| MB_CarSettings | `com.mengbo.carsettings` | Настройки автомобиля (mbCAN) |
| MB_ACSettings | `com.mengbo.acsettings` | Климат / кондиционер |
| MB_SystemSettings | `com.mengbo.systemsettings` | Системные настройки ГУ |
| MB_Avm | `com.mengbo.avm` | Круговой обзор 360° |
| MB_AVMConfig | `com.mengbo.avmconfig` | Конфигурация AVM |
| MB_HwMap | `com.huawei.maps.auto.app` | Навигация Petal Maps |
| WT_MLLocalMediaOpen | `com.wt.multimedia.local` | Локальный медиаплеер |
| WT_MLVideoOpen | `com.wt.multimedia.video` | Видеоплеер |
| WT_BTPhone | `com.autopai.car.dialer` | Bluetooth-телефон |
| WT_RadioService | `com.tinnove.radioservice` | Сервис радио |
| MB_DABService | `com.adayo.service.dab` | DAB-радио (Adayo) |
| MB_IflytekSpeech | `com.iflytek.cutefly.speechclient.hmi` | Голосовой ассистент iFlytek (HMI) |
| MB_IFlySpeechClient | `com.mengbo.speechclient` | Клиент голосового ассистента |
| MB_AIService | `com.mengbo.aiservice` | Фоновый AI/CAN-сервис Mengbo |
| WT_WtBaseService | `com.wt.wtservice` | Базовый сервис платформы WT |
| WT_VirtualCar | `com.wt.virtualcar` | Виртуальный слой автомобиля |
| WT_MLAudioService | `com.wt.media` | Car Audio service (фокус / маршрутизация) |
| WT_MediaScannerService | `com.wt.scanner` | Сканер медиафайлов |
| WT_TinnoveBtService | `com.wt.wtbtservice` | BT-сервис Tinnove |
| nFore_Hades | `com.nforetek.bt` | Стек Bluetooth nFore |
| MB_EasyConnect | `net.easyconn` | CarbitLink / EasyConnect (проекция телефона) |
| WT_FileManager | `com.wtcl.filemanager` | Файловый менеджер |
| WT_NotificationCenter | `com.wt.notification_center` | Центр системных уведомлений |
| MB_UserCenter | `com.mengbo.usercenter` | Пользовательский центр |
| MB_ElectronicManual | `com.mengbo.electronicmanual` | Электронное руководство |
| MB_VarietyShop | `com.mengbo.varietyshop` | Магазин / каталог приложений |
| MB_InputKeyboard | `com.iqqijni.car.keyboard` | Клавиатура Kika |
| MB_FactoryMode | `com.mengbo.factory` | Заводской / сервисный режим |
| MB_Carota | `com.mengbo.carota` | OTA обновление автомобиля |
| WT_FOTA | `com.wutong.fota` | FOTA обновление ГУ |
| SystemUpdater | `com.android.car.systemupdater` | Локальное обновление системы |
| MB_PHEV | `com.mengbo.phevhybrid` | Виджет / логика PHEV (MBCanWidget) |
| MB_Provision | `com.android.provision` | Первичная настройка (provisioning) |
| WT_SmartLog | `com.wt.smartlog` | Сбор интеллектуальных логов |
| WT_IotTube | `com.autopai.iottube` | IoT-туннель Autopai |
| WT_ThemeResourcesClassic | `com.autopai.theme.classic` | Тема Classic |
| WT_ThemeResourcesBlack | `com.autopai.theme.black` | Тема Black |
| MB_YDTool | `com.ydssmart.yodotool_for_jx65` | Сервисный инструмент Yodo для JX65 |

---

## 2) AOSP / Android Automotive / Qualcomm

Системные пакеты Android 9 / Automotive / QTI из того же образа.

| Каталог в образе | Package | Кратко |
|------------------|---------|--------|
| Settings | `com.android.settings` | Android Settings |
| CarService | `com.android.car` | Android Car framework service |
| CarUsbHandler | `android.car.usb.handler` | Обработчик USB (Car) |
| LocalMediaPlayer | `com.android.car.media.localmediaplayer` | AOSP local media player |
| Bluetooth | `com.android.bluetooth` | Системный Bluetooth |
| Telecom | `com.android.server.telecom` | Telecom |
| TeleService | `com.android.phone` | Phone |
| MediaProvider | `com.android.providers.media` | Медиахранилище |
| DownloadProvider | `com.android.providers.downloads` | Download Manager |
| DownloadProviderUi | `com.android.providers.downloads.ui` | UI загрузок |
| ContactsProvider | `com.android.providers.contacts` | Контакты |
| CalendarProvider | `com.android.providers.calendar` | Календарь |
| TelephonyProvider | `com.android.providers.telephony` | SMS / телефонное хранилище |
| SettingsProvider | `com.android.providers.settings` | Хранилище Settings |
| PackageInstaller | `com.android.packageinstaller` | Установщик пакетов |
| Shell | `com.android.shell` | adb shell / bugreport |
| webview | `com.android.webview` | System WebView |
| FusedLocation | `com.android.location.fused` | Fused Location |
| NfcNci | `com.android.nfc` | NFC |
| SecureElement | `com.android.se` | Secure Element |
| KeyChain | `com.android.keychain` | KeyChain |
| CertInstaller | `com.android.certinstaller` | Установка сертификатов |
| CaptivePortalLogin | `com.android.captiveportallogin` | Captive portal Wi‑Fi |
| VpnDialogs | `com.android.vpndialogs` | Диалоги VPN |
| StorageManager | `com.android.storagemanager` | Управление хранилищем |
| ExternalStorageProvider | `com.android.externalstorage` | Внешнее хранилище |
| DefaultContainerService | `com.android.defcontainer` | Package Access Helper |
| InputDevices | `com.android.inputdevices` | Устройства ввода |
| MmsService | `com.android.mms.service` | MMS |
| CellBroadcastReceiver | `com.android.cellbroadcastreceiver` | Cell Broadcast |
| Stk | `com.android.stk` | SIM Toolkit |
| SimAppDialog | `com.android.simappdialog` | Диалог SIM-приложений |
| WAPPushManager | `com.android.smspush` | WAP Push |
| ManagedProvisioning | `com.android.managedprovisioning` | Managed provisioning |
| StatementService | `com.android.statementservice` | Intent Filter Verification |
| SettingsIntelligence | `com.android.settings.intelligence` | Подсказки Settings |
| OneTimeInitializer | `com.android.onetimeinitializer` | One-time init |
| ProxyHandler | `com.android.proxyhandler` | HTTP proxy handler |
| PacProcessor | `com.android.pacprocessor` | PAC-прокси |
| SharedStorageBackup | `com.android.sharedstoragebackup` | Shared storage backup |
| BackupRestoreConfirmation | `com.android.backupconfirm` | Подтверждение backup/restore |
| BlockedNumberProvider | `com.android.providers.blockednumber` | Заблокированные номера |
| UserDictionaryProvider | `com.android.providers.userdictionary` | Словарь пользователя |
| BookmarkProvider | `com.android.bookmarkprovider` | Закладки |
| CompanionDeviceManager | `com.android.companiondevicemanager` | Companion devices |
| ExtShared | `android.ext.shared` | Android Shared Library |
| ExtServices | `android.ext.services` | Android Services Library |
| PrintSpooler | `com.android.printspooler` | Print Spooler |
| BuiltInPrintService | `com.android.bips` | Default Print Service |
| PrintRecommendationService | `com.android.printservice.recommendation` | Print recommendations |
| SoundRecorder | `com.android.soundrecorder` | Диктофон |
| HTMLViewer | `com.android.htmlviewer` | HTML Viewer |
| LiveWallpapersPicker | `com.android.wallpaper.livepicker` | Выбор live wallpaper |
| WallpaperCropper | `com.android.wallpapercropper` | Обрезка обоев |
| WallpaperBackup | `com.android.wallpaperbackup` | Backup обоев |
| BasicDreams | `com.android.dreams.basic` | Basic Daydreams |
| PhotoTable | `com.android.dreams.phototable` | Photo screensaver |
| Protips | `com.android.protips` | Подсказки home screen |
| EasterEgg | `com.android.egg` | Android Easter Egg |
| Traceur | `com.android.traceur` | System Tracing |
| BluetoothMidiService | `com.android.bluetoothmidiservice` | Bluetooth MIDI |
| CtsShimPrebuilt | `com.android.cts.ctsshim` | CTS shim |
| CtsShimPrivPrebuilt | `com.android.cts.priv.ctsshim` | CTS priv shim |
| CNEService | `com.quicinc.cne.CNEService` | Qualcomm CNE |
| dpmserviceapp | `com.qti.dpmserviceapp` | Qualcomm DPM |
| DynamicDDSService | `com.qualcomm.qti.dynamicddsservice` | Dynamic DDS |
| uimlpaservice | `com.qualcomm.qti.lpa` | eSIM LPA |
| smcinvokepkgmgr | `com.qualcomm.qti.smcinvokepkgmgr` | Qualcomm SMC package mgr |
| ConfURIDialer | `com.qti.confuridialer` | Conference URI dialer |

---

## Итого

- **41** OEM/IVI-пакет  
- **69** AOSP / QTI  
- **110** APK всего  

Часто нужные для TBox Monitor package name:

| Назначение | Package |
|------------|---------|
| Лаунчер | `com.wt.launcher3` |
| AppList | `com.android.launcherWT` |
| CarSettings | `com.mengbo.carsettings` |
| Климат | `com.mengbo.acsettings` |
| SystemSettings | `com.mengbo.systemsettings` |
| Камера 360 | `com.mengbo.avm` |
| Голос | `com.iflytek.cutefly.speechclient.hmi` / `com.mengbo.speechclient` |
| Медиа | `com.wt.multimedia.local` |
| EasyConnect | `net.easyconn` |
| Навигация | `com.huawei.maps.auto.app` |
