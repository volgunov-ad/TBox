# CAN backends: mbCAN и VHAL

Этот документ описывает, как в приложении выбирается стек CAN, как работает общий репозиторий и как выполняются подключение/чтение/запись для двух вариантов головного устройства:

- **Android 9**: через `mbCAN`.
- **Android 10**: через `android.car` / VHAL (`CarPropertyManager`).

---

## 1) Выбор между mbCAN и VHAL

Источник выбора режима:

- `HeadUnitCanMode`:
  - `Android9MbCan`
  - `Android10Vhal`
- настройка хранится в `DataStore` (через `SettingsManager` / `SettingsViewModel`).

Где применяется:

- при старте приложения (`TboxApplication`) режим считывается и передаётся в `UniversalCanRepository.setMode(...)`;
- в `BackgroundService` режим также синхронизируется, чтобы фоновые операции шли через правильный backend;
- в UI переключатель находится в настройках (две кнопки: Android 9 / Android 10).

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

Зачем это нужно:

- UI-виджеты и экран настроек работают через один API;
- добавление/исправление backend не требует переписывать все composable и service-слой.

### 2.1 Ключевые функции и аргументы (`UniversalCanRepository`)

- `setMode(mode: HeadUnitCanMode)`  
  `mode` — `Android9MbCan` или `Android10Vhal`.
- `bind(scope: CoroutineScope)` / `unbind()`  
  `scope` — корутинный scope сервиса/приложения для фоновых job.
- `setSourceWidgetKeys(sourceId: String, widgetKeys: Set<String>)`  
  `sourceId` — идентификатор экрана/панели; `widgetKeys` — набор `dataKey` активных виджетов.
- `setSourceSignals(sourceId: String, signals: Set<MbCanSignal>)`  
  Явная подписка на сигналы (например, `AudioVolume`, `EngineRpm`), когда нужно не через `dataKey`.
- `execute(command: MbCanCommand): MbCanCommandResult`  
  `command` — `ToggleProperty/SetProperty/ToggleAudioProperty/SetAudioProperty/RefreshSignal`.
- `setAudioVolume(value: Int): MbCanCommandResult`  
  `value` — целевая громкость.

---

## 3) Как работает Android 9 backend (`MbCanRepository`)

Логика:

1. `bind(...)` подключает mbCAN-движок.
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
  - `CFG_VEHICLE_PUSH_COALESCE_MS = 100 ms`,
  - `CFG_AUDIO_PUSH_COALESCE_MS = 100 ms`;
- после коалесса значения применяются в `StateFlow` на отдельном single-thread dispatcher.

Ключевые вызовы в mbCAN:

- `MbCanEngineFacade.subscribe(dataTypeNames: Set<String>)` / `unSubscribe(...)`  
  `dataTypeNames` — enum-имена vendor-типа, например `eMBCAN_CFG_VEHICLE`, `eMBCAN_CFG_AUDIO`, `eMBCAN_VEHICLE_ENGINE`.
- `MbCanEngineFacade.syncVehicleCfgCmdListener(active: Boolean)`  
  Подключает/отключает `IMBCmdListener` для push по `eMBCAN_CFG_VEHICLE`.
- `MbCanEngineFacade.syncAudioCfgCmdListener(active: Boolean)`  
  Подключает/отключает `IMBCmdListener` для push по `eMBCAN_CFG_AUDIO`.
- `MbCanEngineFacade.registerSettingsTelemetryBridge()` / `unregisterSettingsTelemetryBridge()`  
  Включает callback `onVehicleEngineStatusChange(MBCanVehicleEngine)` (используется для push RPM).
- `MbCanEngineFacade.canGetVehicleParam(propertyId: Int): Int?` / `canSetVehicleParam(propertyId: Int, value: Int): Int?`
- `MbCanEngineFacade.canGetAudioParam(propertyId: Int): Int?` / `canSetAudioParam(propertyId: Int, value: Int): Int?`
- `MbCanEngineFacade.readVehicleEngineRpm(): Float?`  
  Читает RPM через `getMbCanData(22, MBCanVehicleEngine.class)` и `MBCanVehicleEngine.getfSpeed()`.

### 3.2 Poll interval в mbCAN

Интервалы polling задаёт `MbCanJobManager`:

- `NORMAL_POLL_MS = 30_000 ms` (обычный режим);
- `BURST_POLL_MS = 1_500 ms` (ускоренный режим после команд);
- `BURST_DURATION_MS = 15_000 ms` (длительность burst-окна).

То есть после команды чтение идёт чаще (1.5 сек), затем возвращается к 30 сек.

---

## 4) Как работает Android 10 backend (`Android10VhalRepository`)

### 4.1 Подключение к Car/VHAL

Используется схема, совместимая со штатными приложениями прошивки:

- `Car.createCar(Context, ServiceConnection)` (основной путь),
- fallback: `Car.createCar(Context, ServiceConnection, Handler)`,
- `car.connect()`,
- ожидание `onServiceConnected`,
- получение `CarPropertyManager` через `getCarManager(Car.PROPERTY_SERVICE)`.

Функции/аргументы подключения:

- `Car.createCar(context: Context, serviceConnection: ServiceConnection)`
- `Car.createCar(context: Context, serviceConnection: ServiceConnection, handler: Handler?)`
- `car.connect()` / `car.disconnect()`
- `car.getCarManager(serviceName: String)`  
  `serviceName` обычно `Car.PROPERTY_SERVICE` (`"property"`).

Если подключение не удалось:

- `availability = Unavailable(...)`;
- причина логируется в `TboxRepository.addLog` с тегом `VHAL_A10`.

### 4.2 Чтение и polling

- backend отслеживает активные источники сигналов;
- при наличии источников запускается polling-цикл (`POLL_INTERVAL_MS`);
- для каждого сигнала вызывается `refreshSignal(...)`;
- чтение делается через `CarPropertyManager.getIntProperty(propertyId, areaId=0)`.
- для RPM в VHAL используется `propertyId = 291504901` (`ENGINE_RPM`).

Ключевые вызовы чтения/записи:

- `CarPropertyManager.getIntProperty(propertyId: Int, areaId: Int)`
- `CarPropertyManager.setIntProperty(propertyId: Int, areaId: Int, value: Int)`
- `registerListener(listener, propertyId, 0.0f)` / `unregisterListener(listener, propertyId)`  
  `0.0f` — on-change rate, как в штатных приложениях.

### 4.3 Подписка push (callback) в VHAL

Реализована комбинированная схема (как в штатных приложениях `CarSettings` / `AirConditioning`):

- после `Car.createCar(..., ServiceConnection)` и получения `CarPropertyManager` выполняется
  `registerListener(listener, propertyId, 0.0f)` для активных `propertyId`;
- входящие `onChangeEvent` сразу применяются в `StateFlow` (быстрое push-обновление);
- ошибки `onErrorEvent` логируются в `TboxRepository` (`VHAL_A10`);
- при изменении набора виджетов/сигналов список подписок пересобирается (`register/unregister`).

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

- `MBCAN_TMP` и `VHAL_A10` пишутся только когда включён флаг `MbCanDiagnostics.enabled`;
- флаг сессионный (не сохраняется между перезапусками `BackgroundService`).

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

- VHAL ID берутся из `android.car.VehiclePropertyIds` штатной прошивки.
  - локальная reference-копия в проекте: `docs/reference/VehiclePropertyIds.java`
  - исходный файл: `D:\Dashing\CarSettings\sources\android\car\VehiclePropertyIds.java`
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
4. UI/Service выставляют набор интересующих сигналов.
5. Backend читает/пишет данные:
   - Android 9: mbCAN API,
   - Android 10: VHAL (`CarPropertyManager`) с firmware mapping.
6. Обновлённые `StateFlow` попадают в виджеты и экраны.

---

## 8) useMbCanVhal в виджетах

`FloatingDashboardWidgetConfig.useMbCanVhal` доступен только для типов, перечисленных в
`WidgetsRepository.supportsUseMbCanVhal(...)`:

- `mediaVolumeWidgetHorizontal`
- `mediaVolumeWidgetVertical`
- `engineRPM`
- `engineTemperature`
- `carSpeed`

Поведение:

- при `useMbCanVhal = false` виджет использует обычный источник (например, `engineRPM` из CAN-frame pipeline);
- при `useMbCanVhal = true` виджет работает через `UniversalCanRepository` (mbCAN/VHAL backend);
- для таких виджетов панель регистрирует соответствующие CAN interests через `setSourceSignals(...)`.

Какие именно сигналы и функции используются:

- `mediaVolumeWidgetHorizontal` / `mediaVolumeWidgetVertical`
  - interest: `MbCanSignal.AudioVolume`
  - чтение: `UniversalCanRepository.audioVolumeState`
  - запись: `UniversalCanRepository.setAudioVolume(value: Int)`
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

Полный список штатных VHAL push-подписок (ID/имена), извлечённый из `CarSettings`/`AirConditioning`/`Launcher`,
сохранён отдельно: `docs/STOCK_PUSH_SUBSCRIPTIONS_RU.md`.

---

## 9) Что проверять при диагностике

Минимальный чеклист по логам:

1. Есть `VHAL connected, propertyService=property`.
2. Есть `Availability: AVAILABLE`.
3. Есть `polling started: signals=...` при открытии виджетов.
4. Для команды есть `SetProperty request=...` и `SetProperty result=true`.
5. Нет циклических `InvocationTargetException` / `POSSIBLE_PERMISSION`.

Если пункты 1-2 не выполняются:

- проблема на этапе подключения `Car`.

Если 1-2 есть, но пункт 4 не выполняется:

- проблема в правах на property или в маппинге id.
