# Звук через USB на ГУ A10: самая простая инструкция (Блокнот + ADB)

Для неспециалиста: как подключиться к ГУ с компьютера, забрать с него файл, отредактировать в Блокноте, записать обратно и включить звук через USB-карту. Подробная версия с диагностикой — [USB_AUDIO_A10_RU.md](USB_AUDIO_A10_RU.md).

Что нужно заранее: компьютер и ГУ в одной Wi-Fi сети; на компьютере работает команда `adb` (если «не является внутренней или внешней командой» — установите Android platform-tools); USB-карта вставлена в хаб, хаб — в ГУ.

Если на ГУ стоит приложение TBox Monitor — проще всего включить временно (до перезагрузки ГУ): **Настройки → Спец. функции ГУ → «Звук через USB» → «Включить USB-аудио (до перезагрузки)»**, и там же включить «Автовключение при старте». Чтобы включить навсегда — поставьте на ГУ отдельное приложение «USB-аудио ГУ» и нажмите в нём «Включить постоянно». Ручной способ ниже — эквивалент постоянного включения.

## 1) Открыть командную строку

Нажмите `Win + R`, введите `cmd`, нажмите Enter. Дальше все команды вводятся в это окно (вставка — правый клик мышью или Ctrl+V).

## 2) Подключиться к ГУ

```
adb connect 192.168.1.7:5555
adb devices
```

Здесь 192.168.1.7 это IP вашего ГУ в Wi-Fi сети.
Во второй строке должно появиться `192.168.1.7:5555   device`. Если его нет — проверьте Wi-Fi и что ГУ включён.

## 3) Сделать папку и забрать с ГУ файл настроек звука

```
mkdir C:\usbaudio
cd /d C:\usbaudio
adb -s 192.168.1.7:5555 pull /vendor/etc/audio_policy_configuration.xml audio_policy_configuration.xml
copy audio_policy_configuration.xml audio_policy_BACKUP.xml
```

Если первая `pull` пишет «does not exist» — звук на этом ГУ уже включали раньше, тогда забирайте так:

```
adb -s 192.168.1.7:5555 pull /system/etc/audio_policy_configuration.xml audio_policy_configuration.xml
```

В папке `C:\usbaudio` появятся два одинаковых файла. `audio_policy_BACKUP.xml` — страховка «как было», его **не трогаем**. Редактируем первый.

## 4) Открыть файл в Блокноте

```
notepad audio_policy_configuration.xml
```

## 5) Что найти и как отредактировать

В файле ищите строку со словами `usb_audio_policy_configuration` — примерно такую:

`<xi:include href="usb_audio_policy_configuration.xml"/>`

Выделите её целиком и замените на текст ниже (целиком, со всеми строками):

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

Важно: не добавляйте туда строки со словом `input` — иначе USB-карта перехватит микрофон штатного голосового помощника.

Затем в Блокноте: Правка → Заменить (Ctrl+H):

- Найти: `href="`
- Заменить на: `href="/vendor/etc/`
- Нажмите «Заменить все».

## 6) Сохранить

`Ctrl + S`. (Если сохраняете через «Файл → Сохранить как» — внизу выберите кодировку «UTF-8».)

## 7) Создать скрипт установки

В той же папке создайте файл `apply_usb_audio.txt` (Блокнот → Сохранить как → имя в кавычках: `"apply_usb_audio.sh"`), скопируйте в него **целиком**:

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

(Скрипт на время записи снимает заводскую защиту флеш-памяти ГУ и сразу возвращает её обратно; это штатная команда драйвера, она же используется программой «Расширение /data».)

## 8) Записать и применить

```
adb -s 192.168.1.7:5555 push audio_policy_configuration.xml /data/local/tmp/audio_policy_configuration.tbox.xml
adb -s 192.168.1.7:5555 push apply_usb_audio.sh /data/local/tmp/apply_usb_audio.sh
adb -s 192.168.1.7:5555 shell "su 0 sh /data/local/tmp/apply_usb_audio.sh"
```

Команда работает ~25 секунд и в конце должна напечатать `TBOX_USB_APPLIED`. После неё звук на ГУ пропадёт на пару секунд — перезапускается служба звука. Это нормально.

## 9) Проверить

Включите музыку на ГУ (например, Bluetooth с телефона) — звук должен пойти через USB звуковую карту. Настройка сохраняется и после перезагрузки ГУ.

Если музыка играет как обычно (через штатный усилитель ГУ) — ГУ «не заметил» карту после перезапуска звука. Верните её на место одной командой (пере-подключение хаба):

```
adb -s 192.168.1.7:5555 shell "su 0 sh -c 'echo 0 > /sys/bus/usb/devices/2-1/authorized; sleep 4; echo 1 > /sys/bus/usb/devices/2-1/authorized'"
```

и снова запустите музыку. (Если карта воткнута в ГУ напрямую, без хаба — эта инструкция вообще не про ваш случай: напрямую карта на этих ГУ не работает, см. основной документ §2.)

## 10) Вернуть как было (откат)

Откройте в Блокноте страховку `audio_policy_BACKUP.xml` (шаг 3) и сделайте в ней одну замену (Ctrl+H, «Заменить все»):

- Найти: `href="` → Заменить на: `href="/vendor/etc/`

Сохраните как `audio_policy_configuration.xml` (UTF-8). Затем создайте файл `restore_usb_audio.sh` с текстом ниже, залейте оба файла и запустите:

```sh
#!/system/bin/sh
C=/data/local/tmp/audio_policy_configuration.tbox.xml
S=/system/etc/audio_policy_configuration.xml
P=/proc/mmc_debug
wp_on() { echo emmc_enable_all_wp > $P 2>/dev/null; }
fail() { echo "TBOX_USB_ERR $1"; exit 1; }
[ -f "$C" ] || fail nostage
echo emmc_disable_all_wp > "$P" 2>/dev/null || fail wpoff
trap 'wp_on' EXIT
mount -o remount,rw / || fail remountsys
cp "$C" "$S" || fail copysys
chown root:root "$S" && chmod 644 "$S"
chcon u:object_r:system_file:s0 "$S" 2>/dev/null
sync; blockdev --flushbufs /dev/block/mmcblk0 2>/dev/null; echo 3 > /proc/sys/vm/drop_caches
H1=$(md5sum "$C" | cut -d' ' -f1); H2=$(md5sum "$S" | cut -d' ' -f1)
[ "$H1" = "$H2" ] || fail verify
wp_on; trap - EXIT
setprop ctl.restart audioserver || fail restart
echo TBOX_USB_RESTORED
```

```
adb -s 192.168.1.7:5555 push audio_policy_configuration.xml /data/local/tmp/audio_policy_configuration.tbox.xml
adb -s 192.168.1.7:5555 push restore_usb_audio.sh /data/local/tmp/restore_usb_audio.sh
adb -s 192.168.1.7:5555 shell "su 0 sh /data/local/tmp/restore_usb_audio.sh"
```

USB-звук выключится, штатный звук вернётся. Проще всего откатывать кнопкой «Вернуть штатную политику» в приложении «USB-аудио ГУ» — она делает то же самое. (Временное включение из TBox Monitor снимается его же кнопкой «Выключить (снять наложение)».)
