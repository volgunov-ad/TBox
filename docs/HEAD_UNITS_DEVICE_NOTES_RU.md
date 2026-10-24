# Головные устройства A9 и A10: заметки по железу, прошивке и диагностике

Сводка того, что выяснено на живых ГУ (октябрь 2026): платформа, root, загрузка TBox Monitor, нагрузка CPU, разметка диска, сеть, сертификаты, перезагрузка. Логику CAN-бэкендов см. в [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), параметры mbCAN/VHAL — в [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), штатные приложения — в [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md) и [STOCK_APPS_ANDROID10_VHAL_RU.md](STOCK_APPS_ANDROID10_VHAL_RU.md).

Исследование взаимозаменяемости A9/A10, таблицы CAN MCU и ограничения переноса этих карт на поток TBox: [HEAD_UNITS_CAN_COMPATIBILITY_RU.md](HEAD_UNITS_CAN_COMPATIBILITY_RU.md). Полная взаимозаменяемость конкретных экземпляров пока не подтверждена.

## 1. Профили устройств

| | **A9** (mbCAN) | **A10** (Adayo / VHAL) |
|---|---|---|
| Fingerprint | `TINNOVE/sm6150_au/sm6150_au:9/PQ3B.190801.002/272:user/test-keys` | `autochips/full_ac8x_demo/ac8x_demo:9/PI/Jenkins09051534:userdebug/test-keys` |
| SoC / ядра / ОЗУ | Qualcomm SM6150, 8 ядер, ~7,4 ГБ | Autochips AC8x, 4 ядра, ~3,5 ГБ |
| Тип сборки | `user` | `userdebug` |
| `ro.build.version.release` / API | 9 / 28 | 9 / 28 (в UI может показываться «10», см. [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md)) |
| SELinux | Permissive | Permissive |
| root (`su`) | нет | есть: `/system/xbin/su`, `su 0` из `adb shell` |
| `/data` | 37 ГБ (`/dev/block/sda10`, UFS) | **2 ГБ** (`/dev/block/userdata`, раздел 39 eMMC 64 ГБ) |
| Часовой пояс | `Europe/Moscow` | задаётся службой Adayo, см. §5.1 (заводское `PRC`) |
| ADB по Wi-Fi после перезагрузки | `persist.adb.tcp.port=5555` | `persist.adb.tcp.port=5555` |

`/system` на A10 собран неизменяемым: запись даже от root отдаёт `EIO`, `ro.boot.veritymode=disabled`, но перемонтирование rw не помогает. Слоты A/B, активный — `_b`.

## 2. Работа с ГУ по ADB с Windows

- Подключение: `adb connect 192.168.1.7:5555` (A10), `adb connect 192.168.1.128:5555` (A9). **Wi-Fi на ГУ не выключать** — пропадёт доступ (на A10 восстановление — через UART, §2.1).
- PowerShell портит `$(...)`, кавычки и `>` внутри `adb shell "..."`. Надёжно: сохранить скрипт локально **с переводами строк LF** (CRLF даёт `syntax error: unmatched 'if'`), `adb push` в `/data/local/tmp/`, запустить `sh` или `su 0 sh`.
- Скриншот: `adb shell screencap -p /data/local/tmp/s.png` + `adb pull` (перенаправление `screencap -p >` в PowerShell портит PNG).
- `uiautomator dump` на A10 возвращает пустой корень — навигация только по скриншотам.
- AOSP `sgdisk` на A10 считает основной GPT повреждённым (записи по 256 байт, см. §6) — для чтения разметки использовать sysfs (`/sys/block/mmcblk0/<имя>/{start,size,uevent}`), **для записи `sgdisk` не использовать**. Каталоги разделов в sysfs и узлы в `/dev/block` названы по имени раздела (`DEVNAME` = `PARTNAME`, например `/dev/block/recovery_b`); узлов `mmcblk0pN` на A10 нет, номер раздела — только в файле `partition`.

### 2.1. Отладочный UART (A10)

Резервный доступ, не зависящий от Wi-Fi и ADB. Проверено 13.10.2026.

| Параметр | Значение |
|---|---|
| Адаптер | USB-UART Silicon Labs CP210x (`VID_10C4&PID_EA60`), в Windows — `COM21` |
| Скорость | **921600 бод, 8N1**, без управления потоком (на остальных скоростях — мусор) |
| Проводка | TX адаптера → RX ГУ, RX адаптера → TX ГУ, общая земля |
| Что идёт в порт | preloader → ATF → LK → ядро → консоль Android, всё на одной скорости |
| Консоль | `console:/ $` (uid `shell`), `su` → `console:/ #` (root) |

Особенности:

- **Консоль не печатает приглашение сама** — его видно только после ввода (Enter). Пока RX ГУ не найден, ввод молча игнорируется, эха нет. Пин RX подбирался перебором; проверка адаптера — петля TX↔RX на самом адаптере (строка должна вернуться без искажений).
- **Лог ядра идёт в тот же порт** и перемешивается с выводом команд. На стенде без экрана драйвер `max96735` (сериализатор GMSL) непрерывно сыплет `wait_max737_GMSL_lock` и `i2c_read … ERR(-6)` — это отсутствие дисплея, не неисправность. `dmesg -n 1` от `shell` не работает (нет прав).
- **Длинные команды теряют символы**, если отправлять их целиком: строка ломается, shell уходит в продолжение (`>`) из-за незакрытой кавычки. Отправлять порциями по 8 байт с паузой ~15 мс; при зависании на `>` — Ctrl+C, Enter.
- **Символы теряются из-за потока лога ядра** (проверено 20.10.2026). После `su` можно приглушить консоль ядра: `echo 1 4 1 7 > /proc/sys/kernel/printk` (было `4 4 1 7`). Тогда спам `max96735` прекращается, и ввод надёжен на полной скорости: строка в 3000 символов без пауз доходит с верным MD5. До перезагрузки значение сохраняется.
- **Разбор вывода:** оборачивать команду маркерами, которые не совпадают с эхом ввода (`echo QS''TART; <cmd>; echo QE''ND` — в эхе `QS''TART`, в выводе `QSTART`). Сообщение ядра вклинивается в любое место строки, даже посреди эха команды, и заканчивается своим переводом строки. Если вырезать его вместе с этим переводом (`\[\s*\d+\.\d{6}\](\[…\])?\s*<\d>[^\n]*\n`), вывод склеивается без потерь.
- **Передача файлов:** base64 строками по 64 символа (`echo … >> /data/local/tmp/f.b64`), затем `base64 -d` и сверка `md5sum`. С приглушённым `printk` годятся строки по 2000 символов.
- **Готовый комплект восстановления:** [`tools/hu_rescue/`](../tools/hu_rescue/) (`Запуск.cmd`, `Проверка без записи.cmd`, `start.ps1`, `hu_rescue.ps1`, [`ИНСТРУКЦИЯ.md`](../tools/hu_rescue/ИНСТРУКЦИЯ.md), эталонные копии GPT `gpt_2026-10-20/` и `gpt_2026-10-14/`, `platform-tools/`), см. §6.5.
- **Лог загрузки** (перехват при включении питания): preloader от 15.05.2024, LK от 05.09.2024, проект `HMA214A`, диагностическая область `ADAYO DZ`, Wi-Fi — RTL8821CS на SDIO (модуль `8821cs`), PCIe без устройств (`No EP Device` — норма).

Подключение A10 к Wi-Fi через UART (если ADB недоступен). В Android 9 нет `cmd wifi connect-network`, а сеть, добавленная через `wpa_cli`, фреймворку неизвестна — он рвёт соединение и не запускает DHCP. Рабочий путь:

1. `su`, `svc wifi enable`; проверить видимость сети: `wpa_cli -i wlan0 -p /data/vendor/wifi/wpa/sockets scan_results`.
2. Добавить блок `<Network>` (SSID, `PreSharedKey`, `AllowedKeyMgmt` = `02`, `NetworkStatus`, `IpConfiguration` DHCP) в `/data/misc/wifi/WifiConfigStore.xml`, взяв за образец уже сохранённую сеть. Файл перезаписывать через `cat new > WifiConfigStore.xml` — так сохраняются владелец `system`, права `600` и контекст `wifi_data_file`.
3. Сразу `stop; start` (перезапуск фреймворка без перезагрузки ядра) — иначе работающий фреймворк может перезаписать файл своей копией.

Сеть после этого штатная и подключается сама после перезагрузки (`wifi_on=1`). ADB по сети — `setprop persist.adb.tcp.port 5555` и `setprop ctl.restart adbd`; ключи не проверяются (`ro.adb.secure` пуст), подтверждение на экране не нужно. MAC `wlan0` — `a4:04:50:c5:c8:87` (для резервирования адреса в роутере).

### 2.2. USB в режиме устройства: ADB и fastboot (A10)

Проверено на стенде 20.10.2026, кабель от ПК в USB-порт ГУ, через который работает проводной CarPlay.

Порт — двухрежимный контроллер Cadence `13010000.cdncusb`. LK передаёт ядру `otg_state=a_host` (значение берётся из `metazone`), поэтому порт работает как хост. Режим устройства включает CarPlay через `/sys/class/dual_role_usb/dual-role-usb20/data_role` (`device` / `host`, при этом `mode` меняется на `ufp` / `dfp`). Gadget в configfs `/config/usb_gadget/g1` с функциями `ffs.adb`, `mtp`, `ptp`, `ncm`.

Консоли по USB нет: ядро собрано без `CONFIG_USB_CONFIGFS_ACM` и `CONFIG_USB_CONFIGFS_SERIAL`. Init создаёт `acm.gs0`–`acm.gs3`, но таких функций в ядре нет. Вместо консоли есть три пути.

**ADB по USB в Android.** Gadget уже настроен (`sys.usb.config=adb`, UDC привязан, `adbd` выставил конечные точки). Достаточно одной команды от root: `echo device > /sys/class/dual_role_usb/dual-role-usb20/data_role` (или включить тумблер **«Включить ADB USB на ГУ»** в настройках TBox Monitor — на A10 `HuAdbControl.setUsbEnabled` выполняет это переключение и при необходимости привязывает UDC `13010000.cdncusb` через localhost ADB + `su`). На ПК появляется `0x123456789EF` (`VID_2BE1&PID_0000`). Драйвер Windows ставит сам, `su` работает. Скорость `adb pull` около 28 МБ/с против около 7 МБ/с по Wi-Fi. Пока ADB USB включён, CarPlay и флешки на этом порту не работают; после перезагрузки ГУ порт снова в режиме хоста (`dfp`), также он может сам вернуться в режим хоста через несколько минут бездействия.

**Recovery.** `adb reboot recovery` на прошивке `Jenkins03261716` **не работает**. Ядро перезагружается с командой `recovery`, но LK смотрит только на команду в `misc` (`pbcb->command`) и грузит Android. В recovery можно попасть, только записав в `misc` загрузочный блок:

- первые 2048 байт `misc`: `command[32]` (`boot-recovery`), `status[32]`, `recovery[768]` (`recovery\n--аргумент\n`), `stage[32]`, поля Adayo (`bootflag`, `laststatus`, `exception`, `lk_exception`, `reserved`) и в последних 4 байтах (смещение 2044) `checksum` — сумма байтов 0..2043, little-endian;
- `misc` защищён от записи: нужен `emmc_disable_all_wp` / `emmc_enable_all_wp` (§6), `dd` в toybox Android не знает `conv=`;
- без суммы recovery отвергает блок и берёт пустую копию из `miscbk`.

Recovery Adayo — автоматический агент обновления. Без аргументов и с `--show_text` он за 6 секунд вызывает `finish_recovery` и перезагружается. Остаётся работать только с `--sideload`: root-консоль на UART (`#`, в `/sbin` есть `toybox` без ссылок, вызывать `toybox cat` и т. п.) и `adb sideload` по USB после `echo device > …/data_role` (`VID_0E8D&PID_201C`). Но **микроконтроллер ГУ сбрасывает SoC через 61 секунду после старта**, в логе ничего нет. Команда в `misc` остаётся, и ГУ ходит по кругу через recovery. Через 7 минут цикл закончился сам, `misc` оказался чист; кто его стёр, не выяснено. Полный `adbd` вместо `minadbd` запускается (`toybox setprop service.adb.root 1; toybox setprop ctl.stop recovery; toybox setprop ctl.start adbd`, затем снова `data_role` и перепривязка UDC: `echo none > /config/usb_gadget/g1/UDC; echo 13010000.cdncusb > /config/usb_gadget/g1/UDC`) и даёт root-shell по USB, но тоже живёт до сброса на 61-й секунде. Для восстановления recovery поэтому непригоден.

**Fastboot в LK — основной путь восстановления по USB.**

- Вход: `boot-fastboot` в `pbcb->command` (тот же блок в `misc`, LK сумму не проверяет). По коду LK (`lk_a`, функция у `0x6830`): при совпадении он обнуляет все 2048 байт блока и записывает их в `misc`, и только если запись удалась, входит в fastboot. Поэтому сброс или выключение питания возвращают обычную загрузку, застрять в fastboot нельзя.
- Через 12 секунд после перезагрузки на ПК появляется `VID_0BB4&PID_0C01` («Android ADB Interface», драйвер Windows ставит сам). Нужен `fastboot.exe` из Google platform-tools (`D:\Tools\platform-tools`), в сборке scrcpy его нет.
- `getvar`: `product` = `ac8xx-car`, `version` = `0.5`, `max-download-size` = `0x8000000` (128 МБ); остальные переменные «Variable Not found», `getvar all` обрывается.
- По строкам LK: `download`, `flash:<раздел>`, `erase:<раздел>`, `reboot`, `oem updatebegin/updatepartstart/oflash/updatepartend/updateend`, запись GPT («Upgrade gpt data») и preloader. Часть разделов закрыта («Partition[%s] do not be allowed fastboot upgraded»), при `flash` LK может менять метаданные слотов (`set_slot_info_fastbootupgrade`, `reset_slot`). **Запись через fastboot пока не проверялась.**
- Микроконтроллер в fastboot ГУ не сбрасывает минимум 3,5 минуты. Примерно через 4 минуты ГУ перезагрузилось само в обычный Android (`fastboot reboot` к этому моменту уже ждал устройство).
- Записать `boot-fastboot` в `misc` можно из Android (ADB, root), из консоли UART Android после `su` или из консоли recovery. То есть вход в fastboot возможен, пока жив хоть один из них, а сам fastboot работает и при сломанных ядре, Android и recovery.

Кроме того, LK сам восстанавливает одну копию GPT по другой («Update pgpt from sgpt» / «Update sgpt from pgpt») и умеет обновляться с USB-флешки по scatter-файлу (`atc.upd`).

## 3. Загрузка TBox Monitor

Проверено перезагрузкой обоих ГУ:

- TBox поднимается сам примерно через **40 с** после старта (`BootCompleteReceiver` → `BackgroundService`), без крашей и ANR.
- **A9** — лог чистый.
- **A10:**
  - Главный экран при загрузке не открывается, пока выключено `main_screen_open_on_boot` (по умолчанию выкл.) — это не ошибка.
  - Корневые сертификаты после перезагрузки пропадают (tmpfs), если не включена автоустановка (§7).
  - `E/WindowManager: A02 updateViewLayout token null` + стек при каждом перемещении оверлея — отладочная печать прошивки Adayo (у оверлея из сервиса токена нет по определению), не ошибка.
  - `Accessing hidden method Landroid/view/RenderNode;…` — от Compose, безвредно.
  - **Debug-сборка** при первой отрисовке плавающих панелей фризит главный поток ~13 с (`Skipped 780 frames`): debuggable-APK выполняется в интерпретаторе с проверкой классов в рантайме. **Release-сборка** (R8 + `profileinstaller`) этого не даёт: после перезагрузки кадры пропускают только `systemui` и `com.adayo.launcher`, у TBox пропусков нет. Оценивать производительность на A10 только по release.
  - **ANR `MainActivity` при живом «Phone direct» (25.10.2026, A10).** В логе три `Phone direct: snapshot … took 3708/1084/1196 ms` за ~15 с: каждый REFRESH заново тянул весь климат и кузов через `refreshSignalsNow`, на A10 — с IO-потока, мимо `vhal-a10-state-apply`. В том же окне главный поток ещё интерпретировал нескомпилированные `TboxScreen` и `DashboardPanelGrid` (`Verification … took 148 ms`, `Compiler allocated 10MB`), касание не закрывалось 6 с (`Input dispatching timed out`, очередь 7, `am_kill`). `E/WindowManager: A02 updateViewLayout token null` — по-прежнему отладочная печать Adayo, не причина. Снимок больше не делает pull на каждый запрос. Пока APK не в `speed`, первое открытие UI на A10 по-прежнему может не уложиться в окно ввода — компиляция та же, карточка «Компиляция приложения». Имена `UiKt.TboxScreen` в этом логе не обфусцированы: это была не R8 speed-сборка.
  - **ANR при загрузке после обновления APK (19.10.2026).** Упала debug-сборка из Android Studio (`DEBUGGABLE TEST_ONLY`), но release по компиляции не лучше: после `adb install -r` статус `[status=speed-profile] [reason=install]`, а `base.odex` всего 156 КБ — профиля при установке почти нет, код фактически не скомпилирован. Фоновой dexopt на ГУ нет: `dumpsys package dexopt` → `[status=verify] [reason=boot]` (интерпретатор + JIT). Задача `BackgroundDexOptService` (`pm.dexopt.bg-dexopt=speed-profile`) требует `deviceIdle` (экран выключен долго) — в `dumpsys jobscheduler` вечно `Unsatisfied constraints: DEADLINE IDLE`; `pm.dexopt.install=speed-profile` без профиля при установке вне магазина даёт почти ничего, `pm.dexopt.boot=verify`. Профиль `profileinstaller` лежит в `/data/misc/profiles/cur/0/<pkg>/primary.prof`, а `ref/` пуст — по нему никто не компилирует. На загрузке (loadavg ~23) главный поток TBox набрал 11 с CPU и ещё 12,5 с ждал ядро, `Skipped 623 frames`, касание по `MainActivity` → `Input dispatching timed out` → `am_kill`. Лечение — `cmd package compile -m speed -f vad.dashing.tbox` (~130 с на A10, `base.odex` 78 МБ при APK 57 МБ, работающий процесс не убивает, статус становится `speed`; `--reset` без root запрещён). В TBox (`dexopt/AppDexoptCompiler`): карточка «Компиляция приложения» в Настройки → Система (статус, «Скомпилировать», «Проверить статус»), разовая подсказка после каждого обновления APK (кроме debug) и переключатель автокомпиляции (по умолчанию выкл.: через 3 мин после старта, не больше 3 попыток на `lastUpdateTime`). Компиляция запускается в фоне через localhost ADB как `trap '' HUP; nohup cmd package compile … </dev/null >/dev/null 2>&1 &` — `dex2oat` идёт в `installd` и переживает и смерть shell, и перезапуск `adbd` при восстановлении TCP. `trap` обязателен: клиент TBox ходит в `shell:` v1 (pty), и при закрытии сессии HUP обгоняет `nohup` — без `trap` на стенде выживало 3 из 5 фоновых процессов (с ПК через shell v2 проблема не видна; воспроизводится `adb shell -x`), с `trap` 5 из 5; завершение опрашивается `dumpsys package <pkg> | grep status=` (первый раз через 150 с, далее раз в 45 с, до 8 мин). Журнал: тег `APP_DEXOPT`. На A9 (192.168.1.128, те же `pm.dexopt.*`) у debug-установок TBox, своего лаунчера и `:usbaudio` статус ещё хуже — `run-from-apk` (папки `oat/` нет совсем); у установленных через магазин `speed-profile [reason=install]`, у прошивки `quicken`.
  - **Компиляция любых программ — Список программ → «Расширенный» → «Действия» (`PackageAdbActions`).** Пункты «Компилировать (speed)», «Компилировать (speed-profile)», «Сбросить компиляцию (verify)» — та же фоновая команда с `-m <фильтр>` (полный `--reset` без root недоступен, откат — `-m verify -f`). `speed` закрыт для APK > 250 МБ (base+split): на A10 `dex2oat` у Яндекс Карт/Навигатора (300+ МБ) обрывался на ~570 с и оставлял `run-from-apk`; для них — `speed-profile`, полезный только при накопленном JIT-профиле. В строке статуса рядом с «скрыт · отключён · запущен» — фильтр из одного `dumpsys package dexopt` (базовый APK, `arm64` приоритетно, вторичные dex игнорируются; `run-from-apk` → «из APK»), размер APK и «компиляция → <фильтр>…» на время опроса (`dumpsys package <pkg> | grep status=`: через 20 с, затем раз в 30 с, до 10 мин; опрос прекращается при выходе из расширенного режима, сама компиляция продолжается). Одновременно — одна компиляция (в т.ч. из карточки настроек).
  - **Эффект `speed` на A9 (замеры 20.10.2026)** — методика, скрипты `tools/hu_perf/` и результаты в [HU_PERF_TESTS_RU.md](HU_PERF_TESTS_RU.md): у TBox Monitor CPU на старте втрое меньше, фон −30 %, первое открытие главного экрана вдвое быстрее; у Dashing Club Launcher первый кадр на ~1,2 с раньше, но постоянное ~1 ядро на отрисовку 3D-модели компиляция не убирает.
  - Шум штатных приложений при загрузке: `Long monitor contention … CarPropertyManager.registerListener` у `com.adayo.aaopbc`, `Rejecting re-init on previously-failed class` у `ru.vk.store` — к TBox не относятся.

## 4. Нагрузка CPU на A10

- **Load average (14–17) не равен загрузке CPU.** Его раздувают ~8 потоков ядра в состоянии D (непрерываемое ожидание: watchdog-тесты, десериализатор камеры и т.п.) — они процессор не тратят. Реальную загрузку считать по `/proc/stat` или дельтам `top`: **~60–70 %** от 4 ядер в покое. Про десериализатор и его спам в лог — §4.1.
- Основные потребители:
  - `surfaceflinger` (60–80 % одного ядра) — композиция;
  - **штатный лаунчер Adayo** перерисовывается ~**45 кадров/с** постоянно, независимо от TBox (проверено остановкой TBox на 10 с). Вероятные источники — бегущая строка трека в медиакарточке и ADAS-карточка. Из приложения не лечится;
  - iFlytek: постоянное распознавание ключевой фразы и анимированный аватар (`avatarService`);
  - scrcpy, пока открыт с ПК (кодек + `surfaceflinger` + `adbd`);
  - запись логов Adayo во флеш.
- TBox в покое — 3–9 % главного потока, ~2 кадра/с. Кратковременный пик >100 % был при переключении экранов.
- Методика: `dumpsys gfxinfo <pkg>` — дельта «Total frames rendered» за 10 с; `top -n 2 -d 5`; `ps -A -o PID,STAT,NAME | grep ' D '`.

### 4.1. GMSL-спам `max96735` (десериализатор камеры, A10)

Ядро шлёт в лог `bsp:max96735: error: i2c_read Line:523 ###i2c_addr:48--reg_addr=0x000e failed---ERR(-6)` каждые ~27–32 мс (60–180 строк за 15 с, интенсивность плавает). Замер (root): `dmesg -c > /dev/null 2>&1; sleep 15; dmesg | grep -c max96735`. Исследовано 16.10.2026.

- **Источник — драйвер, не пользовательский стек.** `max96735` — загружаемый модуль (`/vendor/lib/modules/max96735.ko`, `/proc/modules`), его поток ядра `[max96718f_task]` (`max96718f_logical_task_thread_func`, почти постоянно в D — один из потоков, раздувающих loadavg) опрашивает по I2C (адрес 0x48, регистр 0x000e) камеру, которой на входе нет. Остановка всего AVM/Camera-стека — `atcavm_server`, `avmhal_daemon`, `vendor.fgefastavm_hal_service`, `fgebackcar-1-0`, `cameraserver`, `camerahalserver` и `am force-stop com.adayo.app.avm` — спам не прекращает (117 → 178 → 104 строк/15 с до/после остановок). Не трогать при экспериментах: `fgesysctrl-2-0`, `vendor.fgemcucomm-1-0` (MCU), `vendor.atceinfo-1-0` (дисплей). Возврат остановленных сервисов — `start <имя>` или перезагрузка.
- **`rmmod max96735` делать нельзя.** Счётчик ссылок 0, и модуль выгружается, но через ~1 с ядро падает: `Unable to handle kernel paging request` → `Kernel panic - not syncing: Fatal exception in interrupt` (в `/proc/last_kmsg` — `[last unloaded: max96735]`, хвост стека `el1_irq → _raw_write_unlock_irq → copy_process`; оставшийся после выгрузки IRQ/work обращается к освобождённой памяти модуля). ГУ уходит в перезагрузку, модуль грузится снова. Параметров у модуля нет (`/sys/module/max96735/parameters` отсутствует). Примечание: после перезагрузки `adbd` возвращается в shell-режим — `adb root` нужно выдать заново, иначе `rmmod`/`setenforce` падают с `EPERM` от uid 2000.
- **Цена спама в CPU невелика.** Сам поток — ~0,1 % ядра (замер `/proc/<pid>/stat` за 20 с). `logd` держит открытый `/dev/kmsg` и перекачивает журнал ядра — в активной фазе это ~10–15 % одного ядра (весь объём логов, не только этот спам); в спокойном состоянии logd < 3 %, нагрузку определяют лаунчер/Яндекс/SurfaceFlinger/iFlytek. Наблюдавшийся ранее `logd` 100 % CPU — пиковое/накопленное (`TIME+` растёт часами), а не постоянное ядро. Лечится только в прошивке: rate-limit в драйвере или корректный опрос отсутствующей камеры.

## 5. Сеть

- **UDP к TBox при включённом Wi-Fi (A10).** Сеть TBox — `usb0` (`192.168.225.x`, в ConnectivityService видна как «Rndis» с низким приоритетом). Сокет `tbox-proxy` (`DatagramSocket(localPort)` в `UdpSocketManager.initialize()`) ни к какой сети не привязан, поэтому трафик идёт по правилу сети по умолчанию: `ip route get 192.168.225.1 uid <app>` → `via 192.168.1.1 dev wlan0`. С меткой сети `usb0` маршрут верный (`… mark 0x75` → `dev usb0`). netId `usb0` меняется при пересоздании интерфейса (0x74 → 0x75 за ~10 мин), его нужно определять динамически (`ip rule` или перебор с пробным `bindSocket`). Решение — `Network.bindSocket` для UDP-сокета (в библиотеке или рефлексией в приложении); `SO_BINDTODEVICE`, `bindProcessToNetwork`, `ip rule` без root не подходят. **Не реализовано по решению владельца.**
- **Время.** Если часы ГУ уходят (31 декабря после сбоя или сдвиг на сутки), падают все HTTPS (`Chain validation failed`) и авторизации. Включать `auto_time`. Пояс на A10 — см. §5.1.
- **DoT к роутеру** (`:853`, `errno=111`) — фон opportunistic Private DNS, не ошибка.

### 5.1. Часовой пояс A10 сбрасывается в `PRC`

Пояс навязывает системная служба Adayo `com.adayo.midware.settings` (`/system/app/AdayoSettingsService`, класс `DateTimeControl`):

- свой пояс она хранит в `/data/user_de/0/com.adayo.midware.settings/shared_prefs/dynamic_data.xml` → `<string name="time_zone">`, заводское значение `PRC`;
- при старте (`SettingsSvcFunc`: «mDateTimeControl setTimeZone time_zone = …») и каждый раз, когда ежеминутная сверка с часами MCU (`MSG_ID_NOTIFY_UPDATE_DATE_TIME`) находит расхождение (`updateMcuDateTime: 从MCU获取时间设置`), она вызывает `AlarmManager.setTimeZone(time_zone)` и выставляет время из MCU. В логе: `DateTimeControl: setTimeZone: PRC setmcu:false` → `AlarmManagerService: Kernel timezone updated to -480 minutes west of GMT`;
- смена пояса через системные настройки Android меняет только `persist.sys.timezone`, `time_zone` службы остаётся `PRC`, поэтому после перезагрузки или первой рассинхронизации с MCU пояс возвращается.

Исправление — штатный метод службы, который сохраняет пояс в её базу, применяет его и синхронизирует часы MCU. Binder-сервис `adayo.setting.v2.0` (`com.adayo.proxy.setting.system.aidl.ISettingsServiceInterfaceAIDL`), доступен из `adb shell` без root:

```
adb shell service call adayo.setting.v2.0 49                      # getTimeZone → "PRC"
adb shell service call adayo.setting.v2.0 15 s16 Europe/Moscow    # setTimeZone, ответ 0 = успех
```

Номера транзакций взяты из `ISettingsServiceInterfaceAIDL$Stub` в `CarSetting.apk` (`TRANSACTION_setTimeZone = 15`, `TRANSACTION_getTimeZone = 49`); перед записью сверять чтением (49). В TBox Monitor — карточка **«Часовой пояс ГУ»** в блоке «Эксперт» (только A10 + экспертный режим): читает пояс службы при открытии, список поясов РФ и Минска, кнопка «Применить» (`hutimezone/HuTimeZoneCodec`, `HuTimeZoneClient`, журнал `HU_TIME_ZONE`). Применено на A10 14.10.2026: после перезагрузки `persist.sys.timezone=Europe/Moscow`, сверка с MCU — «MCU time = System time».

### 5.2. Файрвол Adayo: у приложений нет интернета (A10)

Симптом: браузер, RuStore и другие установленные приложения не открывают ни одного сайта, TCP висит в `SYN_SENT`, а системные службы (uid < 10000) сеть видят.

Причина — iptables-цепочки Adayo:

- netd создаёт цепочки по `/system/etc/adayo/firewall/AdayoFirewallDef.json`: `OUTPUT → adayo_output → adayo_output_wifi` (`wlan+`, `eth+`, `ra+`, `tiwlan+`) и `adayo_output_mobile` (`usb+`, `tbox+`, `rmnet+`, `ppp+` …);
- `CarService.apk` → `com.android.car.network.CarNetworkService` (лог `CAR.Firewall_NET`, отдельный persistent-процесс `com.android.car`) на старте процесса читает белый список `/system/etc/adayo/firewall/AdayoFirewallAppDef.json` и вызывает `INetworkManagementService.setFirewallAllRules(66/67, …)` → netd `firewallReplaceAllChain` в режиме whitelist: `RETURN` для uid 0–9999, без владельца, `esp`, `lo`, TCP RST, в конце **`DROP`**; ресивер `ADAYO_BOOT_COMPLETED` лишь пере-включает цепочки и конфиг не перечитывает;
- на Jetour белый список пуст (`ConfigBean{APPs=[], Hosts=[]}`), поэтому режется трафик всех приложений, включая TBox;
- флаги `isFirewallEnabled: false` (`dumpsys car_service`), `ALL Firewall enabled: false` (`dumpsys network_management`), `persist.adayo.firewall.enable=0` на содержимое цепочек не влияют; `ndc firewall all_firewall_disable` правила не снимает.

Почему раньше интернет был: цепочки заполняются асинхронно при загрузке и, по-видимому, не всегда успевают или могут не заполниться (гонка с netd); после перезагрузки 14.10.2026 01:28 `DROP` стоял стабильно.

Исправление (root, до перезагрузки ГУ):

```
su 0 iptables -w -I adayo_output_wifi 1 -m owner --uid-owner 10000-99999 -j RETURN
su 0 iptables -w -I adayo_output_mobile 1 -m owner --uid-owner 10000-99999 -j RETURN
```

IPv6-цепочек Adayo нет. Проверено на A10 14.10.2026: под uid Яндекс Браузера (10047) соединение с `ya.ru:80/443` и ping проходят. Штатный `ICarNetwork.setFirewallUidRule` (без проверки прав в CarService) — альтернатива без root, не используется.

В TBox Monitor — карточка **«Интернет для приложений»** в блоке «Эксперт» (только A10 + экспертный режим): кнопка добавляет правило (идемпотентно, `iptables -C` перед `-I`), переключатель «Применять автоматически» (по умолчанию выкл.) держит правило с перепроверкой через 30 с, 1, 2, 5 мин, затем раз в 30 мин, потому что CarService пересоздаёт цепочки по `ADAYO_BOOT_COMPLETED` (`hufirewall/HuAppNetworkCodec`, `HuAppNetworkClient`, журнал `HU_APP_NETWORK`).

Формат белого списка (декомпиляция `CarNetworkService` + проверка на ГУ 17.10.2026): файл парсится Gson'ом (`ConfigBean{APPs: List<PackageInfo>}`, gson-lenient терпит штатный `/** */`-комментарий), элементы — объекты `{"packageName": "<пакет>"}`; uid каждого пакета разрешается через `getApplicationInfo(pkg).uid`, неизвестный пакет молча пропускается — **wildcard «all» нет**; `Hosts` этой прошивкой вообще не применяется (в коде применения захардкожен пустой список). Проверено bind-mount'ем поверх стокового файла: одна запись `{"packageName": "vad.dashing.tbox"}` → `uids: [UidInfo{uid=10040, rule=1}]` и `-m owner --uid-owner 10040 -j RETURN` в обеих цепочках, построенное самим netd. Стоковый файл — 206 байт, CRLF.

Постоянное исправление — приложение **«Файрвол ГУ»** (`:fwunlock`, `vad.dashing.fwunlock`, v0.1.0), one-shot по образцу «Расширение /data»/«USB-аудио ГУ»: пишет в `/system/etc/adayo/firewall/AdayoFirewallAppDef.json` белый список всех сторонних пакетов на момент запуска (OEM-записи, если появятся, сохраняются; слияние идемпотентно; недопустимые имена отвергаются), через localhost ADB + su и eMMC WP-танец (`emmc_disable_all_wp > /proc/mmc_debug`, возврат защиты из EXIT-ловушки), с md5-сверкой и рестартом `com.android.car` для применения без перезагрузки (~15 с). Запись атомарная (`.new` + md5 + `mv`, проверка места), на любом выходе `remount,ro /` и возврат WP. Перед первой записью оригинал (если это не наш сгенерированный файл) сохраняется в `/data/local/tmp/fwunlock/AdayoFirewallAppDef.orig.json`; «Вернуть штатный файрвол» пишет его, а при отсутствии резерва — байт-точный сток. Гибридная схема: рантайм-фикс TBox Monitor остаётся страховкой для приложений, установленных позже, — список uid-правил строится только на старте CarService, uid при этом разрешается в момент применения.

## 6. Разметка eMMC на A10 и нехватка места под приложения

eMMC 64 ГБ (124 993 536 секторов), разметка прошита под 32 ГБ:

| # | Раздел | Размер | Примечание |
|---|---|---|---|
| p1–p27 | `preloader`, `hsm`, `lk`, `trustzone`, `viss`, `kernel`, `dtb`, `dtbo`, `recovery`, `vdec`, `vendor` (512 МиБ), `system` (4 ГиБ), `product`, `logo` — по два слота `_a`/`_b` | ~9,7 ГиБ | между `product_a` и `logo_a` дыра 31 МиБ |
| p28–p33 | `oem`, `ProjectConfig`, `UserConfig`, `FactoryConfig`, `diagnosticzone`, `expdb` | ~190 МиБ | |
| p34 | `cache` | 4,5 ГиБ | занято 1,7 ГБ, из них **1,8 ГБ — `/cache/fota` 2021 г.** (`update_os.zip`, `RES.zip`, `HMA214A_MCU.bin`) |
| p35–p38 | `avm`, `misc`, `metazone`, `ramdisk` | ~104 МиБ | |
| **p39** | **`userdata`** | **2 ГиБ** | `/data`, ext4, без шифрования (`ro.crypto.state=unsupported`); на стенде перенесён в хвост и расширен до 30,8 ГиБ (§6.7) |
| p40 | `media` | 4 ГиБ | почти пустой |
| p41 | `map` | 16 МиБ | |
| p42 | `intsd` | 7,5 ГиБ | внутренняя «SD» (`/mnt/media_rw/internal_sdcard`) |
| p43 | `miscbk` | 791 МиБ | доходит до конца 32-гигабайтной разметки |
| — | **свободно** | **~31,5 ГиБ** | хвост диска, не размечен |

GPT:

- основной заголовок CRC-корректен, `AlternateLBA` и диапазон usable LBA уже охватывают весь диск 64 ГБ;
- Disk GUID — вендорная метка `AUTO CHIPS…`;
- 43 записи **по 256 байт** с LBA 2 (стандарт допускает, AOSP `sgdisk` — нет); байты 128–255 записи — метаданные прошивальщика Autochips (имена образов). Таблица вмещает до ~100 записей до первого usable LBA 52.

fstab:

- `/data` — `ext4 …,autoformat … wait,check,quota,reservedsize=128M,formattable`;
- `cache` и `intsd` — тоже `formattable`/`autoformat`;
- boot-хуков в init, исполняющих скрипты с записываемых разделов, нет.

Варианты увеличения `/data` (на стенде выполнен перенос с сохранением данных из Android — §6.7):

1. **Перенос с сохранением данных.** Отформатировать ext4 в хвосте (loop-устройство со смещением на `mmcblk0`), при остановленном фреймворке (`stop`) скопировать `/data` (`cp -a`, затем `restorecon`; `dalvik-cache` можно не копировать), поправить first/last LBA записи 39 в **основной и резервной** таблицах, пересчитать CRC массива записей и CRC обоих заголовков, перезагрузиться. Старая область остаётся для отката.
2. **Чистый `/data`.** Только перенаправить запись 39 в хвост — `fs_mgr` с `autoformat` сам создаст пустую ФС. Проще и безопаснее по GPT, но это сброс приложений и данных.

Риск обоих — ошибка в GPT (загрузчик ищет по ней `lk`, `kernel`, `dtb`, `misc`, `metazone`). Перед любой записью: полный дамп обеих копий GPT (LBA 0–33 и последние 33 сектора) и данных на ПК. Встраивать в TBox Monitor — только в экспертном режиме и только после ручной отработки на стенде; сам TBox при остановке фреймворка тоже останавливается, поэтому сценарий должен идти отдельным root-процессом (`setsid su 0 sh …`).

#### 6.1. Что можно восстановить без программатора (проверено по образам A10, 14.10.2026)

Цепочка загрузки и где в ней GPT:

| Звено | Где лежит | Зависимость от GPT | UART |
|---|---|---|---|
| preloader | аппаратные boot-разделы eMMC `mmcblk0boot0/1` (заголовок `PRELOADER!!`), **вне** основной области | ищет `lk`/`metazone` по GPT: сначала основная, при ошибке CRC — **резервная** («Parsing Primary GPT… Parsing Secondary GPT…») | только лог |
| LK (`lk_a/_b`, аппаратная защита от записи) | основная область | сверяет обе копии («pgpt is consistent with sgpt»); если основная битая, а резервная цела — **сам переписывает основную из резервной** | лог; встроенная консоль LK (`emmc l/w`, `mmc_test`, `md`, fastboot) есть в коде, но штатного способа войти в неё с UART не найдено — не рассчитывать |
| ядро + init Android | `kernel`, `system` (`root=PARTUUID=…`) | по GPT | **консоль `console:/ $` стартует по `ro.debuggable=1` сразу при запуске init, до монтирования `/data`**; `su` есть в `/system/xbin` |
| recovery (`recovery_a/_b`) | основная область | по GPT | консоль **сразу от root** (`user root` в `init.recovery.ac8x.rc`); в ramdisk есть `toybox` (`dd`, `md5sum`), `sgdisk`, `mke2fs_static`, `blkid`; `/cache` монтируется |

Прочее:

- LK при каждой загрузке ищет на USB-флешке `atc.upd` и при наличии шьёт разделы и GPT по `scatter.mmcboot.ext4.xml` («LK upg»). Пакета в этом формате нет.
- В `/cache/fota/update_os.zip` (1,46 ГБ, даты файлов ложные) — **полный A/B OTA текущей сборки** (`post-build=…Jenkins09051534`, без `pre-build`): `preloader`, `lk`, `hsm`, `trustzone`, `viss`, `kernel`, `dtb`, `dtbo`, `recovery`, `vdec`, `vendor`, `system`, `product`, `logo`. Распаковывается `payload-dumper` на ПК. Перед любыми работами с разметкой — скопировать на ПК. GPT, `metazone`, `*Config`, `misc` в нём нет — их брать из собственного дампа.

- `D:\Dashing\Android10-VHAL\QR_8015_T1K_MY1_IHU_ADAYO_V00.07.12.iso` (2,3 ГБ) — **не ISO**, а дилерский USB-пакет Adayo (заголовок `HMA214A`, Windows его не монтирует): `HMA214A_MCU.bin`, `RES.zip`, полный A/B OTA **новее установленного** (`post-build=…Jenkins11171609`, 17.11.2025, против `Jenkins09051534` от 05.09.2024 на ГУ). Ставится через штатное приложение обновления в работающем Android (`update_engine`), GPT и `userdata` не трогает. GPT, scatter и `atc.upd` в нём нет, поэтому от «кирпича» по GPT он не спасает — это второй источник образов загрузочных разделов. Обновление прошивки и переразметку не совмещать: сначала одно, проверка, потом другое; после обновления заново проверить поведение нового LK с GPT.

Вывод: при ошибке **только в записи `userdata`** (или если не смонтировался `/data`) ГУ доходит до init, и через UART есть root-консоль (Android или recovery) для отката GPT из заранее сохранённой копии. Если испорчена одна копия GPT — preloader и LK переживут это сами по второй. Без программатора **не восстанавливается** только случай, когда обе копии GPT корректны по CRC, но ломают записи `lk`/`kernel`/`misc`/`metazone`, или обе копии битые: тогда остаётся только USB-режим BROM с прошивальщиком Autochips и DA (у нас их нет). Поэтому меняются только поля start/end записи 39, остальные 42 записи сверяются побайтно.

#### 6.2. Рекомендуемая процедура (вариант 1 через recovery)

1. В Android по ADB (без записи на eMMC): дамп обеих GPT и копия `update_os.zip` на ПК; на ПК скриптом собрать новые основную и резервную GPT (меняется только запись 39: начало — первый выровненный сектор хвоста, конец — последний usable LBA), проверить CRC заголовков и массива, отсутствие пересечений, побайтное совпадение остальных записей. Положить в `/cache/gptfix/`: новые и старые GPT, `move.sh`, `rollback.sh` с контрольными суммами.
2. `adb reboot recovery` (работало на `Jenkins09051534`; на `Jenkins03261716` LK эту команду игнорирует, а recovery живёт не больше минуты — §2.2), дальше по UART в root-консоли recovery: `/data` там не смонтирован, поэтому копия консистентна. `move.sh`: `dd` старого `userdata` (2 ГиБ) в хвост, сверка `md5sum`, запись обеих GPT, чтение обратно и сверка, `sync`, перезагрузка.
3. В Android: `/data` — та же ФС в новом месте; онлайн-расширение `resize2fs /dev/block/userdata` (от root). Старая область p39 не трогается — откат `rollback.sh` (старые GPT) из консоли UART Android или recovery.

#### 6.3. Цель: инструкция без UART (после отработки на стенде)

Recovery без UART не управляется (сети там нет, скриптов из `/cache` он сам не запускает), поэтому вариант для пользователей без UART — тот же перенос, но **в Android**, отдельным root-процессом (`setsid su 0 sh`). Основная GPT в Android защищена от записи (§6.6) — перед записью GPT защиту снимать штатным механизмом и после проверки возвращать:

- предпроверка: та же сборка и **побайтно та же GPT**, что на стенде (43 записи, имена, смещения), иначе отказ; достаточно места; питание;
- `stop` фреймворка (дальше скрипт работает сам), `fsfreeze -f /data` и сброс кэшей (`remount,ro` не проходит, §6.7); без успешной заморозки — отказ и `start`;
- `dd` в хвост, сверка `md5sum`, только потом запись GPT: **сначала резервная, последней основная**, чтение обратно; при расхождении — возврат старых GPT до перезагрузки;
- перезагрузка, затем `resize2fs` онлайн.

На стенде с UART (только наблюдение) проверить до выпуска инструкции: что делает LK, когда обе копии GPT корректны, но различаются (ожидается «Update sgpt from pgpt» — тогда обрыв питания между записями безопасен); что `remount,ro` проходит; что при неудачном монтировании нового `/data` срабатывает `autoformat`, а не цикл перезагрузок. Остаточный риск для пользователя без UART — непредвиденный сбой, после которого ГУ загружается без своего `/data` (без Wi-Fi-сети и ADB): старые данные целы, но откат потребует UART или дилера.

Решение владельца: отработка и проверки — с UART (только наблюдение и страховка), **в пользовательской инструкции по переразметке UART не упоминается**.

#### 6.4. План: инструкция по восстановлению ГУ через UART

Что восстанавливается через UART, по уровню поломки:

| Уровень | Признак в UART | Чем восстанавливать |
|---|---|---|
| Android падает или зациклен после старта init (битый `/data`, ошибка в записи `userdata`, сломанное приложение/настройка) | лог ядра идёт, есть `console:/ $` | `su` в консоли: откат GPT из копии, `dd` разделов, правка файлов; сеть — через `WifiConfigStore.xml` (§2.1), затем ADB |
| Не грузится Android текущего слота (`system`/`vendor`/`kernel`) | LK стартует, ядро падает или не доходит до консоли | LK после неудачных попыток сам уходит в recovery («slot invalid, set recovery mode») или во второй слот; в recovery консоль сразу root — `dd` образов из полного OTA (`update_os.zip` / ISO V00.07.12, распаковка `payload-dumper`) с USB-флешки (recovery монтирует `/udisk1`, vfat). На `Jenkins03261716` recovery сам перезагружается, а с `--sideload` его сбрасывает микроконтроллер через 61 с (§2.2): надёжнее fastboot LK |
| Испорчена одна копия GPT | лог preloader/LK | ничего: preloader читает резервную, LK сам переписывает основную |
| Испорчены обе копии GPT или записи `misc`/`metazone`, но `lk` цел | лог доходит до LK | fastboot LK по USB (§2.2), если `boot-fastboot` успели записать в `misc`; запись через fastboot ещё не проверена |
| Повреждён `lk` или его запись в GPT | лог обрывается в preloader | ни UART, ни USB не помогают; дальше только BROM + прошивальщик Autochips |

Передача по UART — до ~60 КБ/с (base64), годится для GPT и мелких файлов; образы на сотни мегабайт — только с флешки или по ADB.

Подготовка (сделать заранее, пока ГУ исправно):

1. Полные дампы на ПК: обе копии GPT, `preloader` (`mmcblk0boot0/1`), `lk_a/_b`, `metazone`, `misc`, `miscbk`, `*Config`, `oem`, `expdb`, `diagnosticzone`, `recovery_a/_b`; `update_os.zip` и образы из него. **Сделано 14.10.2026** (§6.6).
2. Копия GPT и скрипт отката в `/cache/gptfix/` (доступен и из Android, и из recovery) и на USB-флешке.
3. **Резервная копия GPT в фиксированном месте eMMC** вне используемых разделов (неразмеченный хвост за новым `userdata` или дыра 31 МиБ между `product_a` и `logo_a`) — для возможного восстановления командами LK `emmc l/w`, без Android.

Исследование на стенде (с UART), чтобы закрыть нижнюю строку таблицы:

- найти вход в консоль LK («Start console mode», «Check cli mode, stop before jump to kernel»): клавиши по UART при старте, флаг в `metazone`/`misc` (`boot-fastboot`, …); по коду LK (дизассемблирование `lk_b` около этих строк) определить условие;
- если вход есть — проверить `emmc l` (чтение в ОЗУ) и `md` на безопасных адресах, затем сценарий «GPT из резервной копии на eMMC → LBA 0»;
- fastboot LK: вход через `boot-fastboot` в `misc` и связь с ПК по USB проверены 20.10.2026 (§2.2). Осталось проверить `flash` на неактивном слоте с тем же образом и выяснить, меняет ли он метаданные слотов;
- формат `atc.upd` (USB-прошивка из LK по scatter): если удастся собрать пакет из образов ISO/OTA и дампов — это путь восстановления даже при битой GPT, пока жив `lk`.

Итоговая инструкция — отдельный документ: подключение UART (§2.1), определение уровня поломки по логу, пошаговые команды для каждого уровня, проверка результата.

#### 6.5. Форма пользовательских инструкций

Обе инструкции — «запустить готовый файл», без ручного ввода команд.

**Переразметка**, два равноценных способа, один и тот же скрипт внутри:

- `tbox_data_resize.txt` для вкладки ADB TBox Monitor (выполнение команд из txt, `AdbShellScriptParser`). Файл самодостаточный: скрипт вшит строками `echo <base64> >> …` (не длиннее ~3,5 КБ — исходящий пакет localhost ADB ограничен `AdbProtocol.MAX_PAYLOAD = 4096`, всего не больше 200 строк), копируется в `/cache/tbox_resize/`, затем запускается проверка (отчёт в логе вкладки) и отсоединённый `setsid su 0 sh …`. TBox гаснет вместе с фреймворком — это ожидаемо; ГУ перезагружается само. Повторный запуск того же txt после загрузки: скрипт по состоянию разметки понимает этап, делает `resize2fs` и пишет итог. Журнал — в `/cache/tbox_resize/log` (переживает перезагрузку и не зависит от `/data`).
- `resize.cmd` для ПК (`adb.exe` рядом): то же самое, плюс ожидание переподключения ADB после перезагрузки и автоматический второй этап.
- Новые GPT вычисляются **на ГУ** из текущих (меняются только начало и конец записи 39 и CRC), поэтому подходят для любого ГУ с такой же разметкой; GUID у разных экземпляров могут отличаться. Проверить на стенде наличие нужного в `toybox` (`base64`, CRC32 через `gzip`/`cksum`, `dd conv=notrunc`).

**Восстановление через UART:** каталог [`tools/hu_rescue/`](../tools/hu_rescue/) (написан 20.10.2026, подробное руководство — [`tools/hu_rescue/ИНСТРУКЦИЯ.md`](../tools/hu_rescue/ИНСТРУКЦИЯ.md)). Работает в Windows PowerShell 5.1 через встроенный `System.IO.Ports`, ставить ничего не нужно, логика GPT на встроенном C#. В каталоге лежат `Запуск.cmd` и `Проверка без записи.cmd` (`start.ps1` сам находит COM-порт адаптера CP210x/CH340/CH9102/FT232/PL2303), эталонные дампы GPT (`gpt_2026-10-20\` и `gpt_2026-10-14\`), контрольные суммы разделов (`MD5SUMS.txt`, `partitions_2026-10-20\MD5SUMS.txt`) и `platform-tools\` (`adb.exe`, `fastboot.exe`).

Запуск: двойной клик по `tools\hu_rescue\Запуск.cmd` (или `powershell -ExecutionPolicy Bypass -File tools\hu_rescue\hu_rescue.ps1 -Port COM21`). Параметры `hu_rescue.ps1`: `-DumpDir` (по умолчанию `tools\hu_rescue\gpt_2026-10-20`), `-LogDir` (по умолчанию `tools\hu_rescue\logs`), `-DryRun` (всё, кроме записи на eMMC). В лог пишется весь обмен по UART, и при каждом чтении туда же сохраняются обе копии GPT.

Как скрипт работает с консолью (§2.1):

- до получения root вводит команды порциями по 8 байт;
- после `su` приглушает `printk` и переходит на полную скорость, при выходе возвращает прежнее значение;
- оборачивает команды маркерами, вырезает лог ядра;
- читает данные с ГУ через `base64` (в recovery без `base64` — через `od`) и сверяет MD5 каждого блока, при расхождении повторяет.

Пункты меню:

1. **Следить за загрузкой.** По логу показывает этапы preloader → LK → ядро → консоль и важные строки: разбор GPT, `GPT_LK`, слот, `atc.upd`, panic, перезагрузку. Если лог остановился на каком-то этапе, подсказывает, что это значит по таблице §6.4.
2. **Диагностика.** Режим (Android или recovery), слот, прошивка, `userdata` в ядре, `df`, Wi-Fi и ADB, состояние программы «Расширение /data». Обе копии GPT: целы ли, совпадают ли, где `userdata`, чем отличаются от дампа.
3. **Включить ADB по Wi-Fi** (`persist.adb.tcp.port 5555`, перезапуск `adbd`) и показать адрес.
4. **Добавить сеть Wi-Fi** в `WifiConfigStore.xml` по образцу уже сохранённой сети, затем `stop; start` (порядок из §2.1). Прежний файл сохраняется в `/cache/hu_rescue` и на ПК.
5. **Перенести запись `userdata`.** Откат переноса или возврат в хвост, места берутся из `/cache/dataresize/params` или `/cache/gptfix`. Таблица строится из текущей GPT, как в программе: меняются только LBA записи и CRC.
6. **Восстановить одну копию GPT по другой.** Если одна копия битая, восстанавливается по целой. Если обе целы, но различаются, пользователь выбирает, какую оставить. Защитный MBR сохраняется. На таблицах стенда пересборка побайтно совпадает с оригиналом.
7. **Записать GPT из дампа** (`gpt_head_1MiB.img` / `gpt_tail_1MiB.img`). Перед записью скрипт показывает, чем дамп отличается от текущей таблицы.
8. **Записать раздел из файла на USB-флешке.** Скрипт ищет файл в `/udisk*`, `/mnt/media_rw/*`, `/storage/*`, сверяет его MD5 с тем же файлом на ПК, пишет со снятой защитой eMMC и проверяет чтением обратно. Раздел ищется по `PARTNAME` в `/sys/block/mmcblk0/*/uevent`, узел — `/dev/block/<DEVNAME>`. `preloader` и `userdata` так не пишутся.
9. **Перезагрузка** (обычная или в fastboot), после неё сразу слежение за загрузкой. Для fastboot в `misc` пишется `boot-fastboot` (первые 2048 байт, со снятой защитой eMMC, проверка чтением), слежение показывает этап «fastboot LK». Recovery из меню убран: на `Jenkins03261716` он недоступен как консоль (§2.2).
- **W. Включить Wi-Fi на ГУ** (`svc wifi enable` с ожиданием IP на `wlan0`).
- **A. Активировать ADB на ГУ** (`adb_enabled=1`, порт `5555`, запуск/рестарт `adbd` и автоматический `adb connect` с ПК при наличии IP).
- **U. ADB по USB.** Порт ГУ для CarPlay переводится в режим устройства (`data_role=device`, при пустом UDC он привязывается заново), затем скрипт ждёт `0x123456789EF` в `adb devices` на ПК. Обратно в режим хоста — тот же пункт. Только в Android.

Кроме того, есть ручной терминал: лог ядра скрыт, F9 его показывает, F10 — выход.

Запись GPT (пункты 5–7) идёт так же, как в программе «Расширение /data»:

1. Текущие копии снимаются в `/cache/hu_rescue`.
2. Новые копии собираются там же из текущих плюс изменённые байты (`dd` и `printf`) и сверяются по MD5.
3. Снимается защита eMMC.
4. Пишется резервная копия, затем основная, каждая с чтением обратно после сброса кэша. Если запись не удалась, возвращаются прежние копии.
5. Защита eMMC возвращается в `finally`.

Проверено на стенде 20.10.2026 через `COM21`, в рабочем Android: диагностика и пункт 5 с `-DryRun`. Таблицы, собранные на ГУ, совпали побайтно с таблицами программы. Там же 20.10.2026 вечером проверены пункт U (включение и возврат) и пункт 9 с fastboot: ГУ в `fastboot devices` через 6 с, `fastboot reboot` возвращает обычную загрузку, `misc` чист. Пункты 4, 6–8 с реальной записью и слежение за загрузкой на живом ГУ пока не прогонялись; фильтры слежения проверены по `uart_logs\gptfix_move3.log`. Консоль LK по-прежнему не найдена.

Дилерская перепрошивка через LK (`atc.upd` + scatter) перестраивает GPT по scatter и вернёт разметку 32 ГБ с потерей `/data`. Обычные OTA (`update_os.zip`, ISO V00.07.12) разметку не трогают.

Безопасное освобождение места без переразметки: удалить `/cache/fota` (1,8 ГБ, раздел `cache`, на `/data` не влияет).

#### 6.6. Защита eMMC от записи и доступ к хвосту диска (проверено на A10, 14.10.2026)

Дампы (обе GPT, `mmcblk0boot0/1`, все разделы кроме `userdata`/`cache`/`intsd`/`media`, файловая копия `/data`, `update_os.zip`, MD5 сверены с ГУ) — `D:\Dashing\Android10-VHAL\emmc_dump_2026-10-14`; лог загрузки по UART — `D:\Dashing\Android10-VHAL\uart_logs\`.

**Разметка по дампу.** Обе GPT корректны и совпадают, 43 записи, usable LBA 52…124 993 484, хвост за `miscbk` — 30,8 ГиБ свободен. Запись `preloader` начинается с LBA 0 (накрывает саму GPT) — при правке таблицы её не трогать. Активный слот `_b`. `sgdisk` на ГУ не понимает короткие ключи (`-p`, `-i`). Встроенный `dd` (toybox) не знает `conv`, `iflag`, `oflag`.

**Два уровня защиты:**

| Уровень | Кто ставит | Что закрыто |
|---|---|---|
| Аппаратный (группы eMMC по 8 МиБ = 16 384 сектора, CMD28) | LK при каждой загрузке («the wp region is from 0 to 980000», «from 9c0000 to 1330000») | LBA 0…9 961 471 (GPT, `preloader` … `product_a` и дыра до `logo_a`) и 10 223 616…20 119 551 (`hsm_b` … `product_b`) |
| Программный (драйвер `SD4HC` в ядре, «sw write protect violation») | ядро по списку разделов | `preloader` (LBA 0–0x400, вместе с основной GPT), все разделы обоих слотов, `oem` |

`EXT_CSD[171]` (USER_WP) = 0: режимы power-on и permanent не включены, группы защищены временной защитой, которая снимается командой CMD29. Резервная GPT, `misc`, `metazone`, `userdata`, `miscbk` и хвост диска не защищены.

Проверка (только неразмеченные сектора, с возвратом нулей): маркер, записанный в LBA 9 900 000, не записался — в `dmesg` «write fail while try to write write-protection area», eMMC переинициализируется. Маркер в LBA 60 500 000 (хвост) записался. **`dd` и `blockdev --flushbufs` в обоих случаях возвращают 0** — результат записи на eMMC проверять только чтением обратно после `echo 3 > /proc/sys/vm/drop_caches`.

Статус защиты без изменений: `echo emmc_check_wp > /proc/mmc_debug` (от root), ответ в `dmesg` — аппаратный статус по разделам (`ALL protected` / `NOT protected`) и программные регионы. `preloader` в аппаратном статусе показан `NOT protected`, хотя LK объявляет регион с LBA 0 — запись в GPT в любом случае блокирует программная защита.

**Штатные способы снять защиту** (найдены по строкам, **не выполнялись**):

- `/proc/mmc_debug` (`root:root 0660`): команды `emmc_hw_wp`, `emmc_sw_wp`, `emmc_disable_all_wp`, `emmc_enable_all_wp` (разбор `%64s %10s`). В справке файла не перечислены.
- `/dev/misc-sd` (`root:root 0600`, `misc_sd_device`), ioctl драйвера: `mmc_clear_wp_and_save` («save hw wp info and disable hw wp»), `mmc_restore_wp`. Им пользуются:
  - `adbd` — `adb remount` вызывает `set_emmc_wp` («Disable eMMC write protect»), т.е. на этой userdebug-сборке `adb root` + `adb remount` штатно снимают защиту для перемонтирования `/system` и `/vendor` на запись;
  - `update_engine` — `emmc_wp_ctrl` (sw, hw, sw+hw) и собственное чтение GPT `atc_read_gpt`;
  - `bootctrl.ac8x.so` — ioctl `boot_part`.
- После перезагрузки LK снова включает аппаратную защиту; записанное за это время остаётся.

**Проверено (без записи на eMMC):** `echo emmc_disable_all_wp > /proc/mmc_debug`, затем `echo emmc_enable_all_wp > /proc/mmc_debug` (от root).

| Состояние | Аппаратная (разделов ALL / NOT) | Программная (разделов) |
|---|---|---|
| исходно | 24 / 8 | 26 |
| после `emmc_disable_all_wp` | 0 / 32 | 0 |
| после `emmc_enable_all_wp` | 24 / 8 | 26 — побайтно как исходно |

Драйвер при снятии читает таблицу групп (7629 групп по 0x4000 секторов; защищены wpg0–607 и wpg624–1227 — группа 0 с GPT тоже, отметка `NOT protected` у `preloader` в `emmc_check_wp` — особенность вывода), сохраняет её, снимает аппаратную защиту и выключает программную («set sw wp disable done»). При возврате восстанавливает сохранённое («restore hw wp done», «set sw wp enable done»). Если возврат не выполнен, защиту включит LK при следующей загрузке.

**Запись при снятой защите проверена:** после `emmc_disable_all_wp` маркер в LBA 9 900 000 (ранее отклонялся) записывается и читается обратно; основная GPT (LBA 0–51) перезаписана теми же байтами — `dd` без ошибок, MD5 после сброса кэша совпадает, в `dmesg` нет «write fail» и переинициализаций eMMC. Затем `emmc_enable_all_wp` — состояние защиты как исходное. Скрипт записи должен вызывать `emmc_enable_all_wp` в любом исходе (`trap … EXIT`).

Журналы `update_engine` на ГУ содержат только запуски — обновления из Android на этом экземпляре не применялись.

**Ранний запуск своих скриптов (для тома под приложения):** записываемых мест, откуда `init` читает `.rc`, нет. Импортируются только `/init.*.rc`, `/system/etc/init`, `/vendor/etc/init`; `/odm` — ссылки на `/vendor/odm`; в `/oem/etc` только пустые `fs_config_*`; скрипты, вызываемые из `.rc`, лежат в `/system` и `/vendor` и ничего не исполняют с `/data`, `/cache`, `/oem`.

**Следствия:**

1. **Переразметка из Android (§6.3) возможна только со снятием защиты.** Без него новая таблица попадёт лишь в резервную GPT, а LK при расхождении, вероятно, восстановит её по основной. Порядок: `emmc_disable_all_wp`, записать GPT (резервная, затем основная), проверить чтением после сброса кэша, `emmc_enable_all_wp`. Проверено полностью, включая загрузку LK с изменённой таблицей (§6.7).
2. **Том под приложения в хвосте диска.** Без раннего хука `/data/app` переносить нельзя: менеджер пакетов сканирует его до любого пользовательского скрипта и при отсутствии тома удаляет приложения. Хук возможен только правкой `/system` или `/vendor` после `adb remount` (снятие защиты): `.rc` с монтированием loop-устройства со смещением на хвост `mmcblk0` до `post-fs-data`. Это изменение системного раздела — OTA его затрёт, а ошибка в `.rc` даёт незагружаемый слот (восстановление — второй слот или UART).
3. Расширение `/sdcard` за счёт хвоста владельцу не нужно.

#### 6.7. Перенос `userdata` в хвост eMMC (стенд A10, 14.10.2026, выполнен)

Файлы — `D:\Dashing\Android10-VHAL\gptfix\` и копия на ГУ в `/cache/gptfix/` (root, 0700):

- `gptgen.py` — на ПК из дампа строит `new_pgpt.bin` (LBA 0–51) и `new_sgpt.bin` (LBA 124 993 485–124 993 535), сохраняет `old_*.bin`; меняются только first/last LBA записи 39 и CRC (15 байт в каждой копии), проверяет CRC, пересечения и неизменность остальных байтов. Итог — `gpt_layout.txt`.
- Новый `userdata`: LBA 60 407 808 … 124 993 484 (30,8 ГиБ; начало выровнено на группу защиты 8 МиБ, сразу за `miscbk`). Копирование: `dd bs=1M seek=29496 count=2048`.
- `gptfix.sh check|move|stage2|rollback` (от root):
  - `check` — только чтение: прошивка, размер eMMC, MD5 файлов, текущие GPT = старые или новые, `/data` не зашифрован, наличие `resize2fs`/`e2fsck`/`/proc/mmc_debug`;
  - `move` (отсоединённо, `setsid`) — `stop`, `toybox fsfreeze -f /data` (иначе отказ и `start`), сброс кэшей, копия 2 ГиБ в хвост со сверкой MD5, снятие защиты, запись резервной и затем основной GPT с проверкой чтением (при ошибке — возврат старых GPT), возврат защиты, `fsfreeze -u`, `reboot`. Разморозка и возврат защиты — в `trap … EXIT`;
  - `stage2` — после загрузки: ядро видит новый `userdata` → `resize2fs` онлайн;
  - `rollback` — старые GPT обратно и `reboot`; старая область `userdata` не трогается, но изменения `/data` после переноса теряются.
- Журнал — `/cache/gptfix/log`; лог UART трёх попыток — `D:\Dashing\Android10-VHAL\uart_logs\gptfix_move*.log`.

**Что выяснилось при выполнении:**

1. `mount -o remount,ro /data` после `stop` **не проходит** (`/data` занят оставшимися процессами) — скрипт отказался и поднял фреймворк. Заменено на `toybox fsfreeze -f /data`: ФС сбрасывает журнал и блокирует новые записи, процессы просто ждут; Wi-Fi и ADB при этом живы до `reboot`.
2. С `fsfreeze` первая копия **не совпала по MD5**: в page cache блочного устройства `/dev/block/userdata` оставались устаревшие страницы (ext4 пишет данные файлов мимо кэша bdev, а чтение `dd` идёт через него). Перед копированием обязательно `blockdev --flushbufs /dev/block/userdata` (и `mmcblk0`) и `echo 3 > /proc/sys/vm/drop_caches`; то же перед каждым чтением для сверки. После этого копия совпадает (диагностика — 8 МиБ блоками, затем целиком).
3. Третий запуск: копия 2 ГиБ — 25 с (86 МБ/с), MD5 совпал, обе GPT записаны и проверены, защита возвращена, весь `move` — 1,5 мин от `stop` до `reboot`.
4. **Загрузка с новой таблицей:** preloader («Parsing Primary GPT… Success») и LK («pgpt is valid», «sgpt is valid», «pgpt is consistent with sgpt») принимают её без замечаний, LK как обычно ставит аппаратную защиту. В дампе разделов preloader `userdata` с новым началом (`start_addr=0x733800000`), но размером `0x80000000` — его парсер, видимо, не показывает размеры больше 2 ГиБ (у `system` 4 ГиБ при этом верно); на загрузку не влияет. Ядро видит p39 = 60 407 808 / 64 585 677 секторов, `/data` смонтирован из нового места (2 ГиБ, данные и приложения на месте).
5. `stage2`: `resize2fs` 1.43.3 онлайн — меньше секунды, 8 073 209 блоков по 4 КБ; `df /data` — **30G, свободно 29G**. Контрольная перезагрузка проходит без ошибок, `/data` 30G, TBox Monitor стартует сам.

Старая область p39 (LBA 30 445 568…, 2 ГиБ) не тронута, но после работы на новом месте откат `rollback` вернёт `/data` на момент переноса.

#### 6.8. Программа «Расширение /data» (`:dataresize`, проверено на стенде A10, 14.10.2026)

Отдельный APK `vad.dashing.dataresize` делает то же, что §6.7, с ходом процесса на экране: слева шаги (проверка, перенос, расширение или откат) с временем и подробностями, справа сводка «сейчас / после», кнопки и журнал.

- Root — через localhost ADB (`127.0.0.1:5555`, общий модуль `:adbcore`, тот же код, что в TBox Monitor) и `su 0`; если ADB по сети выключен, программа включает его теми же `setprop`, что TBox.
- Проверка только читает: инструменты, `/proc/mmc_debug`, обе GPT (CRC, совпадение копий), `/data` не зашифрован. Новые GPT программа строит сама (`GptPlanner`, порт `gptgen.py`; для разметки стенда побайтно совпадает с проверенными таблицами — unit-тест), так что подходит для любого ГУ с тем же устройством разметки, без привязки к прошивке.
- Перед запуском программа кладёт в `/cache/dataresize/` скрипт `dataresize.sh` (`status|move|stage2|rollback`, как `gptfix.sh`), параметры и 4 копии GPT, сверяет их MD5 и запускает `move` отсоединённо. Экран обновляется, пока Android не остановлен; затем ~2 мин экран не меняется, ГУ перезагружается.
- После загрузки программа открывается сама (`BOOT_COMPLETED`), показывает журнал переноса и сразу расширяет ФС. Итог: `/data` 30,2 ГиБ, свободно 29,1 ГиБ. Кнопка «Откатить» возвращает старые GPT (проверено: загрузка со старым `/data`, затем повторный перенос программой).
- Что было сделано, программа узнаёт из `/cache/dataresize/state` (`moved`, `done`, `rolledback`), а не из своих настроек: настройки лежат на `/data`, и откат возвращает их в момент переноса.
- Скрипт в APK приводится к LF перед записью на ГУ (`sh` на ГУ не понимает CRLF из Windows-checkout).
- Защиты скрипта (19.10.2026, на стенде не прогонялись): новое место обязано начинаться за концом старого `userdata` (`GptPlanner` и `move` до `stop`; при пересечении `dd` перетёр бы ещё не прочитанный источник, а MD5 совпал бы); `move`/`stage2`/`rollback`/`fixsgpt` берут блокировку `/cache/dataresize/lock` (pid + режим, после перезагрузки считается устаревшей) до очистки журнала, `running` и `status` (`RUNNING=`) её показывают — повторно открытая программа не запускает второй прогон, а следит за журналом. Если журнал пуст 12 с после запуска — ошибка с кнопкой «Проверить заново». `stage2` отказывается расширять ФС, если в vendor-fstab у `/data` есть `footer` или `length=` (на стенде их нет).
- Обрыв между записями копий GPT: если основная и резервная — побайтно старая и новая таблицы этой программы (MD5 из `params`), программа предлагает «Дописать резервную GPT» (`fixsgpt`): резервная пишется по основной (по ней грузятся LK и ядро) со снятием защиты, без перезагрузки. Неудачный `rollback` возвращает обе новые копии.
- Разметка eMMC на экране (19.10.2026): полоски «Сейчас» и «После переноса» (или «После отката», место берётся из `OLD_FIRST`/`OLD_SECT` в `params`), по ширине пропорционально размеру (`gpt/DiskMap`). Разделы меньше 1,5 % диска, идущие подряд, склеены в серую группу «первый … последний» (полный список — в легенде), `userdata` зелёный, освобождаемое место оранжевое, свободное серое; пустоты меньше 64 МиБ (выравнивание, резервная GPT) присоединяются к соседу. Проверено на стенде (перенесённый `userdata`): 14 сегментов, после отката хвост 30,8 ГиБ «освобождается».
- После переноса GPT переписали без участия программы (20.10.2026, стенд, через 6 дней после переноса). Между 14.10 и 20.10 на стенд поставили обновление прошивки: она стала `Jenkins03261716` вместо `Jenkins09051534`, активный слот сменился с `_b` на `_a`. Установщик обновления правит записи разделов, которые пишет. Атрибуты `preloader` меняются с `0x0003…` на `0x0001…`, байты 60–62 расширения (вторые 128 байт записи, записи по 256 байт) у `hsm_a`, `trustzone_a`, `viss_a`, `kernel_a`, `dtb_a`, `dtbo_a`, `vdec_a`, `logo_a` тоже меняются. Обе копии согласованы, CRC верные, разметка та же, но MD5 больше не совпадает с `new_*`. Поэтому `status` показывал `GPT=other`, а «Откатить» падал с ошибкой «откат невозможен: GPT сейчас other». Записывать сохранённые `old_*` нельзя: вернулись бы устаревшие поля загрузчика. Теперь откат строится как обратный перенос, `GptPlanner.relocate` по текущей таблице: меняются только LBA `userdata` и CRC. Текущие таблицы записываются как `new_*`, таблицы отката как `old_*`, MD5 в `params` переписываются, а скрипт, `fixsgpt` и распознавание обрыва остаются прежними. `stage2` таблицу не пишет и принимает `GPT=other`. Дампы стенда лежат в unit-тестах: `a10_rebooted_*gpt.bin`.
- Перед `reboot` `/data` размораживается намеренно: `init` при выключении пишет `persist.*` в `/data` и на замороженной ФС может зависнуть. Записи нативных демонов после разморозки уходят в старое место и теряются (фреймворк уже остановлен).

## 7. Корневые сертификаты (A10)

Хранилище Android 9 на ГУ (137 корней) не содержит `HARICA TLS RSA/ECC Root CA 2021` (RuStore, `*.rustore.ru`, VK) и `Russian Trusted Root CA` (Сбер, госуслуги). Встроенная установка — карточка «Корневые сертификаты» в блоке «Эксперт» настроек системы (только A10 + экспертный режим): tmpfs-оверлей поверх `/system/etc/security/cacerts` через localhost ADB + `su`, действует до перезагрузки, опционально автоустановка при старте. Подробно — [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), код `cacerts/`.

## 8. Перезагрузка ГУ из TBox Monitor

| | A9 | A10 |
|---|---|---|
| Механизм | mbCAN `SYSTEM_REBOOT` (74) = 1 | localhost ADB + `su` |
| Условие активности кнопки | mbCAN доступен | всегда (ошибка — диалогом) |

На A10 у `shell` нет `android.permission.REBOOT`, поэтому `svc power reboot` без root не работает. Команда (`adb/HuRebootAdb`):

```
if command -v su >/dev/null 2>&1; then setsid su 0 sh -c 'sleep 1; svc power reboot' </dev/null >/dev/null 2>&1 & echo TBOX_REBOOT_OK; else echo TBOX_REBOOT_ERR nosu; fi
```

Перезагрузка отвязана от сеанса (`setsid`) и отложена на 1 с, чтобы маркер вернулся до гибели `adbd`. `svc power reboot` — штатная перезагрузка через PowerManager (`sys.boot.reason=reboot`). Проверено на A10: маркер приходит за ~0,1 с, ГУ перезагружается, TBox поднимается сам.

## 9. Прочие особенности A10

- **Окно приложений лаунчера.** `com.adayo.launcher.LAUNCH_APP` открывает `MainActivity` в ActivityView на виртуальном дисплее ≠ 0 и может перенести уже открытую задачу (`singleTask`) на новый дисплей. Отдельные окна (`Dialog`) в этот момент падают с `BadTokenException`, поэтому редактор плитки рисуется внутри окна (`WidgetEditorScreen` с `inWindow = true`). Виртуальный дисплей существует только пока окно приложения показано. Подробно — [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md).
- **USB-разрешения.** Доработанный `UsbPermissionActivity` Adayo сам выдаёт разрешение (`CBen_grantUsbPermissions`) и закрывается, но в ответе приходит `granted=false`. Приложение перепроверяет `UsbManager.hasPermission()`.
- **localhost ADB.** `adbd` на `127.0.0.1:5555` принимает приложение; на этом пути работают поиск дисплеев, выдача разрешений, сертификаты, часовой пояс, файрвол и перезагрузка (`adb/LocalhostAdbSession`).

## 10. Открытые вопросы

- Включать ли по умолчанию `main_screen_open_on_boot`, автоустановку сертификатов и открытие файрвола на A10.
- Почему в некоторых загрузках `DROP` в цепочках Adayo не появлялся.
- Отключение ключевой фразы iFlytek для снижения нагрузки.
- Переразметка `/data` (§6): на стенде выполнена из Android без recovery (§6.7), затем программой `:dataresize` (§6.8), `/data` = 30 ГиБ. Осталось: инструкция для пользователей (установка APK); поведение LK при расхождении копий GPT и `autoformat` при несмонтированном `/data` не проверялись. Прошивальщика Autochips (BROM/DA) нет.
- Том под приложения в хвосте eMMC (§6.6): возможен только через правку `/system` или `/vendor` после снятия защиты.
- Инструкция по восстановлению ГУ через UART (§6.4): вход в консоль LK и формат `atc.upd` не исследованы. В обычной загрузке LK приглашения консоли не выводит.
- Удаление `/cache/fota`: только после копирования `update_os.zip` на ПК — это единственная полная прошивка (§6.1).
