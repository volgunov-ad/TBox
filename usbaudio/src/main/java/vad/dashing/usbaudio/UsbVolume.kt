package vad.dashing.usbaudio

/**
 * Hardware speaker volume of the USB sound card on the head unit, set through `tinymix` as root.
 * The PCM2902/C-Media feature unit exposes a linear 0..151 scale mapped over -28.375..-0.0625 dB
 * (raw 1/256 dB units: -7264 + value * 48); values above 151 are clamped by the kernel.
 */
object UsbVolume {
    const val MAX_VALUE = 151
    const val CONTROL_NAME = "Speaker Playback Volume"
    const val FALLBACK_CONTROL_NAME = "PCM Playback Volume"

    data class Control(val index: Int, val value: Int, val channels: Int)

    /** First ALSA card driven by USB-Audio, e.g. line ` 1 [Device         ]: USB-Audio - ...` -> 1. */
    fun usbCardNumber(cards: String): Int? =
        cards.lineSequence().firstOrNull { "USB-Audio" in it }
            ?.let { line -> Regex("""^\s*(\d+)\s*\[""").find(line)?.groupValues?.get(1)?.toIntOrNull() }

    /**
     * tinymix list line `5 INT 2 Speaker Playback Volume   150 150` -> index 5, value 150, 2 channels;
     * mono cards print a single value. Falls back to `PCM Playback Volume` when there is no speaker control.
     */
    fun parseControl(tinymixList: String): Control? =
        parseControl(tinymixList, CONTROL_NAME) ?: parseControl(tinymixList, FALLBACK_CONTROL_NAME)

    private fun parseControl(tinymixList: String, name: String): Control? {
        val line = tinymixList.lineSequence().firstOrNull { name in it } ?: return null
        val match = Regex("""^\s*(\d+)(?:\s+\S+\s+(\d+)\s)?""").find(line) ?: return null
        val index = match.groupValues[1].toIntOrNull() ?: return null
        val values = line.substringAfter(name).trim().split(Regex("""\s+""")).mapNotNull { it.toIntOrNull() }
        val value = values.firstOrNull() ?: return null
        val channels = match.groupValues[2].toIntOrNull() ?: values.size
        return Control(index, value, channels.coerceAtLeast(1))
    }

    /** `tinymix -D <card> <index> <v> [<v> ...]` arguments, one value per channel. */
    fun setArgs(control: Control, value: Int): String =
        (listOf(control.index) + List(control.channels) { value }).joinToString(" ")

    fun approxDb(value: Int): Double = -28.375 + value.coerceIn(0, MAX_VALUE) * (28.3125 / MAX_VALUE)
}
