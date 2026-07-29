package vad.dashing.tbox

const val MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY = "mediaVolumeWidgetHorizontal"
const val MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY = "mediaVolumeWidgetVertical"
const val ENGINE_RPM_WIDGET_DATA_KEY = "engineRPM"
const val ENGINE_TEMPERATURE_WIDGET_DATA_KEY = "engineTemperature"
const val CAR_SPEED_WIDGET_DATA_KEY = "carSpeed"
const val ODOMETER_WIDGET_DATA_KEY = "odometer"
const val FUEL_LEVEL_PERCENTAGE_WIDGET_DATA_KEY = "fuelLevelPercentage"
const val OUTSIDE_TEMPERATURE_WIDGET_DATA_KEY = "outsideTemperature"
const val WHEELS_PRESSURE_WIDGET_DATA_KEY = "wheelsPressureWidget"
const val WHEELS_PRESSURE_TEMPERATURE_WIDGET_DATA_KEY = "wheelsPressureTemperatureWidget"
const val WHEEL1_PRESSURE_WIDGET_DATA_KEY = "wheel1Pressure"
const val WHEEL2_PRESSURE_WIDGET_DATA_KEY = "wheel2Pressure"
const val WHEEL3_PRESSURE_WIDGET_DATA_KEY = "wheel3Pressure"
const val WHEEL4_PRESSURE_WIDGET_DATA_KEY = "wheel4Pressure"
const val WHEEL1_TEMPERATURE_WIDGET_DATA_KEY = "wheel1Temperature"
const val WHEEL2_TEMPERATURE_WIDGET_DATA_KEY = "wheel2Temperature"
const val WHEEL3_TEMPERATURE_WIDGET_DATA_KEY = "wheel3Temperature"
const val WHEEL4_TEMPERATURE_WIDGET_DATA_KEY = "wheel4Temperature"
const val WIPER_MAINTENANCE_WIDGET_DATA_KEY = "wiperMaintenanceWidget"
const val PARKING_RADAR_WIDGET_DATA_KEY = "parkingRadarWidget"

fun isMediaVolumeWidgetDataKey(dataKey: String): Boolean {
    return dataKey == MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalMediaVolumeEnabled(): Boolean {
    return isMediaVolumeWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isEngineRpmWidgetDataKey(dataKey: String): Boolean {
    return dataKey == ENGINE_RPM_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalEngineRpmEnabled(): Boolean {
    return isEngineRpmWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isEngineTemperatureWidgetDataKey(dataKey: String): Boolean {
    return dataKey == ENGINE_TEMPERATURE_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalEngineTemperatureEnabled(): Boolean {
    return isEngineTemperatureWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isCarSpeedWidgetDataKey(dataKey: String): Boolean {
    return dataKey == CAR_SPEED_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalCarSpeedEnabled(): Boolean {
    return isCarSpeedWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isOdometerWidgetDataKey(dataKey: String): Boolean {
    return dataKey == ODOMETER_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalOdometerEnabled(): Boolean {
    return isOdometerWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isFuelLevelPercentageWidgetDataKey(dataKey: String): Boolean {
    return dataKey == FUEL_LEVEL_PERCENTAGE_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalFuelLevelPercentageEnabled(): Boolean {
    return isFuelLevelPercentageWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isOutsideTemperatureWidgetDataKey(dataKey: String): Boolean {
    return dataKey == OUTSIDE_TEMPERATURE_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalOutsideTemperatureEnabled(): Boolean {
    return isOutsideTemperatureWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun isWheelsPressureWidgetDataKey(dataKey: String): Boolean {
    return dataKey == WHEELS_PRESSURE_WIDGET_DATA_KEY ||
        dataKey == WHEELS_PRESSURE_TEMPERATURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL1_PRESSURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL2_PRESSURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL3_PRESSURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL4_PRESSURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL1_TEMPERATURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL2_TEMPERATURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL3_TEMPERATURE_WIDGET_DATA_KEY ||
        dataKey == WHEEL4_TEMPERATURE_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalWheelsPressureEnabled(): Boolean {
    return isWheelsPressureWidgetDataKey(dataKey) && isMbCanVhalWidgetEnabled()
}

fun FloatingDashboardWidgetConfig.isMbCanVhalWidgetEnabled(): Boolean {
    return WidgetsRepository.supportsUseMbCanVhal(dataKey) && useMbCanVhal
}
