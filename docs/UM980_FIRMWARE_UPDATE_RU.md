# Обновление прошивки UM980 с ГУ

Прошивка модуля Unicore UM980 файлом `.pkg` по UART (прямой USB или Компаньон ESP32).

Протокол сверен с захватом **UPrecise Receiver Upgrade** + Device Monitoring Studio [`um980.dmslog8`](https://disk.yandex.ru/d/hqLQeQZOD1qGoA) (Soft-путь, 2026-08-05) и подтверждён `VERSIONA` → `R4.10Build25102`.

Reference Commands Manual N4 **не** описывает кадры upgrade — только рекомендацию резервировать COM1. Фактический путь: soft/hard reset → **N4 BootLoader** → меню `2` → **XMODEM-1K** (checksum).

## Последовательность (Host) — как в UPrecise

Таймлайн Soft из `um980.dmslog8` (хост уже на рабочей скорости, затем):

| t (отн.) | Событие |
|----------|---------|
| 0 | несколько пар `unlog` → `$command,unlog,response: OK` |
| +0.7 с | `config com1 460800` + `com2` + `com3` одним блоком (**без** `SAVECONFIG`) |
| +~2 с | ещё `unlog`; host UART → **460800** |
| +~2.6 с | `\r\nreset\r\nreset\r\n` (reset **дважды**) → `$command,reset,response: OK` |
| +~4.3 с | `system is rebooting` |
| +~6.1 с | `N4 BootLoader 2020.04` … меню … `boot>` (timeout меню **2 с**, default = print menu) |
| +~8 с | host шлёт `2\r\n` → `unlock Flash` / `## Ready for binary (xmodem)…` |
| далее | **XMODEM-1K** checksum (`STX` + blk + `~blk` + 1024 + sum&0xFF); magic `.pkg` = `a5 a4 a3 a2` |
| конец | `%FreeRTOS:…` |

Hard reset в этом захвате **нет** (только ASCII `reset`).

### Шаги в приложении

1. (Опционально) `version` / `VERSIONA` — снимок до прошивки.
2. `unlog` несколько раз — остановить NMEA.
3. `config com1/com2/com3 460800` (без `SAVECONFIG`).
4. Host UART → **460800**, короткий settle, ещё пара `unlog`.
5. Сброс в bootloader:
   - **Soft:** `reset` **дважды** → ждать `system is rebooting` / баннер BootLoader / `boot>`.
   - **Hard:** ждать ручной сброс питания/RESET; ASCII `reset` не слать.
     На **прямом USB** не отключайте кабель адаптера — только питание/RESET самого модуля (иначе сессия ГУ рвётся).
6. Дождаться баннера `N4 BootLoader` и приглашения `boot>`.
   - Если Soft уже поймал `BootLoader`/`boot>` — повторно не ждать (байты уже съедены).
   - Если на 460800 тишина (типично после **Hard**: модуль поднялся на **сохранённом** baud, без `SAVECONFIG` на шаге 3): короткий перебор host baud `current → 460800 → pre → 115200 → 57600 → …`, на каждом срезе `\r\n` и ожидание баннера (~5 с, общий бюджет ~35 с). XMODEM дальше идёт на baud, где баннер увидели.
   - В Soft-захвате UPrecise баннер приходит на **460800** (~3–4 с после reset) — перебор не нужен, но безопасен.
7. Отправить `2\r\n` (*Download from uart to flash*).
8. Дождаться `unlock Flash` / `Ready for binary` / xmodem; приёмник в checksum-режиме (в захвате блок 1: `STX|01|FE|…|csum`).
9. Передать `.pkg` **XMODEM-1K**:
   - кадр: `STX (0x02) | blk | ~blk | 1024 data | checksum (1 byte = sum & 0xFF)`;
   - ждать `ACK (0x06)` на блок; при `NAK` — повтор блока;
   - после последнего блока: `EOT (0x04)`, ждать `ACK`.
10. Дождаться выхода в приложение (`%FreeRTOS` / NMEA).
11. **Baud restore (обязательно; в dmslog8 после FreeRTOS CONFIG не виден — делаем сами):**
    - Host → pre-upgrade baud;
    - при тишине — короткий перебор `460800 → pre → 115200 → 57600`;
    - `CONFIG com1/com2/com3 <baud>` + `SAVECONFIG`;
    - `VERSIONA` для проверки build.

## Файл `.pkg`

- Типичный размер ~3 MB (пример `UM980_R4.10Build25102.pkg` = 3004096).
- Магия заголовка: `a5 a4 a3 a2` (подтверждено в первом XMODEM-блоке захвата).
- Имя часто содержит `BuildNNNNN` — сверять с полем build в `#VERSIONA`.

## Транспорты

| Путь | Как |
|------|-----|
| **USB** | Exclusive raw R/W на `UsbNmeaGnssSession` (пауза NMEA). Номер COMx модуля на плате не важен — XMODEM по открытой сессии; baud CONFIG на все три COM. |
| **Компаньон** | Режим `um980Bridge` в прошивке ESP: байтовый туннель Host↔UART (COM3, GPIO17/18). |

Навигация / mock / DR **не** меняются; на время FW только пауза GPS publish на активном транспорте.

## Риски

- Не отключать питание/USB во время XMODEM.
- Обрыв → модуль часто остаётся в BootLoader; повтор Soft/Hard + тот же `.pkg`.
- Неверный `.pkg` для другой модели — не использовать.
- После `CONFIG 460800` без `SAVECONFIG` Hard power-cycle возвращает сохранённый baud — нужен baud-sweep на шаге 6.
- **Причина сбоя Soft на прямом USB:** после `CONFIG … 460800` модуль уже на 460800, а `setBaudLive` на CP210x/CH340 в exclusive-режиме часто **не** переключает адаптер. Хост продолжает (или возвращается) на 115200 → баннер BootLoader не виден, после ошибки связь «мертва» до цикла питания / кнопки «Перезагрузка GNSS». Исправление: после CONFIG — **полный reopen** USB на 460800 ([UsbNmeaGnssSession.reopenExclusiveAtBaud]); при ожидании баннера не отдавать приоритет игле `rebooting` над `BootLoader`/`boot>` в том же RX-куске; при неудаче — [recoverLinkBestEffort] с **reopen** по кандидатам baud + горячий `RESET` (как UI GNSS reboot), затем снова reopen на рабочий baud.
- Soft **нужен** там, где Hard (отдельное питание модуля без отключения USB) физически невозможен.
