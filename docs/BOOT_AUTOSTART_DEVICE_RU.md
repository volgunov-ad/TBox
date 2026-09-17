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
| BOOT_COMPLETED → `BootCompleteReceiver` → `BackgroundService` FGS | ~24–35 c |
| Собственный пайплайн (onCreate 17 мс + trips 33 мс + listeners 93 мс) | **131 мс** |
| Плавающие панели (`FloatingOverlay.sync`) | +76–80 мс |

Тайминги логируются тегом **`TboxTimings`** (`Timings.*`).

Выводы:

- **Ускорять в приложении нечего**: собственный вклад — ~0.2 с, вся задержка определяется прошивкой до `BOOT_COMPLETED`.
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

## 6) Приёмы отладки на этом ГУ

- `am broadcast -a vad.dashing.tbox.DEBUG_BOOT_COMPLETED` срабатывает **только при открытой foreground MainActivity** — из cached-состояния блокируется «Background execution not allowed».
- `BackgroundService` не exported: `am start-foreground-service` → «Requires permission not exported».
- Полный цикл загрузки замерять так: запустить стриминг лога на хосте (напр., PowerShell `Start-Process adb logcat`), затем `adb reboot`/`am crash`.
- Toybox `grep` на устройстве не понимает `\|` — использовать `grep -E 'a|b'`.
- Фильтр сервисов по пакету: `dumpsys activity services vad.dashing.tbox/vad.dashing.tbox.BackgroundService` (без суффикса попадают и сервисы лаунчера-форка).
