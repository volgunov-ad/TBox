# Wi‑Fi модем Olax F95 (reqproc)

Источник данных: HAR `OlaxF95.har` + `OlaxF95_on_off_connection.har` с реального устройства
(админка `http://192.168.0.1`, папка [Модемы на Яндекс.Диске](https://disk.yandex.ru/d/9oqTGVdLADEycA)).

## Статус

| Этап | Состояние |
|------|-----------|
| Живая проверка на физическом **Olax F95** | **да** — HAR разобран (статус + data on/off + reboot) |
| Логин | **VERIFIED** — Base64 пароля |
| Поллер / UI / управление | в коде (`OlaxReqprocClient`, `WifiModemPoller`) |

## API

| | |
|--|--|
| Чтение | `GET /reqproc/proc_get?multi_data=1&cmd=a,b,c` → JSON |
| Запись | `POST /reqproc/proc_post` (`application/x-www-form-urlencoded`) |
| Referer | `http://<host>/index.html` |
| Origin | `http://<host>` (для POST) |

Это **не** ZTE UFI goform (`/goform/…`), а классический Demo-Webs **`/reqproc`**.

## Логин (VERIFIED)

`POST /reqproc/proc_post` с телом:

```
goformId=LOGIN&password=<Base64(plaintext)>
```

На захваченной прошивке `PASSWORD_ENCODE=true` (`js/set.js`). Успех: `{"result":"0"}` или `"4"`.

Сессия IP-bound; cookie в HAR нет. Последующие `proc_get` с того же клиента.

## Опрос статуса

Полный poll делает **два** `multi_data` запроса: `HOME` (полоски, PPP, `realtime_*_thrpt`) и
`RADIO`+`DEVICE` (RSSI/RSRP, IMEI). Поля thrpt и радиометрики на F95 живут на разных страницах UI.

| `cmd` | Назначение | Маппинг |
|-------|------------|---------|
| `signalbar` | 0–5 полосок | → `NetState.signalLevel` 0–4 |
| `rssi`, `lte_rsrp` | dBm | → `NetState.signalDbm`, CSQ |
| `network_type` / `sub_network_type` | `LTE` / `FDD_LTE`… | → `4G`/`3G`/`2G` |
| `network_provider` | оператор | → `NetValues.operator` |
| `imei`, `sim_imsi`, `ziccid` | идентификаторы | → `NetValues` |
| `ppp_status` | `ppp_connected` / … | → `apnStatus` |
| `modem_main_state` | готовность | → `simStatus` |
| `simcard_roam` | `Home` / `Roaming` | → `regStatus` |
| `realtime_rx_thrpt`, `realtime_tx_thrpt` | скорость ↓/↑, байт/с | → `downloadSpeedBps` / `uploadSpeedBps` |
| `wan_ipaddr` | WAN IP | → `APNState.apnIP` |
| `cr_version`, `hw_version` | прошивка | → `WifiModemSnapshot.firmware` |
| `nv_rsrq`, `nv_sinr`, `lte_band`, `cell_id` | радиометрика | snapshot |

Пример из HAR (LTE / BeeLine): `signalbar=5`, `rssi=-83`, `lte_rsrp=-83`,
`realtime_rx_thrpt=368304`, `cr_version=F95SW1.0_FE_OLAX_SL_…`.

## Управление (VERIFIED)

В отличие от ZTE MF79U goform, **AD/RD не нужны**.

| Действие | Body | Ответ |
|----------|------|-------|
| Отключить данные | `notCallback=true&goformId=DISCONNECT_NETWORK` | `{"result":"success"}` |
| Включить данные | `notCallback=true&goformId=CONNECT_NETWORK` | `{"result":"success"}` |
| Перезагрузка | `goformId=REBOOT_DEVICE` | (часто пустой / обрыв) |

## Код

| Файл | Роль |
|------|------|
| `wifimodem/OlaxReqprocClient.kt` | login / status / data / reboot |
| `wifimodem/OlaxReqprocStatusCmds.kt` | списки `cmd=` для poll |
| `wifimodem/ZteReqprocAuth.kt` | Base64 LOGIN (`Dialect.BASE64_PASSWORD`) |
| `wifimodem/ZteReqprocStatusMapper.kt` | JSON → `NetState` (общие имена полей) |
| `WifiModemModel.OLAX_F95` | модель в настройках |
| `app/src/test/resources/wifimodem/olax_f95/` | обезличенные фикстуры из HAR |

См. также: [WIFI_MODEM_ZTE_MF79U_RU.md](./WIFI_MODEM_ZTE_MF79U_RU.md), [WIFI_MODEM_HUAWEI_E3372_RU.md](./WIFI_MODEM_HUAWEI_E3372_RU.md).
