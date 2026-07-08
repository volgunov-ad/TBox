package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import vad.dashing.tbox.mbcan.VehicleBodyState

/** Debug overrides for launcher vehicle animation when not in the car. */
object LauncherDevVehicleState {
    var simulateEnabled by mutableStateOf(false)
    /**
     * Preview steer animation from the physical wheel while stationary (real CAN steer, speed visual = 0).
     */
    var motionPreviewEnabled by mutableStateOf(false)
    var speedKmh by mutableFloatStateOf(0f)
    var steerAngleDeg by mutableFloatStateOf(0f)
    var doorFlOpen by mutableStateOf(false)
    var doorFrOpen by mutableStateOf(false)
    var doorRlOpen by mutableStateOf(false)
    var doorRrOpen by mutableStateOf(false)
    var tailgateOpen by mutableStateOf(false)

    fun bodyState(): VehicleBodyState = VehicleBodyState(
        doorFlOpen = doorFlOpen,
        doorFrOpen = doorFrOpen,
        doorRlOpen = doorRlOpen,
        doorRrOpen = doorRrOpen,
        tailgateOpen = tailgateOpen,
    )

    fun toggleSimulate() {
        simulateEnabled = !simulateEnabled
        if (simulateEnabled) motionPreviewEnabled = false
    }

    fun toggleMotionPreview() {
        motionPreviewEnabled = !motionPreviewEnabled
        if (motionPreviewEnabled) simulateEnabled = false
    }

    fun bumpSpeed(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        speedKmh = (speedKmh + delta).coerceIn(0f, 180f)
    }

    fun bumpSteer(delta: Float) {
        simulateEnabled = true
        motionPreviewEnabled = false
        steerAngleDeg = (steerAngleDeg + delta).coerceIn(-540f, 540f)
    }

    fun toggleDoorFl() {
        simulateEnabled = true
        doorFlOpen = !doorFlOpen
    }

    fun toggleDoorFr() {
        simulateEnabled = true
        doorFrOpen = !doorFrOpen
    }

    fun toggleTailgate() {
        simulateEnabled = true
        tailgateOpen = !tailgateOpen
    }
}
