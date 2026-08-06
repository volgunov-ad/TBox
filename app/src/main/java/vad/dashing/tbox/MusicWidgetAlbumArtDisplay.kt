package vad.dashing.tbox

/** Column width and related defaults for full music widget album-art layout. */
object MusicWidgetAlbumArtDisplay {
    const val DEFAULT_ALBUM_ART_COLUMN_WIDTH_PERCENT = 30
    const val MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT = 20
    const val MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT = 80

    const val ALBUM_ART_SIDE_LEFT = 0
    const val ALBUM_ART_SIDE_RIGHT = 1
    const val DEFAULT_ALBUM_ART_SIDE = ALBUM_ART_SIDE_LEFT

    /** Max edge length for decoded MediaMetadata album art (px). */
    const val MAX_ALBUM_ART_EDGE_PX = 256

    fun normalizeAlbumArtColumnWidthPercent(raw: Int): Int =
        raw.coerceIn(MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT, MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT)

    fun normalizeAlbumArtSide(raw: Int): Int =
        if (raw == ALBUM_ART_SIDE_RIGHT) ALBUM_ART_SIDE_RIGHT else ALBUM_ART_SIDE_LEFT
}
