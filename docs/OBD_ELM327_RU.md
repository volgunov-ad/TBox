# ELM327 / OBD-II (Bluetooth SPP)

Подключение классического Bluetooth-адаптера **ELM327** (RFCOMM/SPP) для чтения live-параметров OBD-II Mode 01 и сохранённых кодов ошибок (Mode 03).

## Возможности

- Вкладка меню **ELM327**: включение, выбор спаренного устройства, статус, напряжение адаптера (ATRV).
- Кнопка **«Читать коды ошибок»** — Mode 03, список SAE-кодов (`P0301` и т.п.).
- Виджет **«Параметр OBD»** (`obdMetricWidget`): один числовой PID с выбором в «Дополнительно».

## Ограничения v1

- Только classic Bluetooth (не BLE / Wi‑Fi ELM).
- Устройство должно быть уже спарено в системе (без discovery).
- Только Mode 01 live + ATRV; Mode 03 по кнопке.
- Очистка кодов (Mode 04) не реализована.
- Нет каталога текстовых расшифровок DTC.

## Архитектура

| Класс | Роль |
|-------|------|
| `obd/Elm327BluetoothSession` | RFCOMM сокет, line IO |
| `obd/Elm327Protocol` | AT-init, parse Mode 01 / ATRV / Mode 03 |
| `obd/Elm327Manager` | reconnect, poll interested PIDs, DTC request |
| `obd/ObdRepository` | StateFlow для UI / виджетов |
| `obd/ObdInterestAggregator` | объединяет PID с панелей |

Служба: `BackgroundService` стартует manager при `elm327Enabled` + непустом MAC.

## Настройки DataStore

- `elm327_enabled` (default false)
- `elm327_device_address` (MAC)

## Виджет

- `dataKey`: `obdMetricWidget`
- конфиг: `obdPidId` (например `rpm`, `coolant_temp`)
- панели публикуют interest через `publishObdInterest` → опрашиваются только видимые PID

## Разрешения

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (API ≤ 30)
- `BLUETOOTH_CONNECT` (API 31+)
- `uses-feature bluetooth` optional
