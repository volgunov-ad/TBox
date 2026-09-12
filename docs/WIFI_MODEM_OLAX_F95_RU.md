# Wi‑Fi модем как источник сети (Olax F95)

Идея: виджет сигнала (`netWidget*`) и вкладка **Модем** могут брать данные не только с TBox (MDC UDP),
а с внешнего 4G MiFi/роутера, к которому ГУ подключена по Wi‑Fi. Пользователь задаёт IP, логин/пароль
и модель; приложение опрашивает HTTP API модема.

Аналогия по архитектуре — **источник геопозиции** (`LocationSource` + fan-in в `TboxRepository`).

## Статус

| Этап | Состояние |
|------|-----------|
| Исследование API семейства Olax / ZTE `reqproc` | сделано (по открытым источникам) |
| Живая проверка на физическом **Olax F95** | **нет** — нужен захват DevTools с реального устройства |
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

## Как подтвердить F95 (один раз на устройстве)

1. Подключиться к Wi‑Fi модема, открыть админку, DevTools → Network (XHR), Preserve log.
2. Залогиниться; для каждого запроса сохранить method, URL, body, JSON.
3. Снять batch:  
   `cmd=network_type,sub_network_type,rssi,signalbar,lte_rsrp,network_provider,imei,sim_imsi,ziccid,ppp_status,modem_main_state,simcard_roam,cr_version`
4. Проверить `Server:` header, `cr_version`, ответ `cmd=LD` и `cmd=get_random_login`.
5. Вложить обезличенные фикстуры в `app/src/test/resources/wifimodem/olax_f95/` и зафиксировать диалект в этом документе.

Открытые референсы (не F95, но тот же протокол):

- [routspan `docs/olax-m100-api.md`](https://github.com/ajshovon/routspan/blob/main/docs/olax-m100-api.md) — Olax M100
- [zltrouter `docs/protocol.md`](https://github.com/exbyte-dev/zltrouter/blob/master/docs/protocol.md) — ZTE/ZLT `reqproc`
- Habr/forpes — SMS через Olax USB + `LOGIN` base64

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
