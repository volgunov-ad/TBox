# ELM327 / OBD-II (Bluetooth SPP)

Подключение классического Bluetooth-адаптера **ELM327** (RFCOMM/SPP) для чтения live-параметров OBD-II Mode 01, freeze frame (Mode 02) и кодов ошибок (Mode 03 / 07).

## Возможности

- Вкладка меню **ELM327**: включение, выбор устройства, статус шины (ATDP/ATDPN, время ответа, ошибки), напряжение адаптера (ATRV).
- **Discovery PID** по кнопке + сброс; сохранение в DataStore; в dropdown виджета пометка «нет в ECU» без фильтрации списка.
- **DTC**: Mode 03 (stored), Mode 07 (pending), Mode 04 clear с подтверждением.
- **Расшифровка DTC**: общий SAE-каталог EN в `assets/obd/dtc_en.tsv` (~3000 кодов, MIT mytrile/obd-trouble-codes). Коды производителя могут отсутствовать.
- **Статус мониторов**: Mode 01 PID `01` / `41` — MIL, число DTC, readiness (spark/compression).
- **Freeze frame (Mode 02)**: по кнопке — DTC-причина (`0202`), support bitfield (`0200`…), затем известные PID с теми же формулами, что Mode 01. Сбрасывается вместе с Mode 04.
- **Экспорт DTC**: кнопка сохраняет последний снимок (мониторы / stored / pending / freeze frame) в `Downloads/tbox_obd_dtc_*.txt`.
- Виджет **«Параметр OBD»** (`obdMetricWidget`): полный список Mode 01 PID + ATRV.

## Ограничения

- Только classic Bluetooth (не BLE / Wi‑Fi ELM).
- Расшифровки DTC на английском (общий каталог); OEM-коды без записи в каталоге показываются как «нет в каталоге».

## Подключение

1. DataStore: `elm327_enabled`, `elm327_device_address`, `elm327_pairing_pin`,
   `elm327_supported_pids`, `elm327_discovery_at_ms`.
2. `BackgroundService` стартует `Elm327Manager` при enable + непустом MAC.
3. При отсутствии bond (типично API ≤ 30 / ГУ Android 9): `Elm327BtPairing` пробует `createBond` с PIN (сохранённый или 1234/0000/6789/8888). Сработавший при переборе PIN записывается в `elm327_pairing_pin`.
4. RFCOMM: insecure SPP UUID → secure SPP → reflection channel 1; таймаут connect 15 с; `stop` закрывает сокет, чтобы сорвать зависший connect.
5. AT-init: `ATZ ATE0 ATL0 ATS0 ATH0 ATSP0`, далее poll только interested PID + ATRV при интересе вкладки/виджета.

## Discovery поддерживаемых PID

- Виджет **всегда** показывает полный enum PID (не фильтруется).
- Во вкладке ELM327: кнопки **«Опросить PID»** / **«Сбросить»**.
- Опрос по кнопке: `0100` → при необходимости `0120` / `0140`…; результат = множество Mode 01 байт + timestamp в DataStore.
- Сброс очищает сохранённый снимок. Poll виджетов **не** ограничивается discovery.

## Архитектура

| Класс | Роль |
|-------|------|
| `obd/Elm327BluetoothSession` | RFCOMM сокет, line IO, connect timeout/fallback |
| `obd/Elm327BtPairing` | createBond + PIN |
| `obd/Elm327Protocol` | AT-init, parse Mode 01 / 02 / ATRV / DTC / monitor status |
| `obd/Elm327Manager` | reconnect, poll interested PIDs, Mode 01 monitors / 02 / 03 / 04 / 07 |
| `obd/ObdDtcCatalog` | TSV lookup for DTC descriptions (`assets/obd/dtc_en.tsv`) |
| `obd/ObdRepository` | StateFlow для UI / виджетов |
| `obd/ObdInterestAggregator` | объединяет PID с панелей |

Служба: `BackgroundService` стартует manager при `elm327Enabled` + непустом MAC.

## Настройки DataStore

- `elm327_enabled` (default false)
- `elm327_device_address` (MAC)
- `elm327_pairing_pin` (опционально)
- `elm327_supported_pids` (CSV hex Mode 01, пусто = нет discovery)
- `elm327_discovery_at_ms` (0 = сброшено / не выполнялось)

## Виджет

- `dataKey`: `obdMetricWidget`
- конфиг: `obdPidId` (например `rpm`, `coolant_temp`)
- панели публикуют interest через `publishObdInterest` → опрашиваются только видимые PID

## Разрешения

- `BLUETOOTH` / `BLUETOOTH_ADMIN` (API ≤ 30)
- `BLUETOOTH_CONNECT` (API 31+)
- `BLUETOOTH_SCAN` + location (только для кнопки сканирования во вкладке)
- `uses-feature bluetooth` optional
