# Прошивка компаньона ESP32-S3 с компьютера

Пошаговая инструкция: первая установка и обновление прошивки компаньона с ПК.  
Плата: **ESP32-S3-DevKitC-1** (N16R8 / N8R8). Исходники: [`firmware/esp32-companion/`](../firmware/esp32-companion/).  
Протокол и OTA с ГУ: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md).  
Схема подключений периферии: [ESP32_COMPANION_WIRING_RU.md](ESP32_COMPANION_WIRING_RU.md).

**ESP-IDF и Python на ПК не обязательны** для первой прошивки. Достаточно готовых `.bin` из GitHub Actions + standalone `esptool.exe`. IDF нужен только если вы сами собираете прошивку из исходников. Python нужен только для CDC OTA-скрипта (§3) или установки esptool через `pip`.

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

## 1. Первая прошивка на Windows (без Python) — рекомендуется

Нужна, если плата пустая, стоит чужая прошивка или менялась partition table.  
Ниже — путь «только `.exe` + файлы прошивки», без установки Python и ESP-IDF.

### 1.1. Скачать esptool.exe (без Python)

Официальные готовые бинарники Espressif (PyInstaller), Python ставить не нужно:

1. Откройте релизы: [github.com/espressif/esptool/releases](https://github.com/espressif/esptool/releases).
2. Возьмите **последний** релиз (например `Version 5.4.0`).
3. В разделе **Assets** скачайте архив для Windows:
   - `esptool-vX.Y.Z-windows-amd64.zip`  
   (подходит для обычного 64‑битного Windows на ПК/ноутбуке).
4. Распакуйте ZIP в удобную папку, например:
   - `C:\esp-flash\`
5. Внутри будет каталог с файлами; главное — **`esptool.exe`** (рядом могут лежать `espefuse.exe`, `espsecure.exe` — они не нужны).

Документация Espressif про binary releases: [esptool — Installation](https://docs.espressif.com/projects/esptool/en/latest/esp32/installation.html) (раздел **Binary Releases**).

> Антивирус иногда ругается на PyInstaller-сборки (ложное срабатывание). При необходимости добавьте папку в исключения или скачайте снова с официальной страницы релизов.

### 1.2. Скачать файлы прошивки

1. Репозиторий TBox → **Actions** → workflow **Build Companion Firmware**.
2. Откройте последний успешный run (ветка `preRelease` или тот commit, который нужен).
3. Внизу страницы **Artifacts** → `esp32-companion-<sha>` (хранение ~30 дней).
4. Скачайте ZIP и распакуйте. Нужны все четыре файла:
   - `esp32_companion.bin`
   - `bootloader.bin`
   - `partition-table.bin`
   - `ota_data_initial.bin`
5. **Скопируйте эти четыре `.bin` в ту же папку, где лежит `esptool.exe`**  
   (например всё в `C:\esp-flash\`), чтобы команды ниже писать короткими именами файлов.

Итого в одной папке должно быть примерно так:

```
C:\esp-flash\
  esptool.exe
  bootloader.bin
  partition-table.bin
  ota_data_initial.bin
  esp32_companion.bin
```

### 1.3. Драйвер USB-UART и номер COM-порта

1. Кабелем USB подключите плату к ПК в порт **USB-UART** (не native «ESP32-S3 USB»).
2. Если порт не появился — установите драйвер моста с платы:
   - **CP210x** (Silicon Labs) или **CH340** — с сайта производителя чипа на вашей плате.
3. Откройте **Диспетчер устройств** → **Порты (COM и LPT)** и запомните порт, например `COM5` или `COM12`.

### 1.4. Открыть командную строку именно в папке с esptool

Любой удобный способ:

**Способ A (Проводник)**  
1. Откройте папку `C:\esp-flash\` в Проводнике.  
2. Щёлкните по адресной строке, введите `cmd` и нажмите **Enter** — откроется `cmd` уже в этой папке.

**Способ B (через меню)**  
1. В Проводнике зайдите в папку с `esptool.exe`.  
2. В пустом месте папки: **Shift + правый клик** → **«Открыть окно PowerShell здесь»** / **«Открыть в терминале»**  
   (формулировка зависит от версии Windows).  
3. Либо в адресной строке: `cmd` → Enter.

**Способ C**  
1. `Win + R` → `cmd` → Enter.  
2. Перейдите в папку:

```bat
cd /d C:\esp-flash
```

Проверка, что вы в нужном месте:

```bat
dir
```

Должны быть видны `esptool.exe` и четыре `.bin`.

### 1.5. Перевести ESP32 в режим загрузки (BOOT + RESET)

На DevKit обычно две кнопки: **BOOT** (иногда IO0) и **RESET** (EN).

Перед первой прошивкой (или если esptool пишет timeout / failed to connect):

1. Кабель в **USB-UART**, порт COM известен.
2. **Зажмите BOOT** и держите.
3. Не отпуская BOOT, **кратко нажмите RESET** (или отключите/подключите USB, если RESET нет).
4. **Отпустите RESET**, затем **отпустите BOOT**.
5. Плата в режиме загрузчика — сразу запускайте команду прошивки (не ждите долго).

На многих ESP32-S3 DevKit автосброс через DTR/RTS работает сам (`--before default_reset`). Если соединение проходит без ручных кнопок — BOOT+RESET не нужен. Если нет — используйте последовательность выше и повторите команду.

### 1.6. Команда прошивки

В окне `cmd` / PowerShell, подставьте **свой** COM-порт вместо `COM5`:

```bat
esptool.exe --chip esp32s3 -p COM5 -b 460800 --before default_reset --after hard_reset write_flash -z --flash_mode dio --flash_freq 80m --flash_size 16MB 0x0 bootloader.bin 0x8000 partition-table.bin 0xf000 ota_data_initial.bin 0x20000 esp32_companion.bin
```

Для платы **N8R8** (8 MB flash) замените `--flash_size 16MB` на `--flash_size 8MB`.

Успех: в конце будет что-то вроде `Hash of data verified` / `Leaving...` / `Hard resetting via RTS pin...`.

Если не коннектится:

1. Снова BOOT+RESET (§1.5).
2. Попробуйте меньшую скорость: `-b 115200`.
3. Проверьте, что кабель в **UART**, порт не занят другим приложением (монитор, Arduino IDE и т.п.).

Адреса — из `partitions.csv` (`otadata` @ `0xf000`, `ota_0` @ `0x20000`).

---

## 2. Первая прошивка через pip (альтернатива)

Если Python уже установлен:

```bash
pip install esptool
```

Та же команда, но вызываете `esptool` / `esptool.py` вместо `esptool.exe`. Файлы `.bin` и порт — как в §1. Кабель в **USB-UART**.

```bash
esptool --chip esp32s3 -p COM5 -b 460800 \
  --before default_reset --after hard_reset write_flash -z \
  --flash_mode dio --flash_freq 80m --flash_size 16MB \
  0x0       bootloader.bin \
  0x8000    partition-table.bin \
  0xf000    ota_data_initial.bin \
  0x20000   esp32_companion.bin
```

Linux / macOS: `-p /dev/ttyUSB0` или `/dev/cu.usbserial-…`.

---

## 3. Обновление — без полной перепрошивки (CDC OTA)

Уже стоит прошивка компаньона с A/B (0.4.x / 0.5.x и новее). Нужен только `esp32_companion.bin` и Python:

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

## 4. С ESP-IDF (только если собираете сами)

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

Подробнее: [ESP32_COMPANION_RU.md](ESP32_COMPANION_RU.md); кратко в приложении — [USER_GUIDE_RU.md](USER_GUIDE_RU.md).

## Типичные проблемы

| Симптом | Что проверить |
|---------|----------------|
| esptool не видит порт | Кабель в **UART**, драйвер моста, data-кабель |
| Flash fails / timeout | BOOT+RESET (§1.5), другой порт/скорость `-b 115200` |
| После flash нет `hello` на ГУ | На ГУ — порт **native USB**, не UART; VID `0x303A` |
| OTA: `FAIL no hello` | Native USB; порт не занят другим приложением |
| OTA: `bad image` | Нужен app `esp32_companion.bin` (`0xE9`), не dump flash |
| OTA «ломается» на старой плате | Один раз полный UART flash (§1) с новой partition table |
| Антивирус блокирует `esptool.exe` | Исключение для папки или установка через `pip` (§2) |

## Кратко

```
Windows, плата новая / пустая
  → esptool.exe из Releases Espressif
  → 4× .bin из Actions в ту же папку
  → cmd в этой папке → UART → (при необходимости BOOT+RESET) → команда §1.6

Без IDF, прошивка уже наша
  → esp32_companion.bin + tools_cdc_ota_flash.py (§3) или OTA с ГУ

Собрать из исходников
  → ESP-IDF + idf.py build / flash
```
