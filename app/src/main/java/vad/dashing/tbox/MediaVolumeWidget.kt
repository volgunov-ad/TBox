package vad.dashing.tbox

const val MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY = "mediaVolumeWidgetHorizontal"
const val MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY = "mediaVolumeWidgetVertical"
const val ENGINE_RPM_WIDGET_DATA_KEY = "engineRPM"
const val ENGINE_TEMPERATURE_WIDGET_DATA_KEY = "engineTemperature"
const val CAR_SPEED_WIDGET_DATA_KEY = "carSpeed"
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

fun FloatingDashboardWidgetConfig.isMbCanVhalWidgetEnabled(): Boolean {
    return WidgetsRepository.supportsUseMbCanVhal(dataKey) && useMbCanVhal
}
