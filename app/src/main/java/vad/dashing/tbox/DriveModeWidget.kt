package vad.dashing.tbox

import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

const val DRIVE_MODE_WIDGET_DATA_KEY = "driveModeWidget"
const val DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE = 2

const val DRIVE_MODE_CYCLE_WIDGET_DATA_KEY = "driveModeCycleWidget"
val DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES: List<Int> = listOf(2, 0, 1)

data class DriveModeWidgetOption(
    val rawValue: Int,
    val label: String,
    val widgetLabel: String,
    val propertyId: Int,
    val propertyValue: Int,
) {
    override fun toString(): String = label
}

val DRIVE_MODE_WIDGET_OPTIONS: List<DriveModeWidgetOption> = listOf(
    DriveModeWidgetOption(
        rawValue = 2,
        label = "ECO",
        widgetLabel = "ECO",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 2
    ),
    DriveModeWidgetOption(
        rawValue = 0,
        label = "NOR",
        widgetLabel = "NOR",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 0
    ),
    DriveModeWidgetOption(
        rawValue = 1,
        label = "SPT",
        widgetLabel = "SPT",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 1
    ),
    DriveModeWidgetOption(
        rawValue = 5,
        label = "SAND",
        widgetLabel = "SAND",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 5
    ),
    DriveModeWidgetOption(
        rawValue = 4,
        label = "MUD",
        widgetLabel = "MUD",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 4
    ),
    DriveModeWidgetOption(
        rawValue = 3,
        label = "SNOW",
        widgetLabel = "SNOW",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE,
        propertyValue = 3
    ),
    DriveModeWidgetOption(
        rawValue = 101,
        label = "ECO (6DCT)",
        widgetLabel = "ECO",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        propertyValue = 1
    ),
    DriveModeWidgetOption(
        rawValue = 102,
        label = "NOR (6DCT)",
        widgetLabel = "NOR",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        propertyValue = 2
    ),
    DriveModeWidgetOption(
        rawValue = 100,
        label = "SPT (6DCT)",
        widgetLabel = "SPT",
        propertyId = MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET,
        propertyValue = 0
    ),
)

fun normalizeDriveModeWidgetRawValue(rawValue: Int): Int {
    return DRIVE_MODE_WIDGET_OPTIONS
        .firstOrNull { it.rawValue == rawValue }
        ?.rawValue
        ?: DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE
}

fun resolveDriveModeWidgetOption(rawValue: Int): DriveModeWidgetOption {
    val normalized = normalizeDriveModeWidgetRawValue(rawValue)
    return DRIVE_MODE_WIDGET_OPTIONS.first { it.rawValue == normalized }
}

fun isDriveMode6dct(rawValue: Int): Boolean {
    val option = DRIVE_MODE_WIDGET_OPTIONS.firstOrNull { it.rawValue == rawValue } ?: return false
    return option.propertyId == MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET
}

fun isDriveModeCycleWidgetDataKey(dataKey: String): Boolean =
    dataKey == DRIVE_MODE_CYCLE_WIDGET_DATA_KEY

/**
 * Keeps valid raw values from one family (standard or 6DCT), ordered like [DRIVE_MODE_WIDGET_OPTIONS].
 * Empty / invalid / mixed input falls back to [DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES].
 * When both families appear, keeps the family of the first valid value in input order.
 */
fun normalizeDriveModeCycleSelection(rawValues: Collection<Int>): List<Int> {
    val validByRaw = DRIVE_MODE_WIDGET_OPTIONS.associateBy { it.rawValue }
    val firstValidInInput = rawValues.firstOrNull { it in validByRaw }
        ?: return DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES
    val prefer6dct = isDriveMode6dct(firstValidInInput)
    val selectedSet = rawValues.filter { raw ->
        val option = validByRaw[raw] ?: return@filter false
        isDriveMode6dct(option.rawValue) == prefer6dct
    }.toSet()
    val ordered = DRIVE_MODE_WIDGET_OPTIONS
        .filter { it.rawValue in selectedSet }
        .map { it.rawValue }
    return ordered.ifEmpty { DRIVE_MODE_CYCLE_WIDGET_DEFAULT_RAW_VALUES }
}

/**
 * Current drive-mode raw value for [DRIVE_MODE_CYCLE_WIDGET_DATA_KEY], reading the CAN property
 * that matches the selected family (standard vs 6DCT).
 *
 * Unlike [DriveModeThemeWatcher.resolveDriveModeThemeKey] (themes: prefer standard, fallback 6DCT),
 * the cycle widget must track the same property it writes — otherwise a populated standard
 * [MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE] makes every tap resolve to the first 6DCT option.
 */
fun resolveDriveModeCycleCurrentRaw(
    drive: Int?,
    wet6dct: Int?,
    selected: Collection<Int>,
): Int? {
    val normalized = normalizeDriveModeCycleSelection(selected)
    val prefer6dct = isDriveMode6dct(normalized.first())
    val propertyId = if (prefer6dct) {
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET
    } else {
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE
    }
    val propertyValue = if (prefer6dct) wet6dct else drive
    return DRIVE_MODE_WIDGET_OPTIONS
        .firstOrNull { it.propertyId == propertyId && it.propertyValue == propertyValue }
        ?.rawValue
}

/**
 * Next mode from [selected] after [currentRaw] in [DRIVE_MODE_WIDGET_OPTIONS] order (wraps).
 * If [currentRaw] is null, unknown, or from the other family than [selected], returns the first
 * selected mode.
 */
fun nextDriveModeCycleTarget(
    currentRaw: Int?,
    selected: Collection<Int>,
): DriveModeWidgetOption {
    val normalizedSelected = normalizeDriveModeCycleSelection(selected)
    val selectedSet = normalizedSelected.toSet()
    val prefer6dct = isDriveMode6dct(normalizedSelected.first())
    if (currentRaw == null ||
        DRIVE_MODE_WIDGET_OPTIONS.none { it.rawValue == currentRaw } ||
        isDriveMode6dct(currentRaw) != prefer6dct
    ) {
        return resolveDriveModeWidgetOption(normalizedSelected.first())
    }
    val currentIndex = DRIVE_MODE_WIDGET_OPTIONS.indexOfFirst { it.rawValue == currentRaw }
    val size = DRIVE_MODE_WIDGET_OPTIONS.size
    for (offset in 1..size) {
        val candidate = DRIVE_MODE_WIDGET_OPTIONS[(currentIndex + offset) % size]
        if (candidate.rawValue in selectedSet) {
            return candidate
        }
    }
    return resolveDriveModeWidgetOption(normalizedSelected.first())
}

/** Toggle [rawValue] in a cycle selection with mutual exclusion of 6DCT vs standard families. */
fun toggleDriveModeCycleSelection(
    current: Collection<Int>,
    rawValue: Int,
): List<Int> {
    if (DRIVE_MODE_WIDGET_OPTIONS.none { it.rawValue == rawValue }) {
        return normalizeDriveModeCycleSelection(current)
    }
    val normalized = normalizeDriveModeCycleSelection(current)
    if (rawValue in normalized) {
        if (normalized.size <= 1) {
            return normalized
        }
        return normalizeDriveModeCycleSelection(normalized - rawValue)
    }
    val sameFamily = isDriveMode6dct(rawValue) == isDriveMode6dct(normalized.first())
    val next = if (sameFamily) {
        normalized + rawValue
    } else {
        listOf(rawValue)
    }
    return normalizeDriveModeCycleSelection(next)
}
