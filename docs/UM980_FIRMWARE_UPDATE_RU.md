# Обновление прошивки UM980 с ГУ

Прошивка модуля Unicore UM980 файлом `.pkg` по UART (прямой USB или Компаньон ESP32). Протокол восстановлен по захвату UPrecise Receiver Upgrade + Device Monitoring Studio (`um980.dmslog8`) и подтверждён `VERSIONA` → `R4.10Build25102`.

Reference Commands Manual N4 **не** описывает кадры upgrade — только рекомендацию резервировать COM1. Фактический путь: soft/hard reset → **N4 BootLoader** → меню `2` → **XMODEM-1K** (checksum).

## Последовательность (Host)

1. (Опционально) `version` / `VERSIONA` — снимок до прошивки.
2. `unlog` (несколько раз) — остановить NMEA.
3. `config com1 460800`, `config com2 460800`, `config com3 460800` (без `SAVECONFIG` на этом шаге).
4. Host UART → **460800**.
5. Сброс в bootloader:
   - **Soft:** `reset` → ждать `system is rebooting` / баннер BootLoader.
   - **Hard:** ждать ручной сброс питания/RESET; ASCII `reset` не слать.
6. Дождаться баннера `N4 BootLoader` и приглашения `boot>`.
7. Отправить `2\r\n` (*Download from uart to flash*).
8. Дождаться `unlock Flash` / готовности к binary download; приёмник шлёт **NAK** (`0x15`) для checksum-режима (иногда также виден `'C'` — предпочтителен ответ в режиме, который запросил device).
9. Передать `.pkg` **XMODEM-1K**:
   - кадр: `STX (0x02) | blk | ~blk | 1024 data | checksum (1 byte = sum & 0xFF)`;
   - ждать `ACK (0x06)` на блок; при `NAK` — повтор блока;
   - после последнего блока: `EOT (0x04)`, ждать `ACK`.
10. Дождаться выхода в приложение (`%FreeRTOS` / NMEA).
11. **Baud restore (обязательно):**
    - Host → pre-upgrade baud;
    - при тишине — короткий перебор `460800 → pre → 115200 → 57600`;
    - `CONFIG com1/com2/com3 <baud>` + `SAVECONFIG`;
    - `VERSIONA` для проверки build.

## Файл `.pkg`

- Типичный размер ~3 MB (пример `UM980_R4.10Build25102.pkg` = 3004096).
- Магия заголовка: `a5 a4 a3 a2`.
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
