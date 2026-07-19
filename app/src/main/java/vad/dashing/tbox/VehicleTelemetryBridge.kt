package vad.dashing.tbox

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.fuellevelcalibration.FuelLevelStableApply
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository

/**
 * Bridges mbCAN/VHAL telemetry into [CanDataRepository] for trip/refuel accounting.
 *
 * Priority (freshness window [FRESHNESS_MS]):
 * - RPM, speed, odometer, fuel %, outside temp: HU first; TBox only if HU stale/absent
 * - Engine coolant:
 *   - Android 9 (mbCAN): **TBox only** (HU coolant is always `0.0` in practice)
 *   - Android 10 (VHAL): TBox first; HU only if TBox stale/absent
 * - Gearbox oil temp: TBox only
 *
 * After [FRESHNESS_MS] with no RPM from either source, forces RPM to 0 so trips can close.
 *
 * Emits a DEBUG snapshot every [DEBUG_SNAPSHOT_MS] (tag `TripFuel`) with active source per
 * signal and current CDR values via [TboxRepository.addLog] (not gated by CAN diagnostics).
 */
object VehicleTelemetryBridge {
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

    fun start(scope: CoroutineScope) {
        stop()
        collectJob = scope.launch {
            UniversalCanRepository.setSourceSignals(TRIP_ACCOUNTING_SOURCE_ID, tripSignals)
            launch {
                UniversalCanRepository.engineRpmState.collect { rpm ->
                    rpm ?: return@collect
                    tryWriteHu(Signal.Rpm) {
                        CanDataRepository.updateEngineRPM(rpm)
                    }
                }
            }
            launch {
                UniversalCanRepository.carSpeedState.collect { speed ->
                    speed ?: return@collect
                    tryWriteHu(Signal.Speed) {
                        CanDataRepository.updateCarSpeed(speed)
                    }
                }
            }
            launch {
                UniversalCanRepository.engineTemperatureState.collect { temp ->
                    temp ?: return@collect
                    tryWriteHu(Signal.EngineTemp) {
                        CanDataRepository.updateEngineTemperature(temp)
                    }
                }
            }
            launch {
                UniversalCanRepository.fuelLevelPercentState.collect { pct ->
                    pct ?: return@collect
                    tryWriteHu(Signal.Fuel) {
                        CanDataRepository.updateFuelLevelPercentage(pct)
                        FuelLevelStableApply.onRawFuelPercent(pct)
                    }
                }
            }
            launch {
                UniversalCanRepository.odometerKmState.collect { km ->
                    km ?: return@collect
                    tryWriteHu(Signal.Odometer) {
                        CanDataRepository.updateOdometer(km)
                    }
                }
            }
            launch {
                UniversalCanRepository.outsideTemperatureState.collect { temp ->
                    // null from HU means invalid (e.g. raw 87); do not clear via bridge
                    temp ?: return@collect
                    tryWriteHu(Signal.OutsideTemp) {
                        CanDataRepository.updateOutsideTemperature(temp)
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

    /** Gate for TBox [CanFramesProcess] writes of HU-priority signals. */
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

    private fun isA9EngineTempTboxOnly(): Boolean {
        a9EngineTempTboxOnlyForTest?.let { return it }
        return UniversalCanRepository.mode.value == HeadUnitCanMode.Android9MbCan
    }

    private fun tryWriteHu(signal: Signal, write: () -> Unit) {
        val now = SystemClock.elapsedRealtime()
        val allow = synchronized(lock) {
            when (signal) {
                Signal.EngineTemp -> {
                    // A9 mbCAN coolant is always 0.0 — trips must not take HU over TBox.
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

    private fun maybeClearStaleRpm() {
        val now = SystemClock.elapsedRealtime()
        val clear = synchronized(lock) {
            val huLast = lastHuElapsedMs[Signal.Rpm.ordinal]
            val tboxLast = lastTboxElapsedMs[Signal.Rpm.ordinal]
            val huFresh = huLast >= 0L && now - huLast <= FRESHNESS_MS
            val tboxFresh = tboxLast >= 0L && now - tboxLast <= FRESHNESS_MS
            !huFresh && !tboxFresh && (CanDataRepository.engineRPM.value ?: 0f) > 0f
        }
        if (clear) {
            CanDataRepository.updateEngineRPM(0f)
        }
    }

    private fun logAccountingDebugSnapshot() {
        TboxRepository.addLog("DEBUG", DEBUG_TAG, buildAccountingDebugSnapshot())
    }

    /** Visible for unit tests. */
    internal fun buildAccountingDebugSnapshot(
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): String {
        val a9CoolantTboxOnly = isA9EngineTempTboxOnly()
        val sources = synchronized(lock) {
            fun fmt(signal: Signal): String {
                val huLast = lastHuElapsedMs[signal.ordinal]
                val tboxLast = lastTboxElapsedMs[signal.ordinal]
                val huAge = if (huLast >= 0L) nowElapsedMs - huLast else null
                val tboxAge = if (tboxLast >= 0L) nowElapsedMs - tboxLast else null
                val huFresh = huAge != null && huAge <= FRESHNESS_MS
                val tboxFresh = tboxAge != null && tboxAge <= FRESHNESS_MS
                val active = when (signal) {
                    Signal.EngineTemp -> when {
                        a9CoolantTboxOnly -> when {
                            tboxFresh -> "TBox"
                            else -> "-"
                        }
                        tboxFresh -> "TBox"
                        huFresh -> "HU"
                        else -> "-"
                    }
                    Signal.GearboxOilTemp -> when {
                        tboxFresh -> "TBox"
                        else -> "-"
                    }
                    else -> when {
                        huFresh -> "HU"
                        tboxFresh -> "TBox"
                        else -> "-"
                    }
                }
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
        val rpm = CanDataRepository.engineRPM.value
        val speed = CanDataRepository.carSpeed.value
        val fuelRaw = CanDataRepository.fuelLevelPercentage.value
        val fuelFilt = CanDataRepository.fuelLevelPercentageFiltered.value
        val liters = CanDataRepository.fuelLevelCalibratedLiters.value
        val odo = CanDataRepository.odometer.value
        val outside = CanDataRepository.outsideTemperature.value
        val engTemp = CanDataRepository.engineTemperature.value
        return "sources[$sources] values[rpm=$rpm speed=$speed fuelRaw%=$fuelRaw fuelFilt%=$fuelFilt " +
            "liters=$liters odoKm=$odo outsideC=$outside engTempC=$engTemp]"
    }
}
