package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global cruise-control hardware type (Settings → Car).
 * Drives all cruise widgets and automation cruise builtins.
 * Defaults to [ACC] when the DataStore key is absent.
 */
enum class GlobalCruiseControlType(val storageKey: String) {
    /** Ordinary cruise (CCS). */
    CCS("ccs"),
    /** Adaptive cruise (ACC). */
    ACC("acc"),
    ;

    fun toCruiseControlType(): CruiseControlType = when (this) {
        ACC -> CruiseControlType.ACC
        CCS -> CruiseControlType.CCS
    }

    companion object {
        val DEFAULT: GlobalCruiseControlType = ACC

        fun fromStorageKey(key: String?): GlobalCruiseControlType {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}

/**
 * Global climate-control layout (Settings → Car).
 * Defaults to [DUAL_ZONE] when the DataStore key is absent.
 */
enum class ClimateControlType(val storageKey: String) {
    /** Ordinary AC: no Auto, no Sync, no passenger temp. */
    ORDINARY_AC("ordinary_ac"),
    /** Single-zone: Auto yes; no Sync, no passenger temp. */
    SINGLE_ZONE("single_zone"),
    /** Dual-zone: Auto, Sync, passenger temp (current full UI). */
    DUAL_ZONE("dual_zone"),
    ;

    companion object {
        val DEFAULT: ClimateControlType = DUAL_ZONE

        fun fromStorageKey(key: String?): ClimateControlType {
            val normalized = key?.trim()?.lowercase().orEmpty()
            return entries.firstOrNull { it.storageKey == normalized } ?: DEFAULT
        }
    }
}

/** UI capability flags derived from [ClimateControlType]. */
data class ClimateUiCapabilities(
    val showAuto: Boolean,
    val showSync: Boolean,
    val showPassengerTemp: Boolean,
) {
    companion object {
        fun forType(type: ClimateControlType): ClimateUiCapabilities = when (type) {
            ClimateControlType.ORDINARY_AC -> ClimateUiCapabilities(
                showAuto = false,
                showSync = false,
                showPassengerTemp = false,
            )
            ClimateControlType.SINGLE_ZONE -> ClimateUiCapabilities(
                showAuto = true,
                showSync = false,
                showPassengerTemp = false,
            )
            ClimateControlType.DUAL_ZONE -> ClimateUiCapabilities(
                showAuto = true,
                showSync = true,
                showPassengerTemp = true,
            )
        }
    }
}

fun ClimateControlType.uiCapabilities(): ClimateUiCapabilities =
    ClimateUiCapabilities.forType(this)

/** Wire value for phone BLE snapshot page 1, body[9]. 0xFF = unknown/legacy. */
fun ClimateControlType.toBleWire(): Int = when (this) {
    ClimateControlType.ORDINARY_AC -> 0
    ClimateControlType.SINGLE_ZONE -> 1
    ClimateControlType.DUAL_ZONE -> 2
}

fun climateControlTypeFromBleWire(raw: Int?): ClimateControlType? = when (raw) {
    0 -> ClimateControlType.ORDINARY_AC
    1 -> ClimateControlType.SINGLE_ZONE
    2 -> ClimateControlType.DUAL_ZONE
    else -> null
}

private const val HVAC_AUTO_WIDGET_DATA_KEY = "hvacAutoWidget"

/**
 * Widget picker: hide climate tiles that the global climate type does not support.
 * Existing placed tiles are left on panels (caller must not auto-delete).
 */
fun isClimateWidgetHiddenFromPicker(dataKey: String, climateType: ClimateControlType): Boolean {
    val caps = climateType.uiCapabilities()
    return when (dataKey) {
        HVAC_AUTO_WIDGET_DATA_KEY -> !caps.showAuto
        HVAC_SYNC_WIDGET_DATA_KEY -> !caps.showSync
        HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
        HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
        -> !caps.showPassengerTemp
        else -> false
    }
}

/** Cruise tiles are always offered; runtime path comes from [GlobalCruiseControlType]. */
fun isCruiseWidgetHiddenFromPicker(
    @Suppress("UNUSED_PARAMETER") dataKey: String,
    @Suppress("UNUSED_PARAMETER") cruiseType: GlobalCruiseControlType,
): Boolean = false

/**
 * Cached global vehicle feature settings for sync readers (widgets, automations, BLE)
 * and Compose collectors. Updated from [TboxApplication] / [SettingsManager] saves.
 * Defaults match absent DataStore keys.
 */
object VehicleFeatureSettings {
    private val _cruiseControlType = MutableStateFlow(GlobalCruiseControlType.DEFAULT)
    private val _climateControlType = MutableStateFlow(ClimateControlType.DEFAULT)

    val cruiseControlTypeFlow: StateFlow<GlobalCruiseControlType> = _cruiseControlType.asStateFlow()
    val climateControlTypeFlow: StateFlow<ClimateControlType> = _climateControlType.asStateFlow()

    val cruiseControlType: GlobalCruiseControlType
        get() = _cruiseControlType.value

    val climateControlType: ClimateControlType
        get() = _climateControlType.value

    fun updateCruise(type: GlobalCruiseControlType) {
        _cruiseControlType.value = type
    }

    fun updateClimate(type: ClimateControlType) {
        _climateControlType.value = type
    }

    fun climateCapabilities(): ClimateUiCapabilities =
        climateControlType.uiCapabilities()
}
