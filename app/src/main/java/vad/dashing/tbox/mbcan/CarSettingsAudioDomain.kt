package vad.dashing.tbox.mbcan

/**
 * Shared UI levels for speed-dependent volume.
 *
 * Android 9 mbCAN uses zero-based values, whereas Android 10 VHAL uses one-based values.
 */
object CarSettingsAudioDomain {
    const val EQ_MODE_CUSTOM = 255
    const val EQ_MODE_POP = 1
    const val EQ_MODE_ROCK = 2
    const val EQ_MODE_JAZZ = 3
    const val EQ_MODE_CLASSIC = 4
    const val EQ_MODE_VOICE = 5
    val eqModes: Set<Int> = setOf(
        EQ_MODE_CUSTOM,
        EQ_MODE_POP,
        EQ_MODE_ROCK,
        EQ_MODE_JAZZ,
        EQ_MODE_CLASSIC,
        EQ_MODE_VOICE,
    )
    val eqBandUiRange: IntRange = -7..7
    val balanceFaderUiRange: IntRange = -7..7
    private const val BALANCE_FADER_RAW_OFFSET = 7

    const val VOLUME_SPEED_UI_OFF = 1
    const val VOLUME_SPEED_UI_LOW = 2
    const val VOLUME_SPEED_UI_MID = 3
    const val VOLUME_SPEED_UI_HIGH = 4

    val volumeSpeedUiRange: IntRange = VOLUME_SPEED_UI_OFF..VOLUME_SPEED_UI_HIGH

    fun decodeVolumeSpeedMbCan(raw: Int): Int? =
        raw.takeIf { it in 0..3 }?.plus(1)

    fun encodeVolumeSpeedMbCan(uiLevel: Int): Int? =
        uiLevel.takeIf { it in volumeSpeedUiRange }?.minus(1)

    fun decodeVolumeSpeedVhal(raw: Int): Int? =
        raw.takeIf { it in volumeSpeedUiRange }

    fun encodeVolumeSpeedVhal(uiLevel: Int): Int? =
        uiLevel.takeIf { it in volumeSpeedUiRange }

    fun decodeEqMode(raw: Int): Int? = raw.takeIf { it in eqModes }

    fun decodeEqBand(raw: Int): Int? = raw.takeIf { it in eqBandUiRange }

    fun encodeEqBand(uiValue: Int): Int? = uiValue.takeIf { it in eqBandUiRange }

    fun decodeBalanceFader(raw: Int): Int? =
        (raw - BALANCE_FADER_RAW_OFFSET).takeIf { it in balanceFaderUiRange }

    fun encodeBalanceFader(uiValue: Int): Int? =
        uiValue.takeIf { it in balanceFaderUiRange }?.plus(BALANCE_FADER_RAW_OFFSET)
}
