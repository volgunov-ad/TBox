package vad.dashing.tbox.mbcan

import java.util.ArrayDeque

/**
 * Coalescing journal sink for the session-only deep CAN diagnostics mode.
 *
 * Every VHAL / mbCAN change event is formatted machine-readably, appended to a
 * bounded ring buffer and forwarded to the DEBUG journal via [MbCanDiagnostics.log]
 * (the journal adds its own `[HH:mm:ss] LEVEL: TAG.` prefix, so lines carry no ts).
 *
 * Delta-only + windowed coalescing keeps drive-session logs readable:
 * a key is emitted only when its value changed and the window elapsed
 * ([DISCRETE_COALESCE_MS] for status-like keys, [CONTINUOUS_COALESCE_MS] for
 * fast telemetry). Same-value repeats are only counted into `suppressed` /
 * [totalSuppressed] and reported with the next emitted line.
 *
 * Must stay cheap: called from OEM binder / CAN callback threads and must never
 * call back into mbCAN JNI or VHAL.
 */
object DeepCanDiagnostics {
    const val VHAL_TAG = "CANDIAG_VHAL"
    const val MBCAN_TAG = "CANDIAG_MBCAN"

    internal val DISCRETE_COALESCE_MS = 1_000L
    internal val CONTINUOUS_COALESCE_MS = 5_000L
    internal val RING_BUFFER_LIMIT = 3_000

    /** Fast-changing A10 signals (by [DeepDiagnosticsCatalog] annotation name). */
    private val CONTINUOUS_VHAL_NAMES = setOf(
        "CarSpeed",
        "EngineRpm",
        "EngineCoolantTemp",
        "SteeringWheelAngle",
        "EmsGasPedalPosition",
        "TotalOdometerKm",
        "LhfPulseCounter",
        "RhfPulseCounter",
        "LhrPulseCounter",
        "RhrPulseCounter",
        "FuelRollingCounter",
        "FrmVSetDis",
        "FrmDxTarObj",
        "TempknobRollingCounter",
        "LfTyrePressure",
        "RfTyrePressure",
        "LrTyrePressure",
        "RrTyrePressure",
        "LfTyreTemperature",
        "RfTyreTemperature",
        "LrTyreTemperature",
        "RrTyreTemperature",
        "Pm25Indensity",
        "Pm25Outdensity",
        "ExternalTemperatureRaw",
    )

    /** Fast-changing A9 data types. */
    private val CONTINUOUS_MBCAN_DATA_TYPES = setOf(
        "eMBCAN_VEHICLE_SPEED",
        "eMBCAN_VEHICLE_ENGINE",
        "eMBCAN_VEHICLE_WHEEL",
        "eMBCAN_VEHICLE_STEERING_ANGLE",
        "eMBCAN_VEHICLE_FUELLEVEL",
        "eMBCAN_VEHICLE_TOTALODOMETER",
        "eMBCAN_VEHICLE_EXTERNAL_TEMP_RAW",
        "eMBCAN_VEHICLE_TIRE",
        "eMBCAN_PM25INFO",
        "eMBCAN_VEHICLE_ICM_INFO",
        "eMBCAN_ICM_TRIP_INFO",
        "eMBCAN_VEHICLE_CONSUMPTION",
        "eMBCAN_VEHICLE_EBS_SOC",
    )

    /** Injectable so unit tests can capture lines without the Tbox journal. */
    internal var sink: (tag: String, line: String) -> Unit = { tag, line ->
        MbCanDiagnostics.log("DEBUG", tag, line)
    }

    private class KeyState(
        var valueText: String,
        var lastEmitMs: Long,
        var suppressed: Long,
    )

    private val lock = Any()
    private val ring = ArrayDeque<String>()
    private val keyStates = HashMap<String, KeyState>()
    private val reportedVhalErrorPropertyIds = HashSet<Int>()
    private var totalSuppressed = 0L

    fun recordVhalEvent(
        propertyId: Int,
        areaId: Int,
        value: Any?,
        valueType: String?,
        status: Int?,
        timestampNanos: Long?,
    ) {
        val name = DeepDiagnosticsCatalog.annotateVhalPropertyId(propertyId)
        val valueText = sanitize(value?.toString() ?: "null")
        val key = "v/$propertyId/$areaId"
        val line = buildString {
            append("vhal propertyId=").append(propertyId)
            append(" areaId=").append(areaId)
            append(" value=").append(valueText)
            append(" type=").append(sanitize(valueType ?: value?.javaClass?.simpleName ?: "null"))
            append(" status=").append(status?.toString() ?: "?")
            append(" tsNanos=").append(timestampNanos?.toString() ?: "?")
            if (name != null) append(" name=").append(name)
        }
        val continuous = name != null && name in CONTINUOUS_VHAL_NAMES
        emit(key = key, tag = VHAL_TAG, line = line, valueText = valueText, continuous = continuous)
    }

    fun recordMbCanCmdChanged(dataType: String, modular: Int, rev: Int, item: Int, value: Int) {
        val name = DeepDiagnosticsCatalog.annotateMbCanItem(item)
        val valueText = value.toString()
        val key = "m/$dataType/$modular/$item"
        val line = buildString {
            append("mbcan dt=").append(dataType)
            append(" modular=").append(modular)
            append(" rev=").append(rev)
            append(" item=").append(item)
            append(" value=").append(valueText)
            if (name != null) append(" name=").append(name)
        }
        val continuous = dataType in CONTINUOUS_MBCAN_DATA_TYPES
        emit(key = key, tag = MBCAN_TAG, line = line, valueText = valueText, continuous = continuous)
    }

    /**
     * Typed OEM object snapshot (doors / seat belt / …) that does **not** arrive as
     * CFG-shaped `onCmdChanged`. Used when deep mode wires a real callback or poll.
     */
    fun recordMbCanObjectSnapshot(dataType: String, fields: String) {
        val valueText = sanitize(fields)
        val key = "mobj/$dataType"
        val line = "mbcan dt=$dataType object=$valueText"
        val continuous = dataType in CONTINUOUS_MBCAN_DATA_TYPES
        emit(key = key, tag = MBCAN_TAG, line = line, valueText = valueText, continuous = continuous)
    }

    /** Direct journal line bypassing coalescing (subscription reports, session markers). */
    fun report(tag: String, message: String) {
        appendLine(message)
        sink(tag, message)
    }

    /** VHAL `onErrorEvent`: logged once per property id per session. */
    fun recordVhalError(propertyId: Int, areaId: Int) {
        val fresh = synchronized(lock) {
            if (reportedVhalErrorPropertyIds.contains(propertyId)) {
                false
            } else {
                reportedVhalErrorPropertyIds.add(propertyId)
                true
            }
        }
        if (fresh) {
            report(VHAL_TAG, "vhal error propertyId=$propertyId areaId=$areaId (once per session)")
        }
    }

    fun reset() {
        synchronized(lock) {
            ring.clear()
            keyStates.clear()
            reportedVhalErrorPropertyIds.clear()
            totalSuppressed = 0L
        }
    }

    fun recentLines(): List<String> = synchronized(lock) {
        ring.toList()
    }

    fun stats(): String = synchronized(lock) {
        "deepDiag lines=${ring.size} keys=${keyStates.size} suppressedTotal=$totalSuppressed"
    }

    /**
     * Visible for unit tests: delta + window decision for one key.
     * Returns null when suppressed, or the number of repeats suppressed since
     * the previous emit when the line should be written.
     */
    internal fun decide(key: String, valueText: String, nowMs: Long, continuous: Boolean): Long? {
        val state = keyStates[key]
        if (state == null) {
            keyStates[key] = KeyState(valueText, nowMs, 0L)
            return 0L
        }
        if (state.valueText == valueText) {
            state.suppressed++
            totalSuppressed++
            return null
        }
        val window = if (continuous) CONTINUOUS_COALESCE_MS else DISCRETE_COALESCE_MS
        if (nowMs - state.lastEmitMs < window) {
            state.suppressed++
            totalSuppressed++
            return null
        }
        val suppressed = state.suppressed
        state.valueText = valueText
        state.lastEmitMs = nowMs
        state.suppressed = 0L
        return suppressed
    }

    private fun emit(key: String, tag: String, line: String, valueText: String, continuous: Boolean) {
        val suppressed = synchronized(lock) {
            decide(key, valueText, System.currentTimeMillis(), continuous) ?: return
        }
        val finalLine = if (suppressed > 0L) "$line suppressed=$suppressed" else line
        appendLine(finalLine)
        sink(tag, finalLine)
    }

    private fun appendLine(line: String) {
        synchronized(lock) {
            ring.addLast(line)
            while (ring.size > RING_BUFFER_LIMIT) {
                ring.removeFirst()
            }
        }
    }

    private fun sanitize(raw: String): String = raw.replace(Regex("\\s+"), "_")
}
