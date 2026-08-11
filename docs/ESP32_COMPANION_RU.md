# ESP32 companion (USB CDC)

Компаньон на **ESP32-S3** (рекомендуется Espressif **ESP32-S3-DevKitC-1** N16R8/N8R8) подключается к ГУ Jetour по USB Host. К ГУ — разъём **ESP32-S3 USB** (native OTG, GPIO19/20), не USB‑UART bridge.

Прошивка: [`firmware/esp32-companion/`](../firmware/esp32-companion/) (версия **0.4.11+**). Таблица разделов: A/B OTA (`ota_0` / `ota_1` по 1.5 MB) — см. `partitions.csv`.

Команды UM980 сверяются с **Unicore Reference Commands Manual For N4 High Precision Products V2 EN R1.14** (локальная PDF в `docs/`, в git не кладётся).

На ГУ Android USB Host обязан выставить **DTR** (`SET_CONTROL_LINE_STATE`), иначе TinyUSB не считает CDC «открытым» и не шлёт `hello`/`hb` (на ПК pyserial делает это сам).

> **Важно (USB Host):** команды UM980 раньше обрабатывались в main loop и глушили heartbeat ~1.2 с/команду. Android watchdog закрывал CDC посреди `bulkTransfer` и мог клинить весь USB Host ГУ (вместе с TBox). С 0.4.1+ UM980/baud уходят в отдельный FreeRTOS task; OTA держит редкий `hb` (5 с), reboot после OTA — из main loop (не из CDC RX). На Android: нет `close()` по heartbeat timeout; reconnect/close блокируются во время UM980/OTA; USB OUT на одном потоке. Поиск компаньона — **только Espressif VID `0x303A`** (без CDC-fallback на другие устройства); иначе reconnect мог захватить RNDIS TBox. DETACH закрывает сессию только для текущего компаньона.

В приложении: вкладка **«Компаньон»** (в левом меню **по умолчанию скрыта** — включить в настройках состава меню) — переключатель **«Подключаться к компаньону»** (по умолчанию выкл., USB не открывается), статус USB/GPIO/реле, время последнего сообщения, перезагрузка, **обновление прошивки с ГУ**, настройки UM980. Пока опция включена, приложение само периодически пытается восстановить USB-сессию при обрыве (ждёт появления Espressif). Вкладка **«Геопозиция»** — источник (**TBox** / **Компаньон** / **Android** / **USB**) и координаты. Источник **Компаньон** можно выбрать только если на USB есть Espressif и включён переключатель «Подключаться к компаньону» (автоматически сессию не включает — иначе на ГУ кратковременно падает RNDIS TBox). Источник **USB** — отдельный путь: пользователь выбирает CDC/UART-мост из списка и читает NMEA напрямую (без компаньона); Espressif в этом списке не показывается; USB-сессия открывается только когда выбранное устройство реально на шине.

## Протокол NDJSON v1

Одна JSON-строка на сообщение, конец строки `\n`. Поле `v` = `1`. Невалидные строки игнорируются.

### Device → Host

| `t` | Поля | Смысл |
|-----|------|--------|
| `hello` | `fw`, `gpioIn`, `relays`, `um980`, `baud` | caps / версия / текущий UART baud ESP↔UM980 |
| `hb` | `uptimeMs` | heartbeat ~1 с |
| `gps` | `fix`, `lat`, `lon`, `alt`, `speedKmh`, `course`, `satsUsed`, `satsVis`, `utc`, `hdop`, `pdop`, `vdop`, `hrms`, `vrms`, `diffAge` | фиксация UM980 (`fix` = GGA quality; DOP из GGA/GSA; RMS из GST; `diffAge` из GGA; `0`/`-1` = нет данных) |
| `gpio` | `mask`, `ms` | bitmask входов |
| `gpioEvent` | `ch`, `level`, `ms` | изменение входа |
| `relay` | `mask` | состояние реле |
| `um980Rsp` | `cmd`, `lines[]`, `ok` | ответ на Unicore-команду (не-NMEA) |
| `um980Baud` | `baud`, `ok` | подтверждение смены UART baud |
| `rebootAck` | — | перед `esp_restart()` |
| `otaAck` | `phase`=`begin`/`chunk`/`end`, `offset`, `ok`, `err?` | подтверждение OTA |
| `otaDone` | `ok`, `err?` | запись завершена; затем reboot ~100 ms |

### Host → Device

| `t` | Поля | Смысл |
|-----|------|--------|
| `hello` | — | запрос caps |
| `relaySet` | `mask` | установить реле (бит = канал) |
| `um980Cmd` | `cmd` | ASCII-команда Unicore без `\r\n` (fw дописывает) |
| `um980Baud` | `baud` | скорость ESP↔UM980; сохраняется в NVS компаньона |
| `reboot` | — | перезапуск компаньона |
| `otaBegin` | `size`, `crc32` | начать OTA (IEEE CRC32 всего образа) |
| `otaEnd` | — | завершить запись и переключить boot partition |
| `um980BridgeBegin` | — | байтовый туннель Host↔UM980 UART (прошивка `.pkg`) |
| `um980BridgeEnd` | — | выйти из туннеля |

После `um980Cmd` прошивка ~0.5–1.5 с собирает не-NMEA строки (`$command` / `#…` / `OK`) в один `um980Rsp`. NMEA по-прежнему уходит как `gps`.

Во время `um980Bridge*` Host шлёт/принимает те же бинарные кадры, что OTA (`0xA5 0x5A | u16be len | payload | u32be crc32`); payload пишется в UART / читается с UART. Device отвечает `um980BridgeAck` `phase=begin|end`.

Допустимые `baud`: 9600, 19200, 38400, 57600, 115200 (по умолчанию), 230400, 460800. Значение хранится в NVS компаньона и переживает перезагрузку ESP.

Смена скорости из UI (если UM980 на связи): `CONFIG COM3 <baud>` → `um980Baud` (ESP+NVS) → `SAVECONFIG`. Без UM980 — только `um980Baud`.

«UM980 на связи» на Android: свежий `gps` (менее ~3 с).

Лимиты протокола: до 16 входов, до 8 реле. Текущая плата/прошивка по умолчанию: **4 входа, 2 выхода**.

### OTA по USB CDC

Файл: только **app image** `esp32_companion.bin` из `build/` (magic первого байта `0xE9`), не полный flash dump. Размер ≤ 1.5 MB (`OTA_MAX_IMAGE_SIZE`).

Последовательность:

1. Host → `otaBegin` `{size, crc32}` → Device → `otaAck` `phase=begin`
2. Host шлёт **бинарные** кадры (не JSON, обход буфера строк 512 B):

   `0xA5 0x5A | u16be len | payload | u32be crc32(payload)`

   `len` ≤ 1024. Device периодически отвечает `otaAck` `phase=chunk` с `offset`.
3. Host → `otaEnd` → Device → `otaAck` `phase=end` + `otaDone` → `esp_restart`

Во время OTA прошивка не шлёт `hb`/`gps`, чтобы не мешать RX.

**Первая установка** после смены partition table (переход с single-app на A/B): один раз прошить с ПК через UART (`idf.py -p COMx flash`), включая новую таблицу разделов. Дальнейшие обновления — с вкладки **«Компаньон»** → **«Обновить прошивку…»**.

Bootloader / partition table с ГУ обновить нельзя. **Прошивка UM980** с ГУ поддерживается (файл `.pkg`, Soft/Hard reset) — см. [UM980_FIRMWARE_UPDATE_RU.md](UM980_FIRMWARE_UPDATE_RU.md); на компаньоне нужен режим `um980Bridge` (прошивка компаньона **0.4.12+**).

## Pin-map (DevKitC-1, по умолчанию)

| Функция | GPIO |
|---------|------|
| UM980 UART RX (ESP ← TX модуля) | 18 |
| UM980 UART TX (ESP → RX модуля) | 17 |
| GPIO in 0…3 | 1, 2, 3, 4 |
| Relay / SSR out 0…1 | 9, 10 |

UM980: питание **3.3 V** (не 5 V на VCC чипа), UART LVTTL 3.3 V, baud 115200, общий GND. TX и RX активны.

Питание DevKitC-1 + UM980 с USB ГУ обычно тянет (**~0.3–0.5 A** суммарно), но 3.3 V LDO на DevKit греется; при активной антенне/просадках лучше отдельный DC-DC 3.3 V на UM980.

USB: Espressif VID `0x303A`.

## Источник геопозиции в приложении

Настройка: **TBox** / **Компаньон** / **Android** / **USB**. Mock location периодически пушит active-координаты при TBox, Компаньоне или USB (период настраивается рядом с переключателем подмены на вкладке «Геопозиция»). При источнике **Android** подмена отключена. Выбор компаньона или USB как источника не включает подмену сам по себе. Retention / дорисовка / CAN-скорость — только если включён режим улучшения подмены (всегда или только при потере фикса, до **10 мин**).

Источник **USB**: список подходящих USB-устройств на вкладке «Геопозиция» виден всегда (CDC DATA или известные UART-мосты; без Espressif и без RNDIS-подобных) — сначала выбрать устройство, затем источник USB. Автоподключения к «первому CDC» нет (на этом ГУ это клинит TBox). Для **CP210x / CH340** после open выполняется vendor baud/DTR (baud из настроек). Сессия USB GNSS открывается только когда выбранное устройство присутствует на шине; assist-loop **ждёт окончания старта сервиса** и ещё **~3 с** (settle USB Host на boot; без привязки к TBox), затем повторяет open/permission, пока нет `connected`, и переоткрывает при тишине NMEA ~10 с. Ошибки open/permission на USB IO не пробрасываются в BroadcastReceiver (не валят процесс). После unplug/replug и reboot ГУ — soft-match по `vid:pid` (serial может быть недоступен до permission); при двух одинаковых адаптерах без читаемого serial открытие блокируется. После выдачи permission id дополняется serial. Запрос VTG/ZDA у модуля — опциональные тумблеры (по умолчанию выкл.).

Источник **Компаньон**: доступен только при наличии Espressif на USB и включённом «Подключаться к компаньону» (без авто-включения сессии). На старте ГУ открытие USB ждёт окончания старта сервиса и ещё ~3 с для стабилизации USB Host (без привязки к TBox); выключение компаньона отменяет ожидание. Живость линка — по любому RX (hello/hb/GPS); при тишине — force-reopen на USB IO-потоке с backoff; запрос USB permission — не чаще чем раз в 45 с.

## UM980 с ГУ

Вкладка **«Компаньон»**: сбросы (RESET / FRESET), **«Получить конфигурацию из модуля»** (`CONFIG` / `MODE`), период GGA+RMC и вспомогательных NMEA, рекомендуемые CONFIG (без смены baud COM3), **«Загрузить рекомендуемый профиль»**, **«Сохранить конфигурацию в модуле»** (SAVECONFIG — обязательно после изменений).

## Future (не MVP)

- Автоперебор baud / автоподстройка под модуль
- UPrecise passthrough (частично: `um980Bridge` для прошивки UM980)
- OTA rollback UI (IDF rollback можно включить позже)
