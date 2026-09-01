package vad.dashing.tbox

import androidx.annotation.StringRes

/**
 * Accordion sections for the widget-type picker in [vad.dashing.tbox.ui.WidgetSelectionDialogForm].
 * Explicit [dataKey] → section map (no name heuristics) so new widgets are not silently dropped.
 */
enum class WidgetTypeSectionId(@StringRes val titleRes: Int) {
    Telemetry(R.string.widget_type_section_telemetry),
    Trips(R.string.widget_type_section_trips),
    GeopositionNetwork(R.string.widget_type_section_geoposition_network),
    Climate(R.string.widget_type_section_climate),
    Chassis(R.string.widget_type_section_chassis),
    Body(R.string.widget_type_section_body),
    Audio(R.string.widget_type_section_audio),
    System(R.string.widget_type_section_system),
    Esp32(R.string.widget_type_section_esp32),
}

object WidgetTypeSections {
    private val dataKeyToSection: Map<String, WidgetTypeSectionId> = buildMap {
        fun putAll(section: WidgetTypeSectionId, vararg keys: String) {
            keys.forEach { key ->
                val prev = put(key, section)
                require(prev == null) { "Duplicate widget section mapping for dataKey=$key" }
            }
        }

        putAll(
            WidgetTypeSectionId.Telemetry,
            "voltage",
            "steerAngle",
            "steerSpeed",
            "engineRPM",
            "carSpeed",
            "carSpeedAccurate",
            "odometer",
            "distanceToNextMaintenance",
            "distanceToFuelEmpty",
            "fuelLevelPercentage",
            "fuelLevelPercentageFiltered",
            "fuelLevelLiters",
            "fuelLevelLitersActual",
            "currentFuelConsumption",
            AVERAGE_FUEL_CONSUMPTION_WIDGET_DATA_KEY,
            "breakingForce",
            GAS_BRAKE_WIDGET_DATA_KEY,
            "engineTemperature",
            "gearBoxOilTemperature",
            "gearBoxCurrentGear",
            "gearBoxPreparedGear",
            "gearBoxChangeGear",
            "gearBoxMode",
            "gearBoxDriveMode",
            "gearBoxWork",
            "outsideTemperature",
            "insideTemperature",
            "outsideAirQuality",
            "insideAirQuality",
            "voltage+engineTemperatureWidget",
            "gearBoxWidget",
            "gearBoxModeCurrentGear",
            "wheel1Pressure",
            "wheel2Pressure",
            "wheel3Pressure",
            "wheel4Pressure",
            "wheel1Temperature",
            "wheel2Temperature",
            "wheel3Temperature",
            "wheel4Temperature",
            "wheelsPressureWidget",
            "wheelsPressureTemperatureWidget",
            "tempInOutWidget",
            "fuelLevelWidget",
            "airQualityWidget",
        )

        putAll(
            WidgetTypeSectionId.Trips,
            "motorHours",
            "motorHoursTrip",
            "motorHoursWidget",
            "activeTripWidget",
            "activeTripWidgetSimple",
            "activeTripWidgetMini",
            ACTIVE_TRIP_WIDGET_CUSTOM_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.GeopositionNetwork,
            "gnssSpeed",
            "visibleSatellites",
            "longitude",
            "latitude",
            "altitude",
            "trueDirection",
            "locWidget",
            GEOPOSITION_DATA_WIDGET_DATA_KEY,
            ROAD_MATCH_MAP_WIDGET_DATA_KEY,
            MOCK_LOCATION_MODE_WIDGET_DATA_KEY,
            GNSS_DEBUG_WIDGET_DATA_KEY,
            OSM_SPEED_LIMIT_WIDGET_DATA_KEY,
            "netWidget",
            "netWidgetNew",
            "netWidgetColored",
        )

        putAll(
            WidgetTypeSectionId.Climate,
            "steeringWheelHeatWidget",
            "frontWindscreenHeatWidget",
            "rearWindowMirrorsDefrostWidget",
            "hvacAirRecirculationWidget",
            "hvacAcWidget",
            "hvacAcCleanWhenLockedWidget",
            "hvacAutoWidget",
            "hvacDefrosterFrontWidget",
            HVAC_SYNC_WIDGET_DATA_KEY,
            HVAC_FAN_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_FAN_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_LEFT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_TEMP_RIGHT_WIDGET_VERTICAL_DATA_KEY,
            HVAC_BLOW_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_HORIZONTAL_DATA_KEY,
            HVAC_BLOW_MODE_PANEL_WIDGET_VERTICAL_DATA_KEY,
            HVAC_CUSTOM_MODE_CYCLE_WIDGET_DATA_KEY,
            HVAC_AC_MAX_WIDGET_DATA_KEY,
            "frontLeftSeatHeatVentWidget",
            "frontRightSeatHeatVentWidget",
            FRONT_LEFT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            FRONT_RIGHT_SEAT_HEAT_VENT_SINGLE_WIDGET_DATA_KEY,
            REAR_LEFT_SEAT_HEAT_WIDGET_DATA_KEY,
            REAR_RIGHT_SEAT_HEAT_WIDGET_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.Chassis,
            DRIVE_MODE_WIDGET_DATA_KEY,
            DRIVE_MODE_CYCLE_WIDGET_DATA_KEY,
            REAR_FOG_WIDGET_DATA_KEY,
            HEADLIGHT_MODE_CYCLE_WIDGET_DATA_KEY,
            AVH_WIDGET_DATA_KEY,
            HDC_WIDGET_DATA_KEY,
            ESP_OFF_WIDGET_DATA_KEY,
            LDW_WIDGET_DATA_KEY,
            LKA_WIDGET_DATA_KEY,
            TJA_ICA_WIDGET_DATA_KEY,
            HMA_WIDGET_DATA_KEY,
            "cruiseSetSpeed",
            ACC_CRUISE_WIDGET_DATA_KEY,
            CRUISE_STATUS_WIDGET_DATA_KEY,
            SLA_SPEED_LIMIT_WIDGET_DATA_KEY,
            SPEED_LIMITER_WIDGET_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.Body,
            WIPER_MAINTENANCE_WIDGET_DATA_KEY,
            PARKING_RADAR_WIDGET_DATA_KEY,
            TRUNK_DOOR_WIDGET_DATA_KEY,
            MIRROR_ADJUST_MODE_WIDGET_DATA_KEY,
            MIRROR_FOLD_WIDGET_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.Audio,
            MUSIC_WIDGET_DATA_KEY,
            MUSIC_COVER_WIDGET_DATA_KEY,
            MUSIC_SQUARE_WIDGET_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_HORIZONTAL_DATA_KEY,
            MUSIC_BUTTONS_WIDGET_VERTICAL_DATA_KEY,
            MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY,
            MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.System,
            "timeWidget",
            "dateWidget",
            CPU_USAGE_WIDGET_DATA_KEY,
            FREE_RAM_PERCENT_WIDGET_DATA_KEY,
            DAY_NIGHT_THEME_WIDGET_DATA_KEY,
            APP_LAUNCHER_WIDGET_DATA_KEY,
            HTTP_REQUEST_WIDGET_DATA_KEY,
            EMPTY_TILE_WIDGET_DATA_KEY,
            "restartTbox",
            WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY,
            HIDE_FLOATING_PANELS_WIDGET_DATA_KEY,
            TOGGLE_FLOATING_PANELS_ENABLED_WIDGET_DATA_KEY,
        )

        putAll(
            WidgetTypeSectionId.Esp32,
            "espConnected",
            "espGpioIn0",
            "espGpioIn1",
            "espGpioIn2",
            "espGpioIn3",
            "espRelay0",
            "espRelay1",
        )
    }

    fun sectionFor(dataKey: String): WidgetTypeSectionId? {
        if (dataKey.isEmpty()) return null
        return dataKeyToSection[dataKey]
    }

    fun requireSectionFor(dataKey: String): WidgetTypeSectionId {
        return requireNotNull(sectionFor(dataKey)) {
            "No widget type section mapping for dataKey=$dataKey"
        }
    }

    /** Keys present in the section map (for completeness tests). */
    fun mappedDataKeys(): Set<String> = dataKeyToSection.keys
}
