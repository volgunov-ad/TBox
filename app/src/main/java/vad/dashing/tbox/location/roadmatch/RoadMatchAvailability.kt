package vad.dashing.tbox.location.roadmatch

import vad.dashing.tbox.location.MockCanSpeedMode
import vad.dashing.tbox.location.MockPowerState

/** When the Geoposition «road match» toggle may be turned on. */
object RoadMatchAvailability {
    fun isToggleEnabled(power: MockPowerState, storedMode: MockCanSpeedMode): Boolean {
        return when (power) {
            MockPowerState.OFF -> false
            MockPowerState.WHEN_NO_FIX -> true
            MockPowerState.ALWAYS_ON -> power.effectiveCanSpeedMode(storedMode).enhancesMock
        }
    }
}
