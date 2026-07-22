package vad.dashing.tbox

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.fuellevelcalibration.FuelLevelStableApply
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.utils.InOutTemperatureNullDebounce

/**
 * Mixed HU + TBox telemetry for trip/refuel accounting (not [CanDataRepository]).
 *
 * Priority (freshness window [FRESHNESS_MS]):
 * - RPM, speed, odometer, fuel %, outside temp: HU first; TBox only if HU stale/absent
 * - Engine coolant:
 *   - Android 9 (mbCAN): **TBox only** (HU coolant is always `0.0` in practice)
 *   - Android 10 (VHAL): TBox first; HU only if TBox stale/absent
 * - Gearbox oil temp: TBox only (tracked for freshness/debug; value stays on CDR)
 *
 * After [FRESHNESS_MS] with no RPM from either source, forces RPM to 0 so trips can close.
 *
 * Cached StateFlows keep the last value for UI / disk restore; [BackgroundService] trip and
 * refuel accounting must use [accountingEngineRpm] / [accountingFuelLevelPercentageFiltered] /
 * etc., which return null when the signal has no fresh HU/TBox update (does not clear CDR).
 *
 * Filtered fuel % and calibrated liters live here only. Emits DEBUG `TripFuel` every
 * [DEBUG_SNAPSHOT_MS] via [TboxRepository.addLog].
 */
object TripTelemetryRepository {
    const val FRESHNESS_MS: Long = 45_000L
    const val TRIP_ACCOUNTING_SOURCE_ID: String = "trip-accounting-telemetry"
    private const val DEBUG_SNAPSHOT_MS: Long = 15_000L
    private const val DEBUG_TAG: String = "TripFuel"

    private val tripSignals: Set<MbCanSignal> = setOf(
        MbCanSignal.EngineRpm,
        MbCanSignal.CarSpeed,
        MbCanSignal.EngineTemperature,
        MbCanSignal.FuelLevel,
        MbCanSignal.TotalOdometer,
        MbCanSignal.OutsideTemperature,
    )

    enum class Signal {
        Rpm,
        Speed,
        Odometer,
        Fuel,
        OutsideTemp,
        EngineTemp,
        GearboxOilTemp,
    }

    private val _engineRpm = MutableStateFlow<Float?>(null)
    val engineRpm: StateFlow<Float?> = _engineRpm.asStateFlow()

    private val _carSpeed = MutableStateFlow<Float?>(null)
    val carSpeed: StateFlow<Float?> = _carSpeed.asStateFlow()

    private val _odometerKm = MutableStateFlow<UInt?>(null)
    val odometerKm: StateFlow<UInt?> = _odometerKm.asStateFlow()

    private val _fuelLevelPercentage = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentage: StateFlow<UInt?> = _fuelLevelPercentage.asStateFlow()

    private val _fuelLevelPercentageFiltered = MutableStateFlow<UInt?>(null)
    val fuelLevelPercentageFiltered: StateFlow<UInt?> = _fuelLevelPercentageFiltered.asStateFlow()

    private val _fuelLevelCalibratedLiters = MutableStateFlow<Float?>(null)
    val fuelLevelCalibratedLiters: StateFlow<Float?> = _fuelLevelCalibratedLiters.asStateFlow()

    private val _fuelLevelCalibratedLitersActual = MutableStateFlow<Float?>(null)
    val fuelLevelCalibratedLitersActual: StateFlow<Float?> = _fuelLevelCalibratedLitersActual.asStateFlow()

    private val _fuelCalibrationConfidence = MutableStateFlow<Float?>(null)
    val fuelCalibrationConfidence: StateFlow<Float?> = _fuelCalibrationConfidence.asStateFlow()

    private val _outsideTemperature = MutableStateFlow<Float?>(null)
    val outsideTemperature: StateFlow<Float?> = _outsideTemperature.asStateFlow()
    private var outsideTemperatureLastTimeNotNull: Long? = null

    private val _engineTemperature = MutableStateFlow<Float?>(null)
    val engineTemperature: StateFlow<Float?> = _engineTemperature.asStateFlow()

    @Volatile
    private var collectJob: Job? = null

    @Volatile
    private var staleWatchJob: Job? = null

    @Volatile
    private var debugSnapshotJob: Job? = null

    /** Test override: null = use [UniversalCanRepository.mode]; true = A9 TBox-only coolant policy. */
    @Volatile
    private var a9EngineTempTboxOnlyForTest: Boolean? = null

    private val lastHuElapsedMs = LongArray(Signal.entries.size) { -1L }
    private val lastTboxElapsedMs = LongArray(Signal.entries.size) { -1L }
    private val lock = Any()

    private fun <T> MutableStateFlow<T>.setIfChanged(newValue: T) {
        if (value != newValue) {
            value = newValue
        }
    }

    fun start(scope: CoroutineScope) {
        stop()
        collectJob = scope.launch {
            UniversalCanRepository.setSourceSignals(TRIP_ACCOUNTING_SOURCE_ID, tripSignals)
            launch {
                UniversalCanRepository.engineRpmState.collect { rpm ->
                    rpm ?: return@collect
                    tryWriteHu(Signal.Rpm) {
                        _engineRpm.setIfChanged(rpm)
                    }
                }
            }
            launch {
                UniversalCanRepository.carSpeedState.collect { speed ->
                    speed ?: return@collect
                    tryWriteHu(Signal.Speed) {
                        _carSpeed.setIfChanged(speed)
                    }
                }
            }
            launch {
                UniversalCanRepository.engineTemperatureState.collect { temp ->
                    temp ?: return@collect
                    tryWriteHu(Signal.EngineTemp) {
                        _engineTemperature.setIfChanged(temp)
                    }
                }
            }
            launch {
                UniversalCanRepository.fuelLevelPercentState.collect { pct ->
                    pct ?: return@collect
                    tryWriteHu(Signal.Fuel) {
                        _fuelLevelPercentage.setIfChanged(pct)
                        FuelLevelStableApply.onRawFuelPercent(pct)
                    }
                }
            }
            launch {
                UniversalCanRepository.odometerKmState.collect { km ->
                    km ?: return@collect
                    tryWriteHu(Signal.Odometer) {
                        _odometerKm.setIfChanged(km)
                    }
                }
            }
            launch {
                UniversalCanRepository.outsideTemperatureState.collect { temp ->
                    temp ?: return@collect
                    tryWriteHu(Signal.OutsideTemp) {
                        _outsideTemperature.setIfChanged(temp)
                    }
                }
            }
        }
        staleWatchJob = scope.launch {
            while (isActive) {
                delay(5_000L)
                maybeClearStaleRpm()
            }
        }
        debugSnapshotJob = scope.launch {
            while (isActive) {
                delay(DEBUG_SNAPSHOT_MS)
                logAccountingDebugSnapshot()
            }
        }
    }

    fun stop() {
        collectJob?.cancel()
        collectJob = null
        staleWatchJob?.cancel()
        staleWatchJob = null
        debugSnapshotJob?.cancel()
        debugSnapshotJob = null
        UniversalCanRepository.enqueueClearSource(TRIP_ACCOUNTING_SOURCE_ID)
        synchronized(lock) {
            lastHuElapsedMs.fill(-1L)
            lastTboxElapsedMs.fill(-1L)
        }
    }

    /** Gate for TBox [CanFramesProcess] writes of HU-priority signals into this repo. */
    fun acceptTboxHuPriority(signal: Signal, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Boolean {
        synchronized(lock) {
            val huLast = lastHuElapsedMs[signal.ordinal]
            val huFresh = huLast >= 0L && nowElapsedMs - huLast <= FRESHNESS_MS
            if (huFresh) return false
            lastTboxElapsedMs[signal.ordinal] = nowElapsedMs
            return true
        }
    }

    /** Gate for TBox writes of TBox-priority temps (coolant / gearbox oil). Always accept. */
    fun noteTboxTempPriority(signal: Signal, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        require(signal == Signal.EngineTemp || signal == Signal.GearboxOilTemp)
        synchronized(lock) {
            lastTboxElapsedMs[signal.ordinal] = nowElapsedMs
        }
    }

    fun applyTboxRpm(rpm: Float) {
        if (acceptTboxHuPriority(Signal.Rpm)) {
            _engineRpm.setIfChanged(rpm)
        }
    }

    fun applyTboxSpeed(speed: Float) {
        if (acceptTboxHuPriority(Signal.Speed)) {
            _carSpeed.setIfChanged(speed)
        }
    }

    fun applyTboxOdometer(km: UInt) {
        if (acceptTboxHuPriority(Signal.Odometer)) {
            _odometerKm.setIfChanged(km)
        }
    }

    fun applyTboxFuelPercent(percent: UInt) {
        if (acceptTboxHuPriority(Signal.Fuel)) {
            _fuelLevelPercentage.setIfChanged(percent)
            FuelLevelStableApply.onRawFuelPercent(percent)
        }
    }

    fun applyTboxEngineTemperature(celsius: Float) {
        noteTboxTempPriority(Signal.EngineTemp)
        _engineTemperature.setIfChanged(celsius)
    }

    fun applyTboxOutsideTemperature(decodedCelsius: Float, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (!acceptTboxHuPriority(Signal.OutsideTemp, nowElapsedMs)) return
        val resolved = InOutTemperatureNullDebounce.resolveAfterProbe(
            current = _outsideTemperature.value,
            lastTimeNotNull = outsideTemperatureLastTimeNotNull,
            decodedCelsius = decodedCelsius,
            now = nowElapsedMs,
            debounceMs = InOutTemperatureNullDebounce.DEFAULT_NULL_DEBOUNCE_MS,
        )
        outsideTemperatureLastTimeNotNull = resolved.lastTimeNotNull
        _outsideTemperature.setIfChanged(resolved.value)
    }

    fun noteTboxGearboxOilTemp() {
        noteTboxTempPriority(Signal.GearboxOilTemp)
    }

    fun updateFuelLevelPercentageFiltered(newValue: UInt) {
        _fuelLevelPercentageFiltered.setIfChanged(newValue)
    }

    fun updateFuelLevelCalibratedLiters(newValue: Float?) {
        _fuelLevelCalibratedLiters.setIfChanged(newValue)
    }

    fun updateFuelLevelCalibratedLitersActual(newValue: Float?) {
        _fuelLevelCalibratedLitersActual.setIfChanged(newValue)
    }

    fun updateFuelCalibrationConfidence(newValue: Float?) {
        _fuelCalibrationConfidence.setIfChanged(newValue)
    }

    fun updateFuelLevelPercentage(newValue: UInt) {
        _fuelLevelPercentage.setIfChanged(newValue)
    }

    /** Visible for unit tests. */
    internal fun noteHuForTest(signal: Signal, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        synchronized(lock) {
            lastHuElapsedMs[signal.ordinal] = nowElapsedMs
        }
    }

    /** Visible for unit tests. */
    internal fun resetFreshnessForTest() {
        synchronized(lock) {
            lastHuElapsedMs.fill(-1L)
            lastTboxElapsedMs.fill(-1L)
        }
        a9EngineTempTboxOnlyForTest = null
    }

    /** Visible for unit tests: force A9 TBox-only coolant policy (`true`) or A10 fallback (`false`). */
    internal fun setA9EngineTempTboxOnlyForTest(enabled: Boolean?) {
        a9EngineTempTboxOnlyForTest = enabled
    }

    /** Visible for unit tests. */
    internal fun tryWriteHuForTest(signal: Signal, write: () -> Unit) {
        tryWriteHu(signal, write)
    }

    private fun isA9EngineTempTboxOnly(): Boolean {
        a9EngineTempTboxOnlyForTest?.let { return it }
        return UniversalCanRepository.mode.value == HeadUnitCanMode.Android9MbCan
    }

    private fun tryWriteHu(signal: Signal, write: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val allow = synchronized(lock) {
            when (signal) {
                Signal.EngineTemp -> {
                    if (isA9EngineTempTboxOnly()) {
                        false
                    } else {
                        val tboxLast = lastTboxElapsedMs[signal.ordinal]
                        val tboxFresh = tboxLast >= 0L && now - tboxLast <= FRESHNESS_MS
                        if (tboxFresh) {
                            false
                        } else {
                            lastHuElapsedMs[signal.ordinal] = now
                            true
                        }
                    }
                }
                Signal.GearboxOilTemp -> false
                else -> {
                    lastHuElapsedMs[signal.ordinal] = now
                    true
                }
            }
        }
        if (allow) write()
    }

    /**
     * True when [signal] has a fresh HU or TBox sample within [FRESHNESS_MS] under the same
     * priority rules as mixing (see class KDoc). Used by trip/refuel accounting; does not clear values.
     */
    fun isSignalFreshForAccounting(
        signal: Signal,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean = synchronized(lock) {
        activeSourceLocked(signal, nowElapsedMs) != null
    }

    /** Cached RPM if fresh for accounting; otherwise null (StateFlow may still hold a last value). */
    fun accountingEngineRpm(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float? =
        valueIfFresh(Signal.Rpm, _engineRpm.value, nowElapsedMs)

    fun accountingCarSpeed(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float? =
        valueIfFresh(Signal.Speed, _carSpeed.value, nowElapsedMs)

    fun accountingOdometerKm(nowElapsedMs: Long = SystemClock.elapsedRealtime()): UInt? =
        valueIfFresh(Signal.Odometer, _odometerKm.value, nowElapsedMs)

    /**
     * Filtered % for trip/refuel steps only when raw [Signal.Fuel] is fresh.
     * Disk-restored filtered values without a live fuel sample stay null here.
     */
    fun accountingFuelLevelPercentageFiltered(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): UInt? = valueIfFresh(Signal.Fuel, _fuelLevelPercentageFiltered.value, nowElapsedMs)

    fun accountingFuelLevelCalibratedLiters(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Float? = valueIfFresh(Signal.Fuel, _fuelLevelCalibratedLiters.value, nowElapsedMs)

    fun accountingOutsideTemperature(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float? =
        valueIfFresh(Signal.OutsideTemp, _outsideTemperature.value, nowElapsedMs)

    fun accountingEngineTemperature(nowElapsedMs: Long = SystemClock.elapsedRealtime()): Float? =
        valueIfFresh(Signal.EngineTemp, _engineTemperature.value, nowElapsedMs)

    /**
     * Gearbox oil lives on [CanDataRepository]; pass its current value — returned only while
     * [Signal.GearboxOilTemp] is fresh from TBox.
     */
    fun accountingGearboxOilTemperature(
        cdrValue: Int?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): Int? = valueIfFresh(Signal.GearboxOilTemp, cdrValue, nowElapsedMs)

    private fun <T> valueIfFresh(signal: Signal, value: T?, nowElapsedMs: Long): T? {
        if (value == null) return null
        return if (isSignalFreshForAccounting(signal, nowElapsedMs)) value else null
    }

    /**
     * Active source label for [signal], or null if neither HU nor TBox is fresh.
     * Must be called under [lock] or via [isSignalFreshForAccounting] / snapshot builder.
     */
    private fun activeSourceLocked(signal: Signal, nowElapsedMs: Long): String? {
        val huLast = lastHuElapsedMs[signal.ordinal]
        val tboxLast = lastTboxElapsedMs[signal.ordinal]
        val huFresh = huLast >= 0L && nowElapsedMs - huLast <= FRESHNESS_MS
        val tboxFresh = tboxLast >= 0L && nowElapsedMs - tboxLast <= FRESHNESS_MS
        return when (signal) {
            Signal.EngineTemp -> when {
                isA9EngineTempTboxOnly() -> if (tboxFresh) "TBox" else null
                tboxFresh -> "TBox"
                huFresh -> "HU"
                else -> null
            }
            Signal.GearboxOilTemp -> if (tboxFresh) "TBox" else null
            else -> when {
                huFresh -> "HU"
                tboxFresh -> "TBox"
                else -> null
            }
        }
    }

    private fun maybeClearStaleRpm() {
        val now = SystemClock.elapsedRealtime()
        val clear = synchronized(lock) {
            val huLast = lastHuElapsedMs[Signal.Rpm.ordinal]
            val tboxLast = lastTboxElapsedMs[Signal.Rpm.ordinal]
            val huFresh = huLast >= 0L && now - huLast <= FRESHNESS_MS
            val tboxFresh = tboxLast >= 0L && now - tboxLast <= FRESHNESS_MS
            !huFresh && !tboxFresh && (_engineRpm.value ?: 0f) > 0f
        }
        if (clear) {
            _engineRpm.setIfChanged(0f)
        }
    }

    private fun logAccountingDebugSnapshot() {
        TboxRepository.addLog("DEBUG", DEBUG_TAG, buildAccountingDebugSnapshot())
    }

    /** Visible for unit tests. */
    internal fun buildAccountingDebugSnapshot(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): String {
        val sources = synchronized(lock) {
            fun fmt(signal: Signal): String {
                val huLast = lastHuElapsedMs[signal.ordinal]
                val tboxLast = lastTboxElapsedMs[signal.ordinal]
                val huAge = if (huLast >= 0L) nowElapsedMs - huLast else null
                val tboxAge = if (tboxLast >= 0L) nowElapsedMs - tboxLast else null
                val active = activeSourceLocked(signal, nowElapsedMs) ?: "-"
                val ages = buildString {
                    append("hu=")
                    append(huAge?.toString() ?: "-")
                    append("ms tbox=")
                    append(tboxAge?.toString() ?: "-")
                    append("ms")
                }
                return "${signal.name}=$active($ages)"
            }
            listOf(
                Signal.Rpm,
                Signal.Speed,
                Signal.Fuel,
                Signal.Odometer,
                Signal.OutsideTemp,
                Signal.EngineTemp,
                Signal.GearboxOilTemp,
            ).joinToString("; ") { fmt(it) }
        }
        return "sources[$sources] values[rpm=${_engineRpm.value} speed=${_carSpeed.value} " +
            "fuelRaw%=${_fuelLevelPercentage.value} fuelFilt%=${_fuelLevelPercentageFiltered.value} " +
            "liters=${_fuelLevelCalibratedLiters.value} odoKm=${_odometerKm.value} " +
            "outsideC=${_outsideTemperature.value} engTempC=${_engineTemperature.value}]"
    }
}
