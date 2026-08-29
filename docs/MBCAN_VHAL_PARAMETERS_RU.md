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

**Car Settings tab:** пока открыта вкладка «Настройки авто», интерес — **объединение сигналов всех секций** (`carSettingsTabMbCanSignals`), а не только текущей. Так не дёргаются `eMBCAN_CFG_AUDIO` ↔ `eMBCAN_CFG_VEHICLE` при быстром переключении пунктов меню. Числовые UI-значения (`Int?`) удерживают последнее валидное (`HoldLastKnown`) на **A9 mbCAN и A10 VHAL**: сырые `-1` / out-of-range / transient unavailable при poll/push не гасят выбранные кнопки режима.

Типы push на Android 9:

| Канал | Когда | Что обновляет |
|-------|--------|---------------|
| `eMBCAN_CFG_VEHICLE` → `scheduleVehicleCfgPush` | Изменение vehicle-cfg property | Бинарные переключатели, HVAC, сиденья, SLA/limiter switch, car settings EPS/drive |
| `eMBCAN_VEHICLE_LKA_STATUS` → `scheduleLkaSlaPush` | LKA/SLA от камеры | Знак: `FCM_2_SLAOnOffsts` + `FCM_2_SLAState` + `FCM_2_SLASpdlimit` (AdasCard) |
| `eMBCAN_VEHICLE_FRM_INFO` → `scheduleFrmAccPush` | FRM ACC | `FRM_3_ACCMode` + `FRM_3_VSetDis` (виджет ACC/CCS) |
| `eMBCAN_VEHICLE_GASPED_STATUS` → `scheduleGaspedCcsPush` / `scheduleGasPedalPush` | CCS status + педаль газа | `nCruiseControlStatus`; `fGasPedalPosition` + `nGasPedalPositionInvalidData` |
| BCM telemetry → `scheduleTrunkBcmPush` | Движение/статус багажника | `TrunkDoorRepository` |
| `eMBCAN_CFG_AUDIO` → `scheduleAudioCfgPush` | Аудио-cfg | Громкость, volume-vs-speed, EQ, balance/fader |
| Engine/speed telemetry → `schedule*Push` | RPM, температура, скорость | Соответствующие `StateFlow` |

---

## Car Settings: климат, HUD и overspeed

| Функция | Android 9 mbCAN R/W | Android 10 VHAL read → write | Значения / декодирование |
|---------|---------------------|-------------------------------|--------------------------|
| First blowing | **53** `eVEHICLE_PROPERTY_POWER_FIRST_BREATH` | **289415188** → **289412677** | A9: 1 Off / 2 On; A10: 2 Off / 1 On |
| BT reduce fan | **51** `eVEHICLE_PROPERTY_BT_REDUCED_WIND_SPEED` | **289415190** → **289412667** | A9: 1 Off / 2 On; A10: 2 Off / 1 On |
| Auto ventilation | **141** `eHVAC_VENTILATION_AUTO_SWITCH` | **289415187** → **289412704** | A9: 1 Off / 2 On; A10: 2 Off / 1 On |
| Anion / очистка воздуха | **42** `eVEHICLE_PROPERTY_HVAC_AQS` | **289415191** `R_0200_CEM_IPM_AnionPurify` → **289415310** `T_0201_IHU_5_AnionPurify_Req` | A9: 1 Off / 2 On; A10 read: **1 On**, write: **2 On / 1 Off** |
| Fragrance switch | **33** `eVEHICLE_PROPERTY_FRAGRANCE_SWITCH` | — (A9-only) | 1 Off / 2 On |
| Fragrance smell | **34** `eVEHICLE_PROPERTY_FRAGRANCE_SMELL` | — (A9-only) | 1 Meteor / 2 Boss / 3 Tea |
| Fragrance concentration | **35** `eVEHICLE_PROPERTY_FRAGRANCE_CONCENTRATION` | — (A9-only) | 1 low / 2 mid / 3 high |
| HUD on/off | **220** | **289412235** → **289412716** | A9: 1 Off / 2 On; A10: 2 Off / 1 On |
| HUD height | **221** | **289412236** → **289412717** | 1…10 |
| HUD brightness | **222** | **289412238** → **289412719** | 1…10 |
| HUD display mode | **223** | **289412239** → **289412718** | 1 standard, 2 snow |
| HUD auto brightness | **227** | **289412243** → **289412723** | A9: 1 Off / 2 On; A10: 2 Off / 1 On |
| Overspeed alarm | **296** `eVEHICLE_OVERSPEEDALARM_SET` (best effort) | **289415091** `T_0901_IHU_21_OverspeedAlarm_Set` read/write | `raw = (km/h − 30) / 5`, display = `raw×5 + 30` |

Это settings-only сигналы (`MbCanSignal`); виджеты для них намеренно не добавлены. Anion использует split backend: A9 `HVAC_AQS` и отдельные A10 read/write VHAL ID. Fragrance реализован только через A9 mbCAN; на Android 10 не используются неподтверждённые stub VHAL ID, поэтому controls disabled.

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
| **Android 9** — Limiter switch | mbCAN **254** `eVEHICLE_SPEEDLIMIT_SWITCH` | **1** → Off, **2** → On (`decodeSpeedLimiterSwitchRaw`); UI/settings also keep raw Int | mbCAN **254** | as-is (`SetAnyInt`; widget encode **1**/**2**) | **Push:** cfg_vehicle 254. **Pull:** `refreshSpeedLimiter()` |
| **Android 10** — Limiter switch | VHAL id из `resolveReadPropertyId(254)` или **254** | raw == 1 On (`decodeSpeedLimiterSwitchVhalRaw`); raw Int retained | VHAL id из `resolveWritePropertyId(254)` или **254** | as-is | **Push:** onChange (если property в firmware). **Pull:** `refreshSignal(SpeedLimiter)` |

> На Jetour Dashing карта VHAL для 253/254 может отсутствовать; виджет и вкладка «Ограничитель скорости» в «Настройки автомобиля» (сырые 253/254 + «Задать») оставлены для отладки на ГУ.

### Ограничитель скорости — целевая скорость (km/h)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Limiter target | mbCAN **253** `eVEHICLE_SPEEDLIMIT_VALUESET` → `speedLimiterValueSetRaw` | identity Int? (нет данных → виджет «—») | mbCAN **253** | виджет: clamp 0…150 шаг 5; без данных первый ± → **30**; settings: as-is (`SetAnyInt`) | **Push:** cfg_vehicle 253. **Pull:** `refreshSpeedLimiter()` |
| **Android 10** — Limiter target | VHAL id из `resolveReadPropertyId(253)` или **253** | то же | VHAL id из `resolveWritePropertyId(253)` или **253** | то же | то же |

DataStore `speedLimiterTargetKmh` пока сохраняется виджетом при ± (возможный будущий fallback), но **отображение** идёт только с CAN VALUESET.

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

Виджет `cruiseStatusWidget`: показывает **текущую** уставку ACC (`VSetDis`) или **запомненную** уставку CCS (сессия процесса). Single — Off → **210** + **SET−** (текущая); Standby → **RES+**, если уставка есть, иначе **SET−**; Active → **212**; Fault → no-op. **Standby**: свайп вниз → **SET−**, вверх → **RES+**. **Active**: свайп вверх → **RES+** (+1), вниз → **SET−** (−1). Double — **210** из Standby/Active. Тот же `cruiseControlType` (**Авто** / **ACC** / **CCS**); **Авто**: живой ненулевой `ACCMode` или сессионный флаг «ACC уже был» → ACC; если CCS engaged при `ACCMode=0` или канал CCS уже отдавал статус (в т.ч. 0), а ACC так и не «проявился» → CCS; иначе FRM-feedback без канала CCS → ACC. На машинах только с обычным круизом FRM часто пушит `ACCMode=0` — этого недостаточно для выбора ACC.

### ACC (адаптивный)

Шаги ±1 км/ч по `VSetDis` после активации; активный цвет, когда ACC Active на уставке виджета. Интервалы шагов — раздельно +/−.

**Контроль уставки после converge:** пауза **1 с** → принудительный `RefreshSignal(AccCruise)` (A10 — реальный pull; A9 push-only, работает как дополнительная выдержка) → ещё **1 с** → сверка `VSetDis` с уставкой. Догон до **5** шагов ±1, и после каждого шага снова refresh + выдержка **700 мс**, чтобы не проскочить на ±1 по устаревшему значению.

### Состояние ACC (режим и отображаемая уставка)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — ACCMode / VSetDis | FRM `getFRM_3_ACCMode` / `getFRM_3_VSetDis` | Mode: Active ∈ **{3,4,5}**, Standby ∈ **{1,2,6,7}**, Fault **9**. VSetDis: byte = **км/ч** (`decodeMbCanVSetDisKmh`) | — (только чтение) | — | **Push:** `registIMBVehicleFrmDectInfoListener` → `scheduleFrmAccPush` (ставит `accFrmFeedbackAvailable`; ненулевой ACCMode — ещё `accModeEverNonZero`). **Pull:** нет (push-only) |
| **Android 10** — ACCMode / VSetDis | VHAL **289415689** `R_0B00_FRM_3_ACCMode`, **289415680** `R_0B00_FRM_3_VSetDis` | Mode: то же. VSetDis: `ceil(raw × 0.5)` км/ч (`decodeVhalVSetDisKmh`, как Launcher) | — | — | **Push:** onChange. **Pull:** `refreshSignal(AccCruise)` |

### CCS (обычный круиз, без ACC)

Цикл converge: замер delta → пачка до **5×±1** → паузы 1 с / verify; in-band wait **2 с**; overshoot → рестарт; макс. **30 с**; затем post-verify **1 с** и догон при уходе. Запуск: после enable+SET− из Off/Standby (ждём Active), или сразу converge из Active если скорость ≠ уставке. Abort: статус не Active (Standby/Off / тормоз) или смена generation (double-tap). TBox `cruiseSetSpeed` **не** используется.

**Запомненная уставка CCS** (`CcsRememberedSetpoint`, только сессия процесса): HU не отдаёт VSetDis, поэтому уставку ведём сами. Пишется при SET− с виджета / входе в Active с руля (если пусто или скорость дальше **2 км/ч** от прежней — новый SET; иначе RES и keep), при stalk ±1 после settle **500 мс**, при завершении CCS converge. **Active→Standby** сохраняет; **Off (0)** и unbind очищают. Окно «наш импульс» **2 с** после MFS с виджета подавляет stalk-эвристику. На статус-плитке в Standby/Active показывается запомненное значение (как VSetDis у ACC).

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
| **Android 10** — Обслуживание дворников | VHAL **289412194** ← 185 | raw == 1 On | VHAL **289412682** ← 185 | **1** on / **2** off (как CarSettings) | onChange + pull. Виджет `wiperMaintenanceWidget`: chrome On/Off от этого сигнала; иконка — live `WiperSts` (см. телеметрию) |
| **Android 9** — Парктроник (PAS) | **218** | 1 Off / 2 On | **218** | 1↔2 | cfg push + pull |
| **Android 10** — Парктроник | VHAL **289412233** ← 218 | raw == 1 On | VHAL **289415942** ← 218 | **2** on / **1** off | onChange + pull |
| **Android 9** — AVH (Auto Hold) | **142** | On если raw == 1 \|\| 2 (`decodeAvhHdcStatusRaw`) | **142** | **2** on / **1** off | cfg push + pull `AvhSwitch` |
| **Android 10** — AVH | VHAL **289412184** ← 142 | On если raw == 1 \|\| 2 (stock ConvertValue) | VHAL **289415945** ← 142 | **1** on / **2** off | onChange + pull |
| **Android 9** — HDC | **143** | On если raw == 1 \|\| 2 (`decodeAvhHdcStatusRaw`) | **143** | **2** on / **1** off | cfg push + pull `HdcSwitch` |
| **Android 10** — HDC | VHAL **289412117** ← 143 | On если raw == 1 \|\| 2 (stock ConvertValue) | VHAL **289415944** ← 143 | **1** on / **2** off | onChange + pull |
| **Android 9** — ESP off | **144** | On если raw == 2 (`decodeEspOffStatusRaw`, 1 = ESP/VDC active) | **144** | **2** on / **1** off | cfg push + pull `EspOffSwitch` |
| **Android 10** — ESP off | VHAL **289412118** ← 144 | On если raw == 1 (stock CarCommon1) | VHAL **289415943** ← 144 | **1** on / **2** off | onChange + pull |
| **Android 9** — LAS mode (LDW/LKA/OFF) | **17** `eVEHICLE_PROPERTY_LAS_MODE_SELECTION` | **1** LDW / **2** LKA / **3** OFF | **17** | **1** / **2** / **3** | cfg push + pull `LasModeSelection`; виджеты LDW/LKA |
| **Android 10** — LAS mode | VHAL **289415706** ← 17 | то же 1/2/3 (stock LDWLKA_LaneAssitfeedback) | VHAL **289415946** ← 17 | **1** LDW / **2** LKA / **3** OFF | onChange + pull |
| **Android 9** — TJA/ICA | **23** `eVEHICLE_PROPERTY_TJA_ICA` | 1 Off / 2 On | **23** | 1↔2 | cfg push + pull `TjaIca` |
| **Android 10** — TJA/ICA | VHAL **289415716** ← 23 | raw == 1 On | VHAL **289415939** ← 23 | **2** on / **1** off | onChange + pull |
| **Android 9** — HMA (smart high beam) | **130** `eVEHICLE_SMART_HIGHBEAM_SWITCH` (не headlights **19**) | 1 Off / 2 On | **130** | 1↔2 | cfg push + pull `HmaSwitch` |
| **Android 10** — HMA | VHAL **289415702** ← 130 | raw == 1 On (stock CarOutLight) | VHAL **289415948** ← 130 | **1** on / **0** off (≠ 1/2) | onChange + pull |
| **Android 9/10** — BSD | A9 **15**; A10 read **289415723** | A9 2 On / 1 Off; A10 raw 1 On | A9 **15**; A10 write **289415055** | A9 2 on / 1 off; A10 1 on / 2 off | settings only, `Bsd` |
| **Android 9/10** — DOW | A9 **13**; A10 read **289415729** | A9 2 On / 1 Off; A10 raw 1 On | A9 **13**; A10 write **289415065** | A9 2 on / 1 off; A10 1 on / 2 off | settings only, `Dow` |
| **Android 9/10** — FCW master | A9 **96**, **20**, **22**; A10 **289415696**, **289415698**, **289415699** | A9 2 On / 1 Off; A10 raw 1 On | A10 **289415937**, **289415941**, **289415942** | 2 on / 1 off; writes all three together | settings only, `Fcw` |
| **Android 9/10** — FCW sensitivity | A9 **97**; A10 **289415697** | **3** Far / **1** Standard / **2** Near (штатка A9 Close/Standard/Far = 2/1/3; A10 Far/Standard/Near = 3/1/2) | A10 **289415936** | **3** / **1** / **2** на обоих бэкендах | settings only |
| **Android 9/10** — LDW sensitivity | A9 **16**; A10 **289415707** | A9 1 High / 0 Low; A10 inverted read | A10 **289415949** | A10 1 High / 0 Low | settings only |
| **Android 9** — Режим фар (Lightcontrol) | **135** `eVEHICLE_LIGHTCONTROL` | **1** AUTO / **2** PARK / **3** LOW / **4** OFF (`decodeLightControlRaw`) | **135** | **1…4** | cfg push + pull `LightControl`; виджет цикла |
| **Android 10** — Режим фар | VHAL **289412613** ← 135 (read = write-echo `T_0405_SET_Lightcontrol`; не LowBeamSts **289412250**, тот binary) | то же 1…4 | VHAL **289412613** ← 135 | **1** AUTO / **2** PARK / **3** LOW / **4** OFF | onChange + pull |
| **Android 9** — Задний ПТФ | **136** `eVEHICLE_REARFOGLIGHT` | **1** Off / **2** On (`decodeRearFogMbCanRaw`) | **136** | 1↔2 | cfg push + pull `RearFogLight` |
| **Android 10** — Задний ПТФ | VHAL **289412136** ← 136 | raw == 1 On (`decodeVhalBinaryOneIsOn`) | VHAL **289412612** ← 136 | **1** on / **2** off (stock CarOutLight) | onChange + pull |
| **Android 9/10** — Auto lock / Auto unlock | **1** / **2** | A9: 1 Off / 2 On; A10: raw 1 On / 2 Off | **1** / **2**; VHAL **289412661** / **289412660** | A9: 1↔2; A10: **1** on / **2** off | cfg push/pull; VHAL onChange + pull |
| **Android 9/10** — Follow-me-home | **7** | A9 30/60/3(off); A10 **289412130** = 1/2/3 | **7**; VHAL **289412656** | A9 30/60/3; A10 1/2/3 | `FollowMeHome`, normalized enum |
| **Android 9/10** — Unlock mode / lock feedback | **131** / **3** | unlock 1/2; A9 feedback **1** light / **2** horn / **3** light+horn; A10 status **289412144** **0** light+horn / **1** light / **2** horn | **131** / **3**; VHAL **289412608** / **289412668** | unlock 1/2; A9 feedback 1/2/3; A10 write **2** light / **3** horn / **1** light+horn | cfg/onChange + pull |
| **Android 9/10** — Wiper sensitivity / rear wiper | **191** / **186** | sensitivity 1..4; rear A9 1 Off / 2 On, A10 **289412193** 1 On / 2 Off | **191** / **186**; VHAL **289412688** / **289412681** | sensitivity 1..4; rear A10 1 on / 2 off | settings only |
| **Android 9/10** — Low beam height / turn flashes | **129** / **8** | A9 1..4 / **1=3 миг. / 2=5 / 3=7**; A10 **289412261** inverted 0..3, **289412257** zero-based 0..2 → UI 1..3 | **129** / **8**; VHAL **289412610** / **289412665** | low beam VHAL UI→4/3/2/1; flashes write **1/2/3** (3/5/7 миганий) | normalized StateFlow |
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
| **Android 9** — AC MAX | **228** `eVEHICLE_SET_RRM_ACMAX_REQ` | 1 Off / 2 On | **228** | 1↔2 | cfg push + pull `HvacAcMax` |
| **Android 10** — AC MAX | VHAL **289412209** ← 228 | On если raw == 2 (stock AcFragment) | VHAL **289412714** ← 228 | **2** on / **1** off | onChange + pull |
| **Android 9** — HVAC custom (ECO/Comfort/Strong) | **140** `eHVAC_CUSTOM` | **1** ECO / **2** Comfort / **3** Strong | **140** | **1** / **2** / **3** | cfg push + pull `HvacCustomMode` |
| **Android 10** — HVAC custom | VHAL **289415186** ← 140 | raw **0..2** → UI = raw+1 | VHAL **289415317** ← 140 | **1..3** | onChange + pull |
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
| **Android 9** — Температура левая | **37** | raw 160…300 → °C = raw/10 (`mbCanTempRawToCelsius`) | **37** | 160…300, шаг 5. Виджет ±: `hvacTempStepTenths` 5 (0,5 °C, по умолчанию) или 10 (1,0 °C: 22,5 + → 23,0, 22,5 − → 22,0, далее ±1,0). `HvacClimateDomain.adjustCelsius` | cfg push + pull |
| **Android 10** — Температура левая | VHAL **289415169** ← 37 | raw 32…60 → °C = raw/2 | VHAL **289415313** ← 37 | VHAL raw через `mbCanTempRawToVhalWrite`. Тот же шаг виджета, запись всё равно на сетке 0,5 °C | onChange + pull |
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

### Автоскладывание зеркал при запирании

Это отдельная настройка Car Settings, не pulse-команда `MIRROR_FOLD_SWITCH`.

| Платформа | Чтение | Запись | Значения | Сигнал |
|---|---|---|---|---|
| **Android 9** | Vehicle **4** `MIRROR_AUTOFOLD_SW` | **4** | 1 Off / 2 On | `MirrorAutoFold` |
| **Android 10** | VHAL **289412131** `R_0400_CEM_2_Mirror_Fold_Sts` | VHAL **289412657** `T_0401_IHU_1_DVD_SET_Mirror_Fold` | write: 1 On / 2 Off; read: **0 On**, иначе Off | `MirrorAutoFold` |

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

Виджеты `driveModeWidget` (кнопка одного режима) и `driveModeCycleWidget` (цикл по выбранным) пишут те же свойства **145** / **149**. Для 6DCT в конфиге плитки используются синтетические rawValue `100–102` (см. `DriveModeWidget.kt`); на шину уходит `propertyValue` 0/1/2. Циклический виджет определяет текущий режим через `resolveDriveModeCycleCurrentRaw` по семейству `selectedDriveModes` (стандарт → **145**, 6DCT → **149**), а не через `resolveDriveModeThemeKey` (темы: сначала стандарт, иначе 6DCT).

---

## Аудио

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Сырые значения записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|----------------------|-------------|
| **Android 9** — Громкость медиа/телефон/навигатор/голос | Platform OpenOS usage **1/2/12/16** (fallback `AudioManager` streams) | 0…31 / 1…31 / 0…10 / 2…10 | same | Car Settings → Аудио; виджет медиа | **не** mbCAN; poll **500 ms** while UI observes |
| **Android 10** — Громкость медиа/телефон/навигатор/голос | SettingsSvc streams **3/6/7/9** | same ranges | same | Car Settings → Аудио; виджет медиа | **не** VHAL; poll **500 ms** while UI observes |
| **Android 9** — Динамик подголовника | Audio **37** `eAUDIO_AUDIO_HEADREST_SPEAKER` | **0** выкл / **1** только подголовник / **2** ассистент → UI 3/1/2 | **37** | UI 1/2/3 → 1/2/0 | pull 30 s / burst 1.5 s на `mbcan-state-apply` (не main; OEM JNI не thread-safe) |
| **Android 10** — Динамик подголовника | SettingsSvc `get/setHeadrestSpeakerMode` | **1** только / **2** ассистент / **3** выкл | same | 1/2/3 | Car Settings → Аудио; poll 30 s / burst 1.5 s |
| **Android 9** — Volume vs speed | Audio **13** | raw **0** Off / **1** Low / **2** Mid / **3** High → UI 1…4 | **13** | UI 1…4 → raw 0…3 | cfg_audio push + pull |
| **Android 10** — Volume vs speed | VHAL **557849227** | raw **1** Off / **2** Low / **3** Mid / **4** High | VHAL **557849227** | 1…4 | onChange + pull |
| **Android 9** — Звук клавиш | Audio **17** `eAUDIO_PROPERTY_VOLUME_KEY` | **0** mute / **1** low / **2** medium / **3** high | Audio **17** | **0…3** | cfg_audio push + pull `AudioKeyToneVolume`; Car Settings only |
| **Android 10** — Звук клавиш | — | Platform audio stream only; verified VHAL map отсутствует | — | — | control disabled |
| **Android 9** — Громкость тревоги парктроника | Audio **11** `eAUDIO_PROPERTY_VOLUME_RADAS` | **1** low / **2** medium / **3** high | Audio **11** | **1…3** | cfg_audio push + pull `AudioRadarAlarmVolume`; Car Settings only |
| **Android 10** — Громкость тревоги парктроника | — | Platform audio stream only; verified VHAL map отсутствует | — | — | control disabled |
| **Android 9** — EQ mode | Audio **10** `eAUDIO_PROPERTY_EQMODE` | **1** Pop / **2** Rock / **3** Jazz / **4** Classic / **5** Voice / **255** Custom (stock `AudioViewModel`; UI position 0 = Custom) | Audio **10** | same | cfg_audio push + pull `AudioEqMode`; Car Settings only |
| **Android 9** — EQ bands | Audio **5** bass, **6** mid, **7** treble | each **−7…+7** | same | **−7…+7** | cfg_audio push + pull; Car Settings only |
| **Android 9** — Balance / fader | Audio **3** / **4** | raw **0…14** → UI **raw−7** (−7…+7) | same | UI **+7** → raw **0…14** | cfg_audio push + pull; Car Settings only |
| **Android 10** — EQ / balance / fader | — | Platform `SettingsSvc` only; no verified VHAL map | — | — | controls disabled; no VHAL subscription or writes |
| **Android 9** — ICM manual brightness | Vehicle **209** `eVEHICLE_ICM_BRIGHTNESS_MANUAL_ADJ` | **1…10** | **209** | 1…10 | cfg_vehicle push + pull `IcmManualBrightness` |
| **Android 10** — ICM manual brightness | VHAL **289414939** `R_0900_ICM_4_BrightnessFed` | **1…10** | VHAL **289415087** `T_0901_IHU_ICMBrightnessManualAdj` | 1…10 | onChange + pull |
| **Android 9** — ICM brightness mode | Vehicle **208** `eVEHICLE_SET_ICM_BRIGHTNESS_MODE` | **0** auto / **1** manual | **208** | 0 auto / 1 manual | cfg_vehicle push + pull `IcmBrightnessMode` |
| **Android 10** — ICM brightness mode | VHAL **289415088** `T_0901_IHU_SET_ICMBrightnessMode` | **0** auto / **1** manual | VHAL **289415088** | 0 auto / 1 manual | onChange + pull |

---

## Телеметрия (RPM, температура двигателя, скорость, PRND, топливо, одометр, t° снаружи)

| Платформа + наименование | Параметр чтения | Сырые значения чтения и декод | Параметр записи | Push / Pull |
|--------------------------|-----------------|-------------------------------|-----------------|-------------|
| **Android 9** — Engine RPM | `readVehicleEngineRpm()` (telemetry) | float ≥ 0 | — | **Push:** telemetry bridge. **Pull:** `refreshEngineRpm` (30 s / burst) |
| **Android 10** — Engine RPM | VHAL **289414951** `R_0900_EMS_1_EngineSpd` | raw × **4** (`decodeEngineRpm`) | — | onChange + pull |
| **Android 9** — Coolant temp | telemetry float | °C as-is from facade; **на практике с mbCAN всегда `0.0`** | — | push + pull; **в учёте поездок не используется** (только TBox CRT) |
| **Android 10** — Coolant temp | VHAL **289414949** | raw × **0,75 − 48** | — | onChange + pull; в поездках приоритет TBox, HU если TBox stale |
| **Android 9** — Vehicle speed | telemetry float | km/h ≥ 0 | — | push + pull |
| **Android 10** — Vehicle speed | VHAL **557845547** `MCU_REPLY_SPEED` (штатный SystemSettings `AdayoCanManager`) | **км/ч = raw as-is** (INT32 ≥ 0; `VehicleSpeedDomain.decodeMcuReplyKmh`). Не CAN raw/16 | — | onChange (continuous rate) + pull |
| **Android 9** — Gear PRND | `readVehicleGearMode()` / `MBCanVehicleSpeed.getGear()` (type **20**, fallback **1**) | **1→N, 2→R, 4→P, 8→D** (`VehicleGearDomain.decodePrndBitmask`); иначе null | — | **Push:** `onCanVehicleSpeed` (тот же callback для `eMBCAN_VEHICLE_GEAR`) + pull. Виджет `gearBoxMode` с `useMbCanVhal` |
| **Android 10** — Gear PRND | VHAL **289408000** `GEAR_SELECTION` (+ fallback **289408001** `CURRENT_GEAR`) | то же bitmask | — | onChange + pull |
| **Android 9** — ReverseGearSwitch | `readReverseGearSwitch()` / `MBCanVehicleBcmStatus.getReverseGearSwitch()` (type **21**) | Dashing CEM inverted: **0** → engaged (`true`) / **1** → not reverse (`false`); иное → null (`decodeReverseGearSwitch`) | — | **Push:** `onVehicleBcmStatusChange` + pull. StateFlow `reverseGearSwitchState`. **DR/mock:** настройка `mock_consider_reverse` + `VehicleGearDomain.isReverseEngaged` — 1) HU PRND `R`, 2) известный не-`R` HU → не задняя (switch игнор), 3) нет HU PRND → switch, 4) иначе TBox PRND `R`. Не применяется в режиме «Прямой» |
| **Android 10** — ReverseGearSwitch | VHAL **289412135** `R_0400_CEM_2_ReverseGearSwitch` | то же inverted 0/1 | — | onChange + pull; та же лестница при включённой опции |
| **Android 9** — AccStatus | `readAccStatus()` / `MBCanVehicleAccStatus.getAccStatus()` (type **6** `eMBCAN_VEHICLE_ACCSTATUS`) | **4→acc** (ACC ON), **5→ign** (ON), **0…3→off**; иное → null (`AccStatusDomain.decodeMbCan`) | — | **Push:** `onVehicleAccStatusChange` (settings telemetry, только payload) + pull. StateFlow `accStatusState`. Автоматизации: HU-only сигнал `acc_status` |
| **Android 10** — AccStatus | VHAL **557845540** `MCU_REPLY_ACC_STATUS` | шкала **не** 4/5: **1 и 2→acc**, **0 и 3→off**; иное → null (`AccStatusDomain.decodeMcuReply`). Штатный CarSettings: 1=доступен, 2=переход 4 с, 3=недоступен | — | onChange + pull; тот же `MbCanSignal.AccStatus` |
| **Android 9** — GasPedal | `readGasPedalPercent()` / `MBCanVehicleGaspedStatus` (type **36** `eMBCAN_VEHICLE_GASPED_STATUS`) | `%` 0…100; invalid ≠ 0 или вне диапазона → null (`PedalDomain.decodeGasPedalPercent`) | — | **Push:** тот же OEM gasped listener, что CCS (`registIMBCanVehicleGaspedStatusListener`, один слот) + pull `getMbCanData(36)`. StateFlow `gasPedalPercentState`. Виджет `gasBrakeWidget`. Автоматизации: HU-only `gas_pedal` |
| **Android 10** — GasPedal | VHAL **289414943** `R_0900_EMS_1_GasPedalPosition` + **289414944** `…InvalidData` | то же 0…100 / invalid ≠ 0 → null | — | continuous + onChange invalid + pull; `MbCanSignal.GasPedal` |
| **Android 9** — BrakePedal | `readBrakePedalPressed()` / `MBCanVehicleBcmStatus.getBrakePedalSts()` (type **21**) | CEM 1-bit: **1** нажата / **0** отпущена; иное → null (`PedalDomain.decodeBrakePressed` = `decodeCemBinaryActive`). Не inverted reverse-gear | — | **Push:** `onVehicleBcmStatusChange` (payload only) + pull. StateFlow `brakePedalPressedState`. Виджет `gasBrakeWidget` (красный текст). Автоматизации: HU-only `brake_pedal` (`on`/`off`) |
| **Android 10** — BrakePedal | VHAL **289412311** `R_0400_CEM_2_BrakePedalSts` | то же CEM 1-bit | — | onChange + pull; `MbCanSignal.BrakePedal` |
| **Android 9** — WiperSts | `readWiperOperatingMode()` / `MBCanVehicleBcmStatus.getWiperSts()` (type **21**) | TTG: **0** Off / **1** INT / **2** Low / **3** High; иное → null (`WiperStsDomain.decode`). TTG на части комплектаций рисует AUTO вместо INT для raw **1** — у нас raw **1** всегда Intermittent, overlay **1/2/3 черты**. Wash в TTG нет | — | **Push:** `onVehicleBcmStatusChange` (payload only) + pull. StateFlow `wiperOperatingModeState`. Виджет `wiperMaintenanceWidget` (иконка). Автоматизации: HU-only `wiper_sts` (`off`/`int`/`low`/`high`) |
| **Android 10** — WiperSts | VHAL **289412138** `R_0400_CEM_2_WiperSts` | та же шкала 0…3 | — | onChange + pull; `MbCanSignal.WiperSts` (piggyback к виджету обслуживания). Автоматизации: тот же HU-only `wiper_sts` |
| **Android 9** — TurnSignals (L/R/hazard) | `MBCanVehicleTurnLight` (type **2** `eMBCAN_VEHICLE_TURNLIGHT`) | raw **2** = active (`TurnSignalsDomain.decodeMbCanTurnLightActive`); оба **2** ⇒ hazard (`fromMbCanTurnLightRaw` / stock `AutoMapTransfer`) | — | **Push:** общий `IMBVehicleListener` (`syncImbVehicleListener`) `onVehicleTurnLightChange` + pull. Один `MbCanSignal.TurnSignals` на все три. StateFlow `turnSignalsState` (сырой). **Защёлка:** `UniversalCanRepository.turnSignalsLatchedSide` / `latchedTurnSignalSide()` — L/R, hold 2,5 с после вспышки; L↔R и hazard сбрасывают другую сторону. **DR/mock:** interest вместе с gear в `mock-location-dr-gear`. Matcher и любые другие потребители читают latched, не сырой. geo-debug: `geo-debug-steering` также держит TurnSignals; `turn.side` сырой, `turn.latched` защёлка |
| **Android 10** — TurnSignals (L/R/hazard) | VHAL **289412258** `DirectionIndLeft`, **289412259** `DirectionIndRight`, **289412154** `HazardLightSW` | CEM 1-bit: **1** on / **0** off (`decodeCemBinaryActive`). Для DR — DirectionInd (стабильный stalk), не мигающий `LH/RHTurnlightSts` | — | onChange + pull тем же `MbCanSignal.TurnSignals`; та же защёлка в `UniversalCanRepository` (хвост 2,5 с после снятия стебля) / geo-debug |
| **Android 9** — Fuel level % | `readVehicleFuelLevelPercent()` / `getFuelLevel()` | **0…100**; иначе null | — | push `onCanVehicleFuelLevel` + pull |
| **Android 10** — Fuel level % | VHAL **289414929** `R_0900_ICM_1_FuelLevel` | int **0…100** | — | onChange + pull |
| **Android 9** — Total odometer | `readTotalOdometerKm()` / `getOdometer()` | float km → UInt | — | push `onVehicleTotalOdoMeterChange` + pull |
| **Android 10** — Total odometer | VHAL **289414930** `R_0900_ICM_1_TotalOdometer_Km` | int km as-is | — | onChange + pull |
| **Android 9** — Wheel pulse counters | `MBCanVehicleWheel` / `eMBCAN_VEHICLE_WHEEL` (4); LHF/RHF/LHR/RHR | int, **wrap 13 бит** (0…8191); `WheelPulseOdometer.COUNTER_BITS` | — | **Push:** `IMBVehicleListener.onPull` (`syncImbVehicleListener` + interest `WheelPulse`) + pull; `WheelPulseOdometer` |
| **Android 10** — Wheel pulse counters | VHAL **289412182/179/175/177** `R_0400_ESP_5_*PulseCounter` LHF/RHF/LHR/RHR | int, **wrap 13 бит** (тот же ESP_5) | — | onChange + pull (`MbCanSignal.WheelPulse`); `WheelPulseOdometer` |
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
| **Android 9** — Steering angle | `MBCanVehicleSteeringAngle` (type 3) | float ° / °/с as-is | — | **Push:** `IMBVehicleListener.onSteeringWheel` → `scheduleSteeringAnglePush` + pull `getMbCanData(3)`; interest держит `eMBCAN_VEHICLE_STEERING_ANGLE` через `MbCanJobManager` (после reapply — `ensureOemSubscriptions`, иначе subscribe мог «застрять» deferred и push молчал ≈30 с poll). geo-debug: `geo-debug-steering`; mock DR: `mock-location-dr-steering` |
| **Android 10** — Steering angle | VHAL **557845548** `MCU_REPLY_STEERING_WHEEL_ANGLE` | **° = raw as-is** (`SteeringAngleDomain.decodeMcuReplyDeg`); °/с нет | — | onChange (continuous) + pull; `steerSpeed` на A10 всегда null; те же interest id, что на A9 |

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

## Пользовательские автоматизации

`AutomationCanCatalog` публикует для автоматизаций проверенное подмножество
`MbCanCommandRegistry` / `MbCanAudioCommandRegistry`. Новых property id, raw encode или
отдельного backend path нет: executor всегда вызывает `UniversalCanRepository.execute`.

Фильтр безопасности:

- `SetAnyInt` не публикуется;
- `SYSTEM_REBOOT`, MFS cruise pulses и raw speed-limiter 253/254 не публикуются;
- `TRUNK_PLG_CONTROL` публикуется только как `MbCanCommand.TrunkPulse(1|2)`, без отдельной
  программной проверки скорости или PRND (как у виджета багажника);
- допустимые set-значения берутся непосредственно из `SetExact` / `SetRange` /
  `ToggleBinary`, поэтому вручную изменённый JSON не обходит policy registry.

Триггеры автоматизаций регистрируют отдельный interest sourceId `user-automations` только
для реально используемых `MbCanSignal`. Источник каждого условия/триггера выбирается явно:
TBox либо текущий backend ГУ (mbCAN/VHAL); автоматического fallback между ними нет.

Подробнее: [AUTOMATIONS_RU.md](AUTOMATIONS_RU.md).

---

## Примечания

1. **mbCAN id** в таблицах — это `MbCanKnownVehiclePropertyId.*` (legacy `MBVehicleProperty`).
2. **Декодеры** намеренно различаются между A9 и A10 там, где stock-приложения используют разную семантику (SLA, SYNC, trunk, VHAL binary read).
3. Параметры из `MbCanCatalog.controls`, не подключённые к отдельному `MbCanSignal`
   (PM2.5 toggle, UV lamp, sterilize, brake feel и т.д.), в основных таблицах не перечислены.
   Они могут записываться из Car Settings/автоматизаций через registry, но не доступны как
   signal-триггеры без отдельного read/decode flow.
4. При изменении decode/write логики обновляйте этот файл вместе с доменными тестами (`*DomainTest`, `MbCanSignalStateEngine`).
