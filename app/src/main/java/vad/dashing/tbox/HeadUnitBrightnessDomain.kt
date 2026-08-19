package vad.dashing.tbox

/** Framework-independent conversion between stock HU backlight and UI levels. */
object HeadUnitBrightnessDomain {
    const val MIN_RAW = 10
    const val MAX_RAW = 100
    const val DEFAULT_RAW = 80

    fun decodeUiLevel(raw: Int?): Int? = raw
        ?.coerceIn(MIN_RAW, MAX_RAW)
        ?.let { ((it + 5) / 10).coerceIn(1, 10) }

    fun encodeRawLevel(level: Int): Int = level.coerceIn(1, 10) * 10
}
