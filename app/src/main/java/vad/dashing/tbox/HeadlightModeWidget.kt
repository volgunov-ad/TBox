package vad.dashing.tbox

/**
 * Headlight / light-control mode (mbCAN [eVEHICLE_LIGHTCONTROL] / VHAL Lightcontrol).
 *
 * Stock A9 [Em_HeadlampControl_ListItem_value] and A10 [CarOutLightFragment]:
 * **1** AUTO, **2** PARK, **3** LOW, **4** OFF.
 */
enum class HeadlightMode(val rawValue: Int, val widgetLabel: String) {
    Auto(1, "AUTO"),
    Park(2, "PARK"),
    Low(3, "LOW"),
    Off(4, "OFF");

    companion object {
        /** Numeric cycle 1→2→3→4→1 (AUTO→PARK→LOW→OFF). */
        val cycleOrder: List<HeadlightMode> = listOf(Auto, Park, Low, Off)

        /** Stock capsule / radio UI order: OFF, PARK, LOW, AUTO. */
        val settingsOrder: List<HeadlightMode> = listOf(Off, Park, Low, Auto)

        fun fromRaw(raw: Int): HeadlightMode? = entries.firstOrNull { it.rawValue == raw }

        fun nextInCycle(currentRaw: Int?): HeadlightMode {
            val current = currentRaw?.let(::fromRaw)
            if (current == null) return Auto
            val idx = cycleOrder.indexOf(current)
            return cycleOrder[(idx + 1) % cycleOrder.size]
        }
    }
}

const val HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY = "headlightModeCycleWidget"
const val REAR_FOG_WIDGET_DATA_KEY = "rearFogWidget"
