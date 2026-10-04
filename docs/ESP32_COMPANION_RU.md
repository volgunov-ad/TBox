# ESP32 companion (USB CDC)

Компаньон на **ESP32-S3** (рекомендуется Espressif **ESP32-S3-DevKitC-1** N16R8/N8R8) подключается к ГУ Jetour по USB Host. К ГУ — разъём **ESP32-S3 USB** (native OTG, GPIO19/20), не USB‑UART bridge.

Прошивка: [`firmware/esp32-companion/`](../firmware/esp32-companion/) (версия **0.9.0+**). Таблица разделов: A/B OTA (`ota_0` / `ota_1` по 1.5 MB) — см. `partitions.csv`.

Команды UM980 сверяются с **Unicore Reference Commands Manual For N4 High Precision Products V2 EN R1.14** (локальная PDF в `docs/`, в git не кладётся).

На ГУ Android USB Host обязан выставить **DTR** (`SET_CONTROL_LINE_STATE`), иначе TinyUSB не считает CDC «открытым» и не шлёт `hello`/`hb` (на ПК pyserial делает это сам).

> **Важно (USB Host):** команды UM980 раньше обрабатывались в main loop и глушили heartbeat ~1.2 с/команду. Android watchdog закрывал CDC посреди `bulkTransfer` и мог клинить весь USB Host ГУ (вместе с TBox). С 0.4.1+ UM980/baud уходят в отдельный FreeRTOS task; OTA держит редкий `hb` (5 с), reboot после OTA — из main loop (не из CDC RX). На Android: нет `close()` по heartbeat timeout; reconnect/close блокируются во время UM980/OTA; USB OUT на одном потоке. Поиск компаньона — **только Espressif VID `0x303A`** (без CDC-fallback на другие устройства); иначе reconnect мог захватить RNDIS TBox. DETACH закрывает сессию только для текущего компаньона.

В приложении: вкладка **«Компаньон»** (в левом меню **по умолчанию скрыта** — включить в настройках состава меню) с горизонтальными разделами **«Настройки»** / **«Данные и отладка»** / **«BLE»** / **«Точка доступа»**. В **«Настройки»** — переключатель **«Подключаться к компаньону»** (по умолчанию выкл., USB не открывается), статус USB/прошивки, перезагрузка, **обновление прошивки с ГУ**, настройки UM980/CAN. В **«Данные и отладка»** — GPIO/реле, GNSS/маг live, протокол-лог. В **«BLE»** — Shelly Blu. Пока опция подключения включена, приложение само периодически пытается восстановить USB-сессию при обрыве (ждёт появления Espressif). Вкладка **«Геопозиция»** — источник (**TBox** / **Компаньон** / **Android** / **USB**) и координаты. Источник **Компаньон** можно выбрать только если на USB есть Espressif и включён переключатель «Подключаться к компаньону» (автоматически сессию не включает — иначе на ГУ кратковременно падает RNDIS TBox). Источник **USB** — отдельный путь: пользователь выбирает CDC/UART-мост из списка и читает NMEA напрямую (без компаньона); Espressif в этом списке не показывается; USB-сессия открывается только когда выбранное устройство реально на шине.

## Протокол NDJSON v1

Одна JSON-строка на сообщение, конец строки `\n`. Поле `v` = `1`. Невалидные строки игнорируются.

### Device → Host

| `t` | Поля | Смысл |
|-----|------|--------|
| `hello` | `fw`, `gpioIn`, `relays`, `gnss`, `gnssChip`, `gnssModel`, `um980`, `baud`, `can?`, `canBackend?`, `canBaud?`, `canLight?`, `mag`, `magChip`, `magSeen[]`, `ble?`, `bleOn?`, `bleMacs?` | caps / версия. GNSS и магнитометр **автоопределяются** при старте компаньона (`gnssChip`: `um980` / `neo-m8n` / `ublox` / `nmea`; `magChip`: активный чип I2C). `um980:true` только для Unicore UM980. UART baud — сохранённый/найденный. CAN — как раньше. `ble:true` (fw **0.8+**) — NimBLE observer для Shelly Blu / BTHome |
| `hb` | `uptimeMs` | heartbeat ~1 с |
| `gps` | `fix`, `lat`, `lon`, `alt`, `speedKmh`, `course`, `satsUsed`, `satsVis`, `utc`, `hdop`, `pdop`, `vdop`, `hrms`, `vrms`, `diffAge` | фиксация UM980 (`fix` = GGA quality; DOP из GGA/GSA; RMS из GST; `diffAge` из GGA; `0`/`-1` = нет данных) |
| `mag` | `chip`, `hx`, `hy`, `hz`, `heading`, `fs`, `ok` | магнитометр ~10 Гц (µT, магнитный курс 0…360, \|H\|); не слать во время OTA/bridge |
| `gpio` | `mask`, `ms` | bitmask входов |
| `gpioEvent` | `ch`, `level`, `ms` | изменение входа |
| `relay` | `mask` | состояние реле |
| `bleBtn` | `mac`, `btn` (1…4), `act` (`press`/`double`/`triple`/`long`/`hold`), `bat`, `rssi`, `ms` | Shelly Blu / BTHome (fw **0.8+**); только allowlisted MAC |
| `bleStatus` | `on`, `learn`, `macs[]`, `lastBat?`, `lastRssi?`, `lastMac?` | снимок BLE |
| `bleSeen` | `mac`, `rssi`, `ms` | кандидат во время learn |
| `bleAck` | `phase`=`set`/`learnBegin`/`learnEnd`/`allow`/`forget`, `ok`, `err?` | подтверждения BLE |
| `apStatus` | `on`, `sta`, `ssid`, `psk`, `ip`, `freq`, `ch`, `huIp`, `panel` | SoftAP компаньона (fw **0.9+**). `sta` — связь с точкой ГУ, `ip` — адрес точки компаньона (`192.168.4.1`), `huIp` — шлюз ГУ, `panel` — порт DNAT веб-панели |
| `um980Rsp` | `cmd`, `lines[]`, `ok` | ответ на Unicore-команду (не-NMEA) |
| `um980Baud` | `baud`, `ok` | подтверждение смены UART baud |
| `rebootAck` | — | перед `esp_restart()` |
| `otaAck` | `phase`=`begin`/`chunk`/`end`, `offset`, `ok`, `err?` | подтверждение OTA |
| `otaDone` | `ok`, `err?` | запись завершена; затем reboot ~100 ms |
| `um980BridgeAck` | `phase`=`begin`/`end`, `ok`, `err?` | туннель UART UM980 |
| `canAck` | `phase`=`tx`/`filter`/`lightBegin`/`lightEnd`, `ok`, `err?` | подтверждение CAN |
| `canBaud` | `baud`, `ok` | подтверждение скорости CAN |
| `magChip` | `chip`, `ok`, `mag`, `seen[]` | подтверждение `magChipSet` |

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
| `canTx` | `id` (hex-строка), `ext`, `data` (hex), `dlc?`, `rtr?` | отправить CAN-кадр (JSON, если light выкл.) |
| `canBaud` | `baud` | скорость MCP2515: 100000, 125000, 250000, 500000, 1000000 |
| `canFilter` | `acceptAll:true` **или** `filters:[{id,mask?,ext?}]` | фильтр RX (accept-all или список) |
| `canLightBegin` | — | поток компактных бинарных CAN-кадров |
| `canLightEnd` | — | выйти из light-режима |
| `magChipSet` | `chip` | *(отладка)* принудительный выбор магнитометра; в штатном режиме чип определяется автоматически |
| `bleSet` | `on` | вкл/выкл BLE-сканер (NVS; по умолчанию выкл.) |
| `bleLearnBegin` | `timeoutMs?` (default 30000) | окно обучения: первый BTHome-пульт с кнопкой → allowlist |
| `bleLearnEnd` | — | отменить learn |
| `bleAllow` | `mac` | добавить MAC в allowlist (до 4) |
| `bleForget` | `mac` **или** `all:true` | удалить MAC / очистить allowlist |
| `apCfg` | `on`, `huSsid`, `huPsk` | включить роутер: SoftAP компаньона (`TBox` / `tbox8765`, `192.168.4.1/24`) + STA к точке ГУ + NAT и DNAT `:8765` на шлюз STA. `hello` содержит `ap:true`. Только A9: приложение само переводит точку ГУ на 2,4 ГГц |

После `um980Cmd` прошивка ~0.5–1.5 с собирает не-NMEA строки (`$command` / `#…` / `OK`) в один `um980Rsp`. NMEA по-прежнему уходит как `gps`.

Во время `um980Bridge*` Host шлёт/принимает те же бинарные кадры, что OTA (`0xA5 0x5A | u16be len | payload | u32be crc32`); payload пишется в UART / читается с UART. Device отвечает `um980BridgeAck` `phase=begin|end`.

### CAN MCP2515 (прошивка 0.5.0+)

Опциональный модуль MCP2515 (SPI). **Не** подмешивается в CAN ГУ (`CanFramesProcess` / виджеты) — только консоль и лог на вкладке «Компаньон».

JSON `canTx` / `canBaud` / `canFilter` работают всегда. Для потока RX (и быстрого TX) Host шлёт `canLightBegin`; Device отвечает `canAck` `phase=lightBegin`. Пока light активен, кадры идут теми же бинарными оболочками, что OTA:

`0xA5 0x5A | u16be len | payload | u32be crc32(payload)`

Payload = N × 14 байт:

| Смещение | Поле |
|----------|------|
| 0 | flags: bit0=`EXT`, bit1=`RTR`, bit2=`TX` (Host→Device TX установлен; Device→Host RX сброшен) |
| 1…4 | id big-endian |
| 5 | DLC 0…8 |
| 6…13 | data (неиспользуемые байты 0) |

`canLightEnd` → `canAck` `phase=lightEnd`. Light и `um980Bridge` взаимно исключаются (`busy`). Heartbeat/GPS JSON продолжают идти в light-режиме.

На Android: кнопка **CAN** (если `hello.can`) открывает консоль (baud, accept-all / один фильтр id+mask+ext, отправка кадра, последние ~200 кадров) и держит light. Запись протокола (`tbox_companion_log_YYYYMMDD_HHmmss.txt` в «Загрузки») тоже держит light (refcount). Heartbeat в файл не пишется; GPS и `t:mag` — не чаще 5 с.

Рядом с кнопками записи — переключатель **«Метки mbCAN/VHAL»** (по умолчанию **выкл.**, только на сессию процесса). При включении в лог пишутся строки `MARK UI …` / `MARK PUSH …`: якоря времени для команд из виджетов и «Настроек автомобиля» (`UniversalCanRepository.execute`) и дискретных push с ГУ (cfg vehicle/audio, SLA/ACC/CCS, багажник, PRND, поворотники; на A10 VHAL — без continuous telemetry вроде скорости/RPM/руля/колёс). Это **не** кадры шины: по метке смотрите соседние `CAN RX` на MCP2515. В шапке файла: `# huMarks=on|off`.

Допустимые `baud` UART UM980: 9600, 19200, 38400, 57600, 115200 (по умолчанию), 230400, 460800. Значение хранится в NVS компаньона и переживает перезагрузку ESP.

Смена скорости из UI (если UM980 на связи): `CONFIG COM3 <baud>` → `um980Baud` (ESP+NVS) → `SAVECONFIG`. Без UM980 — только `um980Baud`.

«UM980 на связи» на Android: свежий `gps` (менее ~3 с).

Лимиты протокола: до 16 входов, до 8 реле. Текущая плата/прошивка по умолчанию: **4 входа, 2 выхода**.

### OTA по USB CDC

Файл: только **app image** `esp32_companion.bin` из `build/` (magic первого байта `0xE9`), не полный flash dump. Размер ≤ 1.5 MB (`OTA_MAX_IMAGE_SIZE`).

Последовательность:

1. Host → `otaBegin` `{size, crc32}` → Device → `otaAck` `phase=begin`
2. Host шлёт **бинарные** кадры (не JSON, обход буфера строк 512 B):

   `0xA5 0x5A | u16be len | payload | u32be crc32(payload)`

   `len` ≤ 1024. На практике хост шлёт **512**. Device отвечает `otaAck` `phase=chunk` каждые 8 кадров и на последнем.
3. Host → `otaEnd` → Device → `otaAck` `phase=end` + `otaDone` → `esp_restart` из `app_main`, не из USB callback.

Живой прогон 2026-10-03, ГУ A9, прошивка **0.8 → 0.9.0**, образ **1 043 776** байт: `otaAck` begin не пришёл, дальше `offset` шёл ровно по 4096 байт до `1043776`, затем `otaEnd`. Это рабочий контракт. Его нельзя «упрощать».

#### Что нельзя ломать

Проверено на этом головном устройстве. Отступление снова даёт «Таймаут OTA».

| Правило | Почему |
|---|---|
| Хост шлёт payload **512** байт и после каждого кадра ждёт **~100 мс** | Кадр 1024+8 байт = 1032. Кольцо CDC RX — **2048**. `esp_ota_write` выполняется внутри USB RX callback, а USB ACK ставится, когда байты уже легли в кольцо. Второй кадр 1032 в это окно не влезает, байты пропадают, `otaEnd` съедается как бинарный мусор. |
| Нет `otaAck` begin — **всё равно слать образ**, подождав до **120 с** | `esp_ota_begin` стирает область образа прямо в USB callback и только потом шлёт ack. Пока callback занят, TX FIFO (**512** байт) не опустошается. Heartbeat 0.8 (раз в 1 с, после входа в OTA раз в 5 с) забивает FIFO, и begin ack выбрасывается. Повторный `otaBegin` в этот момент попадает в binary mode и не разбирается. |
| Ждать `otaAck` chunk каждые 8 кадров, но **один** пропуск не обрывает передачу. Обрыв — после **трёх** пропусков подряд или при `ok:false` | 0.8 во время OTA продолжает слать `hb`. Отдельный ack теряется. Обрыв на первом пропуске остановил живую запись на `chunk@638976` при уже подтверждённых 634 880 байтах. |
| Чтение CDC остаётся в потоке `esp-usb-read`, запись — в `esp-usb-io` | Перенос IN на поток записи (`exclusiveRx` / `pumpRx`) на этом ГУ не видит ответы: `timeout begin` при живом компаньоне. |
| `withTimeout` только вокруг `channel.receive()` | Цикл с блокирующим `bulkTransfer` без точки приостановки не отменяется по таймауту, экран «стирания» висит бесконечно. |
| Кадр `A5 5A 00 00` (abort) — только когда передача уже брошена | Он выводит прошивку из binary mode. Перед стартом и во время стирания он срывает сессию. |
| Пока `otaBusy`, сторож USB **не** переоткрывает CDC | Иначе линк рвётся посреди записи. |
| В прошивке во время OTA **нельзя** писать в CDC ничего, кроме ack/done | Исходники после этого прогона: `s_ota_bin_mode` выставляется **до** `esp_ota_begin`, и `hb` на это время не шлётся. В уже прошитом **0.9.0** этого ещё нет: там `hb` раз в 5 с. Хост обязан работать и с таким устройством. Следующая сборка прошивки это молчание уже содержит; не возвращать heartbeat в OTA. |
| CRC кадра и всего образа — IEEE CRC32 (`esp_crc32_le`), на проводе CRC кадра big-endian | Иной порядок байт даёт `chunk crc`, устройство выходит из binary mode, хост больше не получает `otaDone`. |
| `otaDone` ждать до **120 с** | `esp_ota_end` проверяет образ ~1 МБ до ответа. |

Во время OTA прошивка не шлёт `gps`. На **0.8** и на прошитом **0.9.0** heartbeat во время OTA ещё есть (раз в 5 с после `ota_begin`). Новые исходники его на это время глушат.

**Первая установка** после смены partition table (переход с single-app на A/B): один раз прошить с ПК через UART (`idf.py -p COMx flash`), включая новую таблицу разделов. Дальнейшие обновления — с вкладки **«Компаньон»** → **«Обновить прошивку…»**.

Пошаговая инструкция (ПК: UART / CDC OTA, бинарники из Actions, esptool): [ESP32_COMPANION_FLASH_PC_RU.md](ESP32_COMPANION_FLASH_PC_RU.md).

Bootloader / partition table с ГУ обновить нельзя. **Прошивка UM980** с ГУ поддерживается (файл `.pkg`, Soft/Hard reset) — см. [UM980_FIRMWARE_UPDATE_RU.md](UM980_FIRMWARE_UPDATE_RU.md); на компаньоне нужен режим `um980Bridge` (прошивка компаньона **0.4.12+**).

## Pin-map (DevKitC-1, по умолчанию)

**Схемы подключений всех устройств** (обзор, ASCII pin-map, GNSS / магнитометр / MCP2515 / входы / реле / питание): [ESP32_COMPANION_WIRING_RU.md](ESP32_COMPANION_WIRING_RU.md).

| Функция | GPIO |
|---------|------|
| UM980 UART RX (ESP ← TX модуля) | 18 |
| UM980 UART TX (ESP → RX модуля) | 17 |
| GPIO in 0…3 | 1, 2, 3, 4 |
| Relay / SSR out 0…1 | 9, 10 |
| MCP2515 MOSI | 11 |
| MCP2515 SCK | 12 |
| MCP2515 MISO | 13 |
| MCP2515 CS | 14 |
| I2C SDA (магнитометр) | 5 |
| I2C SCL (магнитометр) | 6 |

UM980: питание **3.3 V** (не 5 V на VCC чипа), UART LVTTL 3.3 V, baud 115200, общий GND. TX и RX активны.

MCP2515: модуль HW-184 по SPI. Если модуль 5 V — двунаправленный преобразователь уровня (например EM-409) на SCK/SI/SO/CS. INT не подключать (опрос в прошивке). Кварц по умолчанию **8 МГц**, битрейт **500 кбит/с**.

Магнитометр (fw **0.7.0+**): I2C 400 кГц, GPIO 5/6. Поддерживаются **RM3100**, **MMC5983**, **IST8310**, **HMC5883L**, **HMC5983**, **QMC5883L** — автоопределение по ID-регистрам, без выбора в UI. Модуль на кабеле 20–50 см. `heading` = atan2(hy, hx), ось X вперёд. Калибровка DR — [COMPASS_HEADING_PLAN_RU.md](COMPASS_HEADING_PLAN_RU.md).

### Shelly Blu (Button 1 / RC Button 4) (fw **0.8.0+**)

Пассивный NimBLE observer (BTHome UUID `0xFCD2`). Пульты **не** перепрошиваются — заводской BTHome без encryption.

Поддерживаются:

- **Shelly Blu Button 1** — одна физическая кнопка → в протоколе всегда `btn:1`
- **Shelly Blu RC Button 4** — четыре кнопки → `btn` 1…4

На вкладке «Компаньон» → раздел **«BLE»**:

1. Включить **«Сканировать BLE»** (`bleSet on`).
2. **«Обучить пульт»** → нажать кнопку на пульте в течение ~30 с → MAC в allowlist (до 4 устройств).
3. В таблице устройств: локальное **имя** (DataStore), **батарея %** и **последнее событие** по каждому MAC; глобальной строки батареи нет.
4. Дальше `bleBtn` с `act` press/double/triple/long/hold.

Магнитометр при BLE **не** останавливается (маг на кабеле). Encryption BTHome на MVP игнорируется.

Автоматизации:

- триггер **«Кнопка Shelly Blu (компаньон)»** (`esp_ble_btn`) — **MAC обязателен** (имя+MAC в пикере); забытый/необученный MAC остаётся в JSON правила и просто не совпадает, пока пульт снова не обучат;
- сигнал **`esp_ble_battery`** — порог/условие тоже с обязательным `mac` (батарея конкретного пульта из `bleDevices`);
- `esp_ble_bound` — есть ли хотя бы один allowlisted MAC при включённом сканере.

OTA с ГУ: после успешной записи кратко показывается 100 %, toast один раз, затем прогресс сбрасывается (не «залипает» при повторном входе на вкладку).

GNSS (fw **0.7.0+**): UART GPIO 17/18. Автоопределение **UM980** (VERSIONA), **u-blox/NEO-M8N** (UBX-MON-VER) или generic **NMEA** с перебором baud (115200, 9600, …). NEO-M8N и аналоги: питание 3.3 V, общий GND.

Питание DevKitC-1 + UM980 с USB ГУ обычно тянет (**~0.3–0.5 A** суммарно), но 3.3 V LDO на DevKit греется; при активной антенне/просадках лучше отдельный DC-DC 3.3 V на UM980.

USB: Espressif VID `0x303A`.

## Источник геопозиции в приложении

Настройка: **TBox** / **Компаньон** / **Android** / **USB**. Mock location периодически пушит active-координаты при TBox, Компаньоне или USB (период настраивается рядом с переключателем подмены на вкладке «Геопозиция»). При источнике **Android** подмена отключена. Выбор компаньона или USB как источника не включает подмену сам по себе. Retention / дорисовка / CAN-скорость — только если включён режим улучшения подмены (всегда или только при потере фикса, до **10 мин**).

Источник **USB**: список подходящих USB-устройств на вкладке «Геопозиция» виден всегда (CDC DATA или известные UART-мосты; без Espressif и без RNDIS-подобных) — сначала выбрать устройство, затем источник USB. Автоподключения к «первому CDC» нет (на этом ГУ это клинит TBox). Для **CP210x / CH340** после open выполняется vendor baud/DTR (baud из настроек). Сессия USB GNSS открывается только когда выбранное устройство присутствует на шине; assist-loop **ждёт окончания старта сервиса** и ещё **~3 с** (settle USB Host на boot; без привязки к TBox), затем повторяет open/permission, пока нет `connected`, и переоткрывает при тишине NMEA ~10 с. Ошибки open/permission на USB IO не пробрасываются в BroadcastReceiver (не валят процесс). После unplug/replug и reboot ГУ — soft-match по `vid:pid` (serial может быть недоступен до permission); при двух одинаковых адаптерах без читаемого serial открытие блокируется. После выдачи permission id дополняется serial. Запрос VTG/ZDA у модуля — опциональные тумблеры (по умолчанию выкл.).

Источник **Компаньон**: доступен только при наличии Espressif на USB и включённом «Подключаться к компаньону» (без авто-включения сессии). На старте ГУ открытие USB ждёт окончания старта сервиса и ещё ~3 с для стабилизации USB Host (без привязки к TBox); выключение компаньона отменяет ожидание. Живость линка — по любому RX (hello/hb/GPS); при тишине — force-reopen на USB IO-потоке с backoff; запрос USB permission — не чаще чем раз в 45 с.

## UM980 с ГУ

Вкладка **«Компаньон»**: сбросы (RESET / FRESET), **«Получить конфигурацию из модуля»** (`CONFIG` / `MODE` / `UNILOGLIST`), период GGA+RMC и вспомогательных NMEA, включение NMEA на COM1/COM2/COM3 (статус из `UNILOGLIST`; выключение — `UNLOG` всего порта), рекомендуемые CONFIG (без смены baud COM3), **«Загрузить рекомендуемый профиль»**, **«Сохранить конфигурацию в модуле»** (SAVECONFIG — обязательно после изменений). Тот же диалог открывается и для прямого USB GNSS.

## Future (не MVP)

- Автоперебор baud / автоподстройка под модуль
- UPrecise passthrough (частично: `um980Bridge` для прошивки UM980)
- OTA rollback UI (IDF rollback можно включить позже)
- Калибровка компаса и источники курса DR (`COMPASS` / `GYRO_COMPASS`) — [COMPASS_HEADING_PLAN_RU.md](COMPASS_HEADING_PLAN_RU.md) фазы 2–3. Телеметрия и выбор чипа уже в fw 0.6.
- Приложение на телефоне с тем же управлением, что веб-страница автомобиля: отдельное сопряжение по постоянному идентификатору, ключ от телефона в момент сопряжения, дальше команды и снимок климата/сидений/громкости в эфире зашифрованы. Кнопки Shelly не меняются — [PHONE_COMPANION_BLE_PLAN_RU.md](PHONE_COMPANION_BLE_PLAN_RU.md).
