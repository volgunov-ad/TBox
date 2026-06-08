package vad.dashing.tbox

const val MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY = "mediaVolumeWidgetHorizontal"
const val MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY = "mediaVolumeWidgetVertical"
const val ENGINE_RPM_WIDGET_DATA_KEY = "engineRPM"

fun isMediaVolumeWidgetDataKey(dataKey: String): Boolean {
    return dataKey == MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalMediaVolumeEnabled(): Boolean {
    return isMediaVolumeWidgetDataKey(dataKey) && useMbCanVhal
}

fun isEngineRpmWidgetDataKey(dataKey: String): Boolean {
    return dataKey == ENGINE_RPM_WIDGET_DATA_KEY
}

fun FloatingDashboardWidgetConfig.isMbCanVhalEngineRpmEnabled(): Boolean {
    return isEngineRpmWidgetDataKey(dataKey) && useMbCanVhal
}
