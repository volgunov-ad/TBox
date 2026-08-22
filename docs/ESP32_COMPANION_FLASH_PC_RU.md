# Прошивка компаньона ESP32-S3 с компьютера

Пошаговая инструкция: первая установка и обновление прошивки компаньона с ПК.  
Плата: **ESP32-S3-DevKitC-1** (N16R8 / N8R8). Исходники: [`firmware/esp32-companion/`](../firmware/esp32-companion/).  
Протокол и OTA с ГУ: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md).

## Два порта на DevKit — не перепутать

| Порт на плате | Надпись / чип | Назначение |
|---------------|---------------|------------|
| **USB-UART** | обычно «UART» / CP210x / CH340 | **Прошивка с ПК** (`idf.py flash` / `esptool`) и UART-монитор |
| **ESP32-S3 USB** | native USB (GPIO19/20) | Работа с **ГУ** (CDC) и **OTA по CDC** с ПК |

Для первой прошивки (bootloader + partition table) нужен **USB-UART**.  
Позже обновлять app image можно по CDC (порт native USB) или снова по UART.

## Что прошивать

| Файл | Когда нужен |
|------|-------------|
| `esp32_companion.bin` | Всегда (app image, первый байт `0xE9`) |
| `bootloader.bin` | Первая установка / смена таблицы разделов |
| `partition-table.bin` | Первая установка / смена таблицы разделов |
| `ota_data_initial.bin` | Первая установка / сброс otadata |

Таблица разделов A/B (`ota_0` / `ota_1` по 1.5 MB) — в `firmware/esp32-companion/partitions.csv`.  
С ГУ обновляются **только** app image; bootloader и partition table — **только с ПК**.

## Вариант A. Готовые бинарники из GitHub Actions

1. Откройте репозиторий → **Actions** → workflow **Build Companion Firmware**.
2. Запустите **Run workflow** (или возьмите артефакт с последнего успешного run по `preRelease`).
3. Скачайте **Artifacts** → `esp32-companion-<sha>` (хранение 30 дней).
4. Внутри ZIP:
   - `esp32_companion.bin` — OTA / app;
   - `bootloader.bin`, `partition-table.bin`, `ota_data_initial.bin` — первая прошивка по UART;
   - `README.txt` — краткая подсказка.

Дальше — [способ 1](#способ-1-первая-прошивка-uart-esptool) или [способ 2](#способ-2-обновление-по-cdc-ota-уже-есть-рабочая-прошивка).

## Вариант B. Сборка у себя (ESP-IDF)

Рекомендуемая версия IDF: **v5.3.2** (как в CI). Подойдёт и другой 5.x, но при расхождениях ориентируйтесь на CI.

### Установка ESP-IDF (кратко)

Официально: [Get Started — ESP-IDF](https://docs.espressif.com/projects/esp-idf/en/v5.3.2/esp32s3/get-started/index.html).

После установки в каждом новом терминале:

```bash
# Linux / macOS — путь к вашему клону IDF:
. $HOME/esp/esp-idf/export.sh

# Windows (ESP-IDF PowerShell / CMD из установщика Espressif):
# export.bat / Export.ps1 уже вызывается средой
```

Драйверы USB-UART (Windows): CP210x / CH340 — с сайта производителя моста на плате.

### Сборка и flash одной командой

```bash
cd firmware/esp32-companion
idf.py set-target esp32s3
idf.py build
idf.py -p PORT flash monitor
```

| ОС | Пример `PORT` |
|----|----------------|
| Windows | `COM3`, `COM5`, … (Диспетчер устройств → Ports) |
| Linux | `/dev/ttyUSB0` или `/dev/ttyACM0` |
| macOS | `/dev/cu.usbserial-…` / `/dev/cu.SLAB_USBtoUART` |

Подключайте кабель в разъём **USB-UART**, не native USB.

`idf.py flash` пишет bootloader, partition table, otadata и приложение по адресам из сборки — это полный «первый» flash.

Артефакты после `build`:

```
build/esp32_companion.bin
build/bootloader/bootloader.bin
build/partition_table/partition-table.bin
build/ota_data_initial.bin
```

Выход из монитора: `Ctrl+]`.

## Способ 1. Первая прошивка UART (esptool)

Нужен, если:

- плата пустая / другая прошивка;
- менялась partition table (например переход на A/B OTA);
- `idf.py` недоступен, но есть бинарники из Actions.

Подключите **USB-UART**. Установите esptool при необходимости:

```bash
pip install esptool
```

Из каталога с бинарниками (или с полными путями):

```bash
esptool.py --chip esp32s3 -p PORT -b 460800 \
  --before default_reset --after hard_reset write_flash -z \
  --flash_mode dio --flash_freq 80m --flash_size 16MB \
  0x0       bootloader.bin \
  0x8000    partition-table.bin \
  0xf000    ota_data_initial.bin \
  0x20000   esp32_companion.bin
```

Адреса соответствуют `partitions.csv` проекта (`otadata` @ `0xf000`, `ota_0` @ `0x20000`).  
`flash_size 16MB` — как в `sdkconfig.defaults` (`CONFIG_ESPTOOLPY_FLASHSIZE_16MB`); для N8R8 при необходимости укажите `8MB`.

Проверка после reboot: UART-монитор на том же порту или CDC на native USB (строки NDJSON `hello` / `hb`).

## Способ 2. Обновление по CDC OTA (уже есть рабочая прошивка)

Только **app image** `esp32_companion.bin` (magic `0xE9`), размер ≤ 1.5 MB.  
Bootloader / partition table этим способом **не** обновляются.

1. Подключите кабель в разъём **ESP32-S3 USB** (native).
2. Установите зависимость: `pip install pyserial`.
3. Запустите скрипт из репозитория:

```bash
cd firmware/esp32-companion
python tools_cdc_ota_flash.py PORT path/to/esp32_companion.bin
```

Примеры:

```bash
# Windows
python tools_cdc_ota_flash.py COM30 build/esp32_companion.bin

# Linux
python tools_cdc_ota_flash.py /dev/ttyACM0 build/esp32_companion.bin
```

Скрипт шлёт `hello` → `otaBegin` → бинарные кадры → `otaEnd`, ждёт `otaDone` и проверяет `hello` после перезагрузки.

Альтернатива на ГУ: вкладка **«Компаньон»** → **«Обновить прошивку…»** (тот же `esp32_companion.bin`).

## После прошивки: подключение к ГУ

1. Отключите кабель от ПК.
2. Подключите **ESP32-S3 USB** (native) к USB Host ГУ Jetour.
3. В приложении: включите пункт меню **«Компаньон»** (если скрыт) → **«Подключаться к компаньону»**.
4. Должны появиться USB-статус и версия `fw` из `hello`.

Подробнее: [USER_GUIDE_RU.md](USER_GUIDE_RU.md) §«Вкладка Компаньон», [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md).

## Типичные проблемы

| Симптом | Что проверить |
|---------|----------------|
| `idf.py` / esptool не видит порт | Кабель в **UART**, драйвер моста, другой USB-кабель (нужны data-линии) |
| Flash fails / timeout | Зажмите **BOOT**, нажмите **RESET**, отпустите RESET, затем BOOT; повторите flash |
| После flash нет `hello` на ГУ | На ГУ нужен порт **native USB**, не UART; VID Espressif `0x303A` |
| OTA с ПК: `FAIL no hello` | Открыт ли CDC (native USB); занят ли порт другим приложением; DTR (скрипт выставляет сам) |
| OTA: `bad image` | Нужен именно app `esp32_companion.bin` (`0xE9`), не полный dump flash |
| Старое устройство, OTA «ломается» | Один раз полный UART flash (способ 1) с новой partition table |

## Кратко: что выбрать

```
Плата новая / сменилась таблица разделов
  → UART + полный flash (idf.py flash или esptool, способ 1)

Уже стоит прошивка компаньона 0.4.x / 0.5.x с A/B
  → CDC OTA с ПК (tools_cdc_ota_flash.py) или OTA с ГУ
```
