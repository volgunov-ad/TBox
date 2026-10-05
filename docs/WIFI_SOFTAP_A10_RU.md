# Wi-Fi SoftAP на Android 10 (Adayo/VHAL) и план раздачи через компаньон

> Статус: разбор прошивки `D:\Dashing\Android10-VHAL` (образ `QR_8015_T1K_MY1_IHU_ADAYO_V00.07.12`,
> `firmware_analysis\extracted\system` + `vendor`, декомпиляция `SystemSettings`).
> `WifiApConfigStore` дополнительно сверен через JADX 1.5.3 (`D:\Tools\JADX\lib\jadx-gui-1.5.3-all.jar`).
> JADX не открывает `wifi-service.vdex` / compact dex напрямую: сначала baksmali по соседнему `.odex`,
> затем `jadx` по `.smali`. Имена `android.R.*` в этом Java — подстановка с API хоста, не строки прошивки;
> идентификаторы ресурсов сверялись `aapt dump resources` по `framework-res.apk`.
> Live-тестов на ГУ A10 нет. Факты A9 (случайный PSK на каждую запись, `ro.mb.hostapd.state`, пароль в CAN)
> на эту прошивку не переносятся.
> Исследование A9: [WIFI_SOFTAP_ESP32_ROUTER_RU.md](WIFI_SOFTAP_ESP32_ROUTER_RU.md).
> Компаньон: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md). Веб-панель: [EXTERNAL_API_RU.md](EXTERNAL_API_RU.md).

## 1. Цель

Телефон в машине подключается к постоянной точке доступа компаньона, открывает веб-панель
TBox Monitor (`:8765`) и получает интернет с ГУ. На A10 это делается тем же контуром, что на A9
(ESP32-S3: своя SoftAP → STA к SoftAP ГУ → NAT → DNAT порта панели), но конфиг точки ГУ
можно сделать постоянным, а не читать заново после каждой загрузки.

## 2. Чем A10 отличается от A9

| | Android 9 (mbCAN, live) | Android 10 (Adayo/VHAL, по прошивке) |
|---|---|---|
| Кто поднимает AP | `MB_AIService` / `BootStartReceiver` на каждый boot | Отдельного boot-владельца нет. `MB_AIService` в образе нет |
| SSID по умолчанию | `ro.board.serial` lowercase | `AndroidAP_<1000…9999>` (`android:string/wifi_tether_configure_ssid_default`), только если `/data/misc/wifi/softap.conf` пуст |
| Пароль | Случайный на **каждую** `setWifiApConfiguration`, до эфира не доживает | Пишется как есть и лежит в `softap.conf`. Случайные 12 символов UUID — только в дефолтном конфиге |
| Шифрование дефолта | WPA2 (`KeyMgmt` 4) | SAE / WPA3 (`allowedKeyManagement` bit 8) |
| Диапазон дефолта | 5 ГГц (`apBand = 1`) | 5 ГГц, канал **149** (`apChannel = 0x95`) |
| Рестарт AP | `Settings.Global` `ro.mb.hostapd.state` 0→1 | Ключа нет. Рестарт — `stopTethering` / `startTethering` |
| Пароль в CAN | `eCFG_WIFI_PASSWORD` / `eCFG_WIFI_NAME` | В system-приложениях и framework этих строк нет |
| Gate конфига | `SecurityException` для uid приложения; shell uid 2000 мог читать и писать | Та же проверка `OVERRIDE_WIFI_CONFIG`. Отдельного разрешения для shell в коде нет — на ГУ не проверено |
| IP ГУ на AP | Случайный хост в `192.168.42.0/24` | Пул DHCP tethering в `services.vdex`: `192.168.42.0/24` … `192.168.49.0/24`. Фиксированного адреса SAP нет |

## 3. Как устроен стоковый SoftAP A10

Код framework — compact dex в `system/framework/oat/arm64/wifi-service.vdex` (vdex 019),
классы `com.android.server.wifi.*` и `com.autochips.server.wifi.utils.AtcUtils`.

### 3.1 Хранение конфига

`WifiApConfigStore` читает и пишет `/data/misc/wifi/softap.conf`
(`Environment.getDataDirectory()` + `"/misc/wifi/softap.conf"`).

`setApConfiguration`:

- `null` → дефолт: SSID `AndroidAP_` + (`Random.nextInt(9000) + 1000`), пароль = 12 символов UUID
  (`substring(0,8)+substring(9,13)`), `apBand = 1`, `apChannel = 149`, `KeyMgmt` bit 8 (SAE);
- не `null` → конфиг сохраняется после `apBandCheckConvert`. Пароль **не** подменяется.

`config_wifi_convert_apband_5ghz_to_any` в `framework-res.apk` = false, поэтому
`mRequiresApBandConversion = false`:

- `apBand = -1` (ANY) превращается в 5 ГГц, канал 0;
- `apBand = 0` (2,4 ГГц) и `apBand = 1` (5 ГГц) не трогаются.

Валидация принимает open, WPA2 (`getAuthType()==4`), SAE (`==8`) и OWE (`==9`).
Пароль 8…63 символа.

### 3.2 Канал 149 — только первый старт на 5 ГГц

`HostapdHal.addAccessPoint`, и только если одновременно:

- `ro.atc.aosp_enhancement=true` (в коде дефолт false; на ГУ значение не снято);
- `persist.atc.wifi.bcmenhance=true` (дефолт false);
- это первый старт SoftAP процесса (`mFirstStartSoftAp`);
- ACS выключен;
- band hostapd = 5 ГГц (`IHostapd.Band` 1).

Тогда канал принудительно 149, лог `AP channel force use 149 for 5GBand at 1st.`
На 2,4 ГГц эта ветка не входит.

`getBand`: `apBand -1 → ANY(2)`, `0 → 2,4(0)`, `1 → 5(1)`.

### 3.3 Шифрование в hostapd

Два пути:

- HIDL 1.0 `getEncryptionType`: WPA2 (auth 4) → 2. SAE (auth 8) попадает в default и становится **0 (open)**.
- HIDL 1.2 `atcGetEncryptionType`: WPA2 → 2; SAE → 3, а на чипе `persist.atc.wifi.chip=mt6630` → 2.
  Пароль уходит в `NetworkParams.passphrase`.

Для ESP32 конфиг надо выставлять явно как WPA2 (bit 4), не оставлять заводской SAE.

### 3.4 Кто включает точку

`persist.atc.wifi.autoenableap` читается в `AtcUtils.ATC_AUTO_ENABLE_AP` и **никем не используется**.
Автостарта AP из этого флага нет.

`startTethering` есть только в:

| Пакет | Поведение |
|---|---|
| `com.hk.systemsettings` (`SystemSettings.apk`) | CarPlay / Android Auto: если AP выключена — `WifiAPUtil.switchWifiAp` |
| `com.android.settings` (`ATCSettings`, priv-app) | Обычные настройки точки, QS-логика AOSP |
| SystemUI `HotspotTile` | Плитка быстрых настроек |

`WifiAPUtil.switchWifiAp` (живой путь CP/AA):

1. Если включён клиентский Wi-Fi — выключает его.
2. Берёт `Settings.System` `WIFI_CHANNEL_KEY`, дефолт **149**.
3. Читает текущий `WifiConfiguration`, ставит `apBand = 1`, `apChannel =` этот канал, `setWifiApConfiguration`.
4. `ConnectivityManager.startTethering(0, …)`.

SSID и пароль этот путь не меняет. Методы `CPManager.openWifiAp` / `AAManager.openWifiAp`
(фолбэк SSID `JETOUR X50`, пароль `12345678` из `AP_NAME_KEY` / `AP_PASSWORD_KEY`) **не вызываются**.

`ro.mb.hostapd` в образе нет. Рестарт «0 → пауза → 1» с A9 здесь не работает.

### 3.5 Права

`WifiServiceImpl.getWifiApConfiguration` / `setWifiApConfiguration` требуют
`WifiPermissionsUtil.checkConfigOverridePermission` → `OVERRIDE_WIFI_CONFIG`.
Иначе `SecurityException: App not allowed to read or update stored WiFi Ap config (uid = …)`.
`WRITE_SECURE_SETTINGS` этот gate не открывает (как на A9).

Приложение `vad.dashing.tbox` конфиг само не прочитает и не запишет.
Запись — из shell-хелпера `app_process` (uid 2000), и только если на этом user-образе
у shell есть `OVERRIDE_WIFI_CONFIG`. На A9 это было проверено; на A10 — ещё нет.

`startTethering` из обычного uid тоже привилегированный (`TETHER_PRIVILEGED` у system/priv-app).
Включать и выключать точку из TBox Monitor напрямую нельзя; тот же shell-хелпер.

### 3.6 Сеть и mDNS

Адрес SAP выбирает tethering из пула `192.168.42`…`192.168.49` (`services.vdex`).
Строка `192.168.49.1` в `wifi-service` — адрес P2P group owner, не адрес точки раздачи.

mDNS на A10 не проверялся. На A9 `mdnsd` не отвечал; рассчитывать на `*.local` самого ГУ не стоит.
Анонс делает ESP32.

USB-ethernet tethering с ГУ на ESP32 по-прежнему недоступен: линейка A10 на API 28,
USB tethering host появился в Android 11. USB компаньона остаётся каналом NDJSON.

## 4. Целевая архитектура

Та же, что в исследовании A9:

```
Телефон ──Wi-Fi──► ESP32-S3 SoftAP ──NAT──► ESP32 STA ──Wi-Fi 2,4──► SoftAP ГУ ──NAT──► uplink ГУ (TBox RNDIS или Wi-Fi модем)
                       │                                              │
                       │  фиксированные SSID / пароль / IP            │  свои SSID / пароль / подсеть, задаёт хелпер
                       │  http://192.168.43.1:8765                    │  IP = gateway STA ESP32
                       └─ DNAT :8765 → IP_ГУ:8765                     │
                          mDNS tbox.local с ESP32
```

Отличие от плана A9: пароль точки ГУ задаём сами один раз (и повторяем, если CarPlay вернул 5 ГГц),
а не вычитываем из CAN после каждой загрузки. Телефон по-прежнему не знает IP ГУ.

Uplink интернета — уже существующий маршрут ГУ (RNDIS TBox или Wi-Fi модем). Отдельный мост USB→ESP32 не нужен.

## 5. План реализации

Общий для обеих прошивок каркас (хелпер + прошивка ESP32 + NDJSON + DNAT). Ветвление по ГУ — внутри хелпера.

### Шаг 0. Live-проверка на ГУ A10 (до кода в репозитории)

Хелпер по образцу A9: `app_process` + `Looper` + `System.exit(0)`, команды `get` / `set`.

1. `get` от uid 2000 возвращает SSID, `apBand`, `apChannel`, длину и значение PSK, `allowedKeyManagement`.
   Если `SecurityException` — shell на этой прошивке конфиг не пишет, и шаг 1 другим путём не сделать без патча `system.img`.
2. `set` WPA2, свой PSK, `apBand=0`, канал 6 или 11. Повторный `get` видит тот же PSK (не новый 8-hex, как на A9).
3. `stopTethering` + `startTethering` из хелпера. В logcat hostapd: частота 24xx, не 5745/канал 149.
4. После reboot конфиг в `softap.conf` жив. Точка сама может не подняться — это ожидаемо.
5. Включение беспроводного CarPlay: `SystemSettings` возвращает `apBand=1` и канал из `WIFI_CHANNEL_KEY`.
6. Подсеть клиента AP — какая из `192.168.42`…`49`, шлюз `.1` или другой адрес.
7. Имя интерфейса SAP (`wlan1` или иное) и что uplink в этот момент RNDIS, а не STA на том же чипе.

Критерий: пункты 1–3 зелёные. Иначе реализация на A10 стопорится на правах shell.

### Шаг 1. Хелпер конфига и tethering

Команды:

- `get` — ssid, psk, band, channel, auth;
- `set <ssid> <psk> <band> <channel>` — WPA2 (bit 4), скрытый SSID выкл., валидация 8…63;
- `up` / `down` — `startTethering(0)` / `stopTethering(0)`.

На A9 хелпер по-прежнему только `setband 0`: свой PSK прошивка всё равно сотрёт.
На A10 хелпер пишет полный конфиг.

Доставка: localhost ADB (`adb/LocalhostAdbSession.kt`), APK хелпера в `/data/local/tmp`, как в плане A9.
Из uid приложения `WifiManager.setWifiApConfiguration` не вызывать.

### Шаг 2. Политика в TBox Monitor

Настройка «Раздача для панели» (выкл. по умолчанию):

- свой SSID и пароль точки ГУ (дефолт, например `TBox-<хвост серийника>`, пароль фиксированный, не UUID);
- после boot, когда сервис уже поднялся, хелпер делает `set` + `up`;
- если `get` показывает `band != 0` (CarPlay вернул 5 ГГц) — повторить `set` + `up`;
- пароль хранится в настройках приложения и уходит на ESP32. CAN на A10 не используется.

Конфликт: пока опция включена, беспроводные CarPlay и Android Auto теряют 5 ГГц — у чипа одна SoftAP.
В UI это написано прямо. Выключение опции не обязано возвращать заводской SAE/канал 149:
хелпер при выключении делает `down` и оставляет последний конфиг, либо отдельной командой возвращает `band=1`, канал 149. Возврат 5 ГГц — предпочтительнее, чтобы CP снова поднялся своим путём.

На A9 этот шаг остаётся «только band=0 после boot»: пароль по-прежнему случайный и читается из CAN
(`eCFG_WIFI_PASSWORD`), как в существующем плане.

### Шаг 3. Прошивка ESP32-S3

Без изменений относительно плана A9:

- одновременно SoftAP (телефон) и STA (ГУ), один канал, AP садится на канал STA;
- STA: SSID/PSK точки ГУ из NDJSON `apCfg`, не из прошивки намертво;
- своя подсеть, например `192.168.43.1/24`, DHCP;
- lwIP NAPT на uplink STA;
- шлюз STA = текущий IP ГУ, DNAT TCP `192.168.43.1:8765` → `шлюз:8765`;
- mDNS `tbox.local` анонсирует ESP32, не ГУ.

Ожидаемая скорость NAPT — единицы–десяток Мбит/с: панель, карты, музыка. Видео не цель.

Сообщение протокола (fw компаньона, следующее после 0.8):

`apCfg { ssid, psk }` от хоста. При смене — переподключение STA. На A10 смена редкая (наш set).
На A9 — после каждой загрузки ГУ, источник пароля CAN.

### Шаг 4. Веб-панель

Новый HTTP-сервер не нужен. Уже есть External HTTP API на порту **8765** (`ServerSocket` на ГУ).
Телефон открывает `http://192.168.43.1:8765/` (и `http://tbox.local:8765/` там, где mDNS жив).
ESP32 только пробрасывает TCP. Заголовок `Host` не переписывается.

Строка адреса в приложении по-прежнему может показывать IP ГУ для прямого подключения к стоковой AP.
Рядом — адрес через компаньон `192.168.43.1:8765`, если сессия ESP32 есть и `apCfg` применён.

### Шаг 5. Приёмка

1. A10, опция включена, reboot: через ~1 мин SAP ГУ на 2,4 ГГц с заданным PSK, ESP32 в STA, телефон видит AP компаньона.
2. `http://192.168.43.1:8765/` открывает панель. Интернет с телефона идёт через ГУ.
3. Повторный reboot не меняет пароль точки ГУ. ESP32 подключается без нового `apCfg`, если NVS уже сохранил прошлый.
4. Включение беспроводного CarPlay при активной опции: в течение цикла проверки band возвращается на 2,4, панель снова открывается. CP при этом может отвалиться — это заявленное поведение.
5. Опция выкл.: хелпер гасит точку или возвращает 5 ГГц; телефон к AP компаньона не обязан иметь интернет.
6. A9 не ломается: хелпер не пытается закрепить PSK, пароль для ESP32 берётся из CAN.

## 6. Риски

- Shell на user-сборке A10 может не иметь `OVERRIDE_WIFI_CONFIG`. Это первый live-тест. Обхода из приложения нет.
- `SystemSettings` при каждом старте CP/AA переписывает band на 5 ГГц. Пока опция включена, мы перебиваем это обратно; гонка в момент подключения телефона к CarPlay ожидаема.
- Заводской дефолт SAE на пути hostapd 1.0 превращается в open. Свой WPA2 это обходит, но только после нашего `set`.
- Один радиомодуль: если uplink интернета — Wi-Fi модем на `wlan0`, SAP не может свободно выбрать канал 2,4. Для штатного TBox по RNDIS конфликта нет. Для `ModemSource.WIFI_HTTP` раздачу панели лучше не включать.
- `ro.atc.aosp_enhancement` / `bcmenhance` на конкретном ГУ могут быть true, и первый старт на 5 ГГц всё равно щёлкнет канал 149. На 2,4 это не действует; live-лог это подтвердит.
- Пропускная способность NAPT и число клиентов на ESP32-S3.
