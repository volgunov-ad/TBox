# Климат-дисплей (esp32-climate-display)

Прошивка для **Waveshare ESP32-S3-Touch-LCD-7** (7" 800×480 ST7262, GT911, ESP32-S3 N16R8):
экран климата Jetour Dashing. Отдельный вид компаньона — в hello передаётся `"kind":"display"`.
**Без реле и без входов** — GPIO платы заняты RGB-панелью и тачем.

## Железо

- RGB-панель ST7262: pclk 16 МГц, тайминги hsync 4/8/8, vsync 4/8/8, RGB565, 2 framebuffer в PSRAM.
  Пины: VSYNC=3, HSYNC=46, DE=5, PCLK=7, DATA0..15 = 14,38,18,17,10,39,0,45,48,47,21,1,2,42,41,40.
- I2C0: SDA=8, SCL=9, 400 кГц. CH422G: режим выходов `0x01→0x24`, регистр выходов по `0x38`
  (7-bit): reset-танец тача `0x2C` → GPIO4 low → `0x2E`; мультиплексор USB/CAN на EXIO5:
  `0x1E` = USB+подсветка, `0x3E` = CAN+подсветка.
- GT911: адрес 0x5D, INT/RST в драйвере не задействованы (NC), reset — танец выше.
- **Второй CAN** (бортовой SN65HVD230): TX=GPIO20, RX=GPIO19, 500 кбит/с (Kconfig),
  режим **listen-only**. После EXIO5=1 USB отключён — CDC недоступен, связь с HU идёт
  по консольному UART0 (115200).

## Прошивка

- LVGL 9 через `espressif/esp_lvgl_port` v2 (`lvgl_port_add_disp_rgb`, direct mode + avoid_tearing).
- Климат: температуры 16.0–30.0 °C шаг 0.5 (десятые доли °C, соответствует
  `HvacClimateDomain`: TEMP_MB_CAN 160..300 step 5 / TEMP_VHAL 32..60), вентилятор 0..7,
  A/C, SYNC, режимы обдува, передний/задний обдув.
- Декод CAN: `can_map.c` пока только накапливает список видных ID (лог на новых ID);
  конкретные ID климата Jetour — после стендовой проверки.
- Симуляция: `CONFIG_CLIMATE_UI_SIM=y` (по умолчанию) двигает модель раз в 2 с и
  останавливается, когда по CAN пошёл реальный трафик (окно 2 с).
- Компаньон-линк (`companion_link.c`): линия hello раз при старте и hb каждую секунду,
  JSON-строки, `v=1` (совпадает с `EspCompanionProtocol.PROTOCOL_VERSION`),
  `"kind":"display"`. Текущее приложение HU незнакомые поля игнорирует — поддержка
  отдельного вида на стороне HU будет отдельной задачей.

## Сборка

ESP-IDF 5.5.5 (`C:\esp\v5.5.5\esp-idf`):

```
cmd /c ""C:\esp\v5.5.5\esp-idf\export.bat" && idf.py set-target esp32s3 && idf.py build"
```

Прошивка/OTA-слоты по 0x2A0000 (partitions.csv), 16 МБ flash, octal PSRAM 120 МГц.

## Планы

- Декод реальных CAN-климата ID (стенд), отправка команд в CAN.
- Русский шрифт (встроенные Montserrat — только латиница, сейчас UI на английском).
- Отображение статуса CAN/SIM в UI динамически, пороговое освещение/подсветка.
