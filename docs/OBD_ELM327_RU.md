# ELM327 / OBD-II (Bluetooth SPP)

Подключение классического Bluetooth-адаптера **ELM327** (RFCOMM/SPP) для чтения live-параметров OBD-II Mode 01 и сохранённых кодов ошибок (Mode 03).

## Возможности

- Вкладка меню **ELM327**: включение, выбор спаренного устройства, статус, напряжение адаптера (ATRV).
- Кнопка **«Читать коды ошибок»** — Mode 03, список SAE-кодов (`P0301` и т.п.).
- Виджет **«Параметр OBD»** (`obdMetricWidget`): один числовой PID с выбором в «Дополнительно».

## Ограничения v1

- Только classic Bluetooth (не BLE / Wi‑Fi ELM).
- Mode 01 live + ATRV; Mode 03 по кнопке.
- Очистка кодов (Mode 04) не реализована.
- Нет каталога текстовых расшифровок DTC.

## Подключение

1. DataStore: `elm327_enabled`, `elm327_device_address`, `elm327_pairing_pin`.
2. `BackgroundService` стартует `Elm327Manager` при enable + непустом MAC.
3. При отсутствии bond (типично API ≤ 30 / ГУ Android 9): `Elm327BtPairing` пробует `createBond` с PIN (сохранённый или 1234/0000/6789/8888).
4. RFCOMM: insecure SPP UUID → secure SPP → reflection channel 1; таймаут connect 15 с; `stop` закрывает сокет, чтобы сорвать зависший connect.
5. AT-init: `ATZ ATE0 ATL0 ATS0 ATH0 ATSP0`, далее poll только interested PID + ATRV при интересе вкладки/виджета.

## Архитектура

| Класс | Роль |
|-------|------|
| `obd/Elm327BluetoothSession` | RFCOMM сокет, line IO, connect timeout/fallback |
| `obd/Elm327BtPairing` | createBond + PIN |
| `obd/Elm327Protocol` | AT-init, parse Mode 01 / ATRV / Mode 03 |
| `obd/Elm327Manager` | reconnect, poll interested PIDs, DTC request |
| `obd/ObdRepository` | StateFlow для UI / виджетов |
| `obd/ObdInterestAggregator` | объединяет PID с панелей |

Служба: `BackgroundService` стартует manager при `elm327Enabled` + непустом MAC.

## Настройки DataStore

- `elm327_enabled` (default false)
- `elm327_device_address` (MAC)
- `elm327_pairing_pin` (опционально)

## Виджет

- `dataKey`: `obdMetricWidget`
- конфиг: `obdPidId` (например `rpm`, `coolant_temp`)
- панели публикуют interest через `publishObdInterest` → опрашиваются только видимые PID

## Разрешения

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (API ≤ 30)
- `BLUETOOTH_CONNECT` (API 31+)
- `BLUETOOTH_SCAN` + location (только для кнопки сканирования во вкладке)
- `uses-feature bluetooth` optional
