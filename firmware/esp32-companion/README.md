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

CI (GitHub Actions **Build Companion Firmware**) builds the same target and uploads
`esp32_companion.bin` (OTA) plus bootloader/partition helpers as an artifact — see root README.

Use the **USB-UART** port for flashing; connect the head unit to the **ESP32-S3 USB** (native) port for CDC.

**PC flash guide (RU):** [docs/ESP32_COMPANION_FLASH_PC_RU.md](../../docs/ESP32_COMPANION_FLASH_PC_RU.md) — first UART install (`idf.py` / `esptool`), CDC OTA via `tools_cdc_ota_flash.py`, artifacts from Actions.

## Protocol

Newline-delimited JSON v1 — see [docs/ESP32_COMPANION_RU.md](../../docs/ESP32_COMPANION_RU.md).

Firmware **0.5.0+**: optional MCP2515 CAN. JSON `canTx` / `canBaud` / `canFilter`; `canLightBegin`/`canLightEnd` stream compact 14-byte records inside the same OTA framing as UM980 bridge (`0xA5 0x5A | u16be len | payload | u32be crc32`). Record: flags (EXT/RTR/TX) + id BE + DLC + 8 data bytes.

## Default pins

| Function | GPIO |
|----------|------|
| UM980 UART RX | 18 |
| UM980 UART TX | 17 |
| Inputs 0–3 | 1–4 |
| Relays / SSR 0–1 | 9–10 |
| MCP2515 MOSI | 11 |
| MCP2515 SCK | 12 |
| MCP2515 MISO | 13 |
| MCP2515 CS | 14 |

UM980 VCC = 3.3 V, UART 115200 8N1. Default caps: 4 inputs, 2 outputs.

### MCP2515 CAN (optional)

HW-184 module via SPI. If the module is 5 V, use a bidirectional level shifter (e.g. EM-409) on SCK/SI/SO/CS. Leave INT disconnected (firmware polls). Crystal default **8 MHz**, bitrate default **500 kbit/s**.

Firmware **0.5.0+**: `canLightBegin`/`canLightEnd` stream compact binary CAN frames; JSON `canTx` / `canBaud` / `canFilter` for control.
