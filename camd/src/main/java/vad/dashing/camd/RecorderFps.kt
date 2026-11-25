package vad.dashing.camd

import kotlin.math.roundToInt

/**
 * Recording frame rate as a divisor of the sensor rate: every frame, every 2nd or every
 * 5th keeps the intervals even (40 / 80 / 200 ms); 20 or 15 fps from a 25 fps sensor
 * would drop frames unevenly and judder. Bitrate scales with the rate.
 */
internal object RecorderFps {
    const val PREF_DIVISOR = "recorder_fps_divisor"
    const val SENSOR_FPS = 25
    const val SENSOR_FRAME_MS = 1000L / SENSOR_FPS
    const val FULL_BIT_RATE = 6_000_000
    val DIVISORS = listOf(1, 2, 5)

    fun normalize(divisor: Int): Int = if (divisor in DIVISORS) divisor else 1

    fun fps(divisor: Int): Double = SENSOR_FPS.toDouble() / divisor

    /** MediaFormat takes an integer rate; 12.5 is announced as 13. */
    fun encoderFps(divisor: Int): Int = fps(divisor).roundToInt()

    fun frameMs(divisor: Int): Long = SENSOR_FRAME_MS * divisor

    fun bitRate(divisor: Int): Int = FULL_BIT_RATE / divisor

    fun label(divisor: Int): String = when (divisor) {
        1 -> "25"
        2 -> "12,5"
        else -> "%d".format(SENSOR_FPS / divisor)
    }

    fun bitRateLabel(divisor: Int): String {
        val mbit = bitRate(divisor) / 1_000_000.0
        return if (mbit == mbit.toInt().toDouble()) "${mbit.toInt()}" else "%.1f".format(mbit).replace('.', ',')
    }
}
