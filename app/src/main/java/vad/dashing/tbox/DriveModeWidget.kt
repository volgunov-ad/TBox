package vad.dashing.tbox

const val DRIVE_MODE_WIDGET_DATA_KEY = "driveModeWidget"
const val DRIVE_MODE_WIDGET_DEFAULT_RAW_VALUE = 2

data class DriveModeWidgetOption(
    val rawValue: Int,
    val label: String,
) {
    override fun toString(): String = label
}

val DRIVE_MODE_WIDGET_OPTIONS: List<DriveModeWidgetOption> = listOf(
    DriveModeWidgetOption(rawValue = 2, label = "ECO"),
    DriveModeWidgetOption(rawValue = 0, label = "NOR"),
    DriveModeWidgetOption(rawValue = 1, label = "SPT"),
    DriveModeWidgetOption(rawValue = 5, label = "SAND"),
    DriveModeWidgetOption(rawValue = 4, label = "MUD"),
    DriveModeWidgetOption(rawValue = 3, label = "SNOW"),
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
