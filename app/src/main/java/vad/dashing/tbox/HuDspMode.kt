package vad.dashing.tbox

import vad.dashing.tbox.hutimezone.HuTimeZoneCodec

internal object HuDspMode {
    val MODES: List<Int> = (0..10).toList() + 250
    const val DEFAULT_MODE = 1

    fun normalize(mode: Int?): Int = mode?.takeIf { it in MODES } ?: DEFAULT_MODE

    fun startupMode(enabled: Boolean, headUnitMode: HeadUnitCanMode, mode: Int): Int? =
        mode.takeIf { enabled && headUnitMode == HeadUnitCanMode.Android10Vhal && it in MODES }

    fun writeCommand(mode: Int): String {
        require(mode in MODES) { "Unsupported DSP mode" }
        return "service call adayo.setting.v2.0 86 i32 $mode"
    }

    fun parseStatus(output: String): Int? = HuTimeZoneCodec.parseInt(output)
}

internal object HuDspStartup {
    val ATTEMPT_DELAYS_MS = listOf(0L, 10_000L, 20_000L, 30_000L, 60_000L)

    suspend fun run(
        pause: suspend (Long) -> Unit,
        apply: suspend () -> Unit,
    ) {
        for (wait in ATTEMPT_DELAYS_MS) {
            if (wait > 0) pause(wait)
            apply()
        }
    }
}
