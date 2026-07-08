package vad.dashing.tbox.ui.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.mbcan.UniversalCanRepository

/** Resolved speed (km/h) and steering wheel angle (degrees) for launcher visuals. */
data class LauncherVehicleMotion(
    val speedKmh: Float,
    val steerAngleDeg: Float,
    val speedFromTbox: Boolean,
    val steerFromTbox: Boolean,
    /** Show steer animation while stationary (motion preview / physical wheel test). */
    val steerPreviewActive: Boolean = false,
)

@Composable
fun rememberLauncherVehicleMotion(
    tboxConnected: Boolean,
    canViewModel: CanDataViewModel,
): LauncherVehicleMotion {
    val tboxSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val canSpeedAccurate by canViewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val tboxSteer by canViewModel.steerAngle.collectAsStateWithLifecycle()
    val headUnitSpeed by UniversalCanRepository.carSpeedState.collectAsStateWithLifecycle()

    if (LauncherDevVehicleState.simulateEnabled) {
        return LauncherVehicleMotion(
            speedKmh = LauncherDevVehicleState.speedKmh,
            steerAngleDeg = LauncherDevVehicleState.steerAngleDeg,
            speedFromTbox = false,
            steerFromTbox = false,
        )
    }
    if (LauncherDevVehicleState.motionPreviewEnabled) {
        return LauncherVehicleMotion(
            speedKmh = 0f,
            steerAngleDeg = tboxSteer ?: 0f,
            speedFromTbox = false,
            steerFromTbox = tboxSteer != null,
            steerPreviewActive = true,
        )
    }

    val speed = when {
        headUnitSpeed != null && headUnitSpeed!! >= 0f -> headUnitSpeed!!
        canSpeedAccurate != null && canSpeedAccurate!! >= 0f -> canSpeedAccurate!!
        tboxConnected && tboxSpeed != null -> tboxSpeed!!
        tboxSpeed != null -> tboxSpeed!!
        else -> 0f
    }
    val steer = tboxSteer ?: 0f
    return LauncherVehicleMotion(
        speedKmh = speed,
        steerAngleDeg = steer,
        speedFromTbox = tboxConnected && tboxSpeed != null,
        steerFromTbox = tboxSteer != null,
    )
}

@Composable
fun rememberLauncherVisualSteer(
    motion: LauncherVehicleMotion,
): Float = LauncherSteerVisual.visualSteerDeg(motion.steerAngleDeg)
