package vad.dashing.tbox

import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

const val DRIVE_MODE_WIDGET_DATA_KEY = "driveModeWidget"
const val DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE = 2

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
