# Камеры A9: чистый кадр через ais_server

Проверено 6 ноября 2026 на головном устройстве Android 9 (`192.168.1.128:5555`, `sm6150_au`, `PQ3B.190801.002 test-keys`). Код приложений при этой проверке не менялся.

Две разные картинки:

| Путь | Что это | Кто показывает |
|------|---------|----------------|
| Чистый кадр сенсора | Клиент Qualcomm AIS. Кадр UYVY в буфере клиента или аппаратное окно на экране | `ais_server` → `AVMapp`, `qcarcam_test`, `mb_cameraservice` |
| Превью для приложений | Уже обработанный буфер: callback SDK Mengbo и файлы `*_sharecamera` | `:camview` (`MBAvm` / `MBOms` / `MBDms`) |

Штатная «Камера 360» (`com.mengbo.avm`) — Java-оболочка. Сам поток держит ранний процесс `AVMapp`.

## 1. Кто за что отвечает

Снято `ps -AZ` в той же сессии.

| Процесс | Домен / uid | Бинарник | Роль |
|---------|-------------|----------|------|
| `ais_server` | `u:r:ais_server:s0` / root | `/early_services/bin/ais_server` | Владеет сенсорами. Слушает сокеты, отдаёт кадры клиентам |
| `AVMapp` | `u:r:shell:s0` / root | `/early_services/system/bin/AVMapp` | Клиент входов 4, 5, 8, 9. Лог-тег `avmcamera`, библиотека `libmbcamera_avm.so` → `libais_client.so` |
| `oms_service` | `u:r:shell:s0` / root | `/early_services/system/bin/oms_service` | Камера салона |
| `dms_service` | `u:r:shell:s0` / root | `/early_services/system/bin/dms_service` | Камера водителя |
| `mb_cameraservice` | `u:r:shell:s0` / root | `/vendor/bin/mb_cameraservice` | Обёртка Mengbo: `libmbcamera.so` → `libais_client_proprietary.so`. Шарит кадры (`camera%d.shm`, `MBTransmit`) |
| `qcarcam_test` | запускается вручную | `/system/bin/qcarcam_test` (копия есть в `/early_services/system/bin`) | Тестовый клиент Qualcomm. Читает XML и рисует окно. Библиотеки `libais_client.so`, `libais_test_util.so` |
| `mb_camview_service` | в сессии не запущен | `/vendor/bin/mb_camview_service` | Сокетный сервер `MB_CAMERAVIEW_SERVER`, память `camview_sharecamera` |
| `com.mengbo.avm` | system | APK | Оболочка AVM, не держатель сенсора |
| `vt_dvr` | `u:r:shell:s0` / root | `/system/bin/vt_dvr` | Отдельный процесс видеорегистратора. В этой проверке не разбирался |

`ais_server` пишет кадр сенсору `max96712mb` (десериализатор) и `imx390`. Живой AVM-кадр из лога: fmt `0x7080102`, 1280×800, stride 2560, 25 fps. Stride 2560 = 1280×2, это UYVY.

Рядом с сокетами лежат однобайтные файлы-метки, не сокеты: `/early_services/dev/socket/fdavminit`, `fdomsinit`. В строках библиотек есть ещё `fddmsinit` и `fdapainit`; в момент проверки на диске их не было. `libmbcamera_avm.so` читает оттуда тип привода (`Avm camera nDriveValue`).

Общая память превью (раздел `earlydata`, он rw):

| Файл | Размер |
|------|--------|
| `/early_services/earlydata/avm_sharecamera` | 9 830 400 |
| `/early_services/earlydata/oms_sharecamera` | 2 048 003 |
| `/early_services/earlydata/dms_sharecamera` | 2 048 003 |

Права `-------rwx`, контекст `unlabeled`. Это буфер, который читает `:camview`, а не окно `qcarcam_test`.

## 2. Запрет на подключение

Сокеты: `/early_services/dev/socket/camera/ais_socket_*`.

На диске они `srwxr-xr-x`, владелец `root:root`. Подключиться может только root: у сокета нет бита записи для группы и для остальных. Каталог сокетов — `drwxrwxr-x root:camera`, но сами файлы сокетов группе `camera` запись не дают.

`adb shell` (uid 2000, домен `shell`) доходит до `qcarcam_initialize` и получает отказ:

```
ais_conn_connect  -6  errno 13
ais_client_create -1 -2
qcarcam_initialize failed 4
```

Errno 13 — `EACCES`. SELinux на этом ГУ `Permissive`: отказы пишутся в лог и не блокируют. Когда тот же `qcarcam_test` запущен от root из `mb_daemon` (домен `u:r:mb_daemon:s0`), в логе есть `avc: denied { connectto }` на `ais_socket_0`, и следом соединение проходит.

`adb root` на этой прошивке не работает (`adbd cannot run as root in production builds`). Бинарника `su` нет. Root-запуск — `mb_daemon`, как в [HEAD_UNITS_DEVICE_NOTES_RU.md](HEAD_UNITS_DEVICE_NOTES_RU.md) §10: скрипт с LF в `/sdcard/autorun.sh`, флаг-файл, `setprop vendor.autorun.sdcard.logfile`.

На время проверки права сокетов один раз ставились в `0666` и сразу возвращались. После проверки снова `srwxr-xr-x root:root`, каталог `drwxrwxr-x root:camera`. Рабочий клиент в удачном прогоне был root и в смене прав не нуждался.

`AVMapp` и `mb_cameraservice` тоже домен `shell`, но uid root, поэтому DAC их пускает. Обычное приложение (`untrusted_app`, отдельный uid) в сокет не попадает, пока файл принадлежит root и закрыт на запись.

## 3. XML окон

Готовые конфиги Qualcomm лежат в `/vendor/bin/*.xml`. Раздел `/vendor` смонтирован только для чтения, `/early_services` тоже ro, `earlydata` — rw. Дописывать файл в `/vendor/bin` без перемонтирования нельзя, и это не нужно: `qcarcam_test` принимает `-config=` с любым путём.

Проверенный файл: `/data/local/tmp/tbox_in0.xml`. Разбор занял 21 мс.

Положение окна — доли экрана, не пиксели:

```xml
<qcarcam_inputs>
    <input_device>
        <properties input_id="0"/>
        <display_setting display_id="0" nbufs="3"
            window_width="0.28" window_height="0.28"
            window_pos_x="0.02" window_pos_y="0.70" />
        <output_setting width="1488" height="616" nbufs="5" />
    </input_device>
</qcarcam_inputs>
```

`window_*` читается один раз при старте (`xml parsing` в логе ровно один раз). Правка файла у живого процесса окно не двигает. Новое место — остановить `qcarcam_test` и запустить снова с другим XML. В интерактивном меню теста есть дамп, экспозиция, fps, гамма, hue, saturation и выход; команды сдвинуть окно нет.

Штатные файлы и входы (снято `cat` с ГУ):

| Файл | input | Кадр | Окно |
|------|-------|------|------|
| `1cam.xml` | 0 | 1920×1024 | почти весь `display_id=0`, есть `src_*` |
| `1cam1488X616ar.xml` | 0 | 1488×616 | 0.22×0.30 в точке (0.22, 0), display 0 |
| `1cam_pproc_nv12.xml` | 0, `op_mode=1` | 1928×1208 NV12 | display 1 |
| `2cam1280X720oms.xml` | 1 | 1280×720 | 0.22×0.30 в (0.22, 0.35), display 1 |
| `4cam1280X800dms.xml`, `4cam1280X800rvc.xml` | 3 | 1280×800 | 0.22×0.30 в (0.22, 0.70), display 0 |
| `5cam1280X800avmf.xml`, `5cam1920x720apa.xml` | 4 | 1280×800 или 1920×720 | 0.22×0.30 в (0.45, 0) |
| `6cam1280X800avmb.xml` | 5 | 1280×800 | 0.22×0.30 в (0.68, 0) |
| `6cam3840X1080mdcfbc.xml` | 5 | 3840×1081 | то же окно, `src_width=2` |
| `9cam1280X800avml.xml` | 8 | 1280×800 | 0.22×0.30 в (0.45, 0.35) |
| `10cam1280X800avmr.xml` | 9 | 1280×800 | 0.22×0.30 в (0.68, 0.35) |
| `56910cam1280X800-avm.xml` | 4, 5, 8, 9 | 1280×800 | четыре полосы по 0.25 ширины на display 0 |
| `8cam.xml` | 0, 1, 2, 3 и 4, 5, 8, 9 | 1920×1024 | четверти на display 0 и display 1 |
| `ais_v4l2loopback_config.xml` | 0 и 1 | 1488×616 UYVY | не экран, узлы `/dev/video51`, `/dev/video52` |
| `ais_v4l2loopback_mdc_config.xml` | 5 дважды | 3840×1081 UYVY | те же video-узлы |

Имена AVM-файлов совпадают с тем, что держит `AVMapp`: перед 4, зад 5, лево 8, право 9.

Рядом лежат ещё `1cam1488x616-ar.xml`, `2cam1280x720p-oms.xml`, `4cam1280X722dms.xml`, `4cam1280x800-dms.xml` и `5cam1280X720mdcavm.xml`. Их содержимое побайтно не снималось; по именам это те же входы, что уже есть в таблице.

## 4. Что получилось запустить

От root, вход 0, конфиг из `/data/local/tmp` (камера 1488×616, не камеры кругового обзора):

| Шаг | Результат |
|-----|-----------|
| `qcarcam_open` входа 0 | 809 мс, успех |
| `qcarcam_s_buffers` | 3 мс |
| `qcarcam_s_param` | 1 мс |
| `qcarcam_start` | 697 мс |
| `-seconds=4 -nonInteractive -noDisplay` | лог: `TEST Aborted after running for 4 secs successfully` |
| `-seconds=6 -nonInteractive -dumpFrame=2` (окно по XML включено) | те же 6 секунд `successfully` |

Код выхода процесса при этом `1`: так завершается штатный `abort_test` по таймеру, даже после успешного прогона.

Ранние строки второго прогона (инициализация дисплейного окна) из logcat вытеснил другой поток логов. Ошибки `display window init` в сохранённом хвосте нет. Стеклянный экран с ПК не виден, поэтому факт пикселей в углу экрана этим логом не доказан. Поток при этом стартовал: это видно по первому прогону, где строки `open`/`start` сохранились.

Входы 4, 5, 8 и 9 в этой сессии открывал `AVMapp`. Повторный клиент на тот же номер отдельным экспериментом не делался. В логе `ais_server` по этим входам и без постороннего клиента уже были таймаут `qcarcam_get_frame` (`Ret=6`), `qcarcam_start Ret=1` и `sensor failed to start streaming` с пересозданием контекста. Пока AVM их держит, второй `qcarcam_open` того же `input_id` запускать не стоит.

## 5. Запись

В `qcarcam_test` есть флаг `-dumpFrame=` и строка пути `/data/vendor/camera/frame_%d_%i.raw`. Интерактивная клавиша `s` — «Dump Image». В `libmbcamera_avm.so` есть `MBCamera_DumpImage` и `MBCamera_DumpVideo` (шаблон имени `camera_%d_%d.raw`).

Прогон `-dumpFrame=2` от root каталог `/data/vendor/camera` оставил пустым (`total 0`). Из `adb shell` этот каталог не читается (`Permission denied`). Файл записи этим флагом не подтверждён. Кадр в буфер клиента при этом приходит: `qcarcam_start` входа 0 прошёл.

## 6. Как открыть и закрыть окно

Из TBox напрямую сокет недоступен: у процесса приложения нет uid root. Рабочая схема на сегодня — root-скрипт через `mb_daemon`:

```
qcarcam_test -config=/data/local/tmp/окно.xml -nonInteractive
```

Закрытие — завершение этого процесса. `-seconds=N -nonInteractive` сам останавливается через N секунд. Полезные флаги из строк бинарника: `-config=`, `-seconds=`, `-nonInteractive`, `-noDisplay`, `-printfps`, `-dumpFrame=`, `-pauseResume=`, `-startStop=`.

Смена места окна — новый XML и новый запуск, не правка файла на лету.

## 7. Приложение `:camd`

Описание, план и что уже сделано: [CAMD_RU.md](CAMD_RU.md).
