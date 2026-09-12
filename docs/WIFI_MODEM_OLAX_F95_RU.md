# Wi‑Fi модем как источник сети (Olax F95)

Идея: виджет сигнала (`netWidget*`) и вкладка **Модем** могут брать данные не только с TBox (MDC UDP),
а с внешнего 4G MiFi/роутера, к которому ГУ подключена по Wi‑Fi. Пользователь задаёт IP, логин/пароль
и модель; приложение опрашивает HTTP API модема.

Аналогия по архитектуре — **источник геопозиции** (`LocationSource` + fan-in в `TboxRepository`).

## Статус

| Этап | Состояние |
|------|-----------|
| Исследование API семейства Olax / ZTE `reqproc` | сделано (по открытым источникам) |
| Живая проверка на физическом **Olax F95** | **нет** — нужен захват DevTools / HAR |
| Живая проверка на **ZTE MF79U** (goform) | **да** — HAR разобран, см. [WIFI_MODEM_ZTE_MF79U_RU.md](./WIFI_MODEM_ZTE_MF79U_RU.md) |
| Каркас парсера / маппинга / unit-тесты | в коде (`wifimodem/`) |
| Settings UX, поллер, переключение источника | ещё не подключено |

## Что уже есть в приложении

- Сигнал и вкладка «Модем» → только `TboxRepository.netState` / `netValues` / `APN*` из MDC (`BackgroundService`).
- Гео уже multi-source: `LocationSource` (TBOX / ESP32 / ANDROID / USB).
- HTTP-клиент с basic/digest есть у `HttpRequestWidget`, но он **не парсит ответ** и не пишет в net-state.
- `usesCleartextTraffic=true` — HTTP к `192.168.x.x` допустим.

## Olax F95 — что известно

Продукт: компактный **4G USB dongle / MiFi** с dual-band Wi‑Fi (2.4 + 5.8), внешние антенны CRC9.
Типичный доступ к веб-UI у линейки Olax: `http://192.168.0.1` или `192.168.1.1` / `192.168.8.1`,
логин/пароль часто `admin` / `admin` (на части моделей только пароль).

**Точный диалект прошивки F95 не подтверждён без устройства.** По смежным Olax/ZTE MiFi почти
наверняка используется тот же стек, что и у веб-UI:

| | |
|--|--|
| Чтение | `GET /reqproc/proc_get?isTest=false&multi_data=1&cmd=a,b,c` → JSON |
| Запись | `POST /reqproc/proc_post` (`application/x-www-form-urlencoded`), `goformId=…` |
| Заголовок | `Referer: http://<host>/` (часто обязателен) |

### Полезные `cmd=` для виджета / вкладки «Модем»

| Поле API | Назначение | Маппинг в приложение |
|----------|------------|----------------------|
| `signalbar` (0–5) | полоски UI модема | → `NetState.signalLevel` 0–4 (5→4) |
| `rssi` | dBm | → оценка CSQ ≈ `(rssi+113)/2`, clamp 0..31 |
| `network_type` / `sub_network_type` | LTE / WCDMA / GSM… | → `2G` / `3G` / `4G` |
| `network_provider` | оператор (строка) | → `NetValues.operator` |
| `imei`, `sim_imsi` / `imsi`, `ziccid` / `iccid` | идентификаторы | → `NetValues` |
| `ppp_status` | `ppp_connected` и др. | → `apnStatus` / `APNState.apnStatus` |
| `modem_main_state` | готовность модема/SIM | → `simStatus` |
| `simcard_roam` | `Home` / `Roaming` | → `regStatus` |
| `lte_rsrp`, `nv_rsrq`/`lte_rsrq`, `nv_sinr`/`lte_snr`, `lte_band`, `cell_id` | расширенная радиометрика | пока только в `WifiModemSnapshot` (для будущей вкладки) |
| `wan_ipaddr` / DHCP LAN | IP | → `APNState.apnIP` при наличии |

### Диалекты логина (нужен probe на F95)

Разные прошивки ZTE/Olax делают `LOGIN` по-разному:

1. **Base64 пароля** (Olax M100 / часть USB Olax):  
   `password = base64(plaintext)`, без username; сессия часто привязана к IP клиента, cookie нет.
2. **LD/RD/AD challenge** (классический ZTE): хеш с токенами `LD`/`RD`/`AD`.
3. **Nonce SHA-256** (новые CPE / ZLT):  
   `get_random_login` → `password = base64(sha256_hex(nonce + plaintext))`, иногда `username=base64(user)`, cookie `random`.

Каркас умеет кодировать варианты 1 и 3; вариант 2 и точный выбор для F95 — после захвата с устройства.

Управление (фаза 2+): `CONNECT_NETWORK` / `DISCONNECT_NETWORK`, `REBOOT_DEVICE`, `SET_BEARER_PREFERENCE`
(аналог режимов ON / flight / OFF на вкладке Модем — семантика другая, не AT+CFUN).

## Предлагаемая архитектура

```
ModemSource: TBOX | WIFI_HTTP
WifiModemModel: OLAX_F95 | … (драйверы)

Settings: host, username, password, model, poll interval
  → BackgroundService: start/stop WifiModemPoller vs MDC net/APN updaters
  → драйвер пишет в TboxRepository.netState / netValues / apn* / apnStatus
     (как geo → locValues)

Виджеты и вкладка Модем остаются на тех же StateFlow.
requiresTboxConnection для netWidget* нужно ослабить, если источник WIFI_HTTP.
```

Android-нюанс (как у Routspan): при «Wi‑Fi без интернета» ГУ может уводить HTTP на mobile —
запросы к `192.168.x.x` надо **биндить к Wi‑Fi Network** (`ConnectivityManager.bindProcessToNetwork`
или per-socket `Network.bindSocket`).

## Инструкция: захват API Olax F95 (один раз)

Нужен ПК с Chrome (или Edge) и модем с Wi‑Fi. Достаточно **одного HAR-файла**.

### Шаги

1. Подключите ПК к Wi‑Fi модема (SSID/пароль на наклейке). VPN выключите.
2. В браузере откройте админку — обычно один из адресов:
   `http://192.168.0.1` · `http://192.168.1.1` · `http://192.168.8.1`
3. **До логина** нажмите `F12` → вкладка **Network** / **Сеть**.
4. Включите **Preserve log** / «Сохранять журнал». Список запросов очистите (🚫).
5. Залогиньтесь (часто `admin` / `admin`).
6. Покликайте основные разделы админки: главная/статус, мобильная сеть, SIM/о устройстве,
   Wi‑Fi — чтобы UI сам запросил нужные поля. 20–30 секунд достаточно.
7. В панели Network: ПКМ по списку запросов → **Save all as HAR with content**  
   → файл `olax_f95.har`.

Этого файла хватает: в нём и логин, и опросы статуса, URL, заголовки и тела ответов.

### Перед отправкой (желательно)

Откройте `.har` в блокноте и замените на `***` (поиск по файлу):

- свой недефолтный пароль (если меняли);
- IMEI / IMSI / ICCID / номер телефона — если не хотите светить SIM.

Имена полей (`signalbar`, `ppp_status`, `goformId=LOGIN` и т.п.) трогать не нужно.

### Что прислать

Один файл: **`olax_f95.har`**.  
По желанию одной строкой: какой IP открылся и логин/пароль по умолчанию или свой.

В репозитории после разбора обезличенные куски попадут в  
`app/src/test/resources/wifimodem/olax_f95/`, диалект в этом документе — **VERIFIED**.

### Если админка только с телефона

С телефона HAR снять неудобно — лучше ПК в той же Wi‑Fi. Запасной вариант: прокси
(`mitmproxy` / Charles) на ПК и браузер телефона через него.

Референсы протокола (не F95): [routspan Olax M100](https://github.com/ajshovon/routspan/blob/main/docs/olax-m100-api.md),
[zltrouter reqproc](https://github.com/exbyte-dev/zltrouter/blob/master/docs/protocol.md).

## План внедрения

1. **Сейчас:** документ + чистый маппинг JSON → `NetState`/`NetValues` + unit-тесты + enum источника/модели.
2. OkHttp-клиент `ZteReqprocClient` + авто-probe логина + poller.
3. Prefs + UI на вкладке Модем / в настройках сети (IP, user, pass, модель).
4. Переключение `ModemSource`, ослабление `requiresTboxConnection` для net-виджетов.
5. Управление: data on/off, reboot; позже — расширенные поля (RSRP/SINR) на вкладке.
6. После live-capture — пометить диалект F95 как VERIFIED и добавить фикстуры с реального устройства.

## Код

| Файл | Роль |
|------|------|
| `wifimodem/ModemSource.kt` | `TBOX` / `WIFI_HTTP` |
| `wifimodem/WifiModemModel.kt` | каталог моделей (`OLAX_F95`) |
| `wifimodem/ZteReqprocAuth.kt` | кодирование пароля (base64 / sha256-nonce) |
| `wifimodem/ZteReqprocStatusMapper.kt` | JSON → snapshot / Net* |
| `wifimodem/WifiModemSnapshot.kt` | богатый снимок для UI |
| тесты `wifimodem/*Test.kt` | регрессия маппинга без устройства |
