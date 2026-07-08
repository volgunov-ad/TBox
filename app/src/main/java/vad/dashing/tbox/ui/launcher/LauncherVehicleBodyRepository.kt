package vad.dashing.tbox.ui.launcher

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.mbcan.Android10VhalRepository
import vad.dashing.tbox.mbcan.MbCanEngineFacade
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.VehicleBodyState
import vad.dashing.tbox.mbcan.decodeMbCanDoorByte
import vad.dashing.tbox.mbcan.decodeMbCanTrunkByte

/**
 * Polls vehicle body state (doors, tailgate) from mbCAN / VHAL for the launcher schematic.
 */
object LauncherVehicleBodyRepository {
    private const val POLL_MS = 1_500L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var pollJob: Job? = null
    private var subscribed = false

    private val _state = MutableStateFlow(VehicleBodyState())
    val state: StateFlow<VehicleBodyState> = _state.asStateFlow()

    fun ensurePolling() {
        if (pollJob?.isActive == true) return
        subscribeDoorTelemetry()
        pollJob = scope.launch {
            while (isActive) {
                refresh()
                delay(POLL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refresh() {
        val body = when (UniversalCanRepository.mode.value) {
            HeadUnitCanMode.Android9MbCan -> readFromMbCan()
            HeadUnitCanMode.Android10Vhal -> Android10VhalRepository.readVehicleBodyState()
                ?: readFromMbCan()
        }
        if (body != null) {
            _state.value = body
        }
    }

    private fun subscribeDoorTelemetry() {
        if (subscribed) return
        subscribed = true
        MbCanEngineFacade.subscribe(setOf("eMBCAN_VEHICLE_DOOR"))
    }

    private fun readFromMbCan(): VehicleBodyState? {
        val snap = MbCanEngineFacade.readVehicleDoor() ?: return null
        return VehicleBodyState(
            doorFlOpen = decodeMbCanDoorByte(snap.driverDoor),
            doorFrOpen = decodeMbCanDoorByte(snap.passengerDoor),
            doorRlOpen = decodeMbCanDoorByte(snap.rearLeftDoor),
            doorRrOpen = decodeMbCanDoorByte(snap.rearRightDoor),
            tailgateOpen = decodeMbCanTrunkByte(snap.trunk),
            hoodOpen = decodeMbCanTrunkByte(snap.hood),
        )
    }
}
