package vad.dashing.tbox

/** Playback control row height for full music widgets (percent of tile height). */
object MusicWidgetControlsDisplay {
    const val MIN_CONTROLS_HEIGHT_PERCENT = 5
    const val MAX_CONTROLS_HEIGHT_PERCENT = 50
    const val DEFAULT_STANDARD_CONTROLS_HEIGHT_PERCENT = 35
    const val DEFAULT_COVER_CONTROLS_HEIGHT_PERCENT = 15

    fun defaultControlsHeightPercent(dataKey: String): Int =
        if (dataKey == MUSIC_COVER_WIDGET_DATA_KEY) {
            DEFAULT_COVER_CONTROLS_HEIGHT_PERCENT
        } else {
            DEFAULT_STANDARD_CONTROLS_HEIGHT_PERCENT
        }

    fun normalizeControlsHeightPercent(raw: Int): Int =
        raw.coerceIn(MIN_CONTROLS_HEIGHT_PERCENT, MAX_CONTROLS_HEIGHT_PERCENT)

    fun resolveControlsHeightPercent(dataKey: String, raw: Int?): Int =
        normalizeControlsHeightPercent(raw ?: defaultControlsHeightPercent(dataKey))
}
