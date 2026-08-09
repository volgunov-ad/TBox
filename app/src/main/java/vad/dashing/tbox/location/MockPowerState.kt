package vad.dashing.tbox.location

/**
 * Master power for system mock location injection (Geoposition three-button control).
 *
 * [OFF] — do not inject.
 * [WHEN_NO_FIX] — inject only while GNSS has no fix; DR engine is always [MockCanSpeedMode.CONSTANT].
 * [ALWAYS_ON] — inject every period; enhancement mode is the stored [MockCanSpeedMode].
 *
 * Distinct from [MockCanSpeedMode.WHEN_FIX_LOST]: that mode still writes mock while fix is good
 * (passthrough GNSS); [WHEN_NO_FIX] removes the mock provider while fix is present.
 */
enum class MockPowerState {
    OFF,
    WHEN_NO_FIX,
    ALWAYS_ON;

    val isMockEnabled: Boolean get() = this != OFF

    /** Mode used by [MockLocationJob] for the current power. */
    fun effectiveCanSpeedMode(stored: MockCanSpeedMode): MockCanSpeedMode =
        if (this == WHEN_NO_FIX) MockCanSpeedMode.CONSTANT else stored

    companion object {
        fun fromStorage(raw: String?, legacyMockEnabled: Boolean): MockPowerState {
            return when (raw?.trim()?.uppercase()) {
                "WHEN_NO_FIX", "ON_WHEN_NO_FIX", "NO_FIX" -> WHEN_NO_FIX
                "ALWAYS_ON", "ON", "ENABLED", "ALWAYS" -> ALWAYS_ON
                "OFF", "FALSE", "0", "DISABLED" -> OFF
                null, "" -> if (legacyMockEnabled) ALWAYS_ON else OFF
                else -> if (legacyMockEnabled) ALWAYS_ON else OFF
            }
        }
    }
}

/**
 * Dashboard widget cycle for mock power+mode (does **not** include [MockPowerState.WHEN_NO_FIX]):
 * 0 Off → 1 Direct → 2 On-loss → 3 Always → 4 Advanced → 0 …
 */
object MockLocationWidgetCycle {
    const val INDEX_OFF = 0
    const val INDEX_DIRECT = 1
    const val INDEX_WHEN_FIX_LOST = 2
    const val INDEX_ALWAYS = 3
    const val INDEX_ADVANCED = 4

    data class Selection(
        val power: MockPowerState,
        val mode: MockCanSpeedMode,
    )

    /**
     * Widget digit 0…4, or null when power is [MockPowerState.WHEN_NO_FIX]
     * (menu-only — not part of this cycle).
     */
    fun indexOf(power: MockPowerState, mode: MockCanSpeedMode): Int? {
        return when (power) {
            MockPowerState.OFF -> INDEX_OFF
            MockPowerState.WHEN_NO_FIX -> null
            MockPowerState.ALWAYS_ON -> when (mode) {
                MockCanSpeedMode.NONE -> INDEX_DIRECT
                MockCanSpeedMode.WHEN_FIX_LOST -> INDEX_WHEN_FIX_LOST
                MockCanSpeedMode.ALWAYS -> INDEX_ALWAYS
                MockCanSpeedMode.CONSTANT -> INDEX_ADVANCED
            }
        }
    }

    fun selectionForIndex(index: Int): Selection {
        return when (index.coerceIn(INDEX_OFF, INDEX_ADVANCED)) {
            INDEX_DIRECT -> Selection(MockPowerState.ALWAYS_ON, MockCanSpeedMode.NONE)
            INDEX_WHEN_FIX_LOST -> Selection(MockPowerState.ALWAYS_ON, MockCanSpeedMode.WHEN_FIX_LOST)
            INDEX_ALWAYS -> Selection(MockPowerState.ALWAYS_ON, MockCanSpeedMode.ALWAYS)
            INDEX_ADVANCED -> Selection(MockPowerState.ALWAYS_ON, MockCanSpeedMode.CONSTANT)
            else -> Selection(MockPowerState.OFF, MockCanSpeedMode.NONE)
        }
    }

    fun next(power: MockPowerState, mode: MockCanSpeedMode): Selection {
        val cur = indexOf(power, mode)
        // Menu-only WHEN_NO_FIX: first tap enters the widget cycle at Off.
        if (cur == null) return selectionForIndex(INDEX_OFF)
        val nextIndex = if (cur >= INDEX_ADVANCED) INDEX_OFF else cur + 1
        return selectionForIndex(nextIndex)
    }
}
