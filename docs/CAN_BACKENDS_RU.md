# CAN backends: mbCAN и VHAL

Этот документ описывает, как в приложении выбирается стек CAN, как работает общий репозиторий и как выполняются подключение/чтение/запись для двух вариантов головного устройства:

- **Android 9**: через `mbCAN`.
- **Android 10**: через `android.car` / VHAL (`CarPropertyManager`).

Таблицы всех **используемых** property (чтение/запись, raw-декод, push/pull): [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).  
Сводная таблица **scale/offset** формул (TBox + mbCAN + VHAL): [RAW_VALUE_FORMULAS_RU.md](RAW_VALUE_FORMULAS_RU.md).  
Каталог штатных APK / package name прошивки **Android 9 mbCAN** (X50 V000000279): [STOCK_APPS_ANDROID9_MBCAN_RU.md](STOCK_APPS_ANDROID9_MBCAN_RU.md).  
Каталог штатных APK / package name прошивки **Android 10 VHAL** (Adayo): [STOCK_APPS_ANDROID10_VHAL_RU.md](STOCK_APPS_ANDROID10_VHAL_RU.md).

### Пометка про «Android 10» (Adayo)

В проекте и в UI настроек название **«Android 10»** означает линейку ГУ **Adayo + VHAL** (в отличие от mbCAN). Это **продуктовое** имя, его оставляем.

По выгрузке штатной прошивки Adayo (`D:\Dashing\Android10-VHAL`):

- в заводском/сервисном UI версия часто берётся из `Build.VERSION.RELEASE` (например `SystemSettings` → factory, строка «Android版本» / `and_vertion`) и может отображаться как **10**;
- при этом платформенный уровень API у штатных APK ориентирован на **API 28** (Pie): у `Launcher` `minSdkVersion`/`targetSdkVersion` = **28**; `FactoryMode` показывает и `SDK_INT`, и `RELEASE` отдельно;
- `build.prop` в локальной выгрузке отсутствует, поэтому точный `ro.build.version.sdk` с устройства здесь не зафиксирован, но стек приложений и ключи Settings (`adayo_skin`, VHAL) соответствуют Adayo-линейке, а не «чистому» Android 10 AOSP.

Итого: **не путать** маркетинговую/штатную надпись «Android 10» с `Build.VERSION.SDK_INT == 29`. Выбор бэкенда в TBox — ручной/авто через `HeadUnitCanMode`, а не только по `SDK_INT`.

---

## 1) Выбор между mbCAN и VHAL

Источник выбора режима:

- `HeadUnitCanMode`:
  - `Android9MbCan`
  - `Android10Vhal`
- настройка хранится в `DataStore` (через `SettingsManager` / `SettingsViewModel`).

Дополнительно к ручному выбору работает автоподбор backend, пока режим ещё не закреплён:

- на старте, если режим **не закреплён**, выполняется цикл `3 + 3`:
  - 3 попытки bind для сохранённого режима;
  - при неуспехе — автопереключение на альтернативный режим и ещё 3 попытки;
  - если оба backend неуспешны — возврат в исходный режим **без закрепления** (`locked_after_fail:` в `can_auto_bind_last_result`); следующий старт снова проверит обе схемы. Старые сборки в этом случае ставили `can_auto_bind_locked`; такую блокировку с `locked_after_fail:` политика тоже не считает закреплением.
- режим **закрепляется** и больше не переключается на другую схему, если:
  - bind этого режима хотя бы раз завершился успехом (`primary_ok` / `alternative_ok` / `pinned_ok` в `can_auto_bind_last_result`, либо уже выставлен `can_auto_bind_locked`);
  - пользователь вручную выбрал Android 9 или Android 10 (тот же `can_auto_bind_locked`, результат `user:<mode>`).
- отдельного нового флага нет: достаточно `can_auto_bind_locked`. Успех прошлых запусков тоже считается закреплением. Режим берётся из `can_auto_bind_last_result` (`primary_ok` / `alternative_ok` / `pinned_ok` / `user`), а не из текущего значения: неудачный автоподбор записывает альтернативную схему ещё до того, как она подключится.
- пока режим закреплён, старт делает **3 попытки того же режима** и не пробует альтернативу. Временный обрыв VHAL режим не меняет. Неудачный старт закреплённого режима не затирает прежнюю запись об успехе в `can_auto_bind_last_result`.
- первая попытка режима отключает другой backend, который служба подняла по сохранённому значению до автоподбора: mbCAN и VHAL не работают одновременно.
- если эти 3 попытки не подняли закреплённый режим (и если на первом запуске не поднялись оба backend), тот же режим повторяется **каждые 10 с**, пока не подключится, пока пользователь не сменит схему или пока служба не остановится. На другую схему эти поздние попытки не переключают. Смена схемы проверяется на каждом тике, поэтому запоздавшее событие настроек со старым значением, которое сразу сменилось обратно, повторы не останавливает.
- между быстрыми попытками выдерживается пауза `1.2s`;
- окно одной попытки после возврата из `bind()` — `3.5s` (с финальной проверкой `warmUpAvailabilityForUi()` перед fail). Само ожидание `onServiceConnected` на A10 идёт внутри `bind()` и может занять до 8 с;
- `SettingsManager` хранит служебные поля:
  - `can_auto_bind_enabled`,
  - `can_auto_bind_locked`,
  - `can_auto_bind_last_primary_mode`,
  - `can_auto_bind_last_result`.

Где применяется:

- `TboxApplication` и UI настроек подписываются на `headUnitCanModeFlow` и вызывают `UniversalCanRepository.setMode(...)`;
- **`bind()` и автоfallback `autoResolveModeOnStartup()` выполняются в `BackgroundService.onCreate`** — только там поднимается реальное подключение к mbCAN/VHAL;
- в UI переключатель находится в настройках (две кнопки: Android 9 / Android 10);
- при выбранном **Android 10** дополнительно показывается **«Запускать TBox Monitor в окне приложений»** (`launch_main_in_stock_app_window`, по умолчанию **вкл.**): программные открытия `MainActivity` (автозапуск главного экрана, плавающие панели, возврат из плеера, выход из freeform □, виджеты через router, broadcast `show` и т.п.) идут через `com.adayo.launcher.LAUNCH_APP` → ActivityView; выкл. — прямой fullscreen. Системный ярлык / недавние не перехватываются. Код: `MainActivityIntentHelper.bringToFront`, `LaunchMainInStockAppWindowSetting`, `AdayoStockAppWindow`;
- `can_auto_bind_enabled` по умолчанию **включён** (отдельного переключателя в UI нет);
- если режим в DataStore не задан, используется **Android 9 (mbCAN)**.

Поведение при переключении:

- `UniversalCanRepository` переключает активный backend;
- для предыдущего backend вызывается `unbind()`;
- для нового backend вызывается `bind(...)`.

---

## 2) Общий репозиторий (`UniversalCanRepository`)

`UniversalCanRepository` — единая точка доступа для UI и сервисов, чтобы код виджетов/настроек не зависел от конкретного транспорта.

Что он делает:

- хранит текущий `mode` (`StateFlow<HeadUnitCanMode>`);
- делегирует команды и чтение в:
  - `MbCanRepository` (Android 9),
  - `Android10VhalRepository` (Android 10);
- предоставляет единые `StateFlow` для состояний (подогревы, сиденья, drive mode, аудио и т.д.);
- предоставляет единые `StateFlow` для RPM (`engineRpmState`) в режимах mbCAN/VHAL;
- управляет `bind/unbind`, `setSourceSignals`, `execute(...)`, `setAudioVolume(...)`.

Синхронизация переключений:

- `setMode`, `bind`, `unbind`, `warmUpAvailabilityForUi`, `autoResolveModeOnStartup` сериализованы единым `modeSwitchMutex`;
- это убирает гонки rebind между `headUnitCanModeFlow` и автоfallback.

Зачем это нужно:

- UI-виджеты и экран настроек работают через один API;
- добавление/исправление backend не требует переписывать все composable и service-слой.

### 2.1 Ключевые функции и аргументы (`UniversalCanRepository`)

- `setMode(mode: HeadUnitCanMode)` *(suspend)*  
  `mode` — `Android9MbCan` или `Android10Vhal`.
- `bind(scope: CoroutineScope)` / `unbind()`  
  `scope` — корутинный scope сервиса/приложения для фоновых job.
- `setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>)`  
  `sourceId` — идентификатор экрана/панели; `widgetKeys` — набор `dataKey` активных виджетов.
- `setSourceSignals(sourceId: String, signals: Set<MbCanSignal>)`  
  Явная подписка на сигналы (например, `AudioVolume`, `EngineRpm`), когда нужно не через `dataKey`.
- `refreshSignalsNow(signals: Collection<MbCanSignal>)` *(suspend)*  
  Немедленный последовательный pull (видимая секция «Настройки авто»). Native/VHAL get остаётся на apply-потоке.
- `execute(command: MbCanCommand): MbCanCommandResult`  
  `command` — `ToggleProperty/SetProperty/ToggleAudioProperty/SetAudioProperty/RefreshSignal`.  
  На A9 (`MbCanRepository`) native get/set идут на `mbcan-state-apply` (не main): Car Settings может звать `execute` с Main. VHAL не переключается.
- `setAudioVolume(value: Int): MbCanCommandResult`  
  `value` — целевая громкость. На A9 тоже native get/set на `mbcan-state-apply`.
- `autoResolveModeOnStartup(settingsManager: SettingsManager, scope: CoroutineScope)`  
  пока режим не закреплён — автоподбор `3+3`; после успеха или ручного выбора — только повтор того же режима.
- `enqueueClearSource(sourceId: String)`  
  снимает интересы источника с debounce **3 минуты** (одинаково в обоих backend).
- `widgetConfigsNeedMbCan(dataKeys: Set<String>)`  
  проверяет, нужны ли mbCAN/VHAL для набора `dataKey` плиток.

Операции `setSourceWidgetKeys`, `setSourceSignals`, `execute`, `setAudioVolume` **не** сериализуются `modeSwitchMutex` — только переключение режима и bind/unbind.

---

## 3) Как работает Android 9 backend (`MbCanRepository`)

**Жёсткие ограничения JNI (нагрузка / SIGABRT):** см. [MBCAN_JNI_THREADING_RU.md](MBCAN_JNI_THREADING_RU.md). Кратко: OEM get/set **не** с main и **не** чаще poll `MbCanJobManager`; mixer ≠ mbCAN.

Доступ к vendor API идёт через **reflection** (`MbCanEngineFacade`), а не через прямой compile-time import классов mbCAN.

Логика:

1. `bind(...)` проверяет наличие классов mbCAN (`probeAvailability` → `Unknown` до первой инициализации).
2. Подписки/источники сигналов управляют, какие данные нужно обновлять.
3. Чтение параметров идёт через mbCAN API (`canGet...`).
4. Запись команд идёт через mbCAN API (`canSet...`) с политиками допустимых значений из `MbCanCommandRegistry`.
5. Сырые значения декодируются в доменные состояния (`MbCanSignalStateEngine`).

Особенности:

- propertyId в этом режиме — legacy mbCAN ids (`MbCanKnownVehiclePropertyId`, `MbCanKnownAudioPropertyId`);
- поведение старого ГУ не должно изменяться при развитии VHAL backend.

### 3.1 Подписка push (callback) в mbCAN

В `mbCAN` используется комбинированная модель:

- **push callback** от vendor-движка (через `IMBCmdListener.onCmdChanged`);
- **poll** как страховка и для периодической валидации состояния.

Как это реализовано:

- `MbCanRepository` через `MbCanEngineFacade.sync*CmdListener(...)` включает callback-listener только когда есть активные интересы сигналов;
- для RPM дополнительно используется callback `onVehicleEngineStatusChange(MBCanVehicleEngine)` и polling чтение `MBCanVehicleEngine.getfSpeed()`;
- входящие push-события буферизуются и коалесцируются:
  - `PUSH_STATE_COALESCE_MS = 200 ms` для применения в `StateFlow`,
  - `PUSH_DEBUG_LOG_COALESCE_MS = 1000 ms` для debug-логов push;
- после коалесса значения применяются в `StateFlow` на отдельном single-thread dispatcher.

Что означает `PUSH_STATE_COALESCE_MS`:

- это окно времени, в течение которого backend собирает несколько быстрых push-событий по одному и тому же сигналу и применяет в `StateFlow` только последнее значение;
- с `200 ms` UI получает стабильные обновления без лишней "дребезги" и без потери актуального состояния;
- это не задержка polling-цикла и не таймаут подключения — только анти-спам для push-path записи в state.

Ключевые вызовы в mbCAN:

- `MbCanEngineFacade.subscribe(dataTypeNames: Set<String>)` / `unSubscribe(...)`  
  `dataTypeNames` — enum-имена vendor-типа, например `eMBCAN_CFG_VEHICLE`, `eMBCAN_CFG_AUDIO`, `eMBCAN_VEHICLE_ENGINE`.
- `MbCanEngineFacade.syncVehicleCfgCmdListener(active: Boolean)`  
  Подключает/отключает `IMBCmdListener` для push по `eMBCAN_CFG_VEHICLE`.
- `MbCanEngineFacade.syncAudioCfgCmdListener(active: Boolean)`  
  Подключает/отключает `IMBCmdListener` для push по `eMBCAN_CFG_AUDIO`.
- `MbCanEngineFacade.registerSettingsTelemetryBridge()` / `unregisterSettingsTelemetryBridge()`  
  Включает callback `onVehicleEngineStatusChange(MBCanVehicleEngine)` (и др. telemetry push).  
  **Важно (A9):** в callback только разбор payload; повторный `getMbCanData` / `read*` запрещён — при «нет данных» (IFC=0, DTE≤0, sentinel температуры) re-entrant binder ломал push/CFG. Актуальные значения без поля в push — через poll `MbCanJobManager`.
- `MbCanEngineFacade.canGetVehicleParam(propertyId: Int): Int?` / `canSetVehicleParam(propertyId: Int, value: Int): Int?`
- `MbCanEngineFacade.canGetAudioParam(propertyId: Int): Int?` / `canSetAudioParam(propertyId: Int, value: Int): Int?`  
  OEM JNI **не thread-safe**: get/set сериализуются lock’ом в фасаде и должны вызываться с `mbcan-state-apply` (как `refreshSignal` и `MbCanRepository.execute` / `setAudioVolume`), **не с main**. Подголовник A9 (`PlatformAudioRepository`) уходит туда же; mixer OpenOS — отдельный poll **500 ms**, не mbCAN.
- `MbCanEngineFacade.readVehicleEngineRpm(): Float?`  
  Читает RPM через `getMbCanData(22, MBCanVehicleEngine.class)` и `MBCanVehicleEngine.getfSpeed()` (только poll / не из push-callback).

### 3.2 Poll interval в mbCAN

Интервалы polling задаёт `MbCanJobManager`:

- `NORMAL_POLL_MS = 30_000 ms` (обычный режим);
- `BURST_POLL_MS = 1_500 ms` (ускоренный режим после команд);
- `BURST_DURATION_MS = 15_000 ms` (длительность burst-окна).

То есть после команды чтение идёт чаще (1.5 сек), затем возвращается к 30 сек.

---

## 4) Как работает Android 10 backend (`Android10VhalRepository`)

### 4.1 Подключение к Car/VHAL

Используется схема, совместимая со штатными приложениями прошивки. В коде доступ к `Car` / `CarPropertyManager` идёт через **reflection** (`CarPropertyBridge`), чтобы не зависеть от compile SDK с полным Android Car API.

- `Car.createCar(Context, ServiceConnection)` (основной путь),
- `car.connect()`,
- ожидание `onServiceConnected`: выход сразу по колбэку (на тёплом старте около 2 с). Первая попытка за процесс ждёт до **30 с**, последующие — до **8 с**. На холодном старте Car-сервис отвечает через 5–18 с после `sys.boot_completed=1`, примерно к рассылке `BOOT_COMPLETED`. Один `Car` дожидается этого момента, а не пересоздаётся каждые несколько секунд,
- получение property manager через `getCarManager("property")` (до **20** повторов по 100 ms).

Notification Listener может поднять `BackgroundService` до `sys.boot_completed`. На A9 mbCAN это безвредно, на A10 Car-сервис к этому моменту часто ещё не готов. Первый connect VHAL в таком случае ждёт свойство `sys.boot_completed=1`, но не дольше **25 с** (опрос каждые 500 мс), и только один раз за процесс. Если загрузка уже завершена или свойство прочитать нельзя, паузы нет: рестарт службы на работающем ГУ не откладывается.

Каждый `CarPropertyBridge` получает номер `session=N`. Он есть в строках `Using Car.createCar`, `Car service connected/disconnected`, `Car service connection timeout`, `VHAL connected`, `VHAL connect failed` и `VHAL disconnected`, так что поздний колбэк сопоставляется со своей попыткой.

Если `onServiceConnected` не пришёл за отведённое время, в журнал пишется `Car service connection timeout` с `waitedMs` и `limitMs`, и `getCarManager` в этой попытке уже не вызывается. Неудачная попытка всегда вызывает `car.disconnect()` (объект `Car` сохраняется до `connect()`), иначе каждая попытка оставляла бы привязку к Car-сервису. Если `getCarManager` падает после колбэка, `VHAL connect failed` содержит `serviceConnected` и цепочку причин: `InvocationTargetException` разворачивается до `targetException` / `cause`.

Итог попытки и проверка «уже подключено» перед поздним повтором читаются из `availability` самого backend, а не из общей `stateIn`-копии: копия ещё мгновение держит прошлый `Unavailable`, и следующая попытка из-за этого делала `disconnect` уже поднятому VHAL.

`onServiceDisconnected` переподключает только текущую сессию. Колбэк от уже брошенной попытки connect не рвёт живое подключение и не запускает второй connect.

Функции/аргументы подключения:

- `Car.createCar(context: Context, serviceConnection: ServiceConnection)`
- `car.connect()` / `car.disconnect()`
- `getCarManager(serviceName: String)`  
  `serviceName` = `"property"` (`Car.PROPERTY_SERVICE`).

Если подключение не удалось:

- `availability = Unavailable(...)`;
- причина логируется в `TboxRepository.addLog` с тегом `VHAL_A10`.

### 4.2 Чтение и polling

- backend отслеживает активные источники сигналов;
- при наличии источников запускается polling-цикл (`POLL_INTERVAL_MS`);
- для каждого сигнала вызывается `refreshSignal(...)`;
- чтение делается через reflective `getIntProperty` / `getFloatProperty(propertyId, areaId=0)`.
- для **RPM, температуры и скорости** используются **фиксированные firmware ID** из `FirmwareVehicleJsonMapper`, а не стандартные `VehiclePropertyIds`:
  - RPM: `289_414_951` (`R_0900_EMS_1_EngineSpd`), после чтения умножается на **4** (`VHAL_ENGINE_RPM_SCALE`);
  - температура: `289_414_949`;
  - скорость: `557_845_547` (`MCU_REPLY_SPEED`, штатный SystemSettings `AdayoCanManager`);
    **км/ч = raw as-is** (INT32 ≥ 0). Поездки, mockLocation (`TripTelemetry`) и виджеты берут
    `UniversalCanRepository.carSpeedState` — тот же HU-путь.
  - угол руля: `557_845_548` (`MCU_REPLY_STEERING_WHEEL_ANGLE`); **° = raw as-is**;
    скорость вращения руля на A10 недоступна (`steerSpeedState` = null).
- Справочная копия стандартных ID: `docs/reference/VehiclePropertyIds.java` — для команд управления, не для этой телеметрии.

Ключевые вызовы чтения/записи:

- `CarPropertyManager.getIntProperty(propertyId: Int, areaId: Int)`
- `CarPropertyManager.setIntProperty(propertyId: Int, areaId: Int, value: Int)`
- `registerListener(listener, propertyId, 0.0f)` / `unregisterListener(listener, propertyId)`  
  `0.0f` — on-change rate, как в штатных приложениях.

### 4.3 Подписка push (callback) в VHAL

Реализована комбинированная схема (как в штатных приложениях `CarSettings` / `AirConditioning`):

- после `Car.createCar(..., ServiceConnection)` и получения `CarPropertyManager` выполняется
  `registerListener(listener, propertyId, rateHz)` для активных `propertyId`;
- входящие `onChangeEvent` коалесцируются:
  - `PUSH_STATE_COALESCE_MS = 200 ms` для применения в `StateFlow`,
  - `PUSH_DEBUG_LOG_COALESCE_MS = 1000 ms` для debug-логов push;
- ошибки `onErrorEvent` логируются в `TboxRepository` (`VHAL_A10`);
- при изменении набора виджетов/сигналов список подписок пересобирается (`register/unregister`).

Детали регистрации:

- `rateHz` выбирается по типу property через `VhalPushRatePolicy`:
  - телеметрия (RPM/скорость/руль/темп. двигателя) — continuous `1 Hz`, fallback `5 Hz`;
  - дискретные переключатели (ADAS LDW/TJA/HMA, HVAC, багажник, …) — **только on-change `0.0f`**,
    без эскалации до 1/5 Hz (иначе панели с этими виджетами держат binder-трафик даже на стоянке);
- перед подпиской логируется конфиг property (`changeMode/access/minRate/maxRate/areaIds`);
- proxy-listener явно обрабатывает `hashCode/equals/toString`, чтобы исключить NPE при `registerListener` на некоторых HU-сборках.

### 4.4 Poll interval в VHAL

Интервалы такие же, как в `mbCAN`:

- `NORMAL_POLL_INTERVAL_MS = 30_000 ms`;
- `BURST_POLL_INTERVAL_MS = 1_500 ms`;
- `BURST_DURATION_MS = 15_000 ms`.

Polling остаётся fallback-механизмом: даже при push-событиях выполняется периодическая валидация состояний.
После успешных команд (`set/toggle`) запускается burst-окно, затем интервал возвращается к 30 сек.

### 4.5 Запись команд

Команды (`ToggleProperty`, `SetProperty`, аудио-команды) идут через:

- `CarPropertyManager.setIntProperty(propertyId, areaId=0, value)`.

Перед записью применяется резолвинг `propertyId` (см. раздел 5).

### 4.6 Диагностика и логи

Диагностика `mbCAN` и `VHAL` включается **единой** опцией (`ACTION_SET_MBCAN_DIAGNOSTICS`):

- `ERROR` / `WARN` / `INFO` в тегах `MBCAN_TMP` и `VHAL_A10` пишутся **всегда** (с учётом глобального минимального уровня журнала);
- подробный `DEBUG` (`MBCAN_TMP` / `VHAL_A10`) — только когда включён флаг `MbCanDiagnostics.enabled`;
- `TripTelemetryRepository` раз в **15 с** всегда пишет DEBUG с тегом `TripFuel` (источник HU/TBox по сигналам учёта поездок/заправок + текущие значения trip-репо) — **не** зависит от флага диагностики CAN;
- жизненный цикл поездок (`start` / `resume` / `end` / …) пишет DEBUG с тегом `Trip` через `TboxRepository`, тоже без флага диагностики;
- флаг диагностики сессионный (не сохраняется между перезапусками `BackgroundService`).

#### 4.6.1 Расширенная диагностика (`ACTION_SET_MBCAN_DEEP_DIAGNOSTICS`)

Отдельный сессионный режим «Расширенная диагностика mbCAN/VHAL» (тумблер в настройках рядом с обычной диагностикой, только в экспертном режиме). Включение автоматически включает и обычную диагностику; выключение обычной диагностики выключает и расширенную.

Что делает (только чтение, ничего не пишет в автомобиль):

- единый источник списков — `DeepDiagnosticsCatalog`;
- **A10 (VHAL)**: подписывает все property id из каталога (константы `FirmwareVehicleJsonMapper` + `explicitReadIdMap` + экспериментальные id) через отдельный deep-listener (не рабочий `syncPushSubscriptions`), порциями по 10 id с паузой 500 мс, rate = on-change (`0.0f`);
- **A9 (mbCAN)**: подписывает все `MBCanDataType` из каталога через refcount `MbCanJobManager.setDeepTypes`; неизвестные OEM-сборке имена отбрасываются с WARN; raw `onCmdChanged` для не-CFG типов приходит через отдельные `IMBCmdListener`-прокси (`startDeepCmdListeners`) — **но OEM `registCMDListener` реально хранит listeners только для CFG_***; для `eMBCAN_VEHICLE_DOOR` / `eMBCAN_SEAT_BELT_STATUS` deep включает typed path (`registCarDorListener` + poll `getMbCanData`); для `eMBCAN_CFG_VEHICLE` / `eMBCAN_CFG_AUDIO` — fan-out из production-листенеров (`setCfgCmdDeepDiagnosticListener`), потому что OEM `unRegistCMDListener(type)` чистит тип целиком; object-снимки пишутся как `mbcan dt=… object=…`;
- события пишутся в DEBUG-журнал тегами `CANDIAG_VHAL` / `CANDIAG_MBCAN` через `DeepCanDiagnostics` (машиночитаемый формат `propertyId=… areaId=… value=… type=… status=… name=…` / `dt=… modular=… rev=… item=… value=… name=…` / `dt=… object=…`), с delta-фильтром, окнами коалессинга 1 с (дискретные) / 5 с (быстрая телеметрия), кольцевым буфером 3000 строк и счётчиком `suppressed=`;
- write-only `T_*` id (импульсы MFS, SLA req) в каталоге намеренно отсутствуют.
- **A10 deep experimental** также включает CEM door ajar / hood / lock и ICM/ABM seat-belt property id (см. `DeepDiagnosticsCatalog.vhalExperimentalIdNames`).
- **A10**: дополнительно подписываются все 374 `R_*` id таблицы прошивки (`VhalFirmwareReadIds`, сгенерирован из `docs/reference/VehiclePropertyIds.java`); имя `R_…` идёт в `name=`, если у id нет короткого имени приложения. Id, которые VHAL не принимает, попадают в `failures=` итоговой строки.
- **A9 object mirror**: payload всех OEM push-колбэков (`IMBCanSettingsCallback`, `IMBVehicleListener`, LKA/SLA, FRM, Gasped, двери) разбирается рефлексией по полям (`MbCanObjectDump`, без вызова getter-ов и JNI) и пишется построчно `mbcan dt=<MBCanDataType> field=<path> value=<v>`; каждое поле — отдельный delta-ключ, так что пуш BCM пишет только изменившиеся поля. Метод без известного типа пишется как `dt=cb.<method>`. Пока режим включён, эти мосты регистрируются независимо от виджетов (`reapplyAllInterests`, флаг `deep`). В каталог добавлены типы `GASPED_STATUS`, `EPB_STATUS`, `FUELTANK`, `ICM_FAULT_INFO`, `ICM_ALARM_INFO`, `CEM_FRAG`, `AVM_STATUS`, `CHIME_STATUS`, `INSTRUMENT_CMDREPLY`.
- **A9 extra callbacks** на время режима: AVM, BSD, DOW, RCTA, радар, Chime, ICM alarm, DVR param — через публичные register/unregister (каждый снимает только свой тип). Сиденья, DVR status, system mode и PM2.5 приходят через поля `mbCanVehicleAccStatusCallback` и `mbAirPurgeListener`: они ставятся напрямую, без `registACCListener` / `registIMBAirPurgeListener`, потому что их unregister отписывает BCM.
- **A9 baseline**: при включении один проход по `mbcan-state-apply` читает все объекты (`getMbCanData`), все vehicle/audio int и байтовые массивы `canGetVehicleValue`, если в массиве больше одного байта (`baseline.vehicleBytes`). Пауза 15 мс между чтениями. Дальше раз в 30 с опрашиваются типы без push (`DeepDiagnosticsCatalog.mbcanPollOnlyDataTypes`: ICM drive info, EPB, DTC, gear, radio, instrument, DMS, engine gear).
- **A10 property list**: при включении пишется `vhal config propertyId=… access=… changeMode=… areas=… type=… name=…` по `CarPropertyManager.getPropertyList`. Читаемые id (access read / read-write) добавляются к подписке каталога. До подписки снимается начальное значение каждого id (`getProperty`, иначе int/float/boolean), до 4 area. Окно журнала хранит 500 строк: для разбора включите непрерывную запись.
- **Метка**: вкладка журнала, кнопка «Метка», строка `CANDIAG_MARK` `MARK <текст>`. `tools/app_log_mbcan_to_xlsx.py` строит лист Marks: сигналы, значение которых изменилось в ±3 с вокруг метки.

Логи `VHAL_A10` содержат:

- `bind/unbind`, старт/стоп polling;
- какой overload `Car.createCar` выбран;
- успешность connect и текущая `availability`;
- read/write ошибки с `propertyId`, `areaId`, `value`;
- попытки команд и итог `result=true/false`.

При `SecurityException` лог помечается как `POSSIBLE_PERMISSION`.

---

## 5) Маппинг propertyId для Android 10

В VHAL режиме нельзя использовать legacy mbCAN ids напрямую. Для этого используется `FirmwareVehicleJsonMapper`.

Источники ID:

- **Команды записи/чтения настроек** — явные таблицы `explicitWriteIdMap` / `explicitReadIdMap` и membership в `send.json` / `receive.json` прошивки; reference: `docs/reference/VehiclePropertyIds.java`.
- **Телеметрия RPM/температура/скорость** — константы в `FirmwareVehicleJsonMapper` (см. §4.2), не из `VehiclePropertyIds.ENGINE_RPM` (`291504901`).
- Эвристического «угадывания» семантики по числу ID **нет** — только явный map или попадание в таблицы прошивки.
- mbCAN ID берутся из vendor-библиотеки `com.mengbo.mbCan`:
  - `com.mengbo.mbCan.defines.*` (типы/enum/константы),
  - `com.mengbo.mbCan.entity.*` (структуры данных, например `MBCanVehicleEngine`),
  - плюс наши внутренние маппинги `MbCanKnownVehiclePropertyId` / `MbCanKnownAudioPropertyId`.

Источники маппинга:

- `send.json` и `receive.json` из прошивки (`/system/etc/adayo/vehicle/...`);
- явные таблицы `explicitWriteIdMap` / `explicitReadIdMap`, собранные по `AirConditioning` и `CarSettings`.

Алгоритм резолвинга:

1. сначала проверяется явный mapping (`explicit*Map`);
2. если нет явного — fallback по наличию id в таблицах прошивки;
3. если id неразрешим — операция возвращает ошибку/неуспех.

Это позволяет:

- сохранить совместимость команд приложения;
- направлять их в фактические VHAL `propertyId` новой прошивки.

---

## 6) Права и ограничения

Для доступа к части car properties нужны `android.car.permission.*`.

Важно:

- наличие `uses-permission` в `AndroidManifest` может быть **недостаточно**;
- на некоторых ГУ/прошивках права дополнительно ограничены системной политикой (`car_service`, подпись, privileged app).

Практический индикатор:

- если в логах `POSSIBLE_PERMISSION ... SecurityException ... requires android.car.permission...`,
  значит доступ к конкретному property заблокирован на уровне системы.

---

## 7) Поток данных end-to-end

1. Пользователь выбирает режим ГУ в настройках.
2. `SettingsManager` публикует режим.
3. `UniversalCanRepository` переключает backend.
4. UI/Service выставляют набор интересующих сигналов (`setSourceWidgetKeys` с панелей, `setSourceSignals` из настроек авто и `DriveModeThemeWatcher`).
5. Backend читает/пишет данные:
   - Android 9: mbCAN API,
   - Android 10: VHAL (`CarPropertyManager`) с firmware mapping.
6. Обновлённые `StateFlow` попадают в виджеты и экраны.

---

## 8) useMbCanVhal в виджетах

`FloatingDashboardWidgetConfig.useMbCanVhal` доступен только для типов, перечисленных в
`WidgetsRepository.supportsUseMbCanVhal(...)`:

- `engineRPM`
- `engineTemperature`
- `carSpeed`
- `odometer`
- `fuelLevelPercentage`
- `outsideTemperature`
- `wheelsPressureWidget` / `wheelsPressureTemperatureWidget` / `wheel1…4Pressure` / `wheel1…4Temperature`
- `currentFuelConsumption`
- `distanceToNextMaintenance`
- `distanceToFuelEmpty`
- `insideAirQuality` / `outsideAirQuality` / `airQualityWidget`
- `steerAngle` / `steerSpeed` (A9 mbCAN + A10 MCU angle; A10 без °/с)

Поведение:

- при `useMbCanVhal = false` виджет использует обычный источник (например, `engineRPM` из CAN-frame pipeline);
- при `useMbCanVhal = true` виджет работает через `UniversalCanRepository` (mbCAN/VHAL backend);
- для таких виджетов панель регистрирует соответствующие CAN interests через `setSourceSignals(...)`.
- `enqueueClearSource(...)` в обоих backend работает с одинаковым debounce (`3 минуты`), чтобы поведение интересов не расходилось между mbCAN/VHAL.
- при настройке **«Не подключаться к TBox»** (`no_tbox_connect`) для новых/вставленных/импортированных из темы eligible-плиток флаг по умолчанию **вкл.**; при включении режима можно массово включить его на уже стоящих плитках (см. [TBOX_PROXY_RU.md](TBOX_PROXY_RU.md)).

Какие именно сигналы и функции используются:

- `engineRPM`
  - interest: `MbCanSignal.EngineRpm`
  - чтение: `UniversalCanRepository.engineRpmState`
  - запись не используется (read-only сигнал).
- `engineTemperature`
  - interest: `MbCanSignal.EngineTemperature`
  - чтение: `UniversalCanRepository.engineTemperatureState`
  - запись не используется (read-only сигнал).
- `carSpeed`
  - interest: `MbCanSignal.CarSpeed`
  - чтение: `UniversalCanRepository.carSpeedState`
  - запись не используется (read-only сигнал).
- `gearBoxMode`
  - interest: `MbCanSignal.VehicleGear` (+ попутно `MbCanSignal.ReverseGearSwitch` для флага задней)
  - чтение: `UniversalCanRepository.gearBoxModeState` (`P`/`R`/`N`/`D`)
  - флаг: `UniversalCanRepository.reverseGearSwitchState` (для DR / mock location)
  - enhanced mock: опция «Учитывать заднюю передачу» → `VehicleGearDomain.isReverseEngaged` (HU PRND → switch → TBox); не действует в режиме «Прямой»
  - запись не используется (read-only сигнал).
- `odometer`
  - interest: `MbCanSignal.TotalOdometer`
  - чтение: `UniversalCanRepository.odometerKmState`
- `fuelLevelPercentage`
  - interest: `MbCanSignal.FuelLevel`
  - чтение: `UniversalCanRepository.fuelLevelPercentState`
- `outsideTemperature`
  - interest: `MbCanSignal.OutsideTemperature`
  - чтение: `UniversalCanRepository.outsideTemperatureState`
- `currentFuelConsumption`
  - interest: `MbCanSignal.CurrentFuelConsumption`
  - чтение: `UniversalCanRepository.currentFuelConsumptionState` (raw/10)
- `distanceToNextMaintenance`
  - interest: `MbCanSignal.DistanceToNextMaintenance`
  - чтение: `UniversalCanRepository.distanceToNextMaintenanceKmState`
- `distanceToFuelEmpty`
  - interest: `MbCanSignal.DistanceToFuelEmpty`
  - чтение: `UniversalCanRepository.distanceToFuelEmptyKmState` (A10: км as-is)
- `insideAirQuality` / `outsideAirQuality` / `airQualityWidget`
  - interest: `MbCanSignal.Pm25AirQuality`
  - чтение: `UniversalCanRepository.insideAirQualityState` / `outsideAirQualityState`
- `steerAngle` / `steerSpeed`
  - interest: `MbCanSignal.SteeringAngle`
  - чтение: `UniversalCanRepository.steerAngleState` / `steerSpeedState`
    (A9: угол+скорость; A10: угол из `MCU_REPLY_STEERING_WHEEL_ANGLE`, `steerSpeed` null)

Отдельно от `useMbCanVhal`: виджет `averageFuelConsumption` выбирает источник в «Дополнительно»
(`avgFuelConsumptionSource`): mbCAN/VHAL (кластерный ICM_4, interest `MbCanSignal.AverageFuelConsumption`),
текущая поездка или суточная поездка. Флаг `useMbCanVhal` для этого типа не показывается.

Полный список штатных VHAL push-подписок (ID/имена), извлечённый из `CarSettings`/`AirConditioning`/`Launcher`,
сохранён отдельно: `docs/STOCK_PUSH_SUBSCRIPTIONS_RU.md`.

---

## 9) Что проверять при диагностике

Минимальный чеклист по логам:

1. Есть `VHAL connected session=N propertyService=property`, и после него нет `VHAL disconnected session=N` с тем же номером.
2. Есть `Availability: AVAILABLE`.
3. Есть `polling started: signals=...` при открытии виджетов.
4. Для push-пути есть `VHAL push onChange propertyId=...` (если property поддерживает push).
   В текущей реализации вместо частых одиночных строк ожидается агрегированный debug-лог
   `VHAL push coalesced[...]` (раз в ~1 секунду при активном потоке событий).
5. Для команды есть `SetProperty request=...` и `SetProperty result=true`.
6. Нет циклических `InvocationTargetException` / `POSSIBLE_PERMISSION` / `registerListener ... NullPointerException`.

Если пункты 1-2 не выполняются:

- проблема на этапе подключения `Car`.

Если 1-2 есть, но пункт 4 не выполняется:

- проблема в правах на property или в маппинге id.
