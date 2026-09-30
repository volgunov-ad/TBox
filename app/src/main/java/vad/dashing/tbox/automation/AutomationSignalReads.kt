package vad.dashing.tbox.automation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.CarDataRepository
import vad.dashing.tbox.ForegroundAppMonitor
import vad.dashing.tbox.HeadUnitBrightnessRepository
import vad.dashing.tbox.HeadUnitDayNightRepository
import vad.dashing.tbox.MediaPlayerState
import vad.dashing.tbox.PlatformAudioDomain
import vad.dashing.tbox.PlatformAudioRepository
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.TripTelemetryRepository
import vad.dashing.tbox.Wheels
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.LocIndicatorState
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.trip.TripRepository

internal object AutomationSignalReads {
    fun flowFor(key: AutomationSignalKey): Flow<AutomationSignalValue>? =
        when (key.source) {
            AutomationSignalSource.TBOX -> tboxFlow(key.signal)?.withAvailability(
                TboxRepository.tboxConnected,
            )

            AutomationSignalSource.HEAD_UNIT -> headUnitFlowFor(key.signal)?.withAvailability(
                UniversalCanRepository.availability.map { it is MbCanAvailability.Available },
            )

            AutomationSignalSource.APP -> when (key.signal) {
                AutomationSignalId.GEO_POSITION -> geoDisplayFlow()
                AutomationSignalId.ESP_GPIO_IN_0 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 0)
                AutomationSignalId.ESP_GPIO_IN_1 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 1)
                AutomationSignalId.ESP_GPIO_IN_2 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 2)
                AutomationSignalId.ESP_GPIO_IN_3 -> espMaskBitFlow(EspCompanionRepository.gpioMask, 3)
                AutomationSignalId.ESP_RELAY_0 -> espMaskBitFlow(EspCompanionRepository.relayMask, 0)
                AutomationSignalId.ESP_RELAY_1 -> espMaskBitFlow(EspCompanionRepository.relayMask, 1)
                AutomationSignalId.ESP_BLE_BOUND -> espBleBoundFlow()
                AutomationSignalId.ESP_BLE_BATTERY -> {
                    val mac = key.mac?.trim().orEmpty()
                    if (mac.isEmpty()) {
                        kotlinx.coroutines.flow.flowOf(AutomationSignalValue.Unavailable)
                    } else {
                        espBleBatteryFlow(mac)
                    }
                }
                AutomationSignalId.WIFI_ENABLED -> wifiSnapshotFlow().map { snap ->
                    AutomationSignalValue.State(snap.radioState())
                }.distinctUntilChanged()
                AutomationSignalId.WIFI_ASSOCIATED -> wifiSnapshotFlow().map { snap ->
                    AutomationSignalValue.State(snap.associatedState())
                }.distinctUntilChanged()
                AutomationSignalId.WIFI_SSID -> wifiSnapshotFlow().map { snap ->
                    AutomationSignalValue.State(snap.ssidState())
                }.distinctUntilChanged()
                AutomationSignalId.HU_INTERNET_STATUS ->
                    TboxRepository.huInternetStatus.map { status ->
                        AutomationSignalValue.State(
                            vad.dashing.tbox.internet.HuInternetStatusLogic.automationStateKey(status),
                        )
                    }.distinctUntilChanged()
                AutomationSignalId.WIFI_MODEM_LINK_STATUS ->
                    TboxRepository.wifiModemLinkStatus.map { status ->
                        AutomationSignalValue.State(
                            vad.dashing.tbox.wifimodem.ModemAutomationStates.linkStatusKey(status),
                        )
                    }.distinctUntilChanged()
                AutomationSignalId.MODEM_MOBILE_DATA ->
                    TboxRepository.apnStatus.map { up ->
                        AutomationSignalValue.State(
                            vad.dashing.tbox.wifimodem.ModemAutomationStates.mobileDataKey(up),
                        )
                    }.distinctUntilChanged()
                AutomationSignalId.MODEM_NET_TYPE ->
                    TboxRepository.netState.map { net ->
                        AutomationSignalValue.State(
                            vad.dashing.tbox.wifimodem.ModemAutomationStates.netTypeKey(net.netStatus),
                        )
                    }.distinctUntilChanged()
                AutomationSignalId.MODEM_SIM_STATUS ->
                    TboxRepository.netState.map { net ->
                        AutomationSignalValue.State(
                            vad.dashing.tbox.wifimodem.ModemAutomationStates.simStatusKey(net.simStatus),
                        )
                    }.distinctUntilChanged()
                AutomationSignalId.APP_THEME_MODE -> appThemeModeFlow()
                AutomationSignalId.APP_THEME -> appThemeEffectiveFlow()
                AutomationSignalId.HU_SCREEN_BRIGHTNESS -> huScreenBrightnessFlow()
                AutomationSignalId.HU_SCREEN_AUTO_BRIGHTNESS -> huScreenAutoBrightnessFlow()
                AutomationSignalId.HU_MEDIA_VOLUME -> platformVolumeFlow(
                    PlatformAudioRepository.mediaVolume,
                )
                AutomationSignalId.HU_PHONE_VOLUME -> platformVolumeFlow(
                    PlatformAudioRepository.phoneVolume,
                )
                AutomationSignalId.HU_NAVI_VOLUME -> platformVolumeFlow(
                    PlatformAudioRepository.naviVolume,
                )
                AutomationSignalId.HU_VOICE_VOLUME -> platformVolumeFlow(
                    PlatformAudioRepository.voiceVolume,
                )
                AutomationSignalId.HU_HEADREST_SPEAKER -> platformHeadrestFlow()
                AutomationSignalId.FOREGROUND_APP -> foregroundAppFlow()
                AutomationSignalId.TBOX_CONNECTED ->
                    TboxRepository.tboxConnected.map { connected ->
                        AutomationSignalValue.State(if (connected) "on" else "off")
                    }.distinctUntilChanged()
                AutomationSignalId.MODEM_SIGNAL_LEVEL ->
                    TboxRepository.netState.map { net ->
                        AutomationSignalValue.Number(net.signalLevel.toDouble())
                    }.distinctUntilChanged()
                AutomationSignalId.LOCATE_STATUS ->
                    GeoDisplayRepository.state.map { state ->
                        AutomationSignalValue.State(if (state.locateStatus) "on" else "off")
                    }.distinctUntilChanged()
                AutomationSignalId.FUEL_LEVEL_PERCENT_FILTERED ->
                    TripTelemetryRepository.fuelLevelPercentageFiltered.uintNumberFlow()
                AutomationSignalId.FUEL_LEVEL_LITERS ->
                    TripTelemetryRepository.fuelLevelCalibratedLiters.numberFlow()
                AutomationSignalId.ACTIVE_TRIP_DISTANCE_KM -> activeTripDistanceFlow()
                AutomationSignalId.ACTIVE_TRIP_AVG_FUEL_L100KM -> activeTripAvgFuelFlow()
                AutomationSignalId.ACTIVE_TRIP_DURATION_S -> activeTripDurationFlow()
                AutomationSignalId.ACTIVE_TRIP_MOTOR_HOURS -> activeTripMotorHoursFlow()
                AutomationSignalId.MOTOR_HOURS ->
                    CarDataRepository.motorHours.map { hours ->
                        hours.toDouble().takeIf(Double::isFinite)?.let(AutomationSignalValue::Number)
                            ?: AutomationSignalValue.Unavailable
                    }.distinctUntilChanged()
                AutomationSignalId.MEDIA_TITLE -> mediaNowPlayingFlow { it.track }
                AutomationSignalId.MEDIA_ARTIST -> mediaNowPlayingFlow { it.artist }
                else -> null
            }
        }

    private fun tboxFlow(signal: AutomationSignalId): Flow<AutomationSignalValue>? = when (signal) {
        AutomationSignalId.ENGINE_RPM -> CanDataRepository.engineRPM.numberFlow()
        AutomationSignalId.CAR_SPEED -> CanDataRepository.carSpeed.numberFlow()
        AutomationSignalId.ENGINE_TEMPERATURE -> CanDataRepository.engineTemperature.numberFlow()
        AutomationSignalId.OUTSIDE_TEMPERATURE -> CanDataRepository.outsideTemperature.numberFlow()
        AutomationSignalId.INSIDE_TEMPERATURE -> CanDataRepository.insideTemperature.numberFlow()
        AutomationSignalId.FUEL_LEVEL_PERCENT -> CanDataRepository.fuelLevelPercentage.uintNumberFlow()
        AutomationSignalId.ODOMETER_KM -> CanDataRepository.odometer.uintNumberFlow()
        AutomationSignalId.CURRENT_FUEL_CONSUMPTION ->
            CanDataRepository.currentFuelConsumption.numberFlow()

        AutomationSignalId.DISTANCE_TO_EMPTY_KM -> CanDataRepository.distanceToFuelEmpty.uintNumberFlow()
        AutomationSignalId.DISTANCE_TO_MAINTENANCE_KM ->
            CanDataRepository.distanceToNextMaintenance.uintNumberFlow()

        AutomationSignalId.VOLTAGE -> CanDataRepository.voltage.numberFlow()
        AutomationSignalId.STEERING_ANGLE -> CanDataRepository.steerAngle.numberFlow()
        AutomationSignalId.STEERING_SPEED -> CanDataRepository.steerSpeed.numberFlow()
        AutomationSignalId.CRUISE_SET_SPEED -> CanDataRepository.cruiseSetSpeed.uintNumberFlow()
        AutomationSignalId.GEAR_MODE -> CanDataRepository.gearBoxMode.map { value ->
            value.trim().takeIf(String::isNotEmpty)?.let(AutomationSignalValue::State)
                ?: AutomationSignalValue.Unavailable
        }

        AutomationSignalId.CURRENT_GEAR -> CanDataRepository.gearBoxCurrentGear.numberFlow()
        AutomationSignalId.TARGET_GEAR -> CanDataRepository.gearBoxPreparedGear.numberFlow()
        AutomationSignalId.FRONT_LEFT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_PRESSURE ->
            CanDataRepository.wheelsPressure.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.FRONT_LEFT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel1)

        AutomationSignalId.FRONT_RIGHT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel2)

        AutomationSignalId.REAR_LEFT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel3)

        AutomationSignalId.REAR_RIGHT_WHEEL_TEMPERATURE ->
            CanDataRepository.wheelsTemperature.wheelNumberFlow(Wheels::wheel4)

        AutomationSignalId.INSIDE_AIR_QUALITY -> CanDataRepository.insideAirQuality.uintNumberFlow()
        AutomationSignalId.OUTSIDE_AIR_QUALITY -> CanDataRepository.outsideAirQuality.uintNumberFlow()
        AutomationSignalId.GEAR_BOX_OIL_TEMPERATURE -> CanDataRepository.gearBoxOilTemperature.numberFlow()
        else -> null
    }
}
