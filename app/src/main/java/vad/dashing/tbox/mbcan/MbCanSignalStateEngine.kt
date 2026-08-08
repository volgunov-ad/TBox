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
    private val wiperMaintenanceFlow: MutableStateFlow<MbCanBinaryState>,
    private val parkingRadarFlow: MutableStateFlow<MbCanBinaryState>,
    private val windshieldHeatFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacDefrosterFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAirRecirculationFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAcPowerFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAcCleanWhenLockedFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacAutoStateFlow: MutableStateFlow<MbCanBinaryState>,
    private val hvacDefrosterFrontFlow: MutableStateFlow<MbCanBinaryState>,
    private val wirelessChargingFlow: MutableStateFlow<MbCanBinaryState>,
    private val volumeSpeedFlow: MutableStateFlow<MbCanBinaryState>,
    private val frontLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val frontRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearLeftSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val rearRightSeatFlow: MutableStateFlow<MbCanSeatModeState>,
    private val requiredConsecutiveProblems: Int = 3,
    /**
     * Invoked when published state transitions into a problem streak.
     * A9: [MbCanJobManager.requestBurst]; A10 VHAL: [Android10VhalRepository] burst polling.
     */
    private val onBurstRequested: suspend (MbCanSignal) -> Unit = { MbCanJobManager.requestBurst(it) },
) {
    private var steeringUnknownStreak = 0
    private var steeringUnavailableStreak = 0
    private var wiperMaintenanceUnknownStreak = 0
    private var wiperMaintenanceUnavailableStreak = 0
    private var parkingRadarUnknownStreak = 0
    private var parkingRadarUnavailableStreak = 0
    private var windshieldUnknownStreak = 0
    private var windshieldUnavailableStreak = 0
    private var hvacDefrosterUnknownStreak = 0
    private var hvacDefrosterUnavailableStreak = 0
    private var hvacAirRecirculationUnknownStreak = 0
    private var hvacAirRecirculationUnavailableStreak = 0
    private var hvacAcPowerUnknownStreak = 0
    private var hvacAcPowerUnavailableStreak = 0
    private var hvacAcCleanWhenLockedUnknownStreak = 0
    private var hvacAcCleanWhenLockedUnavailableStreak = 0
    private var hvacAutoStateUnknownStreak = 0
    private var hvacAutoStateUnavailableStreak = 0
    private var hvacDefrosterFrontUnknownStreak = 0
    private var hvacDefrosterFrontUnavailableStreak = 0
    private var wirelessChargingUnknownStreak = 0
    private var wirelessChargingUnavailableStreak = 0
    private var volumeSpeedUnknownStreak = 0
    private var volumeSpeedUnavailableStreak = 0
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
            onBurstRequested(MbCanSignal.SteeringWheelHeat)
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

    suspend fun applyWiperMaintenanceCandidate(decoded: MbCanBinaryState) {
        val published = wiperMaintenanceFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.WiperMaintenance)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                wiperMaintenanceUnknownStreak += 1
                wiperMaintenanceUnavailableStreak = 0
                if (wiperMaintenanceUnknownStreak >= requiredConsecutiveProblems) {
                    wiperMaintenanceFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                wiperMaintenanceUnavailableStreak += 1
                wiperMaintenanceUnknownStreak = 0
                if (wiperMaintenanceUnavailableStreak >= requiredConsecutiveProblems) {
                    wiperMaintenanceFlow.value = decoded
                }
            }
            else -> {
                wiperMaintenanceUnknownStreak = 0
                wiperMaintenanceUnavailableStreak = 0
                wiperMaintenanceFlow.value = decoded
            }
        }
    }

    suspend fun applyParkingRadarCandidate(decoded: MbCanBinaryState) {
        val published = parkingRadarFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.ParkingRadar)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                parkingRadarUnknownStreak += 1
                parkingRadarUnavailableStreak = 0
                if (parkingRadarUnknownStreak >= requiredConsecutiveProblems) {
                    parkingRadarFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                parkingRadarUnavailableStreak += 1
                parkingRadarUnknownStreak = 0
                if (parkingRadarUnavailableStreak >= requiredConsecutiveProblems) {
                    parkingRadarFlow.value = decoded
                }
            }
            else -> {
                parkingRadarUnknownStreak = 0
                parkingRadarUnavailableStreak = 0
                parkingRadarFlow.value = decoded
            }
        }
    }

    suspend fun applyWindshieldHeatCandidate(decoded: MbCanBinaryState) {
        val published = windshieldHeatFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.FrontWindscreenHeat)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                windshieldUnknownStreak += 1
                windshieldUnavailableStreak = 0
                if (windshieldUnknownStreak >= requiredConsecutiveProblems) {
                    windshieldHeatFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                windshieldUnavailableStreak += 1
                windshieldUnknownStreak = 0
                if (windshieldUnavailableStreak >= requiredConsecutiveProblems) {
                    windshieldHeatFlow.value = decoded
                }
            }
            else -> {
                windshieldUnknownStreak = 0
                windshieldUnavailableStreak = 0
                windshieldHeatFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacDefrosterCandidate(decoded: MbCanBinaryState) {
        val published = hvacDefrosterFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacDefroster)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacDefrosterUnknownStreak += 1
                hvacDefrosterUnavailableStreak = 0
                if (hvacDefrosterUnknownStreak >= requiredConsecutiveProblems) {
                    hvacDefrosterFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacDefrosterUnavailableStreak += 1
                hvacDefrosterUnknownStreak = 0
                if (hvacDefrosterUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacDefrosterFlow.value = decoded
                }
            }
            else -> {
                hvacDefrosterUnknownStreak = 0
                hvacDefrosterUnavailableStreak = 0
                hvacDefrosterFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacAirRecirculationCandidate(decoded: MbCanBinaryState) {
        val published = hvacAirRecirculationFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacAirRecirculation)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacAirRecirculationUnknownStreak += 1
                hvacAirRecirculationUnavailableStreak = 0
                if (hvacAirRecirculationUnknownStreak >= requiredConsecutiveProblems) {
                    hvacAirRecirculationFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacAirRecirculationUnavailableStreak += 1
                hvacAirRecirculationUnknownStreak = 0
                if (hvacAirRecirculationUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacAirRecirculationFlow.value = decoded
                }
            }
            else -> {
                hvacAirRecirculationUnknownStreak = 0
                hvacAirRecirculationUnavailableStreak = 0
                hvacAirRecirculationFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacAcPowerCandidate(decoded: MbCanBinaryState) {
        val published = hvacAcPowerFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacAcPower)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacAcPowerUnknownStreak += 1
                hvacAcPowerUnavailableStreak = 0
                if (hvacAcPowerUnknownStreak >= requiredConsecutiveProblems) {
                    hvacAcPowerFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacAcPowerUnavailableStreak += 1
                hvacAcPowerUnknownStreak = 0
                if (hvacAcPowerUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacAcPowerFlow.value = decoded
                }
            }
            else -> {
                hvacAcPowerUnknownStreak = 0
                hvacAcPowerUnavailableStreak = 0
                hvacAcPowerFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacAcCleanWhenLockedCandidate(decoded: MbCanBinaryState) {
        val published = hvacAcCleanWhenLockedFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacAcCleanWhenLocked)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacAcCleanWhenLockedUnknownStreak += 1
                hvacAcCleanWhenLockedUnavailableStreak = 0
                if (hvacAcCleanWhenLockedUnknownStreak >= requiredConsecutiveProblems) {
                    hvacAcCleanWhenLockedFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacAcCleanWhenLockedUnavailableStreak += 1
                hvacAcCleanWhenLockedUnknownStreak = 0
                if (hvacAcCleanWhenLockedUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacAcCleanWhenLockedFlow.value = decoded
                }
            }
            else -> {
                hvacAcCleanWhenLockedUnknownStreak = 0
                hvacAcCleanWhenLockedUnavailableStreak = 0
                hvacAcCleanWhenLockedFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacAutoStateCandidate(decoded: MbCanBinaryState) {
        val published = hvacAutoStateFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacAutoState)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacAutoStateUnknownStreak += 1
                hvacAutoStateUnavailableStreak = 0
                if (hvacAutoStateUnknownStreak >= requiredConsecutiveProblems) {
                    hvacAutoStateFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacAutoStateUnavailableStreak += 1
                hvacAutoStateUnknownStreak = 0
                if (hvacAutoStateUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacAutoStateFlow.value = decoded
                }
            }
            else -> {
                hvacAutoStateUnknownStreak = 0
                hvacAutoStateUnavailableStreak = 0
                hvacAutoStateFlow.value = decoded
            }
        }
    }

    suspend fun applyHvacDefrosterFrontCandidate(decoded: MbCanBinaryState) {
        val published = hvacDefrosterFrontFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.HvacDefrosterFront)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                hvacDefrosterFrontUnknownStreak += 1
                hvacDefrosterFrontUnavailableStreak = 0
                if (hvacDefrosterFrontUnknownStreak >= requiredConsecutiveProblems) {
                    hvacDefrosterFrontFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                hvacDefrosterFrontUnavailableStreak += 1
                hvacDefrosterFrontUnknownStreak = 0
                if (hvacDefrosterFrontUnavailableStreak >= requiredConsecutiveProblems) {
                    hvacDefrosterFrontFlow.value = decoded
                }
            }
            else -> {
                hvacDefrosterFrontUnknownStreak = 0
                hvacDefrosterFrontUnavailableStreak = 0
                hvacDefrosterFrontFlow.value = decoded
            }
        }
    }

    suspend fun applyWirelessChargingCandidate(decoded: MbCanBinaryState) {
        val published = wirelessChargingFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.WirelessChargingSwitch)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                wirelessChargingUnknownStreak += 1
                wirelessChargingUnavailableStreak = 0
                if (wirelessChargingUnknownStreak >= requiredConsecutiveProblems) {
                    wirelessChargingFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                wirelessChargingUnavailableStreak += 1
                wirelessChargingUnknownStreak = 0
                if (wirelessChargingUnavailableStreak >= requiredConsecutiveProblems) {
                    wirelessChargingFlow.value = decoded
                }
            }
            else -> {
                wirelessChargingUnknownStreak = 0
                wirelessChargingUnavailableStreak = 0
                wirelessChargingFlow.value = decoded
            }
        }
    }

    suspend fun applyVolumeSpeedCandidate(decoded: MbCanBinaryState) {
        val published = volumeSpeedFlow.value
        if (decoded.isProblemState() && !published.isProblemState()) {
            onBurstRequested(MbCanSignal.AudioVolumeSpeed)
        }
        when (decoded) {
            is MbCanBinaryState.Unknown -> {
                volumeSpeedUnknownStreak += 1
                volumeSpeedUnavailableStreak = 0
                if (volumeSpeedUnknownStreak >= requiredConsecutiveProblems) {
                    volumeSpeedFlow.value = MbCanBinaryState.Unknown
                }
            }
            is MbCanBinaryState.Unavailable -> {
                volumeSpeedUnavailableStreak += 1
                volumeSpeedUnknownStreak = 0
                if (volumeSpeedUnavailableStreak >= requiredConsecutiveProblems) {
                    volumeSpeedFlow.value = decoded
                }
            }
            else -> {
                volumeSpeedUnknownStreak = 0
                volumeSpeedUnavailableStreak = 0
                volumeSpeedFlow.value = decoded
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
            onBurstRequested(slot.signal)
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

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVHEICEL_FRONTWINDSCREEN_HEAT] — same on/off encoding as steering heat. */
        fun decodeFrontWindscreenHeatRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_PROPERTY_HVAC_DEFROSTER] — rear window + mirrors; same 1/2 as steering if used as binary. */
        fun decodeHvacDefrosterRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** Same raw values as [MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON]/[_OFF] and [MbCanCommandRegistry] toggle. */
        fun decodeHvacAirRecirculationRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** Same raw values as steering heat: 1 off, 2 on (mbCAN / write side). */
        fun decodeHvacAcPowerRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** mbCAN [HVAC_BLOWER_DELAY]: 1 off, 2 on (stock ACSettings MBWTSwitch). */
        fun decodeHvacBlowerDelayMbCanRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** Same raw values as steering heat: 1 off, 2 on (mbCAN / write side). */
        fun decodeHvacAutoStateRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

        /** mbCAN [HVAC_FAN_DIRECTION]: 4/5 = front defrost blow on. */
        fun decodeHvacFrontDefrostMbCanRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE_FOOT -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** VHAL [FrontBlowModeSts]: mode 4 = front defrost blow on. */
        fun decodeHvacFrontDefrostVhalRaw(raw: Int): MbCanBinaryState = when (raw) {
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST -> MbCanBinaryState.On
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_FACE,
            MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST_FOOT,
            1, 2 -> MbCanBinaryState.Off
            else -> MbCanBinaryState.Unknown
        }

        /** [MBFrontDefrostingView.getValue] toggle target for mbCAN. */
        fun resolveHvacFrontDefrostMbCanToggleTarget(currentRaw: Int): Int {
            val active = decodeHvacFrontDefrostMbCanRaw(currentRaw) is MbCanBinaryState.On
            return if (active) {
                if (currentRaw == MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT) {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT
                } else {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FACE
                }
            } else {
                if (currentRaw == MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_FOOT) {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST_FOOT
                } else {
                    MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_DEFROST
                }
            }
        }

        /** Toggle target for VHAL [ModeAdjust_Req]: 4 on, 0 off (face). */
        fun resolveHvacFrontDefrostVhalToggleTarget(currentRaw: Int): Int =
            if (decodeHvacFrontDefrostVhalRaw(currentRaw) is MbCanBinaryState.On) {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_FACE
            } else {
                MbCanKnownVehiclePropertyId.HVAC_FAN_DIRECTION_VHAL_DEFROST
            }

        /** [com.mengbo.mbCan.defines.MBVehicleProperty.eVEHICLE_CHG_WIRELESS_SWITCH] — 1 off, 2 on. */
        fun decodeWirelessChargingRaw(raw: Int): MbCanBinaryState = decodeSteeringWheelHeatRaw(raw)

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

        /** [com.mengbo.mbCan.defines.MBAudioProperty.eAUDIO_PROPERTY_VOLUME_SPEED] — 1 off, 2..4 on. */
        fun decodeVolumeSpeedRaw(raw: Int): MbCanBinaryState = when (raw) {
            1 -> MbCanBinaryState.Off
            2, 3, 4 -> MbCanBinaryState.On
            else -> MbCanBinaryState.Unknown
        }
    }
}
