# Звук через USB-карту на ГУ A10: диагностика и включение

Документ фиксирует результат отладки вывода звука головного устройства **A10 (Adayo/VHAL, Jetour Dashing)** на внешнюю USB-звуковую карту **DEXP «USB PnP Sound Device»**: три независимых барьера (баг USB-перечисления в ядре, выключенная в стоке аудиополитика, защита записи eMMC на системных разделах), способы обхода, интеграцию в приложение и **ручные ADB-команды** включения/отката.

Проверено на реальном ГУ: kernel `4.9.118` (AutoChips X9H), Android 10 (platform API 28), SELinux Permissive, dm-verity **выключен**, root через `su 0`, ADB over TCP `192.168.1.7:5555`.

## 1) Итог (TL;DR)

| Барьер | Причина | Решение |
|--------|---------|---------|
| Карта не видна ядру при прямом подключении | Баг ядра 4.9.118: enumerate full-speed устройства на EP0 падает, устройство слетает с шины | **Непитаемый USB 2.0-хаб** между ГУ и картой |
| Карта видна в ALSA, но Android в неё не играет | В стоке `/vendor/etc/usb_audio_policy_configuration.xml` всё тело модуля `usb` закомментировано — модуль никогда не подключается | Модуль `usb` **инлайном в главный конфиг**, который кладётся в `/system/etc/audio_policy_configuration.xml` |
| Запись в `/vendor` (и `/system`) не попадает на диск | eMMC держит программную защиту записи (драйвер SD4HC, `sw write protect violation`); на обследованном ГУ ext4 `/vendor` к тому же заполнен на 100 % и не выделяет блоки | Снятие защиты штатной командой драйвера `echo emmc_disable_all_wp > /proc/mmc_debug` на время записи (как в `:dataresize`, см. [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §6.6) |

- **Постоянное решение** — полный аудиоконфиг с инлайн-модулем `usb` пишется в `/system/etc/audio_policy_configuration.xml`, имя `/vendor/etc/audio_policy_configuration.xml` удаляется: `libaudiopolicymanagerdefault.so` содержит список кандидатов с обоими путями (`/vendor/etc`, `/system/etc`), и без vendor-файла audioserver сам грузит конфиг из `/system`. Переживает перезагрузку ГУ и удаление приложения. Опасная операция выделена в **отдельное приложение** `:usbaudio` («USB-аудио ГУ», `vad.dashing.usbaudio`) по образцу `:dataresize`.
- **Временное решение (безопасное)** — в самом TBox Monitor: **Настройки → Спец. функции ГУ → «Звук через USB»**. Ничего не пишет в защищённые разделы: конфиг накладывается bind-mount'ом поверх читаемого audioserver файла и живёт до перезагрузки ГУ; переключатель «Автовключение при старте» повторяет наложение после каждой загрузки. См. §8.
- Ручные команды — §6 (включение) и §7 (откат).
- История тупиков (запись в `/vendor`, bind как основной) — §4–5, чтобы не повторять.

## 2) Железо: хаб-обход бага ядра

- Баг: при **прямом** подключении full-speed карты в USB-порт ГУ ядро валится на этапе EP0-перечисления, устройство отваливается от шины (в dmesg ловится плохо — лог затоплен `max96735`; смотреть `/sys/bus/usb/devices`). High-speed устройства ядро перечисляет нормально.
- Обход: **непитаемый USB 2.0-хаб** (`1a40:0101`, TERMINUS FE1.1s) в USB-порт ГУ (шина `usb2`, адрес `2-1`), карта — в порт хаба (`2-1.2`). Карта видна как ALSA **card 1** (`usb-13030000.usb-1.2`).
- Проверено: связка «хаб + карта» **переживает перезагрузки ГУ** и поднимается без вмешательства. Модем Neoway на `1-1` (8 интерфейсов) работе не мешает.

Проверка подключения:

```
cat /proc/asound/cards          # должна быть card 1: USB PnP Sound Device
ls /sys/bus/usb/devices         # хаб 2-1, карта 2-1.2
ls /dev/snd                     # controlC1, pcmC1D0p, pcmC1D0c
```

## 3) Сток-политика и модуль usb

Главный конфиг `/vendor/etc/audio_policy_configuration.xml` (13 107 байт, md5 `dd57231a223ea8894436482db8d7f28a`) подключает модули инклудами: `a2dp_audio_policy_configuration.xml`, `usb_audio_policy_configuration.xml`, `r_submix_audio_policy_configuration.xml`, `audio_policy_volumes.xml`, `default_volume_tables.xml`. В стоковом usb-файле **всё тело модуля лежит внутри одного комментария** — модуль `usb` не поднимается, события подключения карты маршрутизацией игнорируются.

Новый модуль — **output-only** (44100/48000), **без input-портов**: USB-микрофон otherwise перехватывает микрофон iFlytek (VOZ). BT-музыка (A2DP sink) автоматически уходит на карту сразу после активации модуля (проверено на ГУ; в `dumpsys audio` при подключении видно `setForceUse(FOR_MEDIA, FORCE_NO_BT_A2DP)`).

Содержимое модуля (вставляется в главный конфиг вместо строки-инклуда usb):

```xml
<module name="usb" halVersion="2.0">
    <mixPorts>
        <mixPort name="usb_accessory output" role="source">
            <profile name="" format="AUDIO_FORMAT_PCM_16_BIT"
                     samplingRates="44100,48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
        </mixPort>
        <mixPort name="usb_device output" role="source"/>
    </mixPorts>
    <devicePorts>
        <devicePort tagName="USB Host Out" type="AUDIO_DEVICE_OUT_USB_ACCESSORY" role="sink">
            <profile name="" format="AUDIO_FORMAT_PCM_16_BIT"
                     samplingRates="44100,48000" channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
        </devicePort>
        <devicePort tagName="USB Device Out" type="AUDIO_DEVICE_OUT_USB_DEVICE" role="sink"/>
        <devicePort tagName="USB Headset Out" type="AUDIO_DEVICE_OUT_USB_HEADSET" role="sink"/>
    </devicePorts>
    <routes>
        <route type="mix" sink="USB Host Out" sources="usb_accessory output"/>
        <route type="mix" sink="USB Device Out" sources="usb_device output"/>
        <route type="mix" sink="USB Headset Out" sources="usb_device output"/>
    </routes>
</module>
```

Кроме подмены usb-инклуда, при переносе конфига в `/system/etc` **все остальные `xi:include` переводятся на абсолютные пути** `/vendor/etc/...` — относительные href разрешились бы относительно каталога нового файла, а в `/system/etc` этих файлов нет.

## 4) Почему нельзя просто записать в /vendor (история находок)

1. **Программная защита записи eMMC.** Драйвер `SD4HC` по списку разделов отклоняет запись данных в защищённые регионы: dmesg `sw write protect violation, abort!!!`, `write protect violation, abort it`, `blk_update_request: I/O error, dev mmcblk0`. При этом `dd`/`cp`/`sync` **возвращают RC=0** — запись живёт в page cache и «видна» чтением до первой перезагрузки. Вывод: **результат записи на эту eMMC проверять только чтением после `sync; blockdev --flushbufs /dev/block/mmcblk0; echo 3 > /proc/sys/vm/drop_caches`**.
2. Защита снимается штатно: `echo emmc_disable_all_wp > /proc/mmc_debug` (root; снимает и аппаратную по группам, и программную), возврат — `echo emmc_enable_all_wp > /proc/mmc_debug`, статус — `emmc_check_wp` (ответ в dmesg). Проверено на этом ГУ (подробности и происхождение — [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §6.6, там же верифицированная процедура с `trap … EXIT`). После перезагрузки LK возвращает защиту сам; записанное остаётся.
3. **ext4 `/vendor` заполнен на 100 %** (511 932 из 516 040 КБ) и после серии «рваных» записей (коммит метаданных прошёл, данные — нет) аллокатор мёртв: любая новая запись → `ENOSPC`, хотя `df` показывает «Available 4108» (счётчики superblock и битмапи рассинхронизированы). Удаление файлов при этом работает и **персистит** (если делать `rm` + `sync` под снятой защитой).
4. Попытка заменить `/vendor/etc/usb_audio_policy_configuration.xml` оставила **висячую dirent** (каталог ссылается на удалённый inode): любой доступ по имени → `Structure needs cleaning` (ext4 `EFSCORRUPTED`), `rm` не помогает. audioserver битый include молча пропускает — это безопасно, но файл невосстановим без офлайн-fsck.
5. Итог: единственная надёжная цель записи — `/system` (`/dev/root`, 1,8 ГБ свободно, здоровый ext4; `/system` — не отдельная точка монтирования, для записи ремоунтится корень: `mount -o remount,rw /`). Ключ доставки конфига — fallback-список AudioPolicyManager (строки в `libaudiopolicymanagerdefault.so`): при отсутствии vendor-файла конфиг берётся из `/system/etc/audio_policy_configuration.xml`.

Особенность платформы (проверено кодами возврата): `mount -o remount,rw /vendor` — RC=0, а `mount -o remount,ro /vendor` — RC=1 (`I/O error`), раздел остаётся `rw`. Скрипты считают возврат `ro` best-effort.

Побочно: при ручной починке на обследованном ГУ был удалён `/vendor/etc/NOTICE.xml.gz` (тексты лицензий, 19 679 байт; бэкап — `/data/local/tmp/NOTICE.xml.gz.vendor`). Функционально бесполезен, восстановление не требуется.

## 5) Bind-mount — основа временного варианта

`mount --bind` одного файла поверх главного конфига ничего не пишет в защищённые разделы и живёт до перезагрузки ГУ. Именно так устроен безопасный временный вариант в TBox Monitor (§8): наложение поверх конфига, который реально читает audioserver (vendor-имя при его наличии, иначе `/system`). Постоянный вариант (§6, §8b) bind не использует, но его скрипты сначала делают `umount` и проверяют `/proc/mounts` — зачистка остатков наложения.

## 6) Ручное включение через ADB

> Пошаговая версия «для неспециалиста» (Блокнот, копировать-вставить) — [USB_AUDIO_A10_SIMPLE_RU.md](USB_AUDIO_A10_SIMPLE_RU.md). Проще всего: временно — кнопкой в TBox Monitor (§8), постоянно — приложением «USB-аудио ГУ» (§8b). Ниже — ручной эквивалент постоянного варианта.

Предусловия: карта за хабом видна (`cat /proc/asound/cards` → card 1), `adb connect 192.168.1.7:5555`, на ГУ есть `su`.

1) Забрать с ГУ главный конфиг и сохранить сток:

```
adb connect 192.168.1.7:5555
adb -s 192.168.1.7:5555 pull /vendor/etc/audio_policy_configuration.xml audio_policy_configuration.xml
```

(если на ГУ конфиг уже применялся и vendor-файл удалён — забирайте текущий: `adb ... pull /system/etc/audio_policy_configuration.xml audio_policy_configuration.xml`)

2) Отредактировать локально (Блокнот):

- строку `<xi:include href="usb_audio_policy_configuration.xml"/>` заменить целиком на модуль из §3;
- во всех остальных инклудах заменить `href="` на `href="/vendor/etc/` (заменить все вхождения).

3) Залить и применить скриптом (сохраните как `apply_usb_audio.sh`, залейте, запустите):

```
adb -s 192.168.1.7:5555 push audio_policy_configuration.xml /data/local/tmp/audio_policy_configuration.tbox.xml
adb -s 192.168.1.7:5555 push apply_usb_audio.sh /data/local/tmp/apply_usb_audio.sh
adb -s 192.168.1.7:5555 shell "su 0 sh /data/local/tmp/apply_usb_audio.sh"
```

Скрипт (проверен на реальном ГУ):

```sh
#!/system/bin/sh
C=/data/local/tmp/audio_policy_configuration.tbox.xml
S=/system/etc/audio_policy_configuration.xml
V=/vendor/etc/audio_policy_configuration.xml
P=/proc/mmc_debug
D=/dev/block/mmcblk0
wp_on() { echo emmc_enable_all_wp > $P 2>/dev/null; }
fail() { echo "TBOX_USB_ERR $1"; exit 1; }

[ -f "$C" ] || fail nostage
grep -q '<module name="usb" halVersion="2.0">' "$C" || fail stage
if grep -q " $V " /proc/mounts; then umount "$V" 2>/dev/null; fi
echo emmc_disable_all_wp > "$P" 2>/dev/null || fail wpoff
trap 'wp_on' EXIT
mount -o remount,rw / || fail remountsys
cp "$C" "$S" || fail copysys
chown root:root "$S" && chmod 644 "$S"
chcon u:object_r:system_file:s0 "$S" 2>/dev/null
sync; blockdev --flushbufs "$D" 2>/dev/null; echo 3 > /proc/sys/vm/drop_caches
H1=$(md5sum "$C" | cut -d' ' -f1); H2=$(md5sum "$S" | cut -d' ' -f1)
[ "$H1" = "$H2" ] || fail verify
if [ -e "$V" ]; then
    mount -o remount,rw /vendor 2>/dev/null
    rm -f "$V" || fail rmvendor
    sync; blockdev --flushbufs "$D" 2>/dev/null; echo 3 > /proc/sys/vm/drop_caches
    [ ! -e "$V" ] || fail rmvendor
fi
wp_on; trap - EXIT
setprop ctl.restart audioserver || fail restart
sleep 8
C=""
for SD in /sys/class/sound/card*/device; do
    [ -L "$SD" ] || continue
    T=$(readlink -f "$SD" 2>/dev/null)
    case "$T" in */usb*/*:*) C="$T"; break ;; esac
done
if [ -n "$C" ]; then
    U=$(dirname "$C"); HUB=$(basename "$(dirname "$U")")
    case "$HUB" in usb*) HUB=$(basename "$U") ;; esac
    A="/sys/bus/usb/devices/$HUB/authorized"
    if [ -w "$A" ]; then
        echo 0 > "$A" 2>/dev/null; sleep 3
        echo 1 > "$A" 2>/dev/null; sleep 4
    fi
fi
echo TBOX_USB_APPLIED
```

4) Проверка:

```
md5sum /system/etc/audio_policy_configuration.xml        # совпадает с локальным файлом
dumpsys media.audio_policy | grep -iE 'name: (primary|a2dp|usb|r_submix)'   # 4 модуля
dumpsys audio | grep -i usb                              # события AVAILABLE addr:card=1;device=0
dumpsys media.audio_flinger | grep -A8 -i usb            # при играющей музыке — поток на модуле usb
```

Запустите музыку на ГУ — маршрут на карту выбирается автоматически (`FORCE_NO_BT_A2DP` для медиа). Если после рестарта `audioserver` музыка идёт мимо карты — пере-перечислите хаб (§9, п. 3).

## 7) Откат к штатному звуку

Имя в `/vendor` не восстанавливается (аллокация мертва, §4) — откат пишется туда же, в `/system`, но **без** модуля usb: возьмите текущий `/system/etc/audio_policy_configuration.xml`, замените блок `<module name="usb" …>…</module>` обратно на строку `<xi:include href="/vendor/etc/usb_audio_policy_configuration.xml"/>` и залейте тем же скриптом (без блока `rm` и пере-перечисления), либо кнопкой «Вернуть штатную политику» приложения «USB-аудио ГУ» (§8b) — она делает то же самое. Для снятия временного наложения из TBox Monitor есть кнопка «Выключить (снять наложение)» (§8).

## 8) Временный вариант в TBox Monitor

**Настройки → Спец. функции ГУ → «Звук через USB»** (`HuUsbAudioSettingsCard` внутри `HuSpecialOptionsDialog`, рядом с DSP-режимами). Требуется та же связь с ГУ, что и у остальных спец-функций: локальная ADB-сессия (`LocalhostAdbSession`) и `su` на ГУ. Ничего не пишет в защищённые разделы.

- «Включить USB-аудио (до перезагрузки)»: источник — сам конфиг ГУ (`/system/etc/audio_policy_configuration.xml`, если постоянный вариант уже применён, иначе `/vendor/etc/...`), трансформация в Kotlin (инклуд usb → инлайн-модуль, остальные href → абсолютные `/vendor/etc/...`); затем root-скрипт: копия в `/data/local/tmp/tbox-usbaudio/`, `mount --bind` поверх цели (vendor-файл, при его отсутствии — /system-файл), проверка `/proc/mounts`, рестарт `audioserver`, ожидание 8 с и **пере-перечисление USB-карты** (деавторизация/авторизация родителя в `/sys/bus/usb/devices/*/authorized` — без него UsbHostManager не пере-объявляет уже известную карту). Весь скрипт ~20 с.
- «Выключить (снять наложение)»: `umount` цели (дважды, на случай стопки) + проверка + рестарт `audioserver`.
- Переключатель **«Автовключение при старте»** (`hu_usb_audio_auto_apply`, по умолчанию выключен, только A10): после загрузки ГУ приложение повторяет наложение с попытками 0/10/20/30/60 с (как DSP), пропуская работу, если звук уже включён (наложение активно или постоянный вариант применён).
- Статус (каждые 3 с): `/proc/mounts` → наложение активно (временно); иначе читается конфиг, который реально читает audioserver (vendor при наличии имени, иначе /system) → модуль usb вне комментариев = включено постоянно.
- Результат по маркерам: `TBOX_USB_APPLIED` / `TBOX_USB_RESTORED` / `TBOX_USB_ERR <код>` (`bind`, `bindverify`, `umount`, `notarget`, `restart`, `nosu`, `nosource`, `layout`, …). Лог — в журнал приложения с тегом **`HU_USB_AUDIO`**.

Код: `huaudio/HuUsbAudioPolicyCodec.kt` (XML-трансформация, bind-скрипты, разбор результата), `huaudio/HuUsbAudioPolicyClient.kt` (подготовка, ADB-исполнение, стартап-хук), тесты `HuUsbAudioPolicyTest.kt`.

## 8b) Постоянный вариант — отдельное приложение «USB-аудио ГУ»

Модуль `:usbaudio` (`vad.dashing.usbaudio`), отдельный APK по образцу `:dataresize` — опасные одноразовые операции с eMMC живут вне монитора. Ставится на ГУ рядом с TBox Monitor (подпись общая).

- Экран: статус (карта за хабом, наличие конфигов, состояние модуля usb), кнопки «Включить постоянно» и «Вернуть штатную политику».
- «Включить постоянно» делает ровно то, что описано в §6: WP-танец (`emmc_disable_all_wp` + `trap … EXIT`), запись в `/system/etc/audio_policy_configuration.xml`, md5-сверка после `drop_caches`, удаление vendor-имени с синком, рестарт `audioserver`, пере-перечисление карты. Статус-детект учитывает приоритет: пока имя в `/vendor` существует, audioserver читает именно его — конфиг в `/system` включается только после удаления имени.
- «Вернуть штатную политику» пишет в `/system` конфиг без модуля usb (инклуд usb возвращается абсолютным путём); имя в `/vendor` не восстанавливается — на битом vendor-ext4 это невозможно, да и не нужно: поведение аудио становится штатным.
- Связь с ГУ — тот же localhost ADB + `su 0` (RootShell, как в `:dataresize`); конфиг и скрипт доставляются base64-чанками в `/data/local/tmp/usbaudio/`.

Код: `usbaudio/UsbAudioCodec.kt` (трансформация, скрипты, маркеры), `UsbAudioViewModel.kt`, `device/RootShell.kt`, тесты `UsbAudioCodecTest.kt`. Сборка: `./gradlew :usbaudio:assembleDebug`.

## 9) Если звук снова пропал — чек-лист

1. `cat /proc/asound/cards` — карты нет → проверить хаб/порт (§2); перетыкать карту в порт хаба, не напрямую в ГУ.
2. Карта есть, статус кнопки «выключено» → конфиг в `/system` перезаписан (OTA?) — применить заново (§6 или кнопкой).
3. **Карта есть, статус «включено», но музыка идёт мимо** (в `dumpsys media.audio_flinger` у «USB audio module» нет потоков, патчи уходят в `AUDIO_DEVICE_OUT_SPEAKER`) → после рестарта/краша `audioserver` карта не была пере-объявлена: UsbHostManager держит устройство в своей карте и шлёт событие подключения только при настоящем re-enumerate. Лечится пере-перечислением хаба; кнопка «Включить» делает это сама:

   ```
   adb -s 192.168.1.7:5555 shell "su 0 sh -c 'echo 0 > /sys/bus/usb/devices/2-1/authorized; sleep 4; echo 1 > /sys/bus/usb/devices/2-1/authorized'"
   ```

   (`2-1` — адрес хаба из `ls /sys/bus/usb/devices`; деавторизация самой карты ALSA-карту пересоздаёт, но событие подключения не даёт — нужен именно родитель.) Наблюдалось один раз на реальном ГУ: audioserver умер (~15 мин после применения, tombstone отсутствует, причина не установлена), после автоподъёма карта не пере-объявилась, музыка ушла в DSP — усилитель подключён к карте, поэтому наступила тишина.
4. После перезагрузки ГУ звук пропал полностью → проверить, что пережила ребут связка: `md5sum /system/etc/audio_policy_configuration.xml`, отсутствие `/vendor/etc/audio_policy_configuration.xml`, `dumpsys media.audio_policy | grep 'name: usb'`. Если vendor-имя «вернулось» — удаление не лежало на диске (см. §4, п. 3): повторить `rm` под `emmc_disable_all_wp` с обязательным `sync` **до** возврата защиты.
5. Лог приложения, тег `HU_USB_AUDIO` — коды ошибок установки (`nosu` — нет root/ADB-сессии).

## 10) Отличия ГУ A9

На A9 (mbCAN, production) **root отсутствует** — скрипт кнопки (`su 0`) недоступен. Карта за хабом на A9 перечисляется (`1-1.3`), но медиа уходит в offload primary — маршрутизация иная; фича на A9 не поддерживается и в UI не рассчитана на него.
