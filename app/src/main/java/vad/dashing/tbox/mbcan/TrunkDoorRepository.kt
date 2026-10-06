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

    /** mbCAN and VHAL callbacks arrive on different threads; mutators are @Synchronized. */
    private var isOpen: Boolean? = null
    private var moveDir: Int? = null

    @Synchronized
    fun clear() {
        isOpen = null
        moveDir = null
        _displayState.value = TrunkDoorDisplayState.Unknown
    }

    @Synchronized
    fun applyVhalOpenRaw(raw: Int?) {
        TrunkDoorDomain.decodeBinaryOpenVhal(raw)?.let { isOpen = it }
        publish()
    }

    @Synchronized
    fun applyMoveDirRaw(raw: Int?) {
        moveDir = raw
        publish()
    }

    @Synchronized
    fun applyBcmPush(moveDirRaw: Int?, trunkStsRaw: Int?) {
        trunkStsRaw?.let { raw ->
            TrunkDoorDomain.decodeBinaryOpenMbCan(raw)?.let { isOpen = it }
        }
        moveDirRaw?.let { moveDir = it }
        publish()
    }

    private fun publish() {
        _displayState.value = TrunkDoorDomain.buildDisplayState(isOpen, moveDir)
    }
}
