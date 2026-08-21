package vad.dashing.tbox

/**
 * Stock SystemSettings mixer mapping (not mbCAN/VHAL).
 *
 * A9: OpenOS `AudioManager` volume groups by `AudioAttributes.USAGE`.
 * A10: Adayo `SettingsSvcIfManager.setAudioStreamVolume`.
 */
object PlatformAudioDomain {
    const val HEADREST_ONLY = 1
    const val HEADREST_ASSIST = 2
    const val HEADREST_OFF = 3

    val headrestUiValues: Set<Int> = setOf(HEADREST_ONLY, HEADREST_ASSIST, HEADREST_OFF)

    enum class VolumeChannel(val uiRange: IntRange) {
        Media(0..31),
        Phone(1..31),
        Navi(0..10),
        Voice(2..10),
    }

    /** A10 `VolumeData.SETTING_STREAM_TYPE_*`. */
    fun a10StreamType(channel: VolumeChannel): Int = when (channel) {
        VolumeChannel.Media -> 3
        VolumeChannel.Phone -> 6
        VolumeChannel.Navi -> 7
        VolumeChannel.Voice -> 9
    }

    /** A9 OpenOS `getVolumeGroupIdForUsage` / Android `USAGE_*`. */
    fun a9Usage(channel: VolumeChannel): Int = when (channel) {
        VolumeChannel.Media -> 1
        VolumeChannel.Phone -> 2
        VolumeChannel.Navi -> 12
        VolumeChannel.Voice -> 16
    }

    /** Fallback `AudioManager` stream when OpenOS groups are unavailable. */
    fun a9FallbackStream(channel: VolumeChannel): Int = when (channel) {
        VolumeChannel.Media -> android.media.AudioManager.STREAM_MUSIC
        VolumeChannel.Phone -> android.media.AudioManager.STREAM_VOICE_CALL
        VolumeChannel.Navi -> 7
        VolumeChannel.Voice -> 9
    }

    fun sanitizeVolume(channel: VolumeChannel, raw: Int): Int? =
        raw.takeIf { it >= 0 }?.coerceIn(channel.uiRange)

    /**
     * A9 `eAUDIO_AUDIO_HEADREST_SPEAKER` (37): 0 close / 1 headrest / 2 auxiliary.
     * Shared UI matches A10: 1 only / 2 assist / 3 off.
     */
    fun decodeHeadrestMbCan(raw: Int): Int? = when (raw) {
        0 -> HEADREST_OFF
        1 -> HEADREST_ONLY
        2 -> HEADREST_ASSIST
        else -> null
    }

    fun encodeHeadrestMbCan(ui: Int): Int? = when (ui) {
        HEADREST_ONLY -> 1
        HEADREST_ASSIST -> 2
        HEADREST_OFF -> 0
        else -> null
    }

    fun decodeHeadrestVhal(raw: Int): Int? = raw.takeIf { it in headrestUiValues }
}
