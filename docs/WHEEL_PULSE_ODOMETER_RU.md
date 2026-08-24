# Wheel pulse: дистанция, калибровка, DR и поездки

План интеграции **импульсов колёс ESP** (CAN/VHAL) с **автокалибровкой по одометру** для:

- дорисовки геопозиции (mock DR / `SpeedIntegrator`);
- учёта пробега в **поездках** между редкими тиками одометра.

Связанные документы: [Trips.md](Trips.md), [MBCAN_VHAL_PARAMETERS_RU.md](MBCAN_VHAL_PARAMETERS_RU.md), [CAN_BACKENDS_RU.md](CAN_BACKENDS_RU.md).

---

## 1. Зачем

| Проблема сейчас | Что даёт pulse |
|-----------------|----------------|
| Одометр ICM — **целые км** (`UInt`); между тиками `distanceKm` в поездке **не растёт** | Метры между km-тиками |
| DR (`SpeedIntegrator`) — интеграл **CAN speed**; на 1–5 km/h и при дёрганиях speed хуже pulse | Стабильная Δs, ZUPT при нулевых импульсах |
| Штатный `libDR.so` использует odom/pulse; у нас pulse на CAN есть, но **не читается** | Паритет со штатным подходом без `libDR.so` |

---

## 2. Источники данных

### 2.1 Wheel pulse (основной канал, A9 + A10)

| Платформа | Транспорт | Сигналы |
|-----------|-----------|---------|
| **Android 9** | mbCAN `eMBCAN_VEHICLE_WHEEL` (type 16) | `MBCanVehicleWheel`: LHF / RHF / LHR / RHR pulse counters |
| **Android 10** | VHAL | **289412182** LHF, **289412179** RHF, **289412175** LHR, **289412177** RHR (`R_0400_ESP_5_*PulseCounter`) |

Подписка через `UniversalCanRepository.setSourceSignals` (новый `MbCanSignal.WheelPulse`).

### 2.2 Одометр (якорь калибровки, не primary distance)

| Платформа | ID / API | Разрешение в приложении |
|-----------|----------|-------------------------|
| A9 | mbCAN TotalOdometer | `Float` → **`toInt()`** → `UInt` km |
| A10 | VHAL **289414930** `TotalOdometer_Km` | **int km** → `UInt` |

`TripTelemetryRepository.accountingOdometerKm()` — HU first, TBox fallback (как сейчас).

### 2.3 Вспомогательные

- `accountingCarSpeed()` — fallback distance, sanity-check, standstill gate.
- Reverse / PRND — знак Δs (`UniversalCanRepository`, уже для mock DR).
- **A10 опционально:** `PulseInfo` из `A10NaviDrBackend` (middleware) — cross-check, **не** primary (CAN надёжнее без подписи Navi DR).

---

## 3. Критическое ограничение одометра

**Одометр в CAN/VHAL — целые километры.** Дробная часть **не видна** в коде:

- поездка может начаться при «5640,1 км по факту», а в шине будет **5640**;
- первый переход 5640→5641 — это **~1 км**, но неизвестно, где внутри км была машина в начале и в конце окна;
- **ошибка фазы** на одном km-тике — до **~±1 км** относительно истинных 1000 м.

### Правила (hard requirements)

1. **Запрещено** вычислять `k` (м/импульс) по **одному** изменению одометра на 1 km.
2. **Запрещено** приравнивать старт поездки к «5640,000 км» — только целый `odometerStartKm` как справочник.
3. **Primary distance** — интеграл pulse с текущим `k`; одометр — **редкий якорь** и **длинное усреднение**.
4. **Жёсткая** перекалибровка `k` — только после **N ≥ 5…10 km** согласованного окна. На каждом km-тике `k` **не** подстраивается (только закрытие доли км и пометка грязного км для сброса окна).

### 3.1 Знак дистанции: всегда «плюс» (path length)

**Один принцип для pulse, DR и поездок:** считаем **пройденный путь по колёсам** (как одометр ICM), а не знаковое смещение «вперёд/назад».

| Подсистема | Что делать с reverse |
|------------|----------------------|
| **`WheelPulseOdometer`** | `Δcounter` → **\|Δcounter\|** → `Δs_m ≥ 0`. Reverse **не** инвертирует метры. |
| **DR (`MockLocationJob`)** | Метры **положительные** (как сейчас [`SpeedIntegrator`](app/src/main/java/vad/dashing/tbox/location/SpeedIntegrator.kt) + [`takeDrDistanceM`](app/src/main/java/vad/dashing/tbox/location/MockLocationJob.kt)). Направление — только [`travelBearingFromNoseHeading(nose, reverse)`](app/src/main/java/vad/dashing/tbox/location/ConstantDrMath.kt) (+180°). **Двойная инверсия запрещена:** отрицательные метры при reverse двигают точку **вперёд**. |
| **Поездки** | `distanceDelta += flushDistanceM() / 1000f` **всегда ≥ 0**, в т.ч. на заднем ходу. Это согласовано с штатным одометром (вращение колёс) и с pulse. |

**Калибровка `k`:** жёсткие окна — **без reverse** (slip, повороты, шум), но **не** из‑за знака: в окне и odo, и pulse считают **суммарный** path length.

**Steer / bicycle model** в DR по-прежнему может использовать **signed** speed (`signedSteerSpeedKmh`) — это отдельная ось, не path length.

---

## 4. Модуль `WheelPulseOdometer`

Предлагаемое расположение: `app/.../vehicle/WheelPulseOdometer.kt` (singleton или репозиторий, аналог `TripTelemetryRepository`).

### 4.1 Состояние

| Поле | Смысл |
|------|--------|
| `lastCounters[4]` | последние raw pulse counters |
| `kMetersPerPulse` | калиброванный коэффициент (persist DataStore) |
| `calibrationConfidence` | 0…1 |
| `pulseDistanceSinceLastOdoM` | метры pulse с последнего km-тика одометра |
| `totalPulseDistanceSessionM` | для debug |
| `lastOdoKm` | последний целый km одометра |
| `odoAnchorWindowStartKm` / `pulseAtWindowStart` | для длинного окна калибровки |
| `lastAsymmetryRatio` | \|L−R\| / mean на последнем тике (slip gate, debug) |

### 4.2 Обработка сэмпла

1. **Δcounter** per wheel с учётом wrap **13 бит** (`0…8191`, `COUNTER_BITS = 13`). По geo-debug (2026-08-20) сырые счётчики ESP не превышают 8189 и переполняются ~каждые 8 тыс. импульсов; 16-битный wrap давал Δ=0 и ложную асимметрию 200%.
2. **Primary distance** — §4.3: среднее передней оси `(ΔLHF+ΔRHF)/2`.
3. **Δs всегда неотрицательна:** модуль по каждому колесу, затем mean (§3.1). Reverse передаётся в API только для debug / gate калибровки, **не** для знака метров.
4. **Standstill:** все Δcounter ≈ 0 **и** (опционально) CAN speed < порога → Δs = 0.
5. `Δs_m = Δpulses × kMetersPerPulse`; накопить в `pulseDistanceSinceLastOdoM`.

### 4.3 Левое / правое: усреднение и asymmetry

Jetour Dashing — **передний привод**. Четыре счётчика: LHF, RHF, LHR, RHR.

#### Дистанция (primary)

**Не** корректировать distance отдельной формулой по разнице L−R — **усреднять** пару на ведущей оси:

```
ΔL = |wrap(ΔLHF)|     ΔR = |wrap(ΔRHF)|
Δpulses = (ΔL + ΔR) / 2
Δs_m = Δpulses × kMetersPerPulse
```

| Метод | На прямой | В повороте | Решение |
|-------|-----------|------------|---------|
| только L или только R | ок | систематическая ошибка | **не использовать** |
| **(L+R)/2** | ок | ≈ дуга по центру между колёсами | **primary** |
| min(L,R) | занижение | сильное занижение | нет |
| max(L,R) | завышение | завышение | нет |
| среднее всех 4 колёс | ок на прямой | перед/зад — **разные дуги** | нет для distance |

**Задняя ось (LHR, RHR):** не смешивать с передней в одном Δpulses. Использовать как **cross-check** на прямой: если `|(ΔLHF+ΔRHF)/2 − (ΔLHR+ΔRHR)/2| / mean` велик при малом steer — slip или аномалия; optional fallback на speed / confidence↓.

**Ackermann / yaw из (v_r−v_l)/track:** для v1 **не** нужно — курс уже из gyro + steer (`MockLocationJob`); pulse-differential на авто шумнее и дублирует имеющееся.

#### Asymmetry (slip gate, не distance)

Разница L−R **не прибавляется** к пробегу; только **диагностика**:

```
mean = (ΔL + ΔR) / 2
asym = |ΔL − ΔR| / max(mean, ε)        // 0…1+
```

| Условие | Действие |
|---------|----------|
| **Прямая:** \|steer\| < порога, не reverse, speed ≥ **5 km/h**, mean(ΔL,ΔR) ≥ **20** импульсов | если `asym > ASYM_SLIP_THRESHOLD` (0.08) → slip / ESP / лёд: **не** обновлять k, **confidence↓**, сброс доли текущего км |
| **Поворот / reverse / парковка / ползучая скорость** | большая asym **ожидаема** (или это 1 импульс шума) — gate **не** срабатывает; калибровка k по-прежнему только на прямой (§5.2) |
| **Жёсткое окно калибровки** | средний asym за окно на прямой должен быть низким; иначе окно отбросить |

Пороги уточнить в **фазе 0** (geo-debug: `dL`, `dR`, `asym%` на прямой, в дуге, при пробуксовке).

### 4.4 API

```kotlin
fun onWheelSample(counters: WheelCounters, reverse: Boolean, steerDeg: Float?, nowElapsedMs: Long)
fun onOdometerKm(odo: UInt, nowElapsedMs: Long)
fun flushDistanceM(): Float          // метры с прошлого flush (DR / trip tick)
fun peekCalibration(): CalibrationState  // k, confidence, lastAsymmetryRatio
fun resetSession()
```

---

## 5. Калибровка `k`

### 5.1 Стартовое значение

- DataStore: `wheel_pulse_m_per_pulse`, `wheel_pulse_calib_confidence`,
  `wheel_pulse_feature_enabled` (**по умолчанию false**), `wheel_pulse_trips_enabled`,
  `wheel_pulse_mock_dr_enabled`.
- **Master switch** `featureEnabled`: пока выключен — нет подписки на WheelPulse (CAN/VHAL),
  нет приёма сэмплов, trip/DR не используют импульсы. Включается в меню калибровки
  («Приём импульсов колёс») для полевого A/B по крашам.
- До первой успешной длинной калибровки: `k = 0` → pulse **не используется** для distance (fallback speed / odo-only как сейчас).
- Опциональный seed из документации ESP/типового колеса — только как **weak prior** с низким confidence.

### 5.2 Жёсткая калибровка (длинное окно)

Условия **все**:

- накопилось **Δodo_km ≥ N** (N = **5…10**, настраиваемо) **подряд чистых** км (см. §5.3: без сильного поворота, размаха скорости, заднего хода);
- за то же окно накоплен **весь** Δpulse (и прямая, и лёгкая дуга — тот же path length, что у одометра). Раньше в окно попадали только прямые импульсы при полном Δodo, из‑за этого k завышался;
- `k_new = (Δodo × 1000) / Δpulse` в диапазоне **0,010…0,080 м/импульс** (иначе окно отбросить: слишком мало импульсов → завышенный k);
- RPM > 0, speed > **5 km/h**;
- в момент применения: прямолинейно (|steer| < порога), не reverse;
- низкая **L/R asymmetry** на прямой за окно (§4.3);
- |расхождение pulse vs odo| < **25%**. Если расхождение больше, но `k_new` в диапазоне — **перезаписать k** (устаревшая калибровка), а не крутить одно и то же плохое k бесконечно.

Грязный km-тик (**поворот / размах скорости / задний ход**) **сбрасывает** жёсткое окно — 5 км считаются заново.

```
k_new = (Δodo_km × 1000) / Δpulse_window
k ← (1 − α_hard) × k + α_hard × k_new     // α_hard ~ 0.2…0.4
confidence ← min(1, confidence + bump)
```

Погрешность от неизвестной фазы на концах окна: **~2 km / (N×1000 m)** → при N=10 ≈ **0,2%**.

### 5.3 Грязный km-тик (сброс окна, без подстройки `k`)

При `lastOdoKm` → `lastOdoKm + 1`:

- `pulseSince = pulseDistanceSinceLastOdoM`;
- **не** менять `k` по одному km-тику;
- записать residual / закрыть долю км: `pulseDistanceSinceLastOdoM = 0`;
- если km «грязный» — **сброс жёсткого окна** (кроме `range`, который только помечается в логе):
  - `pulseSince` вне **500…1500 m** (`range` — аномалия / фаза первого тика);
  - любой задний ход (`rev`);
  - пик \|steer\| > **25°** или средний \|steer\| по импульсам > **6°** (`turn`);
  - размах скорости (min…max при speed ≥ 5 km/h) > **40 km/h** (`span`);
- geo-debug: `odoNudgeSkip`, `odoSkipReason=range|turn|span|rev` (имя поля историческое; nudge `k` больше нет).

### 5.4 Reconcile drift (поездки)

При km-тике опционально:

```
residualM = 1000 − pulseSinceLastOdoM
tripReconcileAccumM += residualM   // или размазать в k, не скачком distanceKm
```

**Не** добавлять в `distanceKm` скачок +1.0 km целиком — только накопленный pulse + малый residual.

---

## 6. Интеграция: DR (mock / retention)

Точки: `SpeedIntegrator`, `MockLocationJob`.

### Приоритет Δs за DR-тик (~0,5 с)

1. **Pulse** если `confidence ≥ 0.7` и toggle Pulse в Mock DR → `flushDrDistanceM()` (**≥ 0**).
   При **включении** тумблера курсор DR выравнивается (`syncDrCursor`) — накопленные за сессию метры **не** выплёскиваются одним шагом. Пока pulse — источник, `SpeedIntegrator` **discard**, а не копится: выключение тумблера не дампит CAN-путь. Нулевой pulse-тик **не** падает в CAN-интеграл.
2. Иначе **speed × dt** — как сейчас (скорость CAN без знака, reverse только в bearing).
3. **Standstill** (pulse=0 + CAN speed≈0) → не двигать точку.

Подключение pulse: тот же контракт, что у [`takeDrDistanceM`](app/src/main/java/vad/dashing/tbox/location/MockLocationJob.kt) — положительные метры → [`applyDrMotionStep`](app/src/main/java/vad/dashing/tbox/location/MockLocationJob.kt) → `extrapolateLatLon(..., travel, distanceM)` с `travel = nose ± 180°`.

Altitude **не** из pulse — hold last GNSS alt (`MockLocationJob.constantAlt`).

---

## 7. Интеграция: поездки

Точка: `BackgroundService.onTripPeriodicSample` (~1 с).

### Сейчас

```kotlin
distanceDelta = (odo - tripLastOdometer).toFloat()  // только при изменении целого km
```

### Целевое (hybrid)

| Условие | `distanceDelta` за tick |
|---------|-------------------------|
| pulse enabled + confidence OK | `flushDistanceM() / 1000f` km |
| km-тик odo | **не** +1.0 km; reconcile residual (§5.4) |
| fallback | как сейчас (Δodo) или 0 между тиками |

- **Active trip** и **persistent daily** — один и тот же `distanceDelta`.
- `odometerStartKm` — без изменений (справочно, целый km).
- `distanceKm` — **точнее** внутри поездки; улучшаются л/100 km и средние скорости.
- **Задний ход:** pulse-метры **прибавляются** к `distanceKm` (path length), как у штатного одометра по вращению колёс; не вычитаются.

### Поездки (hybrid)

`distanceKm = (odo − odoStart) + pulseSinceLastOdoM / 1000` при toggle **Pulse в поездках** и `confidence ≥ 0.7`.
Целые км — истина одометра; pulse только доля текущего км. Toggle **Pulse в Mock DR** независим (свой курсор `flushDrDistanceM`).

### Mock DR

При toggle **Pulse в Mock DR** + калибровка: primary `flushDrDistanceM()`, иначе CAN speed × dt. Поездки не блокируют DR.

---

## 8. Диагностика

- Geo-debug / вкладка «Данные»: raw counters, `dL`/`dR` (перед), `asym%`, Δpulse, k, confidence, `pulseSinceLastOdoM`, odo, residual на km-тике; опционально задняя пара LHR/RHR для cross-check.
- DEBUG-тег `WheelPulse`: расхождение pulse vs odo на окне.
- Сброс калибровки в UI (очистка k + confidence).

---

## 9. Риски

| Риск | Mitigation |
|------|------------|
| Slip ESP / поворот | distance = mean(LHF,RHF); asym на **прямой** → gate k; в повороте asym игнорировать (§4.3) |
| Reverse | метры **всегда +**; в DR знак через bearing +180° (§3.1); калибровка k — окна без reverse |
| Wrap counters | модульная арифметика; лог при Δ > порога; mask 13 бит на push **и** pull |
| Смена колёс / давление | drift → confidence↓, сброс k |
| Целочисленный одо | §3, §5 — **не** calibrate on single tick |
| A10 Navi DR недоступен | CAN primary |
| Краш / OOM на HU | persist k ≥60 с; не эмитить `wheelPulseState` при тех же счётчиках (стоянка); publish Store **вне** lock odometer; A10 VHAL flush на single-thread + `runCatching` |

---

## 10. Фазы реализации

| Фаза | Содержание | Критерий готовности |
|------|------------|---------------------|
| **0** | Subscribe CAN pulse + лог в geo-debug | counters стабильны на прямой |
| **1** | `WheelPulseOdometer` без калибровки (фикс. k вручную) | Δs растёт при движении, 0 на стоянке |
| **2** | Калибровка §5.2–5.3 + persist | k сходится за поездку 20+ km (только жёсткие окна) |
| **3** | DR: pulse в `SpeedIntegrator` | mock DR ровнее на 1–5 km/h |
| **4** | Trips: hybrid distance §7 | distanceKm растёт плавно, не скачками по 1 km |
| **5** | UI, флаги, unit-тесты | wrap, reverse, L/R mean + asym gate, multi-km calib, trip reconcile |

---

## 11. Unit-тесты

- counter wrap 8191→0 (13 бит; 16-битный разбор 8180→50 даёт Δ=0);
- reverse: counters растут → `flushDistanceM() > 0` (не отрицательный Δs);
- turn: ΔL ≠ ΔR, но `flushDistanceM()` ≈ mean(L,R), не min/max; slip gate не трогает conf;
- straight + injected slip на скорости (ΔL ≫ ΔR при steer≈0, mean≥20) → asym gate, confidence↓;
- ползучая скорость / 1 импульс шума → gate **не** срабатывает, Ready не сбрасывается;
- **запрет:** короткий pulse на первом km-тике не меняет k (range dirty);
- dirty km (сильный поворот / размах скорости / reverse) — пометка dirty и сброс 5 km окна; **k не nudge**;
- чистый km-тик **не** меняет `k` (только жёсткое окно ≥5 km);
- `syncDrCursor` не выплёскивает backlog в DR;
- trip: 500 m pulse без odo tick → `distanceKm +0.5`;
- km-tick: hybrid `distanceKm` **не** +1.0 мгновенно, только odo + доля pulse;

---

## 12. Краткое резюме

- **Pulse** — основной счётчик **метров**; **одометр** — якорь только на **длинных** жёстких окнах (≥5 km). Km-тик `k` не подстраивает.
- **Один km-тик ≠ 1000 m** для калибровки — учтено явно.
- **Знак:** path length всегда **≥ 0**; reverse в DR — только bearing, в поездках — тоже плюс.
- **L/R:** distance = **(LHF+RHF)/2**; разница L−R — только slip gate и debug, не yaw и не отдельная поправка метров.
