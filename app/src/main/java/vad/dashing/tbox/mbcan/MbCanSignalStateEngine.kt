package vad.dashing.tbox.mbcan

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Front and rear seat slots for shared poll + confirmation + burst logic.
 */
enum class MbCanSeatSlot {
    FrontLeft,
    FrontRight,
    RearLeft,
    RearRight;

    val propertyId: Int
        get() = when (this) {
            FrontLeft -> MbCanKnownVehiclePropertyId.FRONT_LEFT_SEAT_HEAT_VENT_SWITCH
            FrontRight -> MbCanKnownVehiclePropertyId.FRONT_RIGHT_SEAT_HEAT_VENT_SWITCH
            RearLeft -> MbCanKnownVehiclePropertyId.REAR_LEFT_SEAT_HEAT_SWITCH
            RearRight -> MbCanKnownVehiclePropertyId.REAR_RIGHT_SEAT_HEAT_SWITCH
        }

    val signal: MbCanSignal
        get() = when (this) {
            FrontLeft -> MbCanSignal.FrontLeftSeatMode
            FrontRight -> MbCanSignal.FrontRightSeatMode
            RearLeft -> MbCanSignal.RearLeftSeatMode
            RearRight -> MbCanSignal.RearRightSeatMode
        }

    fun stateFlow(
        frontLeft: MutableStateFlow<MbCanSeatModeState>,
        frontRight: MutableStateFlow<MbCanSeatModeState>,
        rearLeft: MutableStateFlow<MbCanSeatModeState>,
        rearRight: MutableStateFlow<MbCanSeatModeState>,
    ): MutableStateFlow<MbCanSeatModeState> = when (this) {
        FrontLeft -> frontLeft
        FrontRight -> frontRight
        RearLeft -> rearLeft
        RearRight -> rearRight
    }
}

/**
 * Deterministic mbCAN widget state: confirmation for [Unknown]/[Unavailable], burst on
 * transition from non-problem to problem, single place for push + poll application.
 */
internal class MbCanSignalStateEngine(
    private val steeringFlow: MutableStateFlow<MbCanBinaryState>,
    private val frontLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val frontRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val requiredConsecutiveProblems: Int = 3,
) {
    private var steeringUnknownStreak = 0
    private var steeringUnavailableStreak = 0
    private var frontLeftUnknownStreak = 0
    private var frontLeftUnavailableStreak = 0
    private var frontRightUnknownStreak = 0
    private var frontRightUnavailableStreak = 0
    private var rearLeftUnknownStreak = 0
    private var rearLeftUnavailableStreak = 0
    private var rearRightUnknownStreak = 0
    private var rearRightUnavailableStreak = 0

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
        val flow = slot.stateFlow(
            frontLeftSeatFlow,
            frontRightSeatFlow,
            rearLeftSeatFlow,
            rearRightSeatFlow
        )
        val published = flow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            MbCanJobManager.requestBurst(slot.signal)
        }
        var unknown = when (slot) {
            MbCanSeatSlot.FrontLeft -> frontLeftUnknownStreak
            MbCanSeatSlot.FrontRight -> frontRightUnknownStreak
            MbCanSeatSlot.RearLeft -> rearLeftUnknownStreak
            MbCanSeatSlot.RearRight -> rearRightUnknownStreak
        }
        var unavailable = when (slot) {
            MbCanSeatSlot.FrontLeft -> frontLeftUnavailableStreak
            MbCanSeatSlot.FrontRight -> frontRightUnavailableStreak
            MbCanSeatSlot.RearLeft -> rearLeftUnavailableStreak
            MbCanSeatSlot.RearRight -> rearRightUnavailableStreak
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
            MbCanSeatSlot.FrontLeft -> {
                frontLeftUnknownStreak = unknown
                frontLeftUnavailableStreak = unavailable
            }
            MbCanSeatSlot.FrontRight -> {
                frontRightUnknownStreak = unknown
                frontRightUnavailableStreak = unavailable
            }
            MbCanSeatSlot.RearLeft -> {
                rearLeftUnknownStreak = unknown
                rearLeftUnavailableStreak = unavailable
            }
            MbCanSeatSlot.RearRight -> {
                rearRightUnknownStreak = unknown
                rearRightUnavailableStreak = unavailable
            }
        }
    }

    companion object {
        fun decodeSteeringWheelHeatRaw(raw: Int): MbCanBinaryState = when (raw) {
            2 -> MbCanBinaryState.On
            1 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** Front seat heat + ventilation raw values (1 off, 2–4 heat, 5–7 vent). */
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

        /** Rear seat heating only ([MBVehicleProperty] 318 / 319): 1 off, 2–4 heat levels. */
        fun decodeRearSeatHeatRaw(raw: Int): MbCanSeatModeState = when (raw) {
            1 -> MbCanSeatModeState.Off
            2 -> MbCanSeatModeState.Heat(1)
            3 -> MbCanSeatModeState.Heat(2)
            4 -> MbCanSeatModeState.Heat(3)
            else -> MbCanSeatModeState.Unknown
        }
    }
}
