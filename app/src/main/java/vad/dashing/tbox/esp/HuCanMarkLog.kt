package vad.dashing.tbox.esp

import vad.dashing.tbox.mbcan.FirmwareVehicleJsonMapper
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanCommandResult
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import java.lang.reflect.Modifier

/**
 * Optional markers in the companion protocol log for HU mbCAN/VHAL UI writes and
 * discrete push updates. Gated by [CompanionProtocolLogRecorder] recording + marks toggle.
 */
object HuCanMarkLog {
    fun markUi(detail: String) {
        CompanionProtocolLogRecorder.appendMark("UI", detail)
    }

    fun markPush(detail: String) {
        CompanionProtocolLogRecorder.appendMark("PUSH", detail)
    }

    fun markUiCommand(command: MbCanCommand, result: MbCanCommandResult) {
        val ok = if (result.success) "ok" else "fail"
        markUi("${formatCommand(command)} → $ok")
    }

    fun markUiAudioVolume(value: Int, result: MbCanCommandResult) {
        val ok = if (result.success) "ok" else "fail"
        markUi("setAudioVolume value=$value → $ok")
    }

    fun formatCommand(command: MbCanCommand): String = when (command) {
        is MbCanCommand.SetProperty ->
            "SetProperty ${vehicleProp(command.propertyId)}=${command.value}"
        is MbCanCommand.ToggleProperty ->
            "ToggleProperty ${vehicleProp(command.propertyId)}"
        is MbCanCommand.TrunkPulse ->
            "TrunkPulse value=${command.value}"
        is MbCanCommand.SetAudioProperty ->
            "SetAudioProperty ${audioProp(command.propertyId)}=${command.value}"
        is MbCanCommand.ToggleAudioProperty ->
            "ToggleAudioProperty ${audioProp(command.propertyId)}"
        is MbCanCommand.SetFcwEnabled ->
            "SetFcwEnabled enabled=${command.enabled}"
        is MbCanCommand.RefreshSignal ->
            "RefreshSignal ${command.signal}"
    }

    fun vehicleProp(propertyId: Int): String {
        val name = vehicleNames[propertyId]
        return if (name != null) "$name($propertyId)" else "id=$propertyId"
    }

    fun audioProp(propertyId: Int): String {
        val name = audioNames[propertyId]
        return if (name != null) "$name($propertyId)" else "id=$propertyId"
    }

    /** Skip continuous telemetry so companion logs stay usable for reverse-engineering. */
    fun shouldMarkVhalPush(propertyId: Int): Boolean = propertyId !in highRateVhalPropertyIds

    private val vehicleNames: Map<Int, String> by lazy {
        uniqueConstNameMap(MbCanKnownVehiclePropertyId::class.java)
    }

    private val audioNames: Map<Int, String> by lazy {
        uniqueConstNameMap(MbCanKnownAudioPropertyId::class.java)
    }

    private val highRateVhalPropertyIds: Set<Int> = setOf(
        FirmwareVehicleJsonMapper.VHAL_ENGINE_RPM_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_CAR_SPEED_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_STEERING_WHEEL_ANGLE_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_FUEL_LEVEL_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_TOTAL_ODOMETER_KM_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_FUEL_ROLLING_COUNTER_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_EXTERNAL_TEMPERATURE_RAW_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_LHF_PULSE_COUNTER_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_RHF_PULSE_COUNTER_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_LHR_PULSE_COUNTER_PROPERTY_ID,
        FirmwareVehicleJsonMapper.VHAL_RHR_PULSE_COUNTER_PROPERTY_ID,
    )

    /**
     * Map int consts → names; skip ambiguous ids and value-alias fields
     * (`*_ON` / `*_OFF` / `*_VALUE` / mode enums).
     *
     * Relies on static `@JvmField` ints surviving R8 — see proguard-rules.pro
     * keep for [vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId] /
     * [vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId].
     * `const val` is not used: R8 inlines those and drops the fields even with `-keep`.
     */
    internal fun uniqueConstNameMap(clazz: Class<*>): Map<Int, String> {
        val first = LinkedHashMap<Int, String>()
        val conflicts = HashSet<Int>()
        for (field in clazz.declaredFields) {
            if (!Modifier.isStatic(field.modifiers)) continue
            if (field.type != Int::class.javaPrimitiveType) continue
            val name = field.name
            // Kotlin stability marker (`$stable`) is a static int, not a property id.
            if (name.startsWith("$")) continue
            if (isValueAliasConstName(name)) continue
            field.isAccessible = true
            val id = field.getInt(null)
            if (id in conflicts) continue
            val prev = first.putIfAbsent(id, name)
            if (prev != null && prev != name) {
                first.remove(id)
                conflicts.add(id)
            }
        }
        return first
    }

    private fun isValueAliasConstName(name: String): Boolean {
        // Value enums / write payloads — not property ids (e.g. LIGHTCONTROL_OFF, LAS_MODE_LDW).
        // LAS_MODE_SELECTION is a property id; only the 1/2/3 payloads are aliases.
        if (name.endsWith("_VALUE") || name.contains("_VALUE_")) return true
        if (name.startsWith("LIGHTCONTROL_")) return true
        if (name.startsWith("LAS_MODE_") && name != "LAS_MODE_SELECTION") return true
        if (name.startsWith("HVAC_CUSTOM_")) return true
        if (name.startsWith("HVAC_AIR_RECIRCULATION_VALUE")) return true
        if (name.startsWith("HVAC_FAN_DIRECTION_")) return true
        if (name.startsWith("WINDOW_A10_")) return true
        if (name == "SUNROOF_TILT") return true
        return false
    }
}
