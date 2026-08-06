# Параметры mbCAN и VHAL в TBox Monitor

Справочник по **фактически используемым** в приложении property: чтение, запись, декодирование сырых значений и механизмы push/pull.

Источники в коде:

- ID и политики команд: `MbCanKnownVehiclePropertyId`, `MbCanCommandRegistry`, `MbCanAudioCommandRegistry`
- Маппинг mbCAN ↔ VHAL (Android 10): `FirmwareVehicleJsonMapper.kt`
- Android 9: `MbCanRepository.kt`, `MbCanSignalStateEngine.kt`, домены (`SlaSpeedLimitDomain`, `AccCruiseDomain`, `HvacClimateDomain`, `TrunkDoorDomain`)
- Android 10: `Android10VhalRepository.kt`
- Общий API: `UniversalCanRepository.kt`
- ACC/CCS step-loop: `AccCruiseController.kt`

См. также: [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md), сводная таблица scale/offset — [RAW_VALUE_FORMULAS_RU.md](RAW_VALUE_FORMULAS_RU.md).

---

## Общие правила push и pull

| Механизм | Android 9 (mbCAN) | Android 10 (VHAL) |
|----------|-------------------|-------------------|
| **Pull (опрос)** | `MbCanJobManager`: каждые **30 с**; после команды — **burst 1,5 с** в течение **15 с** (`requestBurst`) | Аналогично: **30 с** / burst **1,5 с × 15 с** (`requestBurstPolling`) |
| **Push (события)** | Coalesce **200 ms**, затем запись в `StateFlow` | `onChangeEvent` VHAL, coalesce **200 ms** |
| **Подписка на pull** | По `MbCanSignal` → `subscribeDataTypes` (например `eMBCAN_CFG_VEHICLE`) | По `MbCanSignal` → `signalReadPropertyIds` + `syncPushSubscriptions` |
| **После записи** | `canSetVehicleParam` / `canSetAudioParam` + burst + `refreshSignal` | `setIntProperty` + burst + `refreshSignal` |

Типы push на Android 9:

| Канал | Когда | Что обновляет |
|-------|--------|---------------|
| `eMBCAN_CFG_VEHICLE` → `scheduleVehicleCfgPush` | Изменение vehicle-cfg property | Бинарные переключатели, HVAC, сиденья, SLA/limiter switch, car settings EPS/drive |
| `eMBCAN_VEHICLE_LKA_STATUS` → `scheduleLkaSlaPush` | LKA/SLA от камеры | Знак: `FCM_2_SLAOnOffsts` + `FCM_2_SLAState` + `FCM_2_SLASpdlimit` (AdasCard) |
| `eMBCAN_VEHICLE_FRM_INFO` → `scheduleFrmAccPush` | FRM ACC | `FRM_3_ACCMode` + `FRM_3_VSetDis` (виджет ACC/CCS) |
| `eMBCAN_VEHICLE_GASPED_STATUS` → `scheduleGaspedCcsPush` | CCS status | `nCruiseControlStatus` (обычный круиз) |
| BCM telemetry → `scheduleTrunkBcmPush` | Движение/статус багажника | `TrunkDoorRepository` |
| `eMBCAN_CFG_AUDIO` → `scheduleAudioCfgPush` | Аудио-cfg | Громкость, volume-vs-speed |
| Engine/speed telemetry → `schedule*Push` | RPM, температура, скорость | Соответствующие `StateFlow` |

---

## ADAS: SLA и ограничитель скорости

### Распознанный дорожный знак (виджет km/h)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Распознанный знак SLA | LKA `FCM_2_SLAOnOffsts` + `FCM_2_SLAState` + `FCM_2_SLASpdlimit` | UI как AdasCard (`resolveSlaSignUiState`): OnOff=**2** и State∈{1,2,3}: Spdlimit **1** → «конец ограничения»; Spdlimit≥2 → **(raw−1)×5** км/ч (cap 130); иначе полупрозрачный «—». State **0**=скрыто/неактивно, **4**=fault (у нас как inactive) | — (только чтение) | — | **Push:** LKA `scheduleLkaSlaPush`. **Pull:** не для знака |
| **Android 10** — Распознанный знак SLA | VHAL **289415709** + **289415708** + **289415711** | То же `resolveSlaSignUiState` | — | — | **Push:** onChange. **Pull:** `refreshSignal(SlaSpeedLimit)` |

### Переключатель «Распознавание дорожных знаков» (SLA on/off)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — SLA on/off | mbCAN **18** `eVEHICLE_PROPERTY_TSR_SPEED_LIMIT_SIGN` | **1** → Off, **2** → On (`decodeSlaOnOffRaw`) | mbCAN **18** | **1** → выкл, **2** → вкл (`encodeSlaSwitchOn`) | **Push:** cfg_vehicle item 18. **Pull:** `refreshSlaSpeedLimit()` (signal `SlaSpeedLimit`). LKA `FCM_2_SLAOnOffsts` **игнорируется** |
| **Android 10** — SLA on/off | VHAL **289415709** `R_0B00_FCM_2_SLAOnOffsts` (read map от 18) | raw == 1 On (`decodeSlaOnOffVhalRaw`) | VHAL **289415947** `T_0B01_IHU_8_SLAOnOffReq` (write map от 18) | **1** → выкл, **2** → вкл (mbCAN-семантика в `encodeSlaSwitchOn`) | **Push:** onChange 289415709. **Pull:** `refreshSignal(SlaSpeedLimit)`. В штатных app (CarSettings / HVAC / Launcher ADAS / MediaService / SpeechHMI) **UI encode/decode не найден** — только ID в `VehiclePropertyIds` |

### Ограничитель скорости — переключатель

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Limiter switch | mbCAN **254** `eVEHICLE_SPEEDLIMIT_SWITCH` | **1** → Off, **2** → On (`decodeSpeedLimiterSwitchRaw`) | mbCAN **254** | **1** / **2** (`encodeSpeedLimiterSwitchOn`) | **Push:** cfg_vehicle 254. **Pull:** `refreshSpeedLimiter()` |
| **Android 10** — Limiter switch | VHAL id из `resolveReadPropertyId(254)` или **254** | raw == 1 On (`decodeSpeedLimiterSwitchVhalRaw`) | VHAL id из `resolveWritePropertyId(254)` или **254** | **1** / **2** (identity) | **Push:** onChange (если property в firmware). **Pull:** `refreshSignal(SpeedLimiter)` |

> **Jetour Dashing:** ограничитель скорости **не работает** на данной машине (нет verified VHAL map; 253/254 на целевом ГУ неэффективны). Код/виджет оставлены, но функциональность не поддерживать.

### Ограничитель скорости — целевая скорость (km/h)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Limiter target | **DataStore** (`speedLimiterTargetKmh`), не CAN | 0…150, шаг 5 (`clampLimiterTargetKmh`) | mbCAN **253** `eVEHICLE_SPEEDLIMIT_VALUESET` | 0…150 (km/h) | **Pull/push по CAN нет**; запись при изменении в UI |
| **Android 10** — Limiter target | **DataStore** (то же) | то же | VHAL id из `resolveWritePropertyId(253)` или **253** | 0…150 (identity) | то же |

---

## ADAS: уставка / статус круиз-контроля (ACC / CCS)

### Штатные логические состояния (0 / 1 / 2)

Условная модель штатного круиза (для виджетов и документации):

| Состояние | Смысл |
|-----------|--------|
| **0 Off** | Выключен; RES+/SET− не активируют |
| **1 Standby** | Предварительно включён; SET− = текущая скорость, RES+ = прежняя уставка |
| **2 Active** | Ведёт; RES+/SET− ±; тормоз → Standby; газ → Standby пока нажат |
| **Fault** | Только ACC: `ACCMode == 9` (ошибка); иконки оранжевые (`WidgetActiveColors.Secondary`), тапы no-op |

**Маппинг чтения ACC** (`ACCMode`): `0 → Off`; `1,2,6,7 → Standby`; `3,4,5 → Active`; `9 → Fault`.

**Маппинг чтения CCS** (`CruiseControlStatus`, ICM-хинт): `0 → Off`; `1 → Active`; `2 → Standby`; иное/null → Off. Для MFS key-mode сток считает «on» оба `{1,2}` (`isCcsEngaged`).

**MFS (дорожная семантика на Dashing):** **210** из Active → полное Off; **212** Cancel → пауза Active→Standby; **214** SET− активирует из Standby; **213** RES+.

### Виджеты

Виджет `accCruiseWidget` (**Уставка круиз-контроля**): single — Off/Standby → enable+SET− затем converge к уставке; Active и не на уставке → только converge; Active и уже на уставке → **212** (пауза). Double — **210** (полное Off), если не Off/Fault. После converge — пауза **1 с**, проверка уставки и догон при ±1; abort converge при уходе из Active (тормоз→Standby или Off). Мигает только нажатая плитка. Ключ данных не менялся.

Виджет `cruiseStatusWidget`: показывает **текущую** уставку ACC (`VSetDis`) или вкл/выкл CCS. Single — Off → **210** + **SET−** (текущая); Standby → **RES+**; Active → **212**; Fault → no-op. **Standby**: свайп вниз → **SET−**, вверх → **RES+**. **Active**: свайп вверх → **RES+** (+1), вниз → **SET−** (−1). Double — **210** из Standby/Active. Тот же `cruiseControlType` (**Авто** / **ACC** / **CCS**); **Авто**: живой ненулевой `ACCMode` или сессионный флаг «ACC уже был» → ACC; если CCS engaged при `ACCMode=0` или канал CCS уже отдавал статус (в т.ч. 0), а ACC так и не «проявился» → CCS; иначе FRM-feedback без канала CCS → ACC. На машинах только с обычным круизом FRM часто пушит `ACCMode=0` — этого недостаточно для выбора ACC.

### ACC (адаптивный)

Шаги ±1 км/ч по `VSetDis` после активации; активный цвет, когда ACC Active на уставке виджета. Интервалы шагов — раздельно +/−.

### Состояние ACC (режим и отображаемая уставка)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — ACCMode / VSetDis | FRM `getFRM_3_ACCMode` / `getFRM_3_VSetDis` | Mode: Active ∈ **{3,4,5}**, Standby ∈ **{1,2,6,7}**, Fault **9**. VSetDis: byte = **км/ч** (`decodeMbCanVSetDisKmh`) | — (только чтение) | — | **Push:** `registIMBVehicleFrmDectInfoListener` → `scheduleFrmAccPush` (ставит `accFrmFeedbackAvailable`; ненулевой ACCMode — ещё `accModeEverNonZero`). **Pull:** нет (push-only) |
| **Android 10** — ACCMode / VSetDis | VHAL **289415689** `R_0B00_FRM_3_ACCMode`, **289415680** `R_0B00_FRM_3_VSetDis` | Mode: то же. VSetDis: `ceil(raw × 0.5)` км/ч (`decodeVhalVSetDisKmh`, как Launcher) | — | — | **Push:** onChange. **Pull:** `refreshSignal(AccCruise)` |

### CCS (обычный круиз, без ACC)

Цикл converge: замер delta → пачка до **5×±1** → паузы 1 с / verify; in-band wait **2 с**; overshoot → рестарт; макс. **30 с**; затем post-verify **1 с** и догон при уходе. Запуск: после enable+SET− из Off/Standby (ждём Active), или сразу converge из Active если скорость ≠ уставке. Abort: статус не Active (Standby/Off / тормоз) или смена generation (double-tap). TBox `cruiseSetSpeed` **не** используется.

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — CCS status | Gasped `getnCruiseControlStatus` | **0** Off, **1** Active, **2** Standby; key-mode on ∈ **{1,2}**; identity | — | — | **Push:** `registIMBCanVehicleGaspedStatusListener` → `scheduleGaspedCcsPush`. **Pull:** нет |
| **Android 10** — CCS status | VHAL **289414945** `R_0900_EMS_1_CruiseControlStatus` (2 bit, receive.json) | то же | — | — | **Push:** onChange. **Pull:** `refreshSignal(AccCruise)`. (`R_0900_ACC_Cruise_Control` **289414946** в штате без UI-декода — не используем) |
| Скорость для converge | `TripTelemetryRepository.carSpeed` (HU, не TBox cruiseSetSpeed) | float км/ч, допуск ±1; пачки до 5×±1; verify 2 с / post-batch 1 с | — | — | — |

### Команды MFS (импульсы)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Cruise / Cancel / RES+ / SET− | — | — | mbCAN **210** / **212** / **213** / **214** | импульс **1** (`SetExact`) | Write-only pulse; HAL/шина сбрасывает |
| **Android 10** — Cruise / Cancel / RES+ / SET− | — | — | VHAL **289415956** / **289415954** / **289415953** / **289415960** | импульс **1** | то же (`reset: true` в send.json) |

Настройки плитки `accCruiseWidget`: `cruiseControlType` (auto/acc/ccs), `accCruiseTargetKmh` (30…150), `accCruiseIncreaseIntervalMs` / `accCruiseDecreaseIntervalMs` (50…1500). Step-loop: `AccCruiseController` (ACC / CCS). Плитка `cruiseStatusWidget`: только `cruiseControlType`.

---

## Кузов и комфорт (бинарные переключатели)

Общая mbCAN-семантика для большинства toggles: **1 = Off, 2 = On** (`decodeSteeringWheelHeatRaw`).  
На VHAL **чтение** бинарных ON/OFF как в штате: **selected = (raw == 1)** (`decodeVhalBinaryOneIsOn`); исключение — **Front OFF**: selected = `(raw == 0)`. **Запись** — per-property (см. таблицу).

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Подогрев руля | **188** | 1 Off / 2 On | **188** | toggle: 1↔2 | cfg push **188** + pull `SteeringWheelHeat` |
| **Android 10** — Подогрев руля | VHAL **289412111** ← 188 | raw == 1 On | VHAL **289412679** ← 188 | **1** on / **2** off | onChange + pull |
| **Android 9** — Обслуживание дворников | **185** | **1** Off (рабочий) / **2** On (сервис) | **185** | **2** on / **1** off (как доп. меню) | cfg push **185** + pull |
| **Android 10** — Обслуживание дворников | VHAL **289412194** ← 185 | raw == 1 On | VHAL **289412682** ← 185 | **1** on / **2** off (как CarSettings) | onChange + pull |
| **Android 9** — Парктроник (PAS) | **218** | 1 Off / 2 On | **218** | 1↔2 | cfg push + pull |
| **Android 10** — Парктроник | VHAL **289412233** ← 218 | raw == 1 On | VHAL **289415942** ← 218 | **2** on / **1** off | onChange + pull |
| **Android 9** — Подогрев лобового стекла | **316** | 1 Off / 2 On | **316** | 1↔2 | cfg push + pull |
| **Android 10** — Подогрев лобового | VHAL **289412114** ← 316 | raw == 1 On | VHAL **289415309** ← 316 | **2** on / **1** off | onChange + pull |
| **Android 9** — Беспроводная зарядка | **264** | 1 Off / 2 On | **264** | 1↔2 | cfg push + pull `WirelessChargingSwitch` |
| **Android 10** — Беспроводная зарядка | — (pull/push **не подключены**) | — | VHAL id из firmware для **264** (если есть) | 1↔2 | **Pull/push в A10 не реализованы** (`signalReadPropertyIds` = ∅) |

---

## Климат (HVAC)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — AC (компрессор) | **36** `HVAC_POWER` | 1 Off / 2 On | **36** | 1↔2 | cfg push + pull |
| **Android 10** — AC | VHAL **289415180** ← 36 | raw == 1 On | VHAL **289415300** ← 36 | **2** on / **1** off | onChange + pull |
| **Android 9** — AUTO | **110** | 1 Off / 2 On | **110** | 1↔2 | cfg push + pull |
| **Android 10** — AUTO | VHAL **289415182** ← 110 | raw == 1 On | VHAL **289415311** ← 110 | **2** on / **1** off | onChange + pull |
| **Android 9** — Рециркуляция | **39** | **1** → On (внутри), **2** → Off (снаружи) | **39** | 1↔2 | cfg push + pull |
| **Android 10** — Рециркуляция | VHAL **289415172** ← 39 | raw == 1 On (внутри); штат UI «снаружи» = raw == 2 | VHAL **289415302** ← 39 | **1** recirc on / **2** off | onChange + pull |
| **Android 9** — Очистка AC при запирании (Blower Delay) | **52** `HVAC_BLOWER_DELAY` | **1** Off / **2** On (`decodeHvacBlowerDelayMbCanRaw`) | **52** | **2** on / **1** off (stock ACSettings `MBWTSwitch`) | cfg push + pull `HvacAcCleanWhenLocked` |
| **Android 10** — Очистка AC при запирании | VHAL **289415189** ← 52 | raw == 1 On (`decodeVhalBinaryOneIsOn`) | VHAL **289412666** ← 52 | **1** on / **2** off (stock `AcFragment`; ≠ mbCAN) | onChange + pull |
| **Android 9** — Обогрев заднего стекла + зеркал | **41** `HVAC_DEFROSTER` | 1 Off / 2 On | **41** | 1↔2 | cfg push + pull |
| **Android 10** — Обогрев заднего стекла | VHAL **289415177** ← 41 | raw == 1 On | VHAL **289415299** ← 41 | **2** on / **1** off | onChange + pull |
| **Android 9** — Front OFF (передняя зона выкл) | **90** | **1** → UI On (климат выкл), **2** → UI Off | **90** | **2** climate on / **1** off (`encodeHvacFrontOffMbCanWrite`) | cfg push + pull |
| **Android 10** — Front OFF | VHAL **289415175** ← 90 | raw == **0** On (`decodeHvacFrontOffVhalRaw`) | VHAL **289415301** ← 90 | **1** on (climate off) / **2** off | onChange + pull; **интерес регистрируется вместе с climate panel виджетами** (`HVAC_CLIMATE_WIDGET_DATA_KEYS` → `MbCanSignal.HvacFrontOff`) |
| **Android 9** — SYNC dual-zone | **94** | **2** On / **1** Off (`decodeHvacSyncMbCanRaw`) | **94** | **2** on / **1** off | cfg push + pull |
| **Android 10** — SYNC | VHAL **289415181** ← 94 | raw == 1 On (`decodeHvacSyncVhalRaw`) | VHAL **289415308** ← 94 | **2** on / **1** off | onChange + pull |
| **Android 9** — Температура левая | **37** | raw 160…300 → °C = raw/10 (`mbCanTempRawToCelsius`) | **37** | 160…300, шаг 5 | cfg push + pull |
| **Android 10** — Температура левая | VHAL **289415169** ← 37 | raw 32…60 → °C = raw/2 | VHAL **289415313** ← 37 | VHAL raw через `mbCanTempRawToVhalWrite` | onChange + pull |
| **Android 9** — Температура правая | **111** | то же | **111** | то же | cfg push + pull |
| **Android 10** — Температура правая | VHAL **289415168** ← 111 | raw/2 | VHAL **289415314** ← 111 | convert | onChange + pull |
| **Android 9** — Скорость вентилятора | **38** | **0…7** | **38** | 0…7 | cfg push + pull |
| **Android 10** — Скорость вентилятора | VHAL **289415171** ← 38 | 0…7 | VHAL **289415296** ← 38 | 0…7 (identity) | onChange + pull |
| **Android 9** — Обдув лобового (defrost blow) | **40** `HVAC_FAN_DIRECTION` | raw **4,5** → On; **1,2,3** → Off (`decodeHvacFrontDefrostMbCanRaw`) | **40** | toggle target 4↔face/foot (`resolveHvacFrontDefrostMbCanToggleTarget`) | cfg push + pull `HvacDefrosterFront` |
| **Android 10** — Обдув лобового | VHAL **289415174** ← 40 | raw **4** → On; 0,1,2,3 → Off | VHAL **289415298** ← 40 | **4** on / **0** off (face) | onChange + pull |
| **Android 9** — Режим обдува (cycle) | **40** (тот же) | **1** face, **2** foot, **3** face+foot, **4** defrost, **5** defrost+foot → `HvacBlowMode` | **40** | mbCAN mode 1…5 | cfg push + pull `HvacBlowMode` |
| **Android 10** — Режим обдува | VHAL **289415174** ← 40 | **0** face, **2** foot, **1** face+foot, **4** defrost, **3** defrost+foot | VHAL **289415298** ← 40 | через `mbCanBlowModeToVhalWrite` | onChange + pull |

---

## Сиденья

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Переднее левое heat/vent | **138** | 1 off; 2–4 heat L1–L3; 5–7 vent L1–L3 | **138** | 1…7 | cfg push + pull |
| **Android 10** — Переднее левое | VHAL **289415193** ← 138 | то же | VHAL **289415316** ← 138 | 1…7 | onChange + pull |
| **Android 9** — Переднее правое | **139** | то же | **139** | 1…7 | cfg push + pull |
| **Android 10** — Переднее правое | VHAL **289415192** ← 139 | то же | VHAL **289415315** ← 139 | 1…7 | onChange + pull |
| **Android 9** — Заднее левое (только heat) | **318** | 1 off; 2–4 heat L1–L3 | **318** | 1…4 | cfg push + pull |
| **Android 10** — Заднее левое | VHAL **289415203** ← 318 | то же | VHAL **289415345** ← 318 | 1…4 | onChange + pull |
| **Android 9** — Заднее правое | **319** | то же | **319** | 1…4 | cfg push + pull |
| **Android 10** — Заднее правое | VHAL **289415202** ← 319 | то же | VHAL **289415344** ← 319 | 1…4 | onChange + pull |

---

## Багажник и зеркала

### Багажник — статус и движение (чтение)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Статус открыт/закрыт | BCM `nTrunkSts` (snapshot) | **1** closed, **2** open (`decodeBinaryOpenMbCan`) | — | — | **Push:** BCM `scheduleTrunkBcmPush`. **Pull:** `refreshTrunkDoor()` → BCM snapshot |
| **Android 9** — Направление движения | BCM `nRearDoorMoveDir` | **0** closing, **1** opening, **2** stopped | — | — | push + pull (то же) |
| **Android 10** — Статус | VHAL **289412273** ← `TRUNK_STATUS` (71343) | **0** closed, **1** open (`decodeBinaryOpenVhal`) | — | — | onChange + pull `TrunkDoor` |
| **Android 10** — Направление | VHAL **289412272** ← `TRUNK_REAR_DOOR_MOVE_DIR` (71341) | 0 / 1 / 2 (как A9) | — | — | onChange + pull |

### Багажник — импульс открыть/закрыть (запись)

| Платформа + наименование | Параметр чтения | Сырые значения чтения | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|----------------------|-----------------|----------------------|-------------|
| **Android 9** — PLG pulse | — | — | **134** `TRUNK_PLG_CONTROL` | **1** или **2**, затем **0** через 310 ms | После pulse — burst + `refreshTrunkDoor` |
| **Android 10** — PLG pulse | — | — | VHAL **289412638** ← 134 | **1** / **2**, затем **0** | то же |

### Складывание зеркал (только запись)

| Платформа + наименование | Параметр чтения | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-----------------|----------------------|-------------|
| **Android 9** — Mirror fold | — (статус не читается) | **230** `MIRROR_FOLD_SWITCH` | **1** fold / **2** unfold | Только команда; без pull |
| **Android 10** — Mirror fold | — | VHAL **289412705** ← 230 | **1** fold / **2** unfold | то же |

Виджет `mirrorFoldWidget`: фактическое положение зеркал недоступно, поэтому приложение запоминает последнюю **успешно отправленную** команду **только в рамках текущего процесса** (`MirrorFoldLastCommandStore` в RAM). После перезапуска приложения снова считается, что зеркала разложены (как при старте автомобиля).

- **Одиночное нажатие** — отправляет противоположную команду относительно последней в этой сессии (toggle). По умолчанию последняя считается **unfold (2)**, значит первый одиночный тап шлёт **fold (1)**.
- **Двойное нажатие** — всегда **fold (1)** и обновляет запомненную команду на fold до конца сессии.

---

## Настройки автомобиля (Car Settings)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — EPS mode | **25** | **0…6** (`decodeCarSettingsIntZeroToSix`) | **25** | 0…6 | cfg push (EPS/Drive ids) + pull `CarSettingsVehicleParams` |
| **Android 10** — EPS mode | VHAL **289412124** ← 25 | 0…6 | VHAL **289412662** ← 25 | 0…6 | onChange + pull |
| **Android 9** — Drive mode | **145** | 0…6 | **145** | 0…6 | cfg push + pull |
| **Android 10** — Drive mode | VHAL **289412123** ← 145 | 0…6 | VHAL **289412695** ← 145 | 0…6 | onChange + pull |
| **Android 9** — Drive mode 6DCT Wet | **149** | 0…6 | **149** | 0…6 | cfg push + pull |
| **Android 10** — Drive mode 6DCT Wet | VHAL **289412692** ← 149* | 0…6 | VHAL **289412692** ← 149 | 0…6 | onChange + pull |

\* В `explicitReadIdMap` для 6DCT Wet указан тот же VHAL id, что и для write — проверяйте на конкретной прошивке.

Виджеты `driveModeWidget` (кнопка одного режима) и `driveModeCycleWidget` (цикл по выбранным) пишут те же свойства **145** / **149**. Для 6DCT в конфиге плитки используются синтетические rawValue `100–102` (см. `DriveModeWidget.kt`); на шину уходит `propertyValue` 0/1/2.

---

## Аудио

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Громкость | Audio **2** `eAUDIO_PROPERTY_VOLUME` | int ≥ 0 | Audio **2** | 0…max (`setAudioVolume`) | cfg_audio push + pull `AudioVolume` |
| **Android 10** — Громкость | VHAL **557849090** | int ≥ 0 | VHAL **557849090** | int | onChange + pull |
| **Android 9** — Volume vs speed | Audio **13** | **1** Off; **2–4** On (уровень в `_audioVolumeSpeedModeState`) | **13** | toggle 1↔2..4 | cfg_audio push + pull |
| **Android 10** — Volume vs speed | VHAL **557849227** | то же | VHAL **557849227** | 1…4 | onChange + pull |

---

## Телеметрия (RPM, температура двигателя, скорость, PRND, топливо, одометр, t° снаружи)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|-------------|
| **Android 9** — Engine RPM | `readVehicleEngineRpm()` (telemetry) | float ≥ 0 | — | **Push:** telemetry bridge. **Pull:** `refreshEngineRpm` (30 s / burst) |
| **Android 10** — Engine RPM | VHAL **289414951** `R_0900_EMS_1_EngineSpd` | raw × **4** (`decodeEngineRpm`) | — | onChange + pull |
| **Android 9** — Coolant temp | telemetry float | °C as-is from facade; **на практике с mbCAN всегда `0.0`** | — | push + pull; **в учёте поездок не используется** (только TBox CRT) |
| **Android 10** — Coolant temp | VHAL **289414949** | raw × **0,75 − 48** | — | onChange + pull; в поездках приоритет TBox, HU если TBox stale |
| **Android 9** — Vehicle speed | telemetry float | km/h ≥ 0 | — | push + pull |
| **Android 10** — Vehicle speed | VHAL dual-source: **289412119** `R_0400_ESP_1_VehicleSpeedVSOSig` + **289414964** `R_0900_ICM_1_DisplayVehicleSpeed` | полный raw: **км/ч = UINT16(raw) / 16**; publish VSOSig если raw **> 0**, иначе Display. Display на Dashing часто усечён до младшего байта (`0…240`, шаг 16) → без правки даёт `speed mod 16`; в коде — **unwrap** (`decodeDisplayTruncatedRaw`). Display в `receive.json` FuncID 9 на краю буфера (mByte=42, len=13). **Probe-only** (DEBUG, не в publish): **291504647** `PERF_VEHICLE_SPEED`, **291507682** `VEHICLE_SPEED`, **557845547** `MCU_REPLY_SPEED` (штатный SystemSettings carSpeed, int км/ч as-is) | — | onChange + pull publish-id; probe тоже subscribe |
| **Android 9** — Gear PRND | `readVehicleGearMode()` / `MBCanVehicleSpeed.getGear()` (type **20**, fallback **1**) | **1→N, 2→R, 4→P, 8→D** (`VehicleGearDomain.decodePrndBitmask`); иначе null | — | **Push:** `onCanVehicleSpeed` (тот же callback для `eMBCAN_VEHICLE_GEAR`) + pull. Виджет `gearBoxMode` с `useMbCanVhal` |
| **Android 10** — Gear PRND | VHAL **289408000** `GEAR_SELECTION` (+ fallback **289408001** `CURRENT_GEAR`) | то же bitmask | — | onChange + pull |
| **Android 9** — ReverseGearSwitch | `readReverseGearSwitch()` / `MBCanVehicleBcmStatus.getReverseGearSwitch()` (type **21**) | Dashing CEM inverted: **0** → engaged (`true`) / **1** → not reverse (`false`); иное → null (`decodeReverseGearSwitch`) | — | **Push:** `onVehicleBcmStatusChange` + pull. StateFlow `reverseGearSwitchState`. **DR/mock:** настройка `mock_consider_reverse` + `VehicleGearDomain.isReverseEngaged` — 1) HU PRND `R`, 2) известный не-`R` HU → не задняя (switch игнор), 3) нет HU PRND → switch, 4) иначе TBox PRND `R`. Не применяется в режиме «Прямой» |
| **Android 10** — ReverseGearSwitch | VHAL **289412135** `R_0400_CEM_2_ReverseGearSwitch` | то же inverted 0/1 | — | onChange + pull; та же лестница при включённой опции |
| **Android 9** — Fuel level % | `readVehicleFuelLevelPercent()` / `getFuelLevel()` | **0…100**; иначе null | — | push `onCanVehicleFuelLevel` + pull |
| **Android 10** — Fuel level % | VHAL **289414929** `R_0900_ICM_1_FuelLevel` | int **0…100** | — | onChange + pull |
| **Android 9** — Total odometer | `readTotalOdometerKm()` / `getOdometer()` | float km → UInt | — | push `onVehicleTotalOdoMeterChange` + pull |
| **Android 10** — Total odometer | VHAL **289414930** `R_0900_ICM_1_TotalOdometer_Km` | int km as-is | — | onChange + pull |
| **Android 9** — Outside temp | `readOutsideTemperatureC()` / `getExternalTemperatureRaw()` | raw byte **°C**; **87** = invalid (`OutsideTemperatureDomain.decodeMbCanCelsiusRaw`) | — | **Push:** `onCanVehicleExternalTemp` (ветка в `MBCanEngine` ранее была пустой) + pull |
| **Android 10** — Outside temp | VHAL **289412223** `R_0400_CEM_IPM_3_ExternalTemperatureRaw` | **°C = (raw & 0xFF) × 0.5 − 40** (`decodeVhalRaw`); вне [−40; 87) → null. То же кодирование, что TBox CAN `0x535` | — | onChange + pull |
| **Android 9** — TPMS (P/T ×4) | `MBCanVehicleTires` / `eMBCAN_VEHICLE_TIRE` (34); `vstTire[0..3]` LF/RF/LR/RR | `fPressure` bar (**−1** = invalid); `nTemperature` °C (**−100** = invalid) → `TirePressureDomain` | — | **Push:** `onCanVehicleTires` + pull; виджеты с «Работа через CAN». **Давление:** null-debounce + disk persist в **отдельные** HU-ключи (`wheel*_pressure_last_hu`) |
| **Android 10** — TPMS pressure | VHAL **289411849–852** `R_0300_CEM_5_*TyrePressure` FL/FR/RL/RR | **бар = raw × 0.0275**; ≤0 или >3.5 → null (stock UI) | — | onChange + pull; тот же null-debounce / HU persist |
| **Android 10** — TPMS temperature | VHAL **289411853–856** `R_0300_CEM_5_*TyreTemperature` | **°C = raw − 60**; raw ≤0 или ≥150 → null | — | onChange + pull (без disk persist) |
| **Android 9** — Instant fuel | `MBCanVehicleEngine.getFuelRollingCounter` (type 22) | **л/100км = raw / 10**; ≤0 → null (`MBOilWearView`) | — | push только из поля engine-callback (без re-read `getMbCanData`); иначе pull; виджеты с `useMbCanVhal` |
| **Android 10** — Instant fuel | VHAL **289414918** `R_0900_ICM_6_FuelRollingCounter` | **л/100км = raw × 0.1** (`convertOilInteger`) | — | onChange + pull |
| **Android 9** — Maintenance tips | `IcmTripInfo.getICM_6_Maintenance_tips` (type 48) | км as-is; &lt;0 → null (`MBMaintenanceView`) | — | pull |
| **Android 10** — Maintenance tips | VHAL **289414920** `R_0900_ICM_6_Maintenance_tips` | км as-is; &lt;0 → null | — | onChange + pull |
| **Android 9** — Distance to empty | `MBCanVehicleFuelLevel.getDistenceToEmpty` (type 12) | float км as-is; ≤0 → null (`MBVehicleFuelLevelView`) | — | push с fuel level + pull |
| **Android 10** — Distance to empty | VHAL **289414938** `R_0900_ICM_4_DistenceToEmpty_Km` | int км as-is; ≤0 → null | — | onChange + pull |
| **Android 9** — PM2.5 density | `MBCanPM25` Indensity/outdensity (type 28) | as-is; вне 1…65534 → null (`MBPM25View`) | — | pull |
| **Android 10** — PM2.5 density | VHAL **289412224** / **289412226** Indensity/Outdensity | as-is; вне 1…65534 → null (HVAC) | — | onChange + pull |
| **Android 9** — Steering angle | `MBCanVehicleSteeringAngle` (type 3) | float ° / °/с as-is | — | pull (нет A10 property); geo-debug держит interest `geo-debug-steering` во время записи и пишет `steering.angleDeg` |
| **Android 10** — Steering angle | — | **недоступно** (нет id в stock `VehiclePropertyIds`); в geo-debug `steering.angleDeg=-` | — | — |

Поездки/заправки читают `TripTelemetryRepository` (смесь HU+TBox); `CanDataRepository` — только TBox. Приоритет HU для RPM/speed/odo/fuel/outside; ОЖ: на **Android 9** только TBox; на **Android 10** — TBox first, HU если TBox stale. Масло КПП — только TBox (в CDR). Смешивание с окном **45 с**; учёт в `BackgroundService` через `accounting*` держит кэш, пока жив путь (TBox UDP или HU collectors), и даёт `null` только при потере обоих путей. CDR не очищается. TPMS / instant fuel / DTE / maintenance / PM2.5 / steering / **PRND (`gearBoxMode`)** через CAN — только виджеты с `useMbCanVhal` (не поездки). Давления TBox и HU **не смешиваются** на диске (`wheel*_pressure_last` vs `wheel*_pressure_last_hu`).

---

## Система

| Платформа + наименование | Параметр чтения | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-----------------|----------------------|-------------|
| **Android 9** — Перезагрузка ГУ | — | **74** `eSYSTEM_REBOOT` | **1** (`SYSTEM_REBOOT_VALUE`) | Только разовая запись из настроек; без pull |
| **Android 10** — Перезагрузка ГУ | — | id из firmware для **74** (если есть) | **1** | то же |

---

## Сводка VHAL id (explicit map)

Полный список verified write/read пар — в `FirmwareVehicleJsonMapper.kt` (`explicitWriteIdMap`, `explicitReadIdMap`).  
Если property нет в explicit map, Android 10 использует **identity fallback** по `send.json` / `receive.json` на ГУ.

Ключевые SLA/VHAL константы:

| Роль | VHAL id | CAN-имя |
|------|---------|---------|
| Знак (read) | 289415711 | `R_0B00_FCM_2_SLASpdlimit` |
| SLA status (read) | 289415709 | `R_0B00_FCM_2_SLAOnOffsts` |
| SLA state (read) | 289415708 | `R_0B00_FCM_2_SLAState` (0 off, 1–3 active, 4 fault) |
| SLA request (write) | 289415947 | `T_0B01_IHU_8_SLAOnOffReq` |

---

## Примечания

1. **mbCAN id** в таблицах — это `MbCanKnownVehiclePropertyId.*` (legacy `MBVehicleProperty`).
2. **Декодеры** намеренно различаются между A9 и A10 там, где stock-приложения используют разную семантику (SLA, SYNC, trunk, VHAL binary read).
3. Параметры из `MbCanCatalog.controls`, не подключённые к `MbCanSignal` / UI (PM2.5 toggle, UV lamp, sterilize, brake feel и т.д.), в этом документе **не перечислены** — приложение их пока не опрашивает.
4. При изменении decode/write логики обновляйте этот файл вместе с доменными тестами (`*DomainTest`, `MbCanSignalStateEngine`).
