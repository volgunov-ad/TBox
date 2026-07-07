package vad.dashing.tbox.utils

const val GEARBOX_MODE_CURRENT_GEAR_DATA_KEY = "gearBoxModeCurrentGear"

fun formatGearBoxModeWithCurrentGear(mode: String, currentGear: Int?): String {
    if (mode.isBlank()) return ""
    return when (mode) {
        "D" -> {
            val gear = currentGear
            if (gear != null && gear > 0) "D$gear" else "D"
        }
        "P", "R", "N" -> mode
        else -> mode
    }
}
