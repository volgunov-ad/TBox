package vad.dashing.tbox

const val MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY = "mediaVolumeWidgetHorizontal"
const val MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY = "mediaVolumeWidgetVertical"

fun isMediaVolumeWidgetDataKey(dataKey: String): Boolean {
    return dataKey == MEDIA_VOLUME_WIDGET_HORIZONTAL_DATA_KEY ||
        dataKey == MEDIA_VOLUME_WIDGET_VERTICAL_DATA_KEY
}
