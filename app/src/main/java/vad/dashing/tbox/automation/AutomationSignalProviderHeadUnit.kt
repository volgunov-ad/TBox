package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vad.dashing.tbox.mbcan.FcwSensitivity
import vad.dashing.tbox.mbcan.FollowMeHomeMode
import vad.dashing.tbox.mbcan.HvacClimateCanRepository
import vad.dashing.tbox.mbcan.HvacCustomMode
import vad.dashing.tbox.mbcan.LdwSensitivity
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSeatModeState
import vad.dashing.tbox.mbcan.TrunkDoorRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.WiperStsDomain

internal fun headUnitFlowFor(signal: AutomationSignalId): Flow<AutomationSignalValue>? = when (signal) {
    AutomationSignalId.ENGINE_RPM -> UniversalCanRepository.engineRpmState.numberFlow()
    AutomationSignalId.CAR_SPEED -> UniversalCanRepository.carSpeedState.numberFlow()
    AutomationSignalId.ENGINE_TEMPERATURE -> UniversalCanRepository.engineTemperatureState.numberFlow()
    AutomationSignalId.OUTSIDE_TEMPERATURE -> UniversalCanRepository.outsideTemperatureState.numberFlow()
    AutomationSignalId.FUEL_LEVEL_PERCENT -> UniversalCanRepository.fuelLevelPercentState.uintNumberFlow()
    AutomationSignalId.ODOMETER_KM -> UniversalCanRepository.odometerKmState.uintNumberFlow()
    AutomationSignalId.CURRENT_FUEL_CONSUMPTION -> UniversalCanRepository.currentFuelConsumptionState.numberFlow()
    AutomationSignalId.DISTANCE_TO_EMPTY_KM -> UniversalCanRepository.distanceToFuelEmptyKmState.uintNumberFlow()
    AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM -> UniversalCanRepository.distanceToNextMaintenanceKmState.uintNumberFlow()
    AutomationSignalId.STEERING_ANGLE -> UniversalCanRepository.steerAngleState.numberFlow()
    AutomationSignalId.STEERING_SPEED -> UniversalCanRepository.steerSpeedState.numberFlow()
    AutomationSignalId.CRUISE_SET_SPEED -> UniversalCanRepository.accCruiseVSetDisKmh.numberFlow()
    AutomationSignalId.GEAR_MODE -> UniversalCanRepository.gearBoxModeState.map { value ->
        value?.trim()?.takeIf(String::isNotEmpty)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.ACC_STATUS -> UniversalCanRepository.accStatusState.map { value ->
        value?.trim()?.takeIf(String::isNotEmpty)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.GAS_PEDAL -> UniversalCanRepository.gasPedalPercentState.numberFlow()
    AutomationSignalId.BRAKE_PEDAL -> UniversalCanRepository.brakePedalPressedState.map {
        it?.let { pressed -> AutomationSignalValue.State(if (pressed) "on" else "off") }
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE -> UniversalCanRepository.wheelsPressureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel1)
    AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE -> UniversalCanRepository.wheelsPressureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel2)
    AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE -> UniversalCanRepository.wheelsPressureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel3)
    AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE -> UniversalCanRepository.wheelsPressureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel4)
    AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE -> UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel1)
    AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE -> UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel2)
    AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE -> UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel3)
    AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE -> UniversalCanRepository.wheelsTemperatureState.wheelNumberFlow(vad.dashing.tbox.Wheels::wheel4)
    AutomationSignalId.INSIDE_AIR_QUALITY -> UniversalCanRepository.insideAirQualityState.uintNumberFlow()
    AutomationSignalId.OUTSIDE_AIR_QUALITY -> UniversalCanRepository.outsideAirQualityState.uintNumberFlow()
    AutomationSignalId.STEERING_WHEEL_HEAT -> UniversalCanRepository.steeringWheelHeatState.binaryFlow()
    AutomationSignalId.WIPER_MAINTENANCE -> UniversalCanRepository.wiperMaintenanceState.binaryFlow()
    AutomationSignalId.WIPER_STS -> UniversalCanRepository.wiperOperatingModeState.map { mode ->
        mode?.let { AutomationSignalValue.State(WiperStsDomain.toAutomationState(it)) }
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.RAIN_DETECTED -> UniversalCanRepository.rainDetectedState.map {
        it?.let { detected -> AutomationSignalValue.State(if (detected) "on" else "off") }
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.SUNSHADE -> UniversalCanRepository.sunshadePositionState.shadeRoofFlow()
    AutomationSignalId.SUNROOF -> UniversalCanRepository.sunroofPositionState.shadeRoofFlow()
    AutomationSignalId.WINDOW_FRONT_LEFT -> UniversalCanRepository.windowFrontLeftState.windowPaneFlow()
    AutomationSignalId.WINDOW_FRONT_RIGHT -> UniversalCanRepository.windowFrontRightState.windowPaneFlow()
    AutomationSignalId.WINDOW_REAR_LEFT -> UniversalCanRepository.windowRearLeftState.windowPaneFlow()
    AutomationSignalId.WINDOW_REAR_RIGHT -> UniversalCanRepository.windowRearRightState.windowPaneFlow()
    AutomationSignalId.PARKING_RADAR -> UniversalCanRepository.parkingRadarState.binaryFlow()
    AutomationSignalId.REAR_FOG -> UniversalCanRepository.rearFogState.binaryFlow()
    AutomationSignalId.AVH -> UniversalCanRepository.avhState.binaryFlow()
    AutomationSignalId.HDC -> UniversalCanRepository.hdcState.binaryFlow()
    AutomationSignalId.ESP_OFF -> UniversalCanRepository.espOffState.binaryFlow()
    AutomationSignalId.TJA_ICA -> UniversalCanRepository.tjaIcaState.binaryFlow()
    AutomationSignalId.HMA -> UniversalCanRepository.hmaState.binaryFlow()
    AutomationSignalId.HVAC_AC_MAX -> UniversalCanRepository.hvacAcMaxState.binaryFlow()
    AutomationSignalId.HVAC_POWER -> UniversalCanRepository.hvacAcPowerState.binaryFlow()
    AutomationSignalId.HVAC_AUTO -> UniversalCanRepository.hvacAutoState.binaryFlow()
    AutomationSignalId.HVAC_RECIRCULATION -> UniversalCanRepository.hvacAirRecirculationState.binaryFlow()
    AutomationSignalId.HVAC_SYNC -> HvacClimateCanRepository.hvacSyncState.binaryFlow()
    AutomationSignalId.DRIVE_MODE -> UniversalCanRepository.carSettingsDriveMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::driveModeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.HEADLIGHT_MODE -> UniversalCanRepository.headlightModeRaw.map { raw ->
        raw?.let(AutomationSignalStateEncoding::headlightFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.REVERSE_GEAR -> UniversalCanRepository.reverseGearSwitchState.map {
        it?.let { engaged -> AutomationSignalValue.State(if (engaged) "on" else "off") }
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.FRONT_LEFT_SEAT_MODE -> UniversalCanRepository.frontLeftSeatModeState.seatModeFlow()
    AutomationSignalId.FRONT_RIGHT_SEAT_MODE -> UniversalCanRepository.frontRightSeatModeState.seatModeFlow()
    AutomationSignalId.REAR_LEFT_SEAT_MODE -> UniversalCanRepository.rearLeftSeatModeState.seatModeFlow()
    AutomationSignalId.REAR_RIGHT_SEAT_MODE -> UniversalCanRepository.rearRightSeatModeState.seatModeFlow()
    AutomationSignalId.DOOR_AUTO_LOCK -> UniversalCanRepository.autoLockState.binaryFlow()
    AutomationSignalId.DOOR_IGNOFF_UNLOCK -> UniversalCanRepository.autoUnlockState.binaryFlow()
    AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME -> UniversalCanRepository.followMeHomeMode.map { mode ->
        mode?.let {
            when (it) {
                FollowMeHomeMode.Sec30 -> "30s"
                FollowMeHomeMode.Sec60 -> "60s"
                FollowMeHomeMode.Off -> "off"
            }
        }?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.DRIVER_UNLOCK_MODE -> UniversalCanRepository.driverUnlockMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::driverUnlockFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.REMOTE_LOCK_FEEDBACK -> UniversalCanRepository.remoteLockFeedback.map { raw ->
        raw?.let(AutomationSignalStateEncoding::remoteLockFeedbackFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.WIPER_SENSITIVITY -> UniversalCanRepository.wiperSensitivity.numberFlow()
    AutomationSignalId.REAR_WIPER -> UniversalCanRepository.rearWiperState.binaryFlow()
    AutomationSignalId.MIRROR_AUTO_FOLD -> UniversalCanRepository.mirrorAutoFoldState.binaryFlow()
    AutomationSignalId.LOW_BEAM_HEIGHT -> UniversalCanRepository.lowBeamHeight.numberFlow()
    AutomationSignalId.TURN_FLASH_COUNT -> UniversalCanRepository.turnFlashCount.numberFlow()
    AutomationSignalId.LAS_MODE -> UniversalCanRepository.lasModeRaw.map { raw ->
        raw?.let(AutomationSignalStateEncoding::lasModeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.BLIND_SPOT_DETECTION -> UniversalCanRepository.bsdState.binaryFlow()
    AutomationSignalId.DOOR_OPEN_WARNING -> UniversalCanRepository.dowState.binaryFlow()
    AutomationSignalId.FCW -> UniversalCanRepository.fcwState.binaryFlow()
    AutomationSignalId.FCW_SENSITIVITY -> UniversalCanRepository.fcwSensitivity.map { value ->
        value?.let {
            when (it) {
                FcwSensitivity.Far -> "far"
                FcwSensitivity.Standard -> "standard"
                FcwSensitivity.Near -> "near"
            }
        }?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.LDW_SENSITIVITY -> UniversalCanRepository.ldwSensitivity.map { value ->
        value?.let {
            when (it) {
                LdwSensitivity.High -> "high"
                LdwSensitivity.Low -> "low"
            }
        }?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.HVAC_CUSTOM_MODE -> HvacClimateCanRepository.hvacCustomMode.map { mode ->
        mode?.let {
            when (it) {
                HvacCustomMode.Eco -> "eco"
                HvacCustomMode.Comfort -> "comfort"
                HvacCustomMode.Strong -> "strong"
            }
        }?.let(AutomationSignalValue::State) ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.FRONT_WINDSCREEN_HEAT -> UniversalCanRepository.hvacDefrosterFrontState.binaryFlow()
    AutomationSignalId.HVAC_REAR_DEFROSTER -> UniversalCanRepository.hvacDefrosterState.binaryFlow()
    AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED -> UniversalCanRepository.hvacAcCleanWhenLockedState.binaryFlow()
    AutomationSignalId.HVAC_ANION_PURIFY -> UniversalCanRepository.hvacAnionPurifyState.binaryFlow()
    AutomationSignalId.FRAGRANCE -> UniversalCanRepository.fragranceSwitchState.binaryFlow()
    AutomationSignalId.FRAGRANCE_SMELL -> UniversalCanRepository.fragranceSmell.map { raw ->
        raw?.let(AutomationSignalStateEncoding::fragranceSmellFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.FRAGRANCE_CONCENTRATION -> UniversalCanRepository.fragranceConcentration.map { raw ->
        raw?.let(AutomationSignalStateEncoding::fragranceConcentrationFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.HVAC_FIRST_BLOWING -> UniversalCanRepository.firstBlowingState.binaryFlow()
    AutomationSignalId.BT_REDUCE_FAN -> UniversalCanRepository.btReduceFanState.binaryFlow()
    AutomationSignalId.HVAC_AUTO_VENTILATION -> UniversalCanRepository.autoVentilationState.binaryFlow()
    AutomationSignalId.HVAC_FAN_DIRECTION -> HvacClimateCanRepository.hvacBlowMode.map { mode ->
        mode?.mbCanValue?.let(AutomationSignalStateEncoding::hvacFanDirectionFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.HVAC_TEMPERATURE_LEFT -> HvacClimateCanRepository.hvacTempLeftCelsius.numberFlow()
    AutomationSignalId.HVAC_TEMPERATURE_RIGHT -> HvacClimateCanRepository.hvacTempRightCelsius.numberFlow()
    AutomationSignalId.HVAC_FAN_SPEED -> HvacClimateCanRepository.hvacFanSpeed.numberFlow()
    AutomationSignalId.HVAC_FRONT_OFF -> HvacClimateCanRepository.hvacFrontOffState.binaryFlow()
    AutomationSignalId.HUD -> UniversalCanRepository.hudSwitchState.binaryFlow()
    AutomationSignalId.HUD_HEIGHT -> UniversalCanRepository.hudHeight.numberFlow()
    AutomationSignalId.HUD_BRIGHTNESS -> UniversalCanRepository.hudBrightness.numberFlow()
    AutomationSignalId.HUD_DISPLAY_MODE -> UniversalCanRepository.hudDisplayMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::hudDisplayModeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.HUD_AUTO_BRIGHTNESS -> UniversalCanRepository.hudAutoBrightnessState.binaryFlow()
    AutomationSignalId.ICM_BRIGHTNESS_MODE -> UniversalCanRepository.icmBrightnessMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::icmBrightnessModeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.ICM_BRIGHTNESS -> UniversalCanRepository.icmManualBrightness.numberFlow()
    AutomationSignalId.OVERSPEED_ALARM -> UniversalCanRepository.overspeedAlarmKmh.map { kmh ->
        kmh?.toDouble()?.let(AutomationSignalValue::Number) ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.STEERING_MODE -> UniversalCanRepository.carSettingsEpsMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::steeringFeelFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.EPS_MODE -> UniversalCanRepository.carSettingsEpsMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::steeringFeelFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.DRIVE_MODE_6DCT -> UniversalCanRepository.carSettingsDriveMode6dctWet.map { raw ->
        raw?.let(AutomationSignalStateEncoding::driveMode6dctFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.TSR_SWITCH -> UniversalCanRepository.slaOnOffState.map { state ->
        when (state) {
            MbCanBinaryState.On -> AutomationSignalValue.State("on")
            MbCanBinaryState.Off -> AutomationSignalValue.State("off")
            is MbCanBinaryState.Unavailable,
            MbCanBinaryState.Unknown,
            -> AutomationSignalValue.Unavailable
        }
    }
    AutomationSignalId.TRUNK_DOOR -> TrunkDoorRepository.displayState.map { display ->
        AutomationSignalStateEncoding.trunkDoorFromDisplay(display)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.AUDIO_VOLUME_SPEED_MODE -> UniversalCanRepository.audioVolumeSpeedModeState.map { raw ->
        raw?.let(AutomationSignalStateEncoding::audioVolumeSpeedFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.AUDIO_KEY_TONE_VOLUME -> UniversalCanRepository.audioKeyToneVolume.numberFlow()
    AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME -> UniversalCanRepository.audioRadarAlarmVolume.map { raw ->
        raw?.let(AutomationSignalStateEncoding::audioRadarVolumeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.AUDIO_EQ_MODE -> UniversalCanRepository.audioEqMode.map { raw ->
        raw?.let(AutomationSignalStateEncoding::audioEqModeFromRaw)?.let(AutomationSignalValue::State)
            ?: AutomationSignalValue.Unavailable
    }
    AutomationSignalId.AUDIO_EQ_BASS -> UniversalCanRepository.audioEqBass.numberFlow()
    AutomationSignalId.AUDIO_EQ_MIDDLE -> UniversalCanRepository.audioEqMiddle.numberFlow()
    AutomationSignalId.AUDIO_EQ_TREBLE -> UniversalCanRepository.audioEqTreble.numberFlow()
    AutomationSignalId.AUDIO_BALANCE -> UniversalCanRepository.audioBalance.numberFlow()
    AutomationSignalId.AUDIO_FADER -> UniversalCanRepository.audioFader.numberFlow()
    else -> null
}

internal fun huInterestForSignal(signal: AutomationSignalId): vad.dashing.tbox.mbcan.MbCanSignal? = when (signal) {
    AutomationSignalId.ENGINE_RPM -> vad.dashing.tbox.mbcan.MbCanSignal.EngineRpm
    AutomationSignalId.CAR_SPEED -> vad.dashing.tbox.mbcan.MbCanSignal.CarSpeed
    AutomationSignalId.ENGINE_TEMPERATURE -> vad.dashing.tbox.mbcan.MbCanSignal.EngineTemperature
    AutomationSignalId.OUTSIDE_TEMPERATURE -> vad.dashing.tbox.mbcan.MbCanSignal.OutsideTemperature
    AutomationSignalId.FUEL_LEVEL_PERCENT -> vad.dashing.tbox.mbcan.MbCanSignal.FuelLevel
    AutomationSignalId.ODOMETER_KM -> vad.dashing.tbox.mbcan.MbCanSignal.TotalOdometer
    AutomationSignalId.CURRENT_FUEL_CONSUMPTION -> vad.dashing.tbox.mbcan.MbCanSignal.CurrentFuelConsumption
    AutomationSignalId.DISTANCE_TO_EMPTY_KM -> vad.dashing.tbox.mbcan.MbCanSignal.DistanceToFuelEmpty
    AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM -> vad.dashing.tbox.mbcan.MbCanSignal.DistanceToNextMaintenance
    AutomationSignalId.STEERING_ANGLE,
    AutomationSignalId.STEERING_SPEED,
    -> vad.dashing.tbox.mbcan.MbCanSignal.SteeringAngle
    AutomationSignalId.CRUISE_SET_SPEED -> vad.dashing.tbox.mbcan.MbCanSignal.AccCruise
    AutomationSignalId.GEAR_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.VehicleGear
    AutomationSignalId.ACC_STATUS -> vad.dashing.tbox.mbcan.MbCanSignal.AccStatus
    AutomationSignalId.GAS_PEDAL -> vad.dashing.tbox.mbcan.MbCanSignal.GasPedal
    AutomationSignalId.BRAKE_PEDAL -> vad.dashing.tbox.mbcan.MbCanSignal.BrakePedal
    AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE,
    AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE,
    AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE,
    AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE,
    AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE,
    AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE,
    AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE,
    AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE,
    -> vad.dashing.tbox.mbcan.MbCanSignal.VehicleTires
    AutomationSignalId.INSIDE_AIR_QUALITY,
    AutomationSignalId.OUTSIDE_AIR_QUALITY,
    -> vad.dashing.tbox.mbcan.MbCanSignal.Pm25AirQuality
    AutomationSignalId.STEERING_WHEEL_HEAT -> vad.dashing.tbox.mbcan.MbCanSignal.SteeringWheelHeat
    AutomationSignalId.WIPER_MAINTENANCE -> vad.dashing.tbox.mbcan.MbCanSignal.WiperMaintenance
    AutomationSignalId.WIPER_STS -> vad.dashing.tbox.mbcan.MbCanSignal.WiperSts
    AutomationSignalId.RAIN_DETECTED -> vad.dashing.tbox.mbcan.MbCanSignal.RainDetected
    AutomationSignalId.SUNSHADE,
    AutomationSignalId.SUNROOF,
    AutomationSignalId.WINDOW_FRONT_LEFT,
    AutomationSignalId.WINDOW_FRONT_RIGHT,
    AutomationSignalId.WINDOW_REAR_LEFT,
    AutomationSignalId.WINDOW_REAR_RIGHT,
    -> vad.dashing.tbox.mbcan.MbCanSignal.BodyComfort
    AutomationSignalId.PARKING_RADAR -> vad.dashing.tbox.mbcan.MbCanSignal.ParkingRadar
    AutomationSignalId.REAR_FOG -> vad.dashing.tbox.mbcan.MbCanSignal.RearFogLight
    AutomationSignalId.AVH -> vad.dashing.tbox.mbcan.MbCanSignal.AvhSwitch
    AutomationSignalId.HDC -> vad.dashing.tbox.mbcan.MbCanSignal.HdcSwitch
    AutomationSignalId.ESP_OFF -> vad.dashing.tbox.mbcan.MbCanSignal.EspOffSwitch
    AutomationSignalId.TJA_ICA -> vad.dashing.tbox.mbcan.MbCanSignal.TjaIca
    AutomationSignalId.HMA -> vad.dashing.tbox.mbcan.MbCanSignal.HmaSwitch
    AutomationSignalId.HVAC_AC_MAX -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAcMax
    AutomationSignalId.HVAC_POWER -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAcPower
    AutomationSignalId.HVAC_AUTO -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAutoState
    AutomationSignalId.HVAC_RECIRCULATION -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAirRecirculation
    AutomationSignalId.HVAC_SYNC -> vad.dashing.tbox.mbcan.MbCanSignal.HvacSync
    AutomationSignalId.DRIVE_MODE,
    AutomationSignalId.DRIVE_MODE_6DCT,
    AutomationSignalId.STEERING_MODE,
    AutomationSignalId.EPS_MODE,
    AutomationSignalId.TSR_SWITCH,
    -> vad.dashing.tbox.mbcan.MbCanSignal.CarSettingsVehicleParams
    AutomationSignalId.HEADLIGHT_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.LightControl
    AutomationSignalId.REVERSE_GEAR -> vad.dashing.tbox.mbcan.MbCanSignal.ReverseGearSwitch
    AutomationSignalId.FRONT_LEFT_SEAT_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.FrontLeftSeatMode
    AutomationSignalId.FRONT_RIGHT_SEAT_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.FrontRightSeatMode
    AutomationSignalId.REAR_LEFT_SEAT_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.RearLeftSeatMode
    AutomationSignalId.REAR_RIGHT_SEAT_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.RearRightSeatMode
    AutomationSignalId.DOOR_AUTO_LOCK -> vad.dashing.tbox.mbcan.MbCanSignal.AutoLock
    AutomationSignalId.DOOR_IGNOFF_UNLOCK -> vad.dashing.tbox.mbcan.MbCanSignal.AutoUnlock
    AutomationSignalId.HEADLIGHTS_FOLLOW_ME_HOME -> vad.dashing.tbox.mbcan.MbCanSignal.FollowMeHome
    AutomationSignalId.DRIVER_UNLOCK_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.DriverUnlockMode
    AutomationSignalId.REMOTE_LOCK_FEEDBACK -> vad.dashing.tbox.mbcan.MbCanSignal.RemoteLockFeedback
    AutomationSignalId.WIPER_SENSITIVITY -> vad.dashing.tbox.mbcan.MbCanSignal.WiperSensitivity
    AutomationSignalId.REAR_WIPER -> vad.dashing.tbox.mbcan.MbCanSignal.RearWiper
    AutomationSignalId.MIRROR_AUTO_FOLD -> vad.dashing.tbox.mbcan.MbCanSignal.MirrorAutoFold
    AutomationSignalId.LOW_BEAM_HEIGHT -> vad.dashing.tbox.mbcan.MbCanSignal.LowBeamHeight
    AutomationSignalId.TURN_FLASH_COUNT -> vad.dashing.tbox.mbcan.MbCanSignal.TurnFlashCount
    AutomationSignalId.LAS_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.LasModeSelection
    AutomationSignalId.BLIND_SPOT_DETECTION -> vad.dashing.tbox.mbcan.MbCanSignal.Bsd
    AutomationSignalId.DOOR_OPEN_WARNING -> vad.dashing.tbox.mbcan.MbCanSignal.Dow
    AutomationSignalId.FCW -> vad.dashing.tbox.mbcan.MbCanSignal.Fcw
    AutomationSignalId.FCW_SENSITIVITY -> vad.dashing.tbox.mbcan.MbCanSignal.FcwSensitivity
    AutomationSignalId.LDW_SENSITIVITY -> vad.dashing.tbox.mbcan.MbCanSignal.LdwSensitivity
    AutomationSignalId.HVAC_CUSTOM_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.HvacCustomMode
    AutomationSignalId.FRONT_WINDSCREEN_HEAT -> vad.dashing.tbox.mbcan.MbCanSignal.FrontWindscreenHeat
    AutomationSignalId.HVAC_REAR_DEFROSTER -> vad.dashing.tbox.mbcan.MbCanSignal.HvacDefroster
    AutomationSignalId.HVAC_AC_CLEAN_WHEN_LOCKED -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAcCleanWhenLocked
    AutomationSignalId.HVAC_ANION_PURIFY -> vad.dashing.tbox.mbcan.MbCanSignal.HvacAnionPurify
    AutomationSignalId.FRAGRANCE -> vad.dashing.tbox.mbcan.MbCanSignal.FragranceSwitch
    AutomationSignalId.FRAGRANCE_SMELL -> vad.dashing.tbox.mbcan.MbCanSignal.FragranceSmell
    AutomationSignalId.FRAGRANCE_CONCENTRATION -> vad.dashing.tbox.mbcan.MbCanSignal.FragranceConcentration
    AutomationSignalId.HVAC_FIRST_BLOWING -> vad.dashing.tbox.mbcan.MbCanSignal.FirstBlowing
    AutomationSignalId.BT_REDUCE_FAN -> vad.dashing.tbox.mbcan.MbCanSignal.BtReduceFan
    AutomationSignalId.HVAC_AUTO_VENTILATION -> vad.dashing.tbox.mbcan.MbCanSignal.AutoVentilation
    AutomationSignalId.HVAC_FAN_DIRECTION -> vad.dashing.tbox.mbcan.MbCanSignal.HvacBlowMode
    AutomationSignalId.HVAC_TEMPERATURE_LEFT -> vad.dashing.tbox.mbcan.MbCanSignal.HvacTempLeft
    AutomationSignalId.HVAC_TEMPERATURE_RIGHT -> vad.dashing.tbox.mbcan.MbCanSignal.HvacTempRight
    AutomationSignalId.HVAC_FAN_SPEED -> vad.dashing.tbox.mbcan.MbCanSignal.HvacFanSpeed
    AutomationSignalId.HVAC_FRONT_OFF -> vad.dashing.tbox.mbcan.MbCanSignal.HvacFrontOff
    AutomationSignalId.HUD -> vad.dashing.tbox.mbcan.MbCanSignal.HudSwitch
    AutomationSignalId.HUD_HEIGHT -> vad.dashing.tbox.mbcan.MbCanSignal.HudHeight
    AutomationSignalId.HUD_BRIGHTNESS -> vad.dashing.tbox.mbcan.MbCanSignal.HudBrightness
    AutomationSignalId.HUD_DISPLAY_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.HudDisplayMode
    AutomationSignalId.HUD_AUTO_BRIGHTNESS -> vad.dashing.tbox.mbcan.MbCanSignal.HudAutoBrightness
    AutomationSignalId.ICM_BRIGHTNESS_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.IcmBrightnessMode
    AutomationSignalId.ICM_BRIGHTNESS -> vad.dashing.tbox.mbcan.MbCanSignal.IcmManualBrightness
    AutomationSignalId.OVERSPEED_ALARM -> vad.dashing.tbox.mbcan.MbCanSignal.OverspeedAlarm
    AutomationSignalId.TRUNK_DOOR -> vad.dashing.tbox.mbcan.MbCanSignal.TrunkDoor
    AutomationSignalId.AUDIO_VOLUME_SPEED_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.AudioVolumeSpeed
    AutomationSignalId.AUDIO_KEY_TONE_VOLUME -> vad.dashing.tbox.mbcan.MbCanSignal.AudioKeyToneVolume
    AutomationSignalId.AUDIO_RADAR_ALARM_VOLUME -> vad.dashing.tbox.mbcan.MbCanSignal.AudioRadarAlarmVolume
    AutomationSignalId.AUDIO_EQ_MODE -> vad.dashing.tbox.mbcan.MbCanSignal.AudioEqMode
    AutomationSignalId.AUDIO_EQ_BASS -> vad.dashing.tbox.mbcan.MbCanSignal.AudioEqBass
    AutomationSignalId.AUDIO_EQ_MIDDLE -> vad.dashing.tbox.mbcan.MbCanSignal.AudioEqMiddle
    AutomationSignalId.AUDIO_EQ_TREBLE -> vad.dashing.tbox.mbcan.MbCanSignal.AudioEqTreble
    AutomationSignalId.AUDIO_BALANCE -> vad.dashing.tbox.mbcan.MbCanSignal.AudioBalance
    AutomationSignalId.AUDIO_FADER -> vad.dashing.tbox.mbcan.MbCanSignal.AudioFader
    else -> null
}

private fun Flow<Float?>.numberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
            ?: AutomationSignalValue.Unavailable
    }

private fun Flow<Int?>.numberFlow(): Flow<AutomationSignalValue> =
    map { value ->
        value?.toDouble()?.let(AutomationSignalValue::Number) ?: AutomationSignalValue.Unavailable
    }
