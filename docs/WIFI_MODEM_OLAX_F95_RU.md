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

## Инструкция: захват API Olax F95 (один раз)

Нужен ноутбук/ПК (Chrome или Firefox) и сам модем с питанием и SIM.
Цель — сохранить 5–10 реальных HTTP-запросов админки, чтобы зафиксировать диалект логина
и имена полей статуса. После этого можно дописать драйвер без угадываний.

### 0. Подготовка

1. Включите F95, дождитесь Wi‑Fi.
2. Подключите ПК к Wi‑Fi модема (SSID/пароль на наклейке корпуса).
3. На ПК **отключите VPN** и по возможности мобильный/другой интернет (чтобы браузер
   ходил на `192.168.x.x` именно через Wi‑Fi модема).
4. Откройте блокнот / пустую папку `olax_f95_capture/` — туда сложите экспорты.

### 1. Открыть админку и DevTools

1. В Chrome: адресная строка → по очереди попробуйте:
   - `http://192.168.0.1`
   - `http://192.168.1.1`
   - `http://192.168.8.1`
   - `http://192.168.100.1`  
   Какой открыл страницу входа — тот IP и есть хост.
2. **До логина** нажмите `F12` (или ПКМ → «Просмотреть код») → вкладка **Network** / **Сеть**.
3. Включите:
   - **Preserve log** / «Сохранять журнал»
   - фильтр **Fetch/XHR** (или «XHR») — HTML/CSS не нужны
4. Очистите список запросов (🚫 Clear).

### 2. Снять логин

1. Введите логин/пароль (часто `admin` / `admin`; на части Olax только поле пароля) → Войти.
2. В Network появятся запросы. Найдите всё, что содержит в URL:
   - `reqproc` / `proc_get` / `proc_post` / `goform` / `login` / `LOGIN`
3. Для **каждого** такого запроса (ПКМ по строке → Copy):
   - **Copy as cURL** (bash) — сохранить в файл `01_login_curl.txt`
   - **Copy response** — в `01_login_response.json` (или `.txt`)
4. Дополнительно откройте запрос → вкладка **Headers** и запишите в `00_notes.txt`:
   - Request URL (полный)
   - Request Method
   - Request Headers: `Referer`, `Cookie`, `Content-Type`
   - Response Headers: `Server`, `Set-Cookie`
   - Form Data / Query String Parameters (все поля: `goformId`, `password`, `username`, …)

**Не присылайте реальный пароль в открытом виде.** В cURL замените значение пароля на `***`
(оставьте вид кодирования: `YWRtaW4=` = base64(`admin`) — это нормально, если пароль дефолтный;
если свой — замаскируйте и напишите «пароль был заменён, формат поля такой-то»).

### 3. Снять статус после логина

Не закрывая DevTools:

1. Походите по пунктам меню админки: статус / сигнал / SIM / о модеме / мобильная сеть —
   чтобы UI сам запросил поля.
2. Либо в адресной строке (подставив свой IP) откройте пробный multi-read:

```text
http://192.168.0.1/reqproc/proc_get?isTest=false&multi_data=1&cmd=network_type,sub_network_type,rssi,signalbar,lte_rsrp,lte_rsrq,lte_snr,lte_band,cell_id,network_provider,imei,sim_imsi,imsi,ziccid,iccid,ppp_status,modem_main_state,simcard_roam,wan_ipaddr,cr_version,wa_inner_version
```

Если путь 404 — попробуйте варианты (встречаются у разных сборок):

```text
http://192.168.0.1/goform/goform_get_cmd_process?isTest=false&multi_data=1&cmd=network_type,rssi,signalbar,ppp_status,imei
http://192.168.0.1/reqproc/proc_get?isTest=false&cmd=network_type,rssi,signalbar
```

3. Ответы JSON → файлы:
   - `02_status_batch.json` — основной batch
   - при других URL — `02_status_alt.json`
4. Снова Copy as cURL для одного успешного status-запроса → `02_status_curl.txt`.

### 4. Проба диалекта логина (важно)

В адресной строке по очереди (тот же IP), ответы сохранить:

| Файл | URL |
|------|-----|
| `03_ld.json` | `http://IP/reqproc/proc_get?isTest=false&cmd=LD` |
| `04_get_random_login.json` | `http://IP/reqproc/proc_get?isTest=false&cmd=get_random_login` |
| `05_loginfo.json` | `http://IP/reqproc/proc_get?isTest=false&cmd=loginfo` |
| `06_cr_version.json` | `http://IP/reqproc/proc_get?isTest=false&cmd=cr_version,wa_inner_version,Language&multi_data=1` |

Интерпретация (для себя / в `00_notes.txt`):

- `LD` пустой `{"LD":""}` + в login только `password=base64(...)` → диалект **BASE64_PASSWORD**
- `get_random_login` отдаёт nonce → скорее **SHA256_NONCE**
- в login есть поля вроде `AD` / хеш с `LD`/`RD` → **LD/RD/AD challenge**
- путь `goform_get_cmd_process` вместо `reqproc` → другой диалект URL (тоже нормально, зафиксируем)

### 5. Экспорт всего лога (по желанию)

В Chrome Network: ПКМ по списку запросов → **Save all as HAR with content** → `olax_f95.har`.

Перед отправкой откройте HAR/JSON текстовым редактором и замените:

- IMEI, IMSI, ICCID, номер телефона, WAN IP оператора
- свой недефолтный пароль / SSID / MAC, если попали

Оставить можно: имена полей, коды `result`, `signalbar`, `network_type`, `ppp_status`, `cr_version`.

### 6. Что прислать / куда положить

Минимум:

```text
olax_f95_capture/
  00_notes.txt              # IP, Server header, что сработало, диалект если понятен
  01_login_curl.txt
  01_login_response.json
  02_status_batch.json
  02_status_curl.txt
  03_ld.json
  04_get_random_login.json
  05_loginfo.json
  06_cr_version.json
  olax_f95.har              # опционально
```

В репозитории после ревью фикстуры лягут в:

`app/src/test/resources/wifimodem/olax_f95/`

и в этом документе диалект будет помечен **VERIFIED**.

### 7. Быстрый чеклист

- [ ] ПК на Wi‑Fi модема, админка открывается
- [ ] Preserve log + Fetch/XHR
- [ ] Login: cURL + response + headers в notes
- [ ] Status batch JSON (хотя бы signal/network/ppp/imei)
- [ ] `LD`, `get_random_login`, `cr_version`
- [ ] Секреты и идентификаторы SIM замазаны

### Альтернатива без DevTools (если только телефон)

С телефона в той же Wi‑Fi удобнее сложнее снять XHR. Варианты:

1. ПК всё же предпочтительнее.
2. Или временно поднять на ПК `mitmproxy`/`Charles` и ходить в админку через него —
   сохраните те же URL/body/response.

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
