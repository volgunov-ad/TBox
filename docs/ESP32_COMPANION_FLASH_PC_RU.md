# Прошивка компаньона ESP32-S3 с компьютера

Пошаговая инструкция: первая установка и обновление прошивки компаньона с ПК.  
Плата: **ESP32-S3-DevKitC-1** (N16R8 / N8R8). Исходники: [`firmware/esp32-companion/`](../firmware/esp32-companion/).  
Протокол и OTA с ГУ: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md).

**ESP-IDF на ПК не обязателен.** Достаточно готовых `.bin` из GitHub Actions + `esptool` (первая прошивка) или Python + `pyserial` (обновление по CDC). IDF нужен только если вы сами собираете прошивку из исходников.

## Два порта на DevKit — не перепутать

| Порт на плате | Надпись / чип | Назначение |
|---------------|---------------|------------|
| **USB-UART** | обычно «UART» / CP210x / CH340 | **Первая прошивка с ПК** (`esptool`) |
| **ESP32-S3 USB** | native USB (GPIO19/20) | Работа с **ГУ** и **OTA по CDC** с ПК |

## Что прошивать

| Файл | Когда нужен |
|------|-------------|
| `esp32_companion.bin` | Всегда (app image, первый байт `0xE9`) |
| `bootloader.bin` | Первая установка / смена таблицы разделов |
| `partition-table.bin` | Первая установка / смена таблицы разделов |
| `ota_data_initial.bin` | Первая установка / сброс otadata |

Таблица разделов A/B (`ota_0` / `ota_1` по 1.5 MB) — в `firmware/esp32-companion/partitions.csv`.  
С ГУ обновляются **только** app image; bootloader и partition table — **только с ПК**.

---

## Без ESP-IDF (рекомендуется)

### 1. Скачать бинарники

1. Репозиторий → **Actions** → workflow **Build Companion Firmware**.
2. **Run workflow** или артефакт последнего успешного run по `preRelease`.
3. **Artifacts** → `esp32-companion-<sha>` (хранение 30 дней).
4. В ZIP:
   - `esp32_companion.bin` — app / OTA;
   - `bootloader.bin`, `partition-table.bin`, `ota_data_initial.bin` — первая прошивка;
   - `README.txt` — краткая подсказка.

Драйверы USB-UART (Windows): CP210x / CH340 — с сайта производителя моста на плате.

| ОС | Пример порта UART |
|----|-------------------|
| Windows | `COM3`, `COM5`, … (Диспетчер устройств → Ports) |
| Linux | `/dev/ttyUSB0` |
| macOS | `/dev/cu.usbserial-…` / `/dev/cu.SLAB_USBtoUART` |

### 2a. Первая прошивка — только esptool

Нужна, если плата пустая, стоит чужая прошивка или менялась partition table.

```bash
pip install esptool
```

Подключите кабель в **USB-UART**. В каталоге с распакованными файлами:

```bash
esptool.py --chip esp32s3 -p PORT -b 460800 \
  --before default_reset --after hard_reset write_flash -z \
  --flash_mode dio --flash_freq 80m --flash_size 16MB \
  0x0       bootloader.bin \
  0x8000    partition-table.bin \
  0xf000    ota_data_initial.bin \
  0x20000   esp32_companion.bin
```

Пример Windows: `-p COM5`. Linux: `-p /dev/ttyUSB0`.

Адреса — из `partitions.csv` (`otadata` @ `0xf000`, `ota_0` @ `0x20000`).  
`flash_size 16MB` — как в `sdkconfig.defaults`; для платы N8R8 при необходимости укажите `8MB`.

Если порт не открывается: зажмите **BOOT**, нажмите **RESET**, отпустите RESET, затем BOOT — и повторите команду.

### 2b. Обновление — без esptool (CDC OTA)

Уже стоит прошивка компаньона с A/B (0.4.x / 0.5.x). Нужен только `esp32_companion.bin`.

```bash
pip install pyserial
```

Кабель в **ESP32-S3 USB** (native). Скрипт из репозитория:

```bash
cd firmware/esp32-companion
python tools_cdc_ota_flash.py PORT path/to/esp32_companion.bin
```

```bash
# Windows
python tools_cdc_ota_flash.py COM30 esp32_companion.bin

# Linux (native CDC часто /dev/ttyACM0)
python tools_cdc_ota_flash.py /dev/ttyACM0 esp32_companion.bin
```

Скрипт: `hello` → `otaBegin` → кадры → `otaEnd` → `otaDone` → проверка `hello` после reboot.

То же с ГУ: вкладка **«Компаньон»** → **«Обновить прошивку…»**.

---

## С ESP-IDF (только если собираете сами)

Рекомендуемая версия: **v5.3.2** (как в CI).  
Официально: [Get Started — ESP-IDF](https://docs.espressif.com/projects/esp-idf/en/v5.3.2/esp32s3/get-started/index.html).

```bash
. $HOME/esp/esp-idf/export.sh   # Linux/macOS; на Windows — среда Espressif
cd firmware/esp32-companion
idf.py set-target esp32s3
idf.py build
idf.py -p PORT flash monitor    # кабель в USB-UART
```

Артефакты: `build/esp32_companion.bin`, `build/bootloader/bootloader.bin`, `build/partition_table/partition-table.bin`, `build/ota_data_initial.bin`.  
Выход из монитора: `Ctrl+]`.

---

## После прошивки: подключение к ГУ

1. Отключите кабель от ПК.
2. Подключите **ESP32-S3 USB** (native) к USB Host ГУ.
3. В приложении: пункт меню **«Компаньон»** (если скрыт) → **«Подключаться к компаньону»**.
4. Должны появиться USB-статус и версия `fw` из `hello`.

Подробнее: [USER_GUIDE_RU.md](USER_GUIDE_RU.md) §«Вкладка Компаньон», [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md).

## Типичные проблемы

| Симптом | Что проверить |
|---------|----------------|
| esptool не видит порт | Кабель в **UART**, драйвер моста, data-кабель |
| Flash fails / timeout | BOOT+RESET (см. выше), другой порт/скорость `-b 115200` |
| После flash нет `hello` на ГУ | На ГУ — порт **native USB**, не UART; VID `0x303A` |
| OTA: `FAIL no hello` | Native USB; порт не занят другим приложением |
| OTA: `bad image` | Нужен app `esp32_companion.bin` (`0xE9`), не dump flash |
| OTA «ломается» на старой плате | Один раз полный UART flash (§2a) с новой partition table |

## Кратко

```
Без IDF, плата новая
  → Actions ZIP + esptool (§2a), кабель UART

Без IDF, прошивка уже наша
  → esp32_companion.bin + tools_cdc_ota_flash.py (§2b) или OTA с ГУ

Собрать из исходников
  → ESP-IDF + idf.py build / flash
```
