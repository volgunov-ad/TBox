# Автозапуск после загрузки: исследование на реальном ГУ

Этот документ фиксирует результаты исследования автозапуска TBox Monitor после включения питания на головном устройстве **Jetour X50 (Android 9, mbCAN)**, выполненные на его основе правки и приёмы отладки. Проверялось установленное приложение v1.0.0 (release 10005).

Устройство: Android 9 (API 28), build `PQ3B.190801.002 test-keys`, **production** (`adb root` запрещён), ADB over TCP (`persist.adb.tcp.port=5555`, соединение восстанавливается после перезагрузки).

## 1) Окружение автозапуска

- **HOME-лаунчер — `ras.dashing.tbox.launcher`** — форк TBox Monitor с теми же классами `vad.dashing.tbox.*` (свой `BackgroundService` FGS id 1601 и `LauncherNavAccessibilityService`). Стартует как HOME, а **не** через BOOT receiver.
- Наш процесс дополнительно хостит `dashingineering.jetour.tboxcore.service.TBoxBridgeService` (FGS id 3001): UDP 50041 от TBox (192.168.225.1) ↔ TCP relay на 2 клиентов (лаунчер и др.). `TBoxClient` ходит на 192.168.225.1:50047 (`DEFAULT_TBOX_IP` в `BackgroundService`, `serverPort = 50047`).
- Оба dashing-приложения шлют debug-спам в logcat (`MBCAN_CLIENT_LOG` D, `UdpSocketManager`/`TcpServer`/`TBoxService` V), из-за чего буфер main 256K ротируется за ~2 минуты. `logcat -G 16M` действует только до перезагрузки.

## 2) Цепочка автозапуска и замеры (2 контрольные перезагрузки)

| Этап | Время от старта ядра |
|------|----------------------|
| Ядро (dmesk/kernel) | ~14.7 c |
| `sys.boot_completed` | ~24 c |
| Наш процесс поднят системой (биндинг `MediaControlNotificationListenerService`) | **до** BOOT_COMPLETED |
| NLS `onListenerConnected` → `BackgroundService` FGS (`ACTION_START`, **без** boot-open Main) | сразу после bind NLS (если выдан доступ к уведомлениям) |
| BOOT_COMPLETED → `BootCompleteReceiver` → `BackgroundService` FGS | ~24–35 c (если FGS уже от NLS — только markPending + эпизод Main, без повторного cold-start пайплайна) |
| Собственный пайплайн (onCreate 17 мс + trips 33 мс + listeners 93 мс) | **131 мс** |
| Плавающие панели (`FloatingOverlay.sync`) | +76–80 мс |

Тайминги логируются тегом **`TboxTimings`** (`Timings.*`).

Выводы:

- При выданном Notification Listener: `MediaControlNotificationListenerService.onListenerConnected` поднимает FGS **без** `EXTRA_START_FROM_BOOT` (панели — как при обычном старте службы; Main по boot-эпизоду только после BOOT/QUICKBOOT).
- **Ускорять в приложении нечего** для ожидания самой прошивки: собственный вклад пайплайна — ~0.2 с; NLS лишь сдвигает старт FGS раньше доставки `BOOT_COMPLETED`, если OEM уже биндит listener.
- На одной из загрузок `BackgroundService` отсутствовал ~3 мин после boot — вероятная причина: задержка/потеря доставки `BOOT_COMPLETED` из-за crash-storm `com.autopai.iottube` (см. §5). После отключения iottube воспроизведений не было.

## 3) Sticky-рестарт (`START_STICKY`)

Проверено через `am crash vad.dashing.tbox` + стриминг logcat с хоста:

- система перезапускает сервис за ~1 с, полный пайплайн выполняется заново (те же 131 мс);
- плавающие панели восстанавливаются **без** открытия главного экрана;
- главный экран откроется только если загрузочный эпизод (§4) ещё pending — т.е. в пределах окна загрузки, а не после ручного краша в произвольный момент. Это ожидаемое поведение.

## 4) Эпизод «открыть главный экран после загрузки»

`MainScreenBootOpen.kt`: повторы `RETRY_GAPS_MS = 0/2/5/15/30 c`, бюджет эпизода `MAX_EPISODE_MS = 30 c`. На исследуемом ГУ настройка была выключена (открываются только панели поверх лаунчера).

Правки по итогам исследования:

- **Бюджет эпизода учитывает задержку запуска**: `MainScreenBootOpenPolicy.newDeadlineWithInitialDelayMs(initialDelayMs, now)` = `now + max(0, delay) + MAX_EPISODE_MS`; `BackgroundService.runBootOpenMainEpisode` продлевает дедлайн через `MainScreenBootOpenStore.extendDeadlineTo` (только в большую сторону). Раньше при задержке ≥30 с эпизод истекал до первой попытки.
- **Включён `QUICKBOOT_POWERON`** (раскомментирована ветка в `BootCompleteReceiver`, action добавлен в intent-filter манифеста).

## 5) Вредные системные пакеты

- **`com.autopai.iottube`** (телематика Chery/TINNOVE, flags `SYSTEM HAS_CODE PERSISTENT`): crash-loop каждые ~31 с — `fastjson JSONException: syntax error, expect {` на облачном ответе. Забивал все 1000 слотов dropbox (~2800 крашей/сутки), грузил систему.
- **`com.iflytek.cutefly.speechclient.hmi`** (голосовой ассистент, PERSISTENT, uid system): постоянно ~28% CPU.

### Метод отключения (без root)

`pm disable-user` и `pm hide` **не действуют мгновенно**: OEM AMS игнорирует их для persistent-приложений и перезапускает процесс (`Start proc … for restart`). `am force-stop` у iflytek вообще не срабатывал (PID не менялся). `pm uninstall --user 0` после disable возвращает `not installed for 0`.

Минимальная рабочая последовательность (одна команда на пакет; проверено A/B-тестом с ребутом и контролем `ps -A`/`top` через 60 c после загрузки):

| Состояние пакетов | Процессы после ребута | CPU |
|---|---|---|
| без изменений | стартуют | iflytek ~28–43% |
| только `pm disable-user --user 0` | **стартуют** — недостаточно | iflytek 42.8% |
| `pm hide` только iottube (iflytek включён) | стартует только iflytek | iflytek ~25% |
| **только `pm hide`** (оба) | не стартуют | 0% |
| `disable-user` + `hide` | не стартуют | 0% |

Вариант «скрыт только iottube» пригоден, если нужен голосовой ассистент: iflytek стартует (uid system) и стабильно держит ~25% CPU; iottube не поднимается, новых записей в dropbox нет. Скрытие обоих освобождает CPU полностью (idle 622–648%/800), но отключает голосовой ассистент.

```
adb shell pm hide com.autopai.iottube
adb shell pm hide com.iflytek.cutefly.speechclient.hmi
adb reboot   # обязательна: persistent-список строится при загрузке
```

После перезагрузки оба процесса **не стартуют вообще**, crash-loop прекращается, CPU освобождается (замер: idle вырос до ~648%/800%). Автозапуск TBox Monitor не пострадал: процесс, `BackgroundService` FGS (id 50047, `tbox_background_channel`) и `TBoxBridgeService` FGS (id 3001) поднимаются как раньше.

### Возврат в исходное состояние

Оба пакета работают (как с завода): crash-loop iottube возобновится, iflytek снова займёт ~28–43% CPU, голосовой ассистент вернётся.

```
adb shell pm unhide com.autopai.iottube
adb shell pm unhide com.iflytek.cutefly.speechclient.hmi
adb shell pm enable com.autopai.iottube                     # только если использовался disable-user
adb shell pm enable com.iflytek.cutefly.speechclient.hmi    # только если использовался disable-user
adb reboot                                                  # обязательна: persistent-список строится при загрузке
```

Проверка после ребута:

```
adb shell dumpsys package com.autopai.iottube | grep "User 0:"                       # enabled=1 hidden=false
adb shell dumpsys package com.iflytek.cutefly.speechclient.hmi | grep "User 0:"      # enabled=1 hidden=false
adb shell "ps -A | grep -E 'iottube|iflytek'"                                        # оба процесса запущены
```

Промежуточный вариант «голосовой ассистент без телематики» — вернуть только iflytek:

```
adb shell pm unhide com.iflytek.cutefly.speechclient.hmi
adb reboot   # iottube остаётся скрытым: iflytek стартует (~25% CPU), crash-loop не возвращается
```

### Влияние на общее время загрузки ГУ (A/B, по 2 загрузки на состояние)

Точная метрика — внутренние маркеры `logcat -b events -d | grep boot_progress` (мс от старта ядра). Wall-clock `adb reboot` → `sys.boot_completed` не годится: graceful shutdown длится 11–24 с непредсказуемо, плюс поздний подъём adbd TCP (замеры 26.8–35.9 с — только верхняя граница).

| Маркер | Пакеты включены | Пакеты отключены |
|---|---|---|
| `boot_progress_start` | 5031–5033 | 4968–5128 |
| `boot_progress_ams_ready` | 9335–9350 | 9206–9489 |
| `sf_stop_bootanim` (готовый UI) | 11978–12023 | 11817–12202 |
| `framework_boot_completed` | 12 с | 12 с |

**Время загрузки не изменилось** (~12 с до готового UI в обоих состояниях): пакеты грузят CPU уже после загрузки, а не во время неё. Выигрыш от отключения — стабильность (нет crash-loop и заполнения dropbox) и освобождение ~30–40% CPU на работающей системе, а не скорость старта.

### Готовность нашего фонового стека (A/B)

Метод: `getprop ro.runtime.firstboot` (epoch мс старта runtime) + `logcat -d -v epoch | grep TboxTimings`; elapsed = таймстамп строки − firstboot. Caveat: при включённых пакетах main-буфер ротируется спамом за ~2 мин, ранние строки теряются — ориентироваться на **последнюю** строку `TboxTimings`.

| Состояние | Последняя `TboxTimings` (готовность стека) | `FloatingOverlay.sync` |
|---|---|---|
| оба включены | +5.63 с | +4.57 с |
| скрыт только iottube | +5.64 с | +4.57 с |
| оба скрыты | +5.64 с | +4.57 с |

На загрузке «скрыт только iottube» уцелела и стартовая строка лаунчера: `Timings.startup` завершён на **+0.72 с** от firstboot (startup_trips_ready 18 мс, tbox_connected 7 мс, listeners 154 мс, `startup_running` +235 мс), панели (`FloatingOverlay.sync`) — +0.88 с в этой загрузке.

**Готовность нашего стека не изменилась** во всех состояниях: основной вклад (~12 с) — подъём системы до BOOT_COMPLETED, поверх которого пакеты не добавляют задержки.

### Остальные штатные приложения (A9, стенд 192.168.1.128, 31.10.2026)

Проверялось, есть ли кроме iottube и iflytek ещё штатные пакеты, которые стоит скрыть ради скорости загрузки. Состояние стенда: iottube, iflytek и SystemUI скрыты (`pm hide`), стоят «Пустые обои» ([SYSTEMUI_A9_DISABLE_RU.md](SYSTEMUI_A9_DISABLE_RU.md)), HOME — Dashing Club Launcher (`ras.dashing.tbox.launcher`).

**Методика.** Нагрузку по процессам снимает `tools/hu_perf/hu_proc_cpu.ps1`: на ГУ по всем `/proc/<pid>/stat` берутся время старта (поле 22), накопленное CPU (`utime + stime`, поля 14–15) и RSS, на ПК — сортировка по CPU. Снимки делаются на заданных секундах аптайма (45 и 120 с после перезагрузки) и на работающей системе (~21 мин). Кто и почему стартует при загрузке — из `am_proc_start` в `logcat -b events` (причина: `added application` — persistent-пакет, `broadcast` — холодный старт ради приёмника, `service`, `content provider`). Очередь `BOOT_COMPLETED` видна в main-логе: прошивка пишет каждый манифестный приёмник строкой `WTL_LoadedApkPlugs: receive Intent { act=android.intent.action.BOOT_COMPLETED … cmp=… }`. Метки времени пересчитываются через `boot_progress_start`, как в [HU_PERF_TESTS_RU.md](HU_PERF_TESTS_RU.md). Один A/B-прогон целиком (перезагрузка, снимки, `MemAvailable`, маркеры, очередь) — `tools/hu_perf/hu_boot_ab.ps1 -Tag <имя>`.

**Что стартует при загрузке.** Около 20 штатных persistent-приложений (`com.mengbo.*`, `com.wt.*`, телефон, BT, `com.android.launcherWT`) поднимаются на 10,7–12,1 с параллельно, на 8 ядрах они друг другу не мешают. Экран готов на ~11,1 с (`sf_stop_bootanim` сразу за `boot_progress_enable_screen`) — это время задаёт фреймворк, штатные пакеты на него не влияют. Сами штатные приложения лёгкие: за 21 минуту работы ни одно не набрало больше 2,5 с CPU, кроме локального медиаплеера `com.wt.multimedia.local` (24 с, ~2 % одного ядра); RSS у каждого 80–150 МБ, свободной памяти после загрузки ~4,8 ГБ из 7,8 ГБ. Все штатные Java-приложения вместе за первые 2 минуты набирают ~25 с CPU из 252 с, тогда как один лаунчер — 93–95 с: он постоянно занимает ~0,7 ядра на отрисовку 3D-модели (см. [HU_PERF_TESTS_RU.md](HU_PERF_TESTS_RU.md)).

**Очередь `BOOT_COMPLETED`.** Манифестные приёмники вызываются по одному, холодный старт процесса ради приёмника задерживает всех, кто стоит за ним. Очередь идёт с 13,7 до 24,0 с (51 приёмник). `vad.dashing.tbox/.BootCompleteReceiver` получает рассылку на 14,1 с, раньше всех медленных, а `BackgroundService` к этому моменту обычно уже поднят через Notification Listener (§2). Самые длинные паузы дают сторонние приложения: холодный старт RuStore (`ru.vk.store`, `com.kavsdk.StartReceiver`) — 2,9 с, плюс 14 с CPU за первые 40 с; Яндекс Навигатора — 2,7 с. Из штатных заметен только `com.wt.multimedia.local` (0,9 с), остальные штатные приёмники занимают по 0,1–0,25 с.

**A/B: 9 ненужных на ГУ штатных пакетов скрыты (`pm hide`) и на месте** (по одной загрузке на состояние):

| | Пакеты на месте | 9 пакетов скрыты |
|---|---|---|
| Экран готов (`sf_stop_bootanim`) | 11,16 с | 11,30 с |
| Конец очереди `BOOT_COMPLETED` | 24,0 с | 23,0 с |
| Процессов запущено при загрузке (`am_proc_start`) | 60 | 53 |
| CPU всех процессов за первые 120 с | 252,3 с | 252,0 с |
| CPU штатных Java-приложений за 120 с | 25,5 с | 22,0 с |
| `MemAvailable` на 120 с | 4,76 ГБ | 4,80 ГБ |

**Время до экрана от штатных пакетов не зависит** (разница 0,14 с — в пределах разброса). Скрытие лишь укорачивает хвост очереди `BOOT_COMPLETED` примерно на 1 с, и это касается только приложений в самом её конце (Навигатор); ни экран, ни лаунчер, ни TBox Monitor раньше не появляются. Других «тяжёлых» штатных пакетов, кроме iottube и iflytek, на A9 нет.

Пакеты, скрывавшиеся в тесте, — можно скрыть ради порядка (без выигрыша в скорости), сбоев на стенде не было:

| Пакет | Назначение |
|---|---|
| `com.android.cellbroadcastreceiver` | экстренные оповещения оператора |
| `com.android.managedprovisioning` | настройка рабочего профиля |
| `com.android.onetimeinitializer` | разовая инициализация после первого запуска |
| `com.android.traceur` | системная трассировка (System Tracing) |
| `com.android.printspooler` | печать |
| `com.qualcomm.qti.qms.service.connectionsecurity` | телеметрия Qualcomm |
| `com.qualcomm.qti.qms.service.telemetry` | телеметрия Qualcomm |
| `com.wt.smartlog` | сбор логов WT |
| `com.wt.multimedia.local` | штатный плеер USB-музыки — скрывать, только если не используется |
| `com.wt.launcher3` | штатный лаунчер — только в порядке `pm enable` → `pm hide`, откат `disable-user` до `unhide` (см. ниже) |

#### Штатный лаунчер `com.wt.launcher3` (A/B, по 2 загрузки на состояние)

`com.wt.launcher3` — штатный «рабочий стол с карточками» WT (`/system/app/WT_Launcher3`, версия 2.1.0.ov): `sharedUserId=android.uid.system` (uid 1000), флаг `PERSISTENT`. В нём HOME-activity и список приложений; в карточки рабочего стола встраиваются activity других приложений (карта и музыка `com.android.launcherWT`, медиа-карточка, климат, ADAS). Наружу он отдаёт провайдер `content://com.wt.launcher3/launcher3` (ссылки на него есть в `WT_MLVideoOpen` и `boot-framework`; при `disabled-user` он и так не публикуется) и два приёмника голосового SDK Tencent WeCar; своих сервисов нет. Запускает его сам system_server как persistent-пакет (`am_proc_start … added application`), другие приложения к нему не биндятся.

На стенде лаунчер был в состоянии `disabled-user`, и, как у iottube/iflytek, это **не мешает старту процесса**: он поднимается на ~12 с и выполняет только инициализацию `Application` (провайдер не публикуется, CPU 0,2–0,3 с за 2 минуты, PSS ~17 МБ; RSS 91 МБ — в основном общие страницы zygote). Остановить его можно только скрытием: `pm enable` + `pm hide` + перезагрузка — после этого процесс не стартует вообще.

| | `disabled-user` (процесс стартует) | `enable` + `hide` |
|---|---|---|
| Экран готов (`boot_progress_enable_screen` / `sf_stop_bootanim`) | 11,07–11,20 / 11,09–11,24 с | 11,13–11,19 / 11,16–11,21 с |
| `framework_boot_completed` | 12,44 / 12,38 с | 12,28 / 12,28 с |
| Старт процессов DCL / TBox | 12,52 / 12,56 и 12,43 / 12,52 с | 12,34 / 12,36 с (оба прогона) |
| Процесс `com.wt.launcher3` | стартует на ~12 с | не стартует |
| Процессов запущено при загрузке (`am_proc_start`) | 61 / 62 | 58 / 60 |
| Java-приложений на 120 с | 57 | 56 |
| CPU всех процессов за первые 120 с | 254,6 с | 257,2–258,8 с |
| Простой CPU (idle) на 120 с | 652,7–654,7 с | 651,8–655,4 с |
| `MemAvailable` на 120 с | 4,76 ГБ | 4,77 ГБ (+12 МБ) |
| Free RAM по `dumpsys meminfo` | 4,80 ГБ | +18…33 МБ |
| Очередь `BOOT_COMPLETED` (51 приёмник) | 13,7–13,8 → 24,08 с | 13,5–13,7 → 24,04–24,24 с |
| Краши, ANR, перезапуски, ошибки про launcher3 | 0 | 0 |

**Скрытие даёт только память, загрузка не меняется**: разница в маркерах и CPU — в пределах разброса между прогонами (idle одинаковый, расхождения — в kworker'ах ядра и Huawei Maps). После скрытия в логах загрузки launcher3 не упоминается вовсе, набор ошибок тот же, `com.android.launcherWT` стартует как прежде, `KEYCODE_HOME` открывает DCL. Методика — как у `hu_boot_ab.ps1`, плюс после загрузки проверка HOME (`input keyevent KEYCODE_HOME` → `mResumedActivity`), скриншот и подсчёт `FATAL EXCEPTION`/`am_crash`/`am_anr`/«Failed to find provider info» в логах.

**Важно про HOME.** DCL был HOME по умолчанию только потому, что launcher3 выключен (единственный включённый кандидат с `CATEGORY_DEFAULT`; `com.mengbo.electronicmanual` без DEFAULT, `FallbackHome` с приоритетом −1000). Сразу после `pm enable` HOME становится неоднозначным: `cmd package resolve-activity … -c android.intent.category.HOME` возвращает `android/com.android.internal.app.ResolverActivity` (диалог выбора). После `pm hide` HOME снова DCL. В preferred activities остаётся старая запись `com.wt.launcher3/.MainActivity` с `mAlways=false` — на выбор она не влияет. Поэтому `enable` и `hide` выполнять подряд, а при желании закрепить DCL явно, чтобы HOME не зависел от набора включённых лаунчеров:

```
adb shell pm enable com.wt.launcher3
adb shell pm hide com.wt.launcher3
adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME   # ras.dashing.tbox.launcher/...
adb shell cmd package set-home-activity ras.dashing.tbox.launcher/vad.dashing.tbox.LauncherHomeActivity       # опционально
adb reboot   # процесс останавливается только после перезагрузки
```

Откат к прежнему состоянию (`disabled-user`, процесс снова стартует). Порядок важен: `disable-user` — **до** `unhide`, иначе на время между командами HOME станет неоднозначным:

```
adb shell pm disable-user --user 0 com.wt.launcher3
adb shell pm unhide com.wt.launcher3
adb reboot
```

Полный возврат штатного лаунчера: `adb shell pm unhide com.wt.launcher3` + `adb reboot`; если DCL не закреплён через `set-home-activity`, нажатие HOME покажет диалог выбора лаунчера.

Не проверено на машине: процесс launcher3 подписывался на `com.coagent.intent.action.ACC_CHANGED`, `android.intent.action.POWER_OFF` и `bring_multimedia_card_to_font`. Судя по набору, это обслуживание его собственного рабочего стола, но после скрытия стоит убедиться, что включение/выключение зажигания отрабатывает нормально.

Не трогать — пакеты, связанные с машиной, BT и телефоном: `com.android.car`, `com.mengbo.carsettings`, `com.mengbo.acsettings`, `com.mengbo.avm`, `com.wt.wtservice`, `com.wt.virtualcar`, `com.nforetek.bt`, `com.autopai.car.dialer`, `com.android.phone`. Их нагрузка на загрузку минимальна, а функции ГУ от них зависят.

Скрыть и вернуть тестовый набор:

```
adb shell pm hide <пакет>      # для каждого пакета из таблицы
adb shell pm unhide <пакет>
adb reboot                     # persistent-список строится при загрузке
```

## 6) Приёмы отладки на этом ГУ

- `am broadcast -a vad.dashing.tbox.DEBUG_BOOT_COMPLETED` срабатывает **только при открытой foreground MainActivity** — из cached-состояния блокируется «Background execution not allowed».
- `BackgroundService` не exported: `am start-foreground-service` → «Requires permission not exported».
- Полный цикл загрузки замерять так: запустить стриминг лога на хосте (напр., PowerShell `Start-Process adb logcat`), затем `adb reboot`/`am crash`.
- Toybox `grep` на устройстве не понимает `\|` — использовать `grep -E 'a|b'`.
- Фильтр сервисов по пакету: `dumpsys activity services vad.dashing.tbox/vad.dashing.tbox.BackgroundService` (без суффикса попадают и сервисы лаунчера-форка).
