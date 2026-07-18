# ESP32-S3 companion firmware

Target: **ESP32-S3-DevKitC-1** (N16R8 or N8R8).  
Stack: ESP-IDF 5.x + TinyUSB CDC (USB Device).

## Build

```bash
# Install ESP-IDF, then:
cd firmware/esp32-companion
idf.py set-target esp32s3
idf.py build
idf.py -p PORT flash monitor
```

Use the **USB-UART** port for flashing; connect the head unit to the **ESP32-S3 USB** (native) port for CDC.

## Protocol

Newline-delimited JSON v1 — see [docs/ESP32_COMPANION_RU.md](../../docs/ESP32_COMPANION_RU.md).

## Default pins

| Function | GPIO |
|----------|------|
| UM980 UART RX | 18 |
| UM980 UART TX | 17 |
| Inputs 0–7 | 1–8 |
| Relays 0–3 | 9–12 |

UM980 VCC = 3.3 V, UART 115200 8N1.
