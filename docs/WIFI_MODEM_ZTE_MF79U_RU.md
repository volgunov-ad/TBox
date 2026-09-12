# Wi‑Fi модем ZTE MF79U (goform)

Источник данных: HAR `ZTE_MF79U.har` с реального устройства (админка `http://192.168.0.1`).

## API

| | |
|--|--|
| Чтение | `GET /goform/goform_get_cmd_process?isTest=false&multi_data=1&cmd=a,b,c` |
| Запись | `POST /goform/goform_set_cmd_process` (`application/x-www-form-urlencoded`) |
| Referer | `http://<host>/index.html` (желателен) |

Это **не** `/reqproc` (Olax F95 и часть ZTE CPE), а классический UFI **goform**.

## Логин (VERIFIED)

1. `GET ...?isTest=false&cmd=LD` → `{"LD":"<64 hex>"}`
2. `password = SHA256_hex_upper( SHA256_hex_upper(plaintext) + LD )`
3. `POST isTest=false&goformId=LOGIN&password=<hash>`
4. Ответ: `{"result":"0"}` — ок, `"3"` — неверный пароль.

На захваченном устройстве пароль был `admin` (хэш сверен с HAR).

Сессия после логина: последующие `goform_get_cmd_process` с того же клиента; cookie в HAR не обязателен (IP-bound, как у многих UFI).

## Поля статуса (для виджета / вкладки «Модем»)

| goform `cmd` | Назначение | Маппинг |
|--------------|------------|---------|
| `signalbar` | 0–5 полосок | → `NetState.signalLevel` 0–4 |
| `rssi` | dBm | → CSQ ≈ `(rssi+113)/2` |
| `network_type` | `LTE` / `WCDMA` / `GSM` / `LIMITED_SERVICE_GSM`… | → `4G`/`3G`/`2G`/`нет сети` |
| `network_provider` | оператор | → `NetValues.operator` |
| `imei`, `imsi` / `sim_imsi`, `iccid` | идентификаторы | → `NetValues` |
| `ppp_status` | `ppp_connected` / `ppp_disconnected` | → `apnStatus` |
| `modem_main_state` | `modem_init_complete` / `modem_sim_undetected`… | → `simStatus` |
| `simcard_roam` | `Home` / `Roaming` | → `regStatus` |
| `wan_ipaddr` | WAN IP | → `APNState.apnIP` |
| `wa_inner_version`, `hardware_version` | прошивка | → `WifiModemSnapshot.firmware` |
| `lte_rsrp`, `lte_rsrq`, `lte_snr`, `lte_band`, `cell_id` | радиометрика | snapshot (для UI позже) |

В захваченном HAR SIM не была вставлена: `modem_sim_undetected`, `LIMITED_SERVICE_GSM`, `ppp_disconnected`. Для кейса «в сети» добавлена синтетическая фикстура `status_connected_synthetic.json`.

## Код

| Файл | Роль |
|------|------|
| `wifimodem/ZteGoformAuth.kt` | LD + double SHA256 LOGIN |
| `wifimodem/ZteGoformStatusCmds.kt` | списки `cmd=` для poll |
| `wifimodem/ZteReqprocStatusMapper.kt` | общий маппинг JSON→`NetState` (поля совместимы) |
| `WifiModemModel.ZTE_MF79U` | модель в настройках |
| `app/src/test/resources/wifimodem/zte_mf79u/` | обезличенные фикстуры из HAR |

## Замечания по захвату

- В HAR IP `192.168.0.1`, SSID вида `ZTE_…`, `hardware_version=MF79U-HW1.0`.
- Сырой `.har` (~35 МБ) в репозиторий не кладём — только вырезанные JSON.
- Управление (фаза 2): `goformId=CONNECT_NETWORK` / `DISCONNECT_NETWORK`, `REBOOT_DEVICE` и т.п. (в этом HAR почти не вызывались).

См. также общий план: [WIFI_MODEM_OLAX_F95_RU.md](./WIFI_MODEM_OLAX_F95_RU.md).
