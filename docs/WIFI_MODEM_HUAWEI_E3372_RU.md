# Wi‑Fi модем Huawei E3372-325 (HiLink XML API)

Источник: HAR `HUAWEI-E3372-325.har` (админка `http://192.168.8.1`).

Это **другой стек**, не ZTE goform и не Olax `/reqproc`: Huawei HiLink REST/XML под `/api/...`.

## Устройство из HAR

| Поле | Значение |
|------|----------|
| `DeviceName` | `E3372-325` |
| `HardwareVersion` | `CL5E3372M` |
| `SoftwareVersion` | `3.0.2.62(H057SP9C983)` |
| `WebUIVersion` | `WEBUI 3.0.2.62(W13SP5C7702)` |
| `Classify` | `hilink` |
| IMEI / ICCID / IMSI | есть в `/api/device/information` |
| WAN / DNS | в `/api/device/information`; трафик в `/api/monitoring/traffic-statistics` |
| Радио | `/api/device/signal` (`rsrp`, `rsrq`, `rssi`, `sinr`, `cell_id`, `mode=7` ≈ LTE) |

`ConnectionStatus` в `/api/monitoring/status`: в захвате **`901`** (данные online) и **`902`** (после выключения dataswitch).

## Управление (VERIFIED)

Заголовок `__RequestVerificationToken` обязателен на POST (токен ротируется).

| Действие | Метод / путь | Body | Ответ |
|----------|--------------|------|--------|
| Выключить mobile data | `POST /api/dialup/mobile-dataswitch` | `<request><dataswitch>0</dataswitch></request>` | `<response>OK</response>` |
| Включить mobile data | `POST /api/dialup/mobile-dataswitch` | `<request><dataswitch>1</dataswitch></request>` | `<response>OK</response>` |
| Прочитать switch | `GET /api/dialup/mobile-dataswitch` | — | `<dataswitch>0\|1</dataswitch>` |
| Перезагрузка | `POST /api/device/control` | `<request><Control>1</Control></request>` | `<response>OK</response>` |

В этом HAR отдельного `POST /api/user/login` нет (типично для HiLink без пароля / уже открытой сессии). Перед записью нужен актуальный verification token.

## Полезные GET для статуса

- `/api/device/information` — IMEI, ICCID, IMSI, версии, WAN IP  
- `/api/monitoring/status` — `ConnectionStatus`, `SignalIcon`, …  
- `/api/monitoring/traffic-statistics` — трафик / uptime сессии  
- `/api/device/signal` — RSRP/RSRQ/RSSI/SINR (часто со суффиксами `dBm`/`dB`; парсер снимает единицы)  
- `/api/net/current-plmn`, `/api/net/net-mode`

## Статус в TBox

Подключён как `WifiModemModel.HUAWEI_E3372`: опрос HiLink XML и управление dataswitch/reboot через `HuaweiHilinkClient` / `WifiModemPoller`.

После **reboot** poller пересоздаёт HTTP-клиент/сессию и в течение ~2 минут не затирает зеркало net/APN на временных ошибках недоступности, чтобы данные снова появились без переключения источника модема.

См. также: [WIFI_MODEM_ZTE_MF79U_RU.md](./WIFI_MODEM_ZTE_MF79U_RU.md), [WIFI_MODEM_OLAX_F95_RU.md](./WIFI_MODEM_OLAX_F95_RU.md).
