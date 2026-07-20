# CAN backends: mbCAN и VHAL

Этот документ описывает, как в приложении выбирается стек CAN, как работает общий репозиторий и как выполняются подключение/чтение/запись для двух вариантов головного устройства:

- **Android 9**: через `mbCAN`.
- **Android 10**: через `android.car` / VHAL (`CarPropertyManager`).

Таблицы всех **используемых** property (чтение/запись, raw-декод, push/pull): [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).  
Сводная таблица **scale/offset** формул (TBox + mbCAN + VHAL): [RAW_VALUE_FORMULAS_RU.md](RAW_VALUE_FORMULAS_RU.md).

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

Дополнительно к ручному выбору работает автоfallback backend:

- на старте выполняется цикл попыток `3 + 3`:
  - 3 попытки bind для сохранённого режима;
  - при неуспехе — автопереключение на альтернативный режим и ещё 3 попытки;
  - если оба backend неуспешны — возврат в исходный режим и `lock` автоfallback.
- между попытками выдерживается пауза `1.2s`;
- окно одной попытки bind — `3.5s` (с финальной проверкой `warmUpAvailabilityForUi()` перед fail);
- `SettingsManager` хранит служебные поля:
  - `can_auto_bind_enabled`,
  - `can_auto_bind_locked`,
  - `can_auto_bind_last_primary_mode`,
  - `can_auto_bind_last_result`.
- при ручном выборе режима lock автоfallback сбрасывается.

Где применяется:

- `TboxApplication` и UI настроек подписываются на `headUnitCanModeFlow` и вызывают `UniversalCanRepository.setMode(...)`;
- **`bind()` и автоfallback `autoResolveModeOnStartup()` выполняются в `BackgroundService.onCreate`** — только там поднимается реальное подключение к mbCAN/VHAL;
- в UI переключатель находится в настройках (две кнопки: Android 9 / Android 10);
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
- `execute(command: MbCanCommand): MbCanCommandResult`  
  `command` — `ToggleProperty/SetProperty/ToggleAudioProperty/SetAudioProperty/RefreshSignal`.
- `setAudioVolume(value: Int): MbCanCommandResult`  
  `value` — целевая громкость.
- `autoResolveModeOnStartup(settingsManager: SettingsManager, scope: CoroutineScope)`  
  выполняет автоfallback `3+3` на старте.
- `enqueueClearSource(sourceId: String)`  
  снимает интересы источника с debounce **3 минуты** (одинаково в обоих backend).
- `widgetConfigsNeedMbCan(dataKeys: Set<String>)`  
  проверяет, нужны ли mbCAN/VHAL для набора `dataKey` плиток.

Операции `setSourceWidgetKeys`, `setSourceSignals`, `execute`, `setAudioVolume` **не** сериализуются `modeSwitchMutex` — только переключение режима и bind/unbind.

---

## 3) Как работает Android 9 backend (`MbCanRepository`)

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

Используется схема, совместимая со штатными приложениями прошивки. В коде доступ к `Car` / `CarPropertyManager` идёт через **reflection** (`CarPropertyBridge`), чтобы не зависеть от compile SDK с полным Android Car API.

- `Car.createCar(Context, ServiceConnection)` (основной путь),
- `car.connect()`,
- ожидание `onServiceConnected` (таймаут ожидания **2,5 с**),
- получение property manager через `getCarManager("property")` (до **20** повторов по 100 ms).

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
  - скорость: `289_414_964`.
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

- `rateHz` выбирается по типу property (`on-change`/`continuous`) и пробуется с fallback-наборами (`0.0/1.0/5.0`);
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

- `MBCAN_TMP` и `VHAL_A10` пишутся только когда включён флаг `MbCanDiagnostics.enabled`;
- при том же флаге `VehicleTelemetryBridge` раз в **15 с** пишет DEBUG с тегом `TripFuel` (источник HU/TBox по сигналам учёта поездок/заправок + текущие значения CDR);
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

- `mediaVolumeWidgetHorizontal`
- `mediaVolumeWidgetVertical`
- `engineRPM`
- `engineTemperature`
- `carSpeed`
- `odometer`
- `fuelLevelPercentage`
- `outsideTemperature`

Поведение:

- при `useMbCanVhal = false` виджет использует обычный источник (например, `engineRPM` из CAN-frame pipeline);
- при `useMbCanVhal = true` виджет работает через `UniversalCanRepository` (mbCAN/VHAL backend);
- для таких виджетов панель регистрирует соответствующие CAN interests через `setSourceSignals(...)`.
- `enqueueClearSource(...)` в обоих backend работает с одинаковым debounce (`3 минуты`), чтобы поведение интересов не расходилось между mbCAN/VHAL.

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
- `odometer`
  - interest: `MbCanSignal.TotalOdometer`
  - чтение: `UniversalCanRepository.odometerKmState`
- `fuelLevelPercentage`
  - interest: `MbCanSignal.FuelLevel`
  - чтение: `UniversalCanRepository.fuelLevelPercentState`
- `outsideTemperature`
  - interest: `MbCanSignal.OutsideTemperature`
  - чтение: `UniversalCanRepository.outsideTemperatureState`
Полный список штатных VHAL push-подписок (ID/имена), извлечённый из `CarSettings`/`AirConditioning`/`Launcher`,
сохранён отдельно: `docs/STOCK_PUSH_SUBSCRIPTIONS_RU.md`.

---

## 9) Что проверять при диагностике

Минимальный чеклист по логам:

1. Есть `VHAL connected, propertyService=property`.
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
