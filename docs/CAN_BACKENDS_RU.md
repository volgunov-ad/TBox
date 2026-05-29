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
- управляет `bind/unbind`, `setSourceSignals`, `execute(...)`, `setAudioVolume(...)`.

Зачем это нужно:

- UI-виджеты и экран настроек работают через один API;
- добавление/исправление backend не требует переписывать все composable и service-слой.

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

---

## 4) Как работает Android 10 backend (`Android10VhalRepository`)

### 4.1 Подключение к Car/VHAL

Используется схема, совместимая со штатными приложениями прошивки:

- `Car.createCar(Context, ServiceConnection)` (основной путь),
- fallback: `Car.createCar(Context, ServiceConnection, Handler)`,
- `car.connect()`,
- ожидание `onServiceConnected`,
- получение `CarPropertyManager` через `getCarManager(Car.PROPERTY_SERVICE)`.

Если подключение не удалось:

- `availability = Unavailable(...)`;
- причина логируется в `TboxRepository.addLog` с тегом `VHAL_A10`.

### 4.2 Чтение и polling

- backend отслеживает активные источники сигналов;
- при наличии источников запускается polling-цикл (`POLL_INTERVAL_MS`);
- для каждого сигнала вызывается `refreshSignal(...)`;
- чтение делается через `CarPropertyManager.getIntProperty(propertyId, areaId=0)`.

### 4.3 Запись команд

Команды (`ToggleProperty`, `SetProperty`, аудио-команды) идут через:

- `CarPropertyManager.setIntProperty(propertyId, areaId=0, value)`.

Перед записью применяется резолвинг `propertyId` (см. раздел 5).

### 4.4 Диагностика и логи

Добавлены диагностические логи в `TboxRepository` (tag: `VHAL_A10`):

- `bind/unbind`, старт/стоп polling;
- какой overload `Car.createCar` выбран;
- успешность connect и текущая `availability`;
- read/write ошибки с `propertyId`, `areaId`, `value`;
- попытки команд и итог `result=true/false`.

При `SecurityException` лог помечается как `POSSIBLE_PERMISSION`.

---

## 5) Маппинг propertyId для Android 10

В VHAL режиме нельзя использовать legacy mbCAN ids напрямую. Для этого используется `FirmwareVehicleJsonMapper`.

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

## 8) Что проверять при диагностике

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
