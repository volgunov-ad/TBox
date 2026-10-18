package vad.dashing.usbaudio

/**
 * Hardware speaker volume of the USB sound card on the head unit, set through `tinymix` as root.
 * The PCM2902/C-Media feature unit exposes a linear 0..151 scale mapped over -28.375..-0.0625 dB
 * (raw 1/256 dB units: -7264 + value * 48); values above 151 are clamped by the kernel.
 */
object UsbVolume {
    const val MAX_VALUE = 151
    const val CONTROL_NAME = "Speaker Playback Volume"

    /** First ALSA card driven by USB-Audio, e.g. line ` 1 [Device         ]: USB-Audio - ...` -> 1. */
    fun usbCardNumber(cards: String): Int? =
        cards.lineSequence().firstOrNull { "USB-Audio" in it }
            ?.let { line -> Regex("""^\s*(\d+)\s*\[""").find(line)?.groupValues?.get(1)?.toIntOrNull() }

    /** tinymix list line `5 INT 2 Speaker Playback Volume   150 150` -> 5 to 150. */
    fun parseControl(tinymixList: String): Pair<Int, Int>? {
        val line = tinymixList.lineSequence().firstOrNull { CONTROL_NAME in it } ?: return null
        val index = Regex("""^\s*(\d+)""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        val value = Regex("""(\d+)\s+(\d+)\s*$""").find(line)?.groupValues?.get(1)?.toIntOrNull() ?: return null
        return index to value
    }

    fun approxDb(value: Int): Double = -28.375 + value.coerceIn(0, MAX_VALUE) * (28.3125 / MAX_VALUE)
}
