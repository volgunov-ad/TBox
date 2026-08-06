package vad.dashing.tbox

/** Column width and related defaults for full music widget album-art layout. */
object MusicWidgetAlbumArtDisplay {
    const val DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT = 30
    const val MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT = 20
    const val MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT = 80

    /** Max edge length for decoded MediaMetadata album art (px). */
    const val MAX_ALBUM_ART_EDGE_PX = 256

    fun normalizeAlbumArtColumnWidthPercent(raw: Int): Int =
        raw.coerceIn(MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT, MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT)
}
