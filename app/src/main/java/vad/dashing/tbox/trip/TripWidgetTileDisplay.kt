package vad.dashing.tbox.trip

object TripWidgetTileDisplay {
    const val DEFAULT_SHOW_ROW_DIVIDERS = true
    const val DEFAULT_LABEL_COLUMN_WIDTH_PERCENT = 60
    const val MIN_LABEL_COLUMN_WIDTH_PERCENT = 20
    const val MAX_LABEL_COLUMN_WIDTH_PERCENT = 80

    fun normalizeLabelColumnWidthPercent(raw: Int): Int =
        raw.coerceIn(MIN_LABEL_COLUMN_WIDTH_PERCENT, MAX_LABEL_COLUMN_WIDTH_PERCENT)
}
