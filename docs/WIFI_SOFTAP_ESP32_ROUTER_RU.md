# Wi-Fi SoftAP ГУ → ESP32 SoftAP → телефоны: исследования и план

> Статус: исследование завершено (live-тесты на ГУ `D:\Dashing\Android9-mbCan`, X50-9, Android 9 mbCAN).
> Из кода TBox Monitor все экспериментальные изменения выведены (дерево чистое); хелпер остался на ГУ.
> Android 10 (Adayo/VHAL): другой владелец точки, пароль можно закрепить, CAN пароль не публикует —
> [WIFI_SOFTAP_A10_RU.md](WIFI_SOFTAP_A10_RU.md).
> См. также: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md), [EXTERNAL_API_RU.md](EXTERNAL_API_RU.md), [EXTERNAL_API_USER_GUIDE_RU.md](EXTERNAL_API_USER_GUIDE_RU.md).

## 1. Цель

Телефоны в машине должны стабильно заходить на веб-панель / External HTTP API ГУ
и иметь интернет, без ручного ввода случайного пароля AP и случайного IP после каждой загрузки.

## 2. Как устроен стоковый SoftAP ГУ

| Элемент | Факт |
|---|---|
| Владелец конфига | `MB_AIService` (`com.mengbo.aiservice`), `BootStartReceiver` (приоритет 1000, на каждый boot) |
| SSID | `ro.board.serial` в lowercase (на тестовом ГУ — `28adfea9`) |
| Пароль | генерируется **при каждой загрузке** (`generateRandomPassword`: 12 симв. из UUID — но до эфира не доживает, см. §3) |
| Band | 5 ГГц (`apBand = 1`), WPA2 (`KeyMgmt 4`), шифры CCMP |
| Канал в CAN | `CommonService.sendWifiInfo` → `eCFG_WIFI_PASSWORD(63)` / `eCFG_WIFI_NAME` (лог `chitin`: `AP_Configuration: sendToCan …`) |
| Observer рестарта | `CommonService.observeWifiLogger` слушает `Settings.Global "ro.mb.hostapd.state"`: onChange → `stopTethering` → 2 с → `startTethering` |
| IP ГУ | случайный хост в `192.168.42.0/24`, выбирается system_server при старте tethering (не конфигурируется) |

Ключевые исходники (декомпиляция): `MB_AIService/sources/com/mengbo/service/receiver/BootStartReceiver.java`
(конфиг — стр. 40–48, пароль — 50–53, CAN — 62–65),
`com/mengbo/service/common/CommonService.java` (observer — 1242–1263, `sendWifiInfo` — 1022–1040),
`com/mengbo/service/utils/ApManager.java` (70–90).

## 3. Проверенные факты (live-тесты)

### 3.1 Из приложения (uid 100xx) конфиг AP недоступен — binder-level uid-gate
- `WifiManager.get/setWifiApConfiguration` **резолвятся** (не greylist, reflection работает),
  но `invoke` даёт `SecurityException: App not allowed to read or update stored WiFi Ap config (uid = 10091)`.
- Это проверка uid/permission **внутри патченного WifiServiceImpl** — reflection-обходы бессмысленны.
- `WRITE_SECURE_SETTINGS` (pm grant через adb) **не помогает** — gate не про настройки.
- Косвенное чтение пароля приложением: CAN `eCFG_WIFI_PASSWORD` (mbCAN/VHAL) или logcat `chitin`.

### 3.2 Из shell (uid 2000) чтение и запись конфига работают
Хелпер: `CLASSPATH=/data/local/tmp/apset.apk app_process /system/bin vad.dashing.tboxcompanion.ApSetMain <cmd>`
- `get` → `RESULT: ssid=28adfea9 band=1 pskLen=8 psk=…` — чтение OK.
- `set <psk>` → `set=true`, `WifiService: setWifiApConfiguration uid=2000` (вендорский лог).
- Контекст в helper: `Looper.prepareMainLooper()` + `ActivityThread.systemMain().getSystemContext()`;
  **обязателен `System.exit(0)`** — иначе app_process держит stdout и adb-команда висит.

### 3.3 Патч прошивки безусловно перегенерирует PSK при каждой записи конфига
- Любой `setWifiApConfiguration` (даже от shell, даже с тем же паролем, при включённой ИЛИ выключенной AP,
  пароль любой длины вплоть до 63) → PSK мгновенно заменяется свежим случайным 8-hex.
- **SSID и `apBand` при этом сохраняются** (проверено: `setfull MyCar test12345` → SSID применился, PSK — нет).
- Даже собственный пароль `BootStartReceiver` (12 симв.) заменяется 8-символьным — генератор рандомайзера в system_server.
- `setWifiApEnabled(config, enable)` в прошивке **отсутствует** (`NoSuchMethodException`).
- Следствие: свой пароль AP и статический IP ГУ достижимы **только патчем system.img** (от которого отказались).

### 3.4 Перевод AP в 2,4 ГГц работает из shell и переживает рестарт AP
- `setband 0` → `set=true`; конфиг: `band=0`; рестарт через `ro.mb.hostapd.state` 0→1 — band сохранился.
- Железо реально уходит в 2,4: `SoftApManager mReportedFrequency: 2462`,
  `hostapd: wlan1: ACS-COMPLETED freq=2462 channel=11`, список каналов SAP расширяется до 1–13.
- При каждой загрузке `BootStartReceiver` возвращает 5 ГГц → переприменять после boot (см. план, шаг 2).

### 3.5 Рестарт AP из приложения — работает
- `Settings.Global "ro.mb.hostapd.state"`: запись `0`, пауза ≥2 с, запись `1`.
- Из приложения нужен `WRITE_SECURE_SETTINGS` (`adb shell pm grant vad.dashing.tbox android.permission.WRITE_SECURE_SETTINGS`);
  запись значения, равного текущему, observer не нотифицирует — поэтому всегда 0 → 1.
- Верификация: `NetworkInterface("wlan1")` down/up (poll 500 мс, до ~20 с).
- После рестарта `CommonService` сам ресинхронизирует SSID/пароль в CAN (`sendToCan` в логе `chitin`).

### 3.6 NSD/mDNS на стоковой прошивке не работает
- `NsdManager.registerService("_http._tcp", "TBox Monitor")` проходит («registered»), но:
- mdnsd **не отвечает на mDNS-запросы вообще**: multicast с самого ГУ (интерфейсы wlan0 и wlan1) и
  unicast с ПК на `192.168.1.128:5353` — ни одного ответа; PTR/SRV-записи не выходят в эфир
  (проба mDNS-сокетом из app_process; принимались только собственные запросы).
- `net.hostname` на ГУ пустой; iOS Bonjour сервис «TBox Monitor» не найдёт.
- Следствие: адрес вида `http://<host>.local:8765/` на стоковой прошивке **не резолвится**.

### 3.7 Интернет ГУ → ESP32 по USB невозможен
- Компаньон — **ESP32-S3** (native USB OTG, TinyUSB CDC, NDJSON v1, fw 0.8+; см. ESP32_COMPANION_RU.md).
- ESP32-S3 умеет USB-gadget (RNDIS/NCM), но Android 9 **не умеет «USB/Ethernet tethering» как host**
  (появилось в Android 11) — ГУ не раздаст модемный интернет в usb0 без патча system.img.
- USB остаётся **управляющим каналом** (NDJSON: передача пароля AP, команд), не интернет-каналом.
- Существующий CDC-туннель TCP поверх serial для интернет-браузинга слишком медленный.

### 3.8 Прочее
- Адрес страницы API в приложении показывает IP ГУ (`ExternalApiController.lanAddresses()`) — это единственный
  рабочий на стоковой прошивке способ (плюс компаньон по gateway, см. §4).
- Инструменты: jadx 1.5.3 CLI — `java -cp D:\Tools\JADX\lib\jadx-gui-1.5.3-all.jar jadx.cli.JadxCLI …` (класс `jadx.cli.JadxCLI`);
  carve cdex из `services.vdex` (vdex 019) с офсетами «на глаз» классы не вернул — для разбора system_server нужен
  корректный vdexExtractor/порядок секций.
- Артефакты: хелпер на ГУ `/data/local/tmp/apset.apk` (source — бэкап `%TEMP%\rufp\companion_backup\`);
  декомпиляции вендора: `D:\Dashing\Android9-mbCan\MB_AIService\sources\…`, `FactoryMode\sources\…`.

## 4. Целевая архитектура

```
Телефон ──Wi-Fi──► ESP32-S3 SoftAP ──NAT (lwIP NAPT)──► ESP32-S3 STA ──Wi-Fi 2,4──► SoftAP ГУ ──tethering NAT──► модем ──► интернет
                       ▲                                                       ▲
                       │  фиксированные SSID/пароль/IP                          │ случайный PSK (OK: ESP32 узнаёт его)
                       │  http://tbox.local:8765 (mdns ESP32)                   │ IP ГУ = default gateway STA-интерфейса ESP32
                       └─ веб-панель: DNAT 8765 → IP_ГУ:8765 ───────────────────┘
```

Телефон получает: постоянный SSID/пароль, постоянный адрес панели (у ESP32 — фиксированный IP,
плюс ответ DNS компаньона на `tbox.local`), интернет через двойной NAT (ГУ свой tethering-NAT
для клиентов AP оставляет как есть). Имя и пароль точки компаньона приложение создаёт один раз
(`TBox-` и четыре hex, пароль из 10 символов), хранит в настройках и передаёт в `apCfg.apSsid` /
`apCfg.apPsk`. Следующие загрузки их не меняют. Пользователь может задать другие в разделе компаньона;
если точка уже включена, новое имя применяется без переподключения к ГУ. Выключение настройки
останавливает радио точки (`esp_wifi_stop`), а не только станцию.

**Как телефон узнаёт адрес ГУ — никак, это не нужно.** Это не HTTP-reverse-proxy, а L4-DNAT:
телефон всегда подключается к фиксированному `<ip_esp32>:<порт>` (или `tbox.local:<порт>`: браузер спрашивает DNS у DHCP, это сам компаньон, и он отвечает A=192.168.4.1; mDNS остаётся запасным),
ESP32 пересылает TCP-соединение на текущий `<ip_гу>:<тот же порт>` своим прокси (lwIP `ip_portmap`
не срабатывает для пакетов на собственный адрес точки, пока на ней включён NAPT). Порт — из настроек API,
поле `apCfg.port`; если его нет, остаётся 8765. Смена порта в приложении перевешивает слушающий сокет без перезапуска точки. Текущий IP ГУ ESP32 узнаёт сам — это
default gateway его STA-интерфейса (DHCP от AP ГУ); при перезагрузке ГУ правило переприменяется
автоматически. Соединение прозрачное: веб-сервер ГУ — простой ServerSocket, Host-заголовок не важен.
Reverse-proxy (кэш/rewrite/auth) в базовой схеме не нужен и не предполагается.

## 5. План внедрения

### Шаг 0 — очистка (ВЫПОЛНЕНО)
Из TBox Monitor удалены все эксперименты с AP (ApHotspotManager, настройки/UI пароля, boot-хук пароля,
NSD-анонс, компаньон-APK, mDNS-строка). Репозиторий = HEAD. На ГУ остался `/data/local/tmp/apset.apk`.

### Шаг 1 — переприменение band=0 после каждой загрузки ГУ

Сделано для A9 в приложении: вкладка компаньона «Точка доступа» и boot-хук через 45 с
(`HuSoftApRouter` → `HuSoftApMain band24`). Решение принимается по живой частоте
`SoftApManager.mReportedFrequency`, а не по сохранённому `apBand`: после загрузки конфиг
может остаться `band=0`, пока hostapd всё ещё маячит на 5 ГГц. `band24` пишет `apBand=0`
и `apChannel=0` (ACS; на этой прошивке фиксированный канал игнорируется, эфир оказывается
на 24xx). Рестарт hostapd — `settings put global ro.mb.hostapd.state` 0→1: `Settings.Global`
из `app_process` получает отказ. Прошивка компаньона 0.9.1 поднимает свою точку на канале
STA, потому что у ESP32-S3 один радиомодуль. На A10 этот шаг не вызывается.
- Механизм: boot-хук в `BackgroundService` (по образцу прежнего `scheduleApPasswordBootApply`, задержка ~45 с —
  вендорский `BootStartReceiver` с приоритетом 1000 отрабатывает раньше) → **локальный ADB** (127.0.0.1:5555,
  готовый клиент в `adb/LocalhostAdbSession.kt`) → `app_process … ApSetMain setband 0` → контроль `get` (band=0).
- Напрямую из app-uid писать конфиг нельзя (§3.1), поэтому только через shell-хелпер.
- Критерий приёмки: после перезагрузки ГУ `hostapd` поднимается на каналах 1–13 (logcat `ACS-COMPLETED freq=24xx`).

### Шаг 2 — прошивка ESP32-S3: AP + STA + NAPT
- База: готовые ESP32 NAT Router проекты (lwIP `ip_napt`), ESP-IDF; WiFi AP+STA одновременно
  (аппаратно один канал — AP встанет на канал STA).
- STA: подключение к AP ГУ (SSID = серийник ГУ; канал 2,4 после шага 1).
- AP: фиксированные SSID/пароль/подсеть (например `192.168.43.1/24`), DHCP для телефонов.
- Ограничения: NAPT на ESP32 ≈ 5–10 Мбит/с — навигация/музыка/панель ок, стриминг видео — нет.

### Шаг 3 — передача пароля AP ГУ в ESP32
- Вариант А (предпочтительно): приложение знает пароль из CAN (`eCFG_WIFI_PASSWORD`) и кладёт его в ESP32
  по существующему NDJSON-каналу (новое сообщение `apCfg {ssid, psk}`; fw компаньона 0.8+).
- Вариант Б: ESP32 сам парсит CAN (MCP2515 уже есть в fw 0.5+) и ловит `eCFG_WIFI_PASSWORD`.
- Учёт: пароль меняется при каждой загрузке ГУ — ESP32 должен переподключаться по факту события
  (from CAN/NDJSON), а не только при старте.

### Шаг 4 — проброс веб-панели и «честный» mDNS
- ESP32 узнаёт текущий IP ГУ = default gateway своего STA-интерфейса.
- DNAT/port-forward: `<ip_esp32>:8765` → `<ip_гу>:8765` (и, при желании, весь HTTP).
- mDNS-анонс от ESP32 (lwIP mdns): `http://tbox.local:8765/` — iOS/Android-браузеры с поддержкой mDNS;
  для остальных — постоянный IP ESP32 (вводить один раз).
- NSD-код в приложении не возвращаем: анонсирует ESP32.

### Шаг 5 — приёмка
1. Reboot ГУ → через ~60 с AP ГУ в 2,4 ГГц, ESP32 подключился, телефон видит свою AP.
2. Телефон: интернет есть (трассировка через двойной NAT), `http://<esp32>:8765/` открывает панель.
3. Смена пароля AP ГУ (перезагрузка) → ESP32 переподключился автоматически ≤ ~2 мин.
4. Без ESP32 (отказ): телефон подключается к AP ГУ напрямую (пароль с экрана ГУ), IP — со страницы API.

## 6. Риски и открытые вопросы

- Какой именно Wi-Fi-путь уже занят у компаньона (сейчас Wi-Fi не используется — NDJSON по USB; конфликтов нет).
- Стабильность ACS на 2,4 ГГц при загруженном эфире; ESP32 и ГУ в одном канале.
- Порядок старта: если `BootStartReceiver` на какой-то прошивке запустится позже 45 с — band-хук применит band=0
  до генерации вендора, и вендор вернёт 5 ГГц; лечится повторной попыткой (цикл с проверкой `get`).
- Throughput NAPT; количество клиентов.
- Если в будущем решим патчить system.img: правки делать в одном месте — WifiServiceImpl
  (убрать перегенерацию PSK) + генератор IP ГУ; тогда шаг 1 и пароль ESP32 станут необязательными.
