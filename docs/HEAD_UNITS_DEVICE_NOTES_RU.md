# Головные устройства A9 и A10: заметки по железу, прошивке и диагностике

Сводка того, что выяснено на живых ГУ (октябрь 2026): платформа, root, загрузка TBox Monitor, нагрузка CPU, разметка диска, сеть, сертификаты, перезагрузка. Логику CAN-бэкендов см. в [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), параметры mbCAN/VHAL — в [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), штатные приложения — в [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md) и [STOCK_APPS_ANDROID10_VHAL_RU.md](STOCK_APPS_ANDROID10_VHAL_RU.md).

## 1. Профили устройств

| | **A9** (mbCAN) | **A10** (Adayo / VHAL) |
|---|---|---|
| Fingerprint | `TINNOVE/sm6150_au/sm6150_au:9/PQ3B.190801.002/272:user/test-keys` | `autochips/full_ac8x_demo/ac8x_demo:9/PI/Jenkins09051534:userdebug/test-keys` |
| SoC / ядра / ОЗУ | Qualcomm SM6150, 8 ядер, ~7,4 ГБ | Autochips AC8x, 4 ядра, ~3,5 ГБ |
| Тип сборки | `user` | `userdebug` |
| `ro.build.version.release` / API | 9 / 28 | 9 / 28 (в UI может показываться «10», см. [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md)) |
| SELinux | Permissive | Permissive |
| root (`su`) | нет | есть: `/system/xbin/su`, `su 0` из `adb shell` |
| `/data` | 37 ГБ (`/dev/block/sda10`, UFS) | **2 ГБ** (`mmcblk0p39`, eMMC 64 ГБ) |
| Часовой пояс | `Europe/Moscow` | задаётся службой Adayo, см. §5.1 (заводское `PRC`) |
| ADB по Wi-Fi после перезагрузки | `persist.adb.tcp.port=5555` | `persist.adb.tcp.port=5555` |

`/system` на A10 собран неизменяемым: запись даже от root отдаёт `EIO`, `ro.boot.veritymode=disabled`, но перемонтирование rw не помогает. Слоты A/B, активный — `_b`.

## 2. Работа с ГУ по ADB с Windows

- Подключение: `adb connect 192.168.1.7:5555` (A10), `adb connect 192.168.1.128:5555` (A9). **Wi-Fi на ГУ не выключать** — пропадёт доступ (на A10 восстановление — через UART, §2.1).
- PowerShell портит `$(...)`, кавычки и `>` внутри `adb shell "..."`. Надёжно: сохранить скрипт локально **с переводами строк LF** (CRLF даёт `syntax error: unmatched 'if'`), `adb push` в `/data/local/tmp/`, запустить `sh` или `su 0 sh`.
- Скриншот: `adb shell screencap -p /data/local/tmp/s.png` + `adb pull` (перенаправление `screencap -p >` в PowerShell портит PNG).
- `uiautomator dump` на A10 возвращает пустой корень — навигация только по скриншотам.
- AOSP `sgdisk` на A10 считает основной GPT повреждённым (записи по 256 байт, см. §6) — для чтения разметки использовать sysfs (`/sys/block/mmcblk0/mmcblk0pN/{start,size,uevent}`), **для записи `sgdisk` не использовать**.

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
- **Разбор вывода:** оборачивать команду маркерами, которые не совпадают с эхом ввода (`echo QS''TART; <cmd>; echo QE''ND` — в эхе `QS''TART`, в выводе `QSTART`), и вырезать строки лога ядра по шаблону `^\[\s*\d+\.\d+\]\[`.
- **Передача файлов:** base64 строками по 64 символа (`echo … >> /data/local/tmp/f.b64`), затем `base64 -d` и сверка `md5sum`.
- **Лог загрузки** (перехват при включении питания): preloader от 15.05.2024, LK от 05.09.2024, проект `HMA214A`, диагностическая область `ADAYO DZ`, Wi-Fi — RTL8821CS на SDIO (модуль `8821cs`), PCIe без устройств (`No EP Device` — норма).

Подключение A10 к Wi-Fi через UART (если ADB недоступен). В Android 9 нет `cmd wifi connect-network`, а сеть, добавленная через `wpa_cli`, фреймворку неизвестна — он рвёт соединение и не запускает DHCP. Рабочий путь:

1. `su`, `svc wifi enable`; проверить видимость сети: `wpa_cli -i wlan0 -p /data/vendor/wifi/wpa/sockets scan_results`.
2. Добавить блок `<Network>` (SSID, `PreSharedKey`, `AllowedKeyMgmt` = `02`, `NetworkStatus`, `IpConfiguration` DHCP) в `/data/misc/wifi/WifiConfigStore.xml`, взяв за образец уже сохранённую сеть. Файл перезаписывать через `cat new > WifiConfigStore.xml` — так сохраняются владелец `system`, права `600` и контекст `wifi_data_file`.
3. Сразу `stop; start` (перезапуск фреймворка без перезагрузки ядра) — иначе работающий фреймворк может перезаписать файл своей копией.

Сеть после этого штатная и подключается сама после перезагрузки (`wifi_on=1`). ADB по сети — `setprop persist.adb.tcp.port 5555` и `setprop ctl.restart adbd`; ключи не проверяются (`ro.adb.secure` пуст), подтверждение на экране не нужно. MAC `wlan0` — `a4:04:50:c5:c8:87` (для резервирования адреса в роутере).

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
  - Шум штатных приложений при загрузке: `Long monitor contention … CarPropertyManager.registerListener` у `com.adayo.aaopbc`, `Rejecting re-init on previously-failed class` у `ru.vk.store` — к TBox не относятся.

## 4. Нагрузка CPU на A10

- **Load average (14–17) не равен загрузке CPU.** Его раздувают ~8 потоков ядра в состоянии D (непрерываемое ожидание: watchdog-тесты, десериализатор камеры и т.п.) — они процессор не тратят. Реальную загрузку считать по `/proc/stat` или дельтам `top`: **~60–70 %** от 4 ядер в покое.
- Основные потребители:
  - `surfaceflinger` (60–80 % одного ядра) — композиция;
  - **штатный лаунчер Adayo** перерисовывается ~**45 кадров/с** постоянно, независимо от TBox (проверено остановкой TBox на 10 с). Вероятные источники — бегущая строка трека в медиакарточке и ADAS-карточка. Из приложения не лечится;
  - iFlytek: постоянное распознавание ключевой фразы и анимированный аватар (`avatarService`);
  - scrcpy, пока открыт с ПК (кодек + `surfaceflinger` + `adbd`);
  - запись логов Adayo во флеш.
- TBox в покое — 3–9 % главного потока, ~2 кадра/с. Кратковременный пик >100 % был при переключении экранов.
- Методика: `dumpsys gfxinfo <pkg>` — дельта «Total frames rendered» за 10 с; `top -n 2 -d 5`; `ps -A -o PID,STAT,NAME | grep ' D '`.

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
- `CarService.apk` → `com.android.car.network.CarNetworkService` (лог `CAR.Firewall_NET`) на старте и по `android.intent.action.ADAYO_BOOT_COMPLETED` читает белый список `/system/etc/adayo/firewall/AdayoFirewallAppDef.json` и вызывает `INetworkManagementService.setFirewallAllRules(66/67, …)` → netd `firewallReplaceAllChain` в режиме whitelist: `RETURN` для uid 0–9999, без владельца, `esp`, `lo`, TCP RST, в конце **`DROP`**;
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

## 6. Разметка eMMC на A10 и нехватка места под приложения

eMMC 64 ГБ (124 993 536 секторов), разметка прошита под 32 ГБ:

| # | Раздел | Размер | Примечание |
|---|---|---|---|
| p1–p27 | `preloader`, `hsm`, `lk`, `trustzone`, `viss`, `kernel`, `dtb`, `dtbo`, `recovery`, `vdec`, `vendor` (512 МиБ), `system` (4 ГиБ), `product`, `logo` — по два слота `_a`/`_b` | ~9,7 ГиБ | между `product_a` и `logo_a` дыра 31 МиБ |
| p28–p33 | `oem`, `ProjectConfig`, `UserConfig`, `FactoryConfig`, `diagnosticzone`, `expdb` | ~190 МиБ | |
| p34 | `cache` | 4,5 ГиБ | занято 1,7 ГБ, из них **1,8 ГБ — `/cache/fota` 2021 г.** (`update_os.zip`, `RES.zip`, `HMA214A_MCU.bin`) |
| p35–p38 | `avm`, `misc`, `metazone`, `ramdisk` | ~104 МиБ | |
| **p39** | **`userdata`** | **2 ГиБ** | `/data`, ext4, без шифрования (`ro.crypto.state=unsupported`) |
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

Варианты увеличения `/data` (**не выполнялось**, нужен явный выбор владельца):

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

Вывод: при ошибке **только в записи `userdata`** (или если не смонтировался `/data`) ГУ доходит до init, и через UART есть root-консоль (Android или recovery) для отката GPT из заранее сохранённой копии. Если испорчена одна копия GPT — preloader и LK переживут это сами по второй. Без программатора **не восстанавливается** только случай, когда обе копии GPT корректны по CRC, но ломают записи `lk`/`kernel`/`misc`/`metazone`, или обе копии битые: тогда остаётся только USB-режим BROM с прошивальщиком Autochips и DA (у нас их нет). Поэтому меняются только поля start/end записи 39, остальные 42 записи сверяются побайтно.

#### 6.2. Рекомендуемая процедура (вариант 1 через recovery)

1. В Android по ADB (без записи на eMMC): дамп обеих GPT и копия `update_os.zip` на ПК; на ПК скриптом собрать новые основную и резервную GPT (меняется только запись 39: начало — первый выровненный сектор хвоста, конец — последний usable LBA), проверить CRC заголовков и массива, отсутствие пересечений, побайтное совпадение остальных записей. Положить в `/cache/gptfix/`: новые и старые GPT, `move.sh`, `rollback.sh` с контрольными суммами.
2. `adb reboot recovery`, дальше по UART в root-консоли recovery: `/data` там не смонтирован, поэтому копия консистентна. `move.sh`: `dd` старого `userdata` (2 ГиБ) в хвост, сверка `md5sum`, запись обеих GPT, чтение обратно и сверка, `sync`, перезагрузка.
3. В Android: `/data` — та же ФС в новом месте; онлайн-расширение `resize2fs /dev/block/userdata` (от root). Старая область p39 не трогается — откат `rollback.sh` (старые GPT) из консоли UART Android или recovery.

Безопасное освобождение места без переразметки: удалить `/cache/fota` (1,8 ГБ, раздел `cache`, на `/data` не влияет).

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

- **Окно приложений лаунчера.** `com.adayo.launcher.LAUNCH_APP` открывает `MainActivity` в ActivityView на виртуальном дисплее ≠ 0 и может перенести уже открытую задачу (`singleTask`) на новый дисплей. Отдельные окна (`Dialog`) в этот момент падают с `BadTokenException`, поэтому редактор плитки рисуется внутри окна (`InWindowAlertDialog`). Виртуальный дисплей существует только пока окно приложения показано. Подробно — [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md).
- **USB-разрешения.** Доработанный `UsbPermissionActivity` Adayo сам выдаёт разрешение (`CBen_grantUsbPermissions`) и закрывается, но в ответе приходит `granted=false`. Приложение перепроверяет `UsbManager.hasPermission()`.
- **localhost ADB.** `adbd` на `127.0.0.1:5555` принимает приложение; на этом пути работают поиск дисплеев, выдача разрешений, сертификаты, часовой пояс, файрвол и перезагрузка (`adb/LocalhostAdbSession`).

## 10. Открытые вопросы

- Включать ли по умолчанию `main_screen_open_on_boot`, автоустановку сертификатов и открытие файрвола на A10.
- Почему в некоторых загрузках `DROP` в цепочках Adayo не появлялся.
- Отключение ключевой фразы iFlytek для снижения нагрузки.
- Переразметка `/data` (§6): рекомендован вариант 1 через recovery (§6.2), ждёт решения владельца. Прошивальщика Autochips (BROM/DA) нет.
- Удаление `/cache/fota`: только после копирования `update_os.zip` на ПК — это единственная полная прошивка (§6.1).
