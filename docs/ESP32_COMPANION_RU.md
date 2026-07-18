# ESP32 companion (USB CDC)

Компаньон на **ESP32-S3** (рекомендуется Espressif **ESP32-S3-DevKitC-1** N16R8/N8R8) подключается к ГУ Jetour по USB Host. К ГУ — разъём **ESP32-S3 USB** (native OTG, GPIO19/20), не USB‑UART bridge.

Прошивка: [`firmware/esp32-companion/`](../firmware/esp32-companion/).

## Протокол NDJSON v1

Одна JSON-строка на сообщение, конец строки `\n`. Поле `v` = `1`. Невалидные строки игнорируются.

### Device → Host

| `t` | Поля | Смысл |
|-----|------|--------|
| `hello` | `fw`, `gpioIn`, `relays`, `um980` | caps / версия |
| `hb` | `uptimeMs` | heartbeat ~1 с |
| `gps` | `fix`, `lat`, `lon`, `alt`, `speedKmh`, `course`, `satsUsed`, `satsVis`, `utc` | фиксация UM980 |
| `gpio` | `mask`, `ms` | bitmask входов |
| `gpioEvent` | `ch`, `level`, `ms` | изменение входа |
| `relay` | `mask` | состояние реле |

### Host → Device

| `t` | Поля | Смысл |
|-----|------|--------|
| `hello` | — | запрос caps |
| `relaySet` | `mask` | установить реле (бит = канал) |

Лимиты: до 16 входов, до 8 реле.

## Pin-map (DevKitC-1, по умолчанию)

| Функция | GPIO |
|---------|------|
| UM980 UART RX (ESP ← TX модуля) | 18 |
| UM980 UART TX (ESP → RX модуля) | 17 |
| GPIO in 0…7 | 1, 2, 3, 4, 5, 6, 7, 8 |
| Relay out 0…3 | 9, 10, 11, 12 |

UM980: питание **3.3 V** (не 5 V на VCC чипа), UART LVTTL 3.3 V, baud 115200, общий GND.

USB: Espressif VID `0x303A`.

## Источник геопозиции в приложении

Настройка: **TBox** / **ESP32** / **Android**. Mock location (expert) подставляет active-координаты при TBox или ESP32.

## Future (не MVP)

- Команды UM980 с ГУ + запись NMEA
- OTA прошивки ESP с ГУ
- Passthrough для UPrecise upgrade UM980
- 4G + Wi‑Fi AP
