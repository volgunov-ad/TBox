package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Left / right front seat for shared poll + confirmation + burst logic.
 */
enum class MbCanSeatSlot {
    Left,
    Right;

    val propertyId: Int
        get() = when (this) {
            Left -> MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH
            Right -> MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH
        }

    val signal: MbCanSignal
        get() = when (this) {
            Left -> MbCanSignal.FrontLeftSeatMode
            Right -> MbCanSignal.FrontRightSeatMode
        }

    fun stateFlow(
        left: MutableStateFlow<MbCanSeatModeState>,
        right: MutableStateFlow<MbCanSeatModeState>,
    ): MutableStateFlow<MbCanSeatModeState> = when (this) {
        Left -> left
        Right -> right
    }
}

/**
 * Deterministic mbCAN widget state: confirmation for [Unknown]/[Unavailable], burst on
 * transition from non-problem to problem, single place for push + poll application.
 */
internal class MbCanSignalStateEngine(
    private val steeringFlow: MutableStateFlow<MbCanBinaryState>,
    private val leftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val requiredConsecutiveProblems: Int = 3,
) {
    private var steeringUnknownStreak = 0
    private var steeringUnavailableStreak = 0
    private var leftUnknownStreak = 0
    private var leftUnavailableStreak = 0
    private var rightUnknownStreak = 0
    private var rightUnavailableStreak = 0

    private fun MbCanBinaryState.isProblemState(): Boolean =
        this is MbCanBinaryState.Unknown || this is MbCanBinaryState.Unavailable

    private fun MbCanSeatModeState.isProblemState(): Boolean =
        this is MbCanSeatModeState.Unknown || this is MbCanSeatModeState.Unavailable

    suspend fun applySteeringCandidate(decoded: MbCanBinaryState) {
        val published = steeringFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            MbCanJobManager.requestBurst(MbCanSignal.SteeringWheelHeat)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                steeringUnknownStreak += 1
                steeringUnavailableStreak = 0
                if (steeringUnknownStreak >= requiredConsecutiveProblems) {
                    steeringFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                steeringUnavailableStreak += 1
                steeringUnknownStreak = 0
                if (steeringUnavailableStreak >= requiredConsecutiveProblems) {
                    steeringFlow.value = decoded
                }
            }
            else -> {
                steeringUnknownStreak = 0
                steeringUnavailableStreak = 0
                steeringFlow.value = decoded
            }
        }
    }

    suspend fun applySeatCandidate(slot: MbCanSeatSlot, decoded: MbCanSeatModeState) {
        val flow = slot.stateFlow(leftSeatFlow, rightSeatFlow)
        val published = flow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            MbCanJobManager.requestBurst(slot.signal)
        }
        var unknown = when (slot) {
            MbCanSeatSlot.Left -> leftUnknownStreak
            MbCanSeatSlot.Right -> rightUnknownStreak
        }
        var unavailable = when (slot) {
            MbCanSeatSlot.Left -> leftUnavailableStreak
            MbCanSeatSlot.Right -> rightUnavailableStreak
        }
        when (decoded) {
            is MbCanSeatModeState.Unknown -> {
                unknown += 1
                unavailable = 0
                if (unknown >= requiredConsecutiveProblems) {
                    flow.value = MbCanSeatModeState.Unknown
                }
            }
            is MbCanSeatModeState.Unavailable -> {
                unavailable += 1
                unknown = 0
                if (unavailable >= requiredConsecutiveProblems) {
                    flow.value = decoded
                }
            }
            else -> {
                unknown = 0
                unavailable = 0
                flow.value = decoded
            }
        }
        when (slot) {
            MbCanSeatSlot.Left -> {
                leftUnknownStreak = unknown
                leftUnavailableStreak = unavailable
            }
            MbCanSeatSlot.Right -> {
                rightUnknownStreak = unknown
                rightUnavailableStreak = unavailable
            }
        }
    }

    companion object {
        fun decodeSteeringWheelHeatRaw(raw: Int): MbCanBinaryState = when (raw) {
            2 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        fun decodeSeatModeRaw(raw: Int): MbCanSeatModeState = when (raw) {
            1 -> MbCanSeatModeState.Off
            2 -> MbCanSeatModeState.Heat(1)
            3 -> MbCanSeatModeState.Heat(2)
            4 -> MbCanSeatModeState.Heat(3)
            5 -> MbCanSeatModeState.Vent(1)
            6 -> MbCanSeatModeState.Vent(2)
            7 -> MbCanSeatModeState.Vent(3)
            else -> MbCanSeatModeState.Unknown
        }
    }
}
