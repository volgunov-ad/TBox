package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Trunk door state for the dashboard widget. Updated by the active CAN backend.
 */
object TrunkDoorRepository {
    private val _displayState = MutableStateFlow(TrunkDoorDisplayState.Unknown)
    val displayState: StateFlow<TrunkDoorDisplayState> = _displayState.asStateFlow()

    private var isOpen: Boolean? = null
    private var moveDir: Int? = null

    fun clear() {
        isOpen = null
        moveDir = null
        _displayState.value = TrunkDoorDisplayState.Unknown
    }

    fun applyVhalOpenRaw(raw: Int?) {
        TrunkDoorDomain.decodeBinaryOpenVhal(raw)?.let { isOpen = it }
        publish()
    }

    fun applyMoveDirRaw(raw: Int?) {
        moveDir = raw
        publish()
    }

    fun applyBcmPush(moveDirRaw: Int?, trunkStsRaw: Int?) {
        trunkStsRaw?.let { raw ->
            TrunkDoorDomain.decodeBinaryOpenMbCan(raw)?.let { isOpen = it }
        }
        moveDir = moveDirRaw
        publish()
    }

    private fun publish() {
        _displayState.value = TrunkDoorDomain.buildDisplayState(isOpen, moveDir)
    }
}
