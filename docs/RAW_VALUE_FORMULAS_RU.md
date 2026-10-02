# Формулы пересчёта сырых значений (TBox / mbCAN / VHAL)

Справочник **масштабирующих** формул: `raw → физическая величина`.  
Дискретные enum/on-off (1=Off, 2=On и т.п.) — в [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).  
Протокол UDP и роли модулей — в [TBOX_PROXY_RU.md](TBOX_PROXY_RU.md).

| Источник | Где в коде |
|----------|------------|
| **TBox LOC / CRT PowVol / Cycle** | `BackgroundService.ansLOCValues`, `ansCRTPowVol`, `ansCRTCycleSignal` |
| **TBox CRT CAN-кадры** | `utils/CanFramesProcess.kt` (зеркало: `tools/can_log_to_xlsx.py`) |
| **mbCAN (Android 9)** | `MbCanEngineFacade`, `HvacClimateDomain`, `SlaSpeedLimitDomain` |
| **VHAL (Android 10)** | `Android10VhalRepository`, те же домены |

> **Важно:** для RPM и t° ОЖ масштаб на шинах **разный**: TBox CAN — `raw/4` и `raw×0.75−48`; VHAL RPM — `raw×4`, VHAL coolant — та же `raw×0.75−48`. mbCAN telemetry часто отдаёт уже готовые float (RPM/скорость as-is; coolant на практике `0.0`).

---

## 1. Сводка одинаковых величин по источникам

| Параметр | TBox (CAN / Cycle / LOC) | mbCAN (A9) | VHAL (A10) | Ед. |
|----------|--------------------------|------------|------------|-----|
| Скорость | CAN `0x430`/`0x502`/`0x310`: **raw/16**; Cycle: **raw/16**; LOC GPS: **raw/10** | as-is (float ≥ 0) | **557845547** `MCU_REPLY_SPEED`: **км/ч = raw as-is** | км/ч |
| RPM | CAN `0xFA` / Cycle: **raw/4** | as-is (float ≥ 0) | **raw × 4** | об/мин |
| t° ОЖ | CAN `0x501`: **raw×0.75 − 48** | as-is °C (часто `0.0`) | **raw×0.75 − 48** | °C |
| Топливо % | CAN `0x430`: as-is 0…100 | 0…100 | 0…100 | % |
| Одометр | CAN `0x430` UINT20 / Cycle UINT32: as-is | km → UInt | int km as-is | км |
| Напряжение бортсети | CAN `0x430`: **raw/10**; PowVol/Cycle: **raw/1000** | — | — | В |
| Давление шин | CAN `0x51B` / Cycle: **raw/36** (`0xFF` → null) | mbCAN: `fPressure` bar as-is (−1 invalid) | VHAL: **raw × 0.0275** (≤0 или >3.5 → null) | бар |
| t° шин | CAN `0x51B`: **raw − 60**; Cycle: as-is при флаге валидности | mbCAN: `nTemperature` °C as-is (−100 invalid) | VHAL: **raw − 60** (raw ≤0 или ≥150 → null) | °C |
| t° снаружи | CAN `0x535`: **raw×0.5 − 40** | signed byte °C; **87** = invalid | **(raw & 0xFF)×0.5 − 40**; вне [−40; 87) → null | °C |
| HVAC setpoint | CAN `0x52F`: **raw/4** | mbCAN **37/111**: **raw/10** (160…300) | VHAL: **raw/2** (32…60) | °C |
| SLA знак | — | LKA Spdlimit: **(raw−1)×5** | то же | км/ч |

---

## 2. TBox — нативные модули (UDP)

### 2.1. LOC GPS — `ansLOCValues` (LOC `0x05` / DID `0x82`)

Декод: `LocPayloadParser` (тело после 6 байт заголовка).

**Бинарный формат** (если первый байт ≠ `$`):

| Параметр | Сырой тип | Формула | Ед. |
|----------|-----------|---------|-----|
| Долгота | int32 + dir uint8 | `raw / 1_000_000 × (−1 если dir==1 иначе 1)` | ° |
| Широта | int32 + dir uint8 | `raw / 1_000_000 × (−1 если dir==1 иначе 1)` | ° |
| Высота | int32 | `raw / 1_000_000` | м |
| Скорость GPS | uint16 | `raw / 10` | км/ч |
| Истинный / магнитный курс | uint16 | `raw / 10` | ° |
| UTC, спутники, locateStatus | uint8 | as-is / `!= 0` | — |

**NMEA** (если тело начинается с `$…RMC` / `$…GGA`): широта/долгота из `ddmm.mmmm` → десятичные градусы; скорость RMC в узлах → **× 1.852** км/ч; `locateStatus` = RMC `A` или GGA quality > 0.

Mock GPS: `speed_m/s = speed_kmh / 3.6` (`LocationMockManager`).

### 2.2. CRT напряжения — `ansCRTPowVol`

| Параметр | Сырой тип | Формула | Ед. |
|----------|-----------|---------|-----|
| voltage1…3 | uint16 LE | `raw / 1000` | В |

### 2.3. CRT Cycle Signal — `ansCRTCycleSignal`

Blob ≥346 байт; `offset = −4` если size==346, иначе `0`.

| Параметр | Смещение / тип | Формула | Ед. |
|----------|----------------|---------|-----|
| Напряжение | uint16 LE @1 | `raw / 1000` | В |
| Одометр | uint32 LE @9+off | as-is | км |
| Давление шин 1–4 | uint8 @21–24+off; `0xFF`→null | `raw / 36` | бар |
| Скорость | uint16 LE @28+off | `raw / 16` | км/ч |
| Ускорение поперечное / продольное | uint16 LE @30 / @32+off | `raw / 1000 − 2` | м/с² |
| RPM | uint16 LE @36+off | `raw / 4` | об/мин |
| Скорости колёс 1–4 | uint16 LE @103…114+off | **без масштаба** (raw float) | сырое |
| t° колёс 1–4 | uint8 (+ флаг валидности) | as-is при флаге==1, иначе null | °C |
| Угловая скорость (yaw) | uint16 LE @135+off | `raw / 100 − 180` | °/с |

---

## 3. TBox — CRT CAN-кадры (`CanFramesProcess`)

Payload 8 байт, multi-byte — big-endian, если не указано иное.

| Параметр | CAN ID | Сырое | Формула | Ед. / примечание |
|----------|--------|-------|---------|------------------|
| Угол руля | `0xC4` | uint16; `65535`→null | `(raw − 32767) / 16` | ° |
| Скорость руля | `0xC4` | int8 (b2) | as-is | °/с (отображение) |
| RPM | `0xFA` | uint16 | `raw / 4` | об/мин |
| param1 (педаль газа, гипотеза) | `0xFA` | uint8 b3 | `raw / 2.55` | % |
| param2 (крутящий момент, гипотеза) | `0xFA` | uint16 @+4 | `(raw − 2000) / 2` | Н·м |
| param3 (скорость по колёсам, гипотеза) | `0x200` | uint16 @+4 | `raw / 100` | км/ч (кратна 1; ≈ средняя 0x310) |
| t° ОЖ (вычисляется, не публикуется с этого ID) | `0x278` | uint8 b0 | `raw × 0.75 − 48` | °C (в UI temp с `0x501`) |
| param4 (дроссель, гипотеза) | `0x278` | uint8 b5 | `raw / 2.55` | % (255 = WOT) |
| До ТО | `0x287` | uint16 @+4 | as-is | км |
| Тормозная сила | `0x2E9` | uint8 b2 | as-is | — |
| t° масла КПП | `0x300` | uint8 b2 | `raw − 40` | °C |
| Круиз setpoint | `0x305` | uint8 b0 | as-is | км/ч |
| Скорости колёс ×4 | `0x310` | 4× uint16 | `raw / 16` | км/ч |
| Скорость авто | `0x430` | uint16 | `raw / 16` | км/ч |
| Напряжение | `0x430` | uint8 b2 | `raw / 10` | В |
| Топливо % | `0x430` | uint8 b4 | as-is | % |
| Одометр | `0x430` | UINT20 nibble-BE @+5 | as-is | км |
| Мгновенный расход | `0x4E0` | uint16 @+2; `0xFFFF`→null | `raw / 160` | л/100 км |
| t° ОЖ | `0x501` | uint8 b2 | `raw × 0.75 − 48` | °C |
| param5 (обороты, гипотеза) | `0x501` | uint8 b4 | `raw × 32` | об/мин (грубая копия RPM с `0xFA`) |
| Скорость точная | `0x502` | UINT12 nibble @+1; b2==0 → 0 | `raw / 16` | км/ч |
| t° шины | `0x51B` | uint8 b3; `0xFF`→null | `raw − 60` | °C |
| Давление шин ×4 | `0x51B` | uint8 b4…b7; `0xFF`→null | `raw / 36` | бар |
| Климат setpoint | `0x52F` | uint8 b5; 0 игнор. | `raw / 4` | °C |
| Запас хода | `0x530` | uint16 @+2 | as-is | км |
| t° салона / снаружи | `0x535` | uint8 b5 / b6 | `raw × 0.5 − 40` | °C |
| Качество воздуха | `0x53A` | 2× uint16; 0 или 65535→null | as-is | AQI-like |

Режимы КПП, сидений, блокировка стёкол — битовые/enum-маппинги (без scale), см. код `CanFramesProcess`.

### Неизвестные CAN ID (журналы `09`, гипотезы)

В логах 62 ID; `CanFramesProcess` декодирует 20. По timestamp-join с проверенными сигналами:

| CAN ID | Наблюдение | Гипотеза |
|--------|------------|----------|
| `0x313` | u16@0 и u16@2: r≈1.00 со скоростями колёс 0x310 w0/w1 | скорости **передних** колёс, `km/h ≈ raw / 117.65` |
| `0x316` | u16@0 и u16@2: r≈1.00 с w2/w3 | скорости **задних** колёс, тот же scale |
| `0x394` | u16@2: `(raw − 32767) / 16` ≈ угол руля 0xC4 (ср. ошибка ~0.6°) | копия / шлюз угла руля |
| `0x127` | u16@2 ≈ 1684…2318, центр ~2000–2048; слабая связь с рулём | вероятно yaw / поперечное ускорение (offset ~2048) |
| `0x1B2`, `0x31C` | байты шагают комплементарно (`xx yy F0…` / `00 yy xx…`) | alive / rolling counter |
| `0x260` | два одинаковых u16; на ХХ ~25776, в движении ~9600–10100 | неизвестно (не RPM; возможно давление/нагрузка — нужна проверка) |
| `0x1AE`, `0x220` | мало уникальных значений + «шум» в младших байтах | статусы / флаги + счётчик |
| `0x3A6`…`0x3C1`, `0x517` | байты с 2 значениями, коррелируют с t° масла/салона | скорее **режимные флаги** (корреляция через время прогрева), не сами температуры |
| `0x560`, `0x565`, `0x58D`, `0x5D4`, `0x5E3`, `0x5F6` | константы весь лог | конфиг / заглушки |
| `0x323`, `0x480`, `0x5EE` | 1–2 значения | битовые флаги |

Не внедрять в UI без дорожной проверки (кроме уже внесённых param1–5).

---

## 4. mbCAN (Android 9) и VHAL (Android 10) — непрерывные величины

| Параметр | mbCAN / A9 | VHAL / A10 (id) | Формула decode | Encode (если ≠) | Ед. |
|----------|------------|-----------------|----------------|-----------------|-----|
| Engine RPM | telemetry float | **289414951** | A9: as-is; A10: **raw × 4** | — | об/мин |
| Coolant temp | telemetry float | **289414949** | A9: as-is °C; A10: **raw × 0.75 − 48** | — | °C |
| Vehicle speed | telemetry float | **557845547** `MCU_REPLY_SPEED` | **км/ч = raw as-is** (INT32 ≥ 0) | — | км/ч |
| Steering angle | telemetry float ° / °/с | **557845548** `MCU_REPLY_STEERING_WHEEL_ANGLE` | **° = raw as-is**; °/с нет | — | ° |
| Fuel % | `getFuelLevel` | **289414929** | 0…100 identity | — | % |
| Odometer | `getOdometer` | **289414930** | km as-is → UInt | — | км |
| Outside temp | unsigned byte (may arrive signed) | **289412223** | **(raw & 0xFF)×0.5 − 40**; вне [−40; 87) → null | — | °C |
| Gas pedal % | `getfGasPedalPosition` (уже %) | **289414943** | A9: as-is 0…100; A10: **% = raw × 100 / 255** (raw 0…255); invalid ≠ 0 → null | — | % |
| Instant fuel | `getFuelRollingCounter` | **289414918** | A9: **raw / 10**; A10: **raw × 0.1**; ≤0 → null | — | л/100 км |
| Average fuel | `getICM_4_AverageFuelConsume` | **289414933** | A9: float as-is; A10: **raw × 0.1**; ≤0 → null | — | л/100 км |
| HVAC temp L/R | **37** / **111** | read **289415169** / **289415168** | A9: **°C = raw/10** (160…300, шаг 5); A10: **°C = raw/2** (32…60) | A9: `°C×10`; A10: `°C×2`; мост `mbCanTempRawToVhalWrite` | °C |
| Fan speed | **38** | **289415171** | 0…7 identity | identity | уровень |
| SLA recognized limit | LKA `FCM_2_SLASpdlimit` | **289415711** | **(raw − 1) × 5**; raw≤1 → null; raw>27 → **130** | — | км/ч |
| Limiter target | DataStore | write resolve(**253**) | clamp 0…150, шаг 5 | identity km/h | км/ч |

Код: `Android10VhalRepository.decodeEngineRpm` / `decodeEngineTemperature`, `HvacClimateDomain.mbCanTempRawToCelsius` / `vhalTempRawToCelsius`, `SlaSpeedLimitDomain.decodeRecognizedSpeedKmh`.

---

## 5. Производные (не шина)

Не сырой decode с TBox/mbCAN/VHAL, но часто рядом:

| Величина | Формула | Где |
|----------|---------|-----|
| Литры (линейно) | `pct / 100 × tankLiters` | `FuelLevelMath` / `FuelCalibrationLive` |
| Калиброванные литры | smart estimator от sensor liters + ambient °C | `FuelSmartEstimator` — см. [fuel-refuels-calibration.md](fuel-refuels-calibration.md) |

---

## 6. Быстрый индекс scale/offset

```
TBox LOC:     lat/lon/alt = raw/1e6 (±dir); speed/course = raw/10
TBox PowVol:  V = raw/1000
TBox Cycle:   V=raw/1000; P=raw/36; v=raw/16; a=raw/1000−2; rpm=raw/4; yaw=raw/100−180
TBox CAN:     steer=(raw−32767)/16; rpm=raw/4; oilT=raw−40; speed=raw/16; V=raw/10
              L/100km=raw/160; engT=raw×0.75−48; tyreT=raw−60; P=raw/36
              setT=raw/4; cabin/out=raw×0.5−40
mbCAN HVAC:   °C = raw/10
VHAL HVAC:    °C = raw/2
VHAL RPM:     rpm = raw×4
VHAL coolant: °C = raw×0.75−48
VHAL gas pedal: % = raw×100/255
SLA limit:    km/h = (raw−1)×5
```

При изменении формул обновляйте этот файл вместе с тестами декодеров и, для property HU, [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md).
