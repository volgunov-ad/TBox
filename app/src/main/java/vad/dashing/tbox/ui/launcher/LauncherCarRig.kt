package vad.dashing.tbox.ui.launcher

/** Offline GLB path for the Jetour Dashing 720 launcher model. */
const val LAUNCHER_CAR_MODEL_ASSET = "models/jetour_dashing_site.glb"

/** Vehicle body / drive state for the 3D model (doors stay static in GLB pose). */
data class LauncherCarRigState(
    val doorFlOpen: Boolean = false,
    val doorFrOpen: Boolean = false,
    val doorRlOpen: Boolean = false,
    val doorRrOpen: Boolean = false,
    val tailgateOpen: Boolean = false,
    val sunroofOpen: Boolean = false,
    val speedKmh: Float = 0f,
    val steeringDeg: Float = 0f,
    val wheelSpinRad: Float = 0f,
)

object LauncherCarRig {
    private const val WHEEL_RADIUS_M = 0.25f

    fun advance(state: LauncherCarRigState, dtSec: Float): LauncherCarRigState {
        val speedMps = (state.speedKmh / 3.6f).coerceAtLeast(0f)
        val spinDelta = if (speedMps <= 0f) 0f else (speedMps / WHEEL_RADIUS_M) * dtSec
        return state.copy(wheelSpinRad = state.wheelSpinRad + spinDelta)
    }
}
