package vad.dashing.tbox.mbcan

/**
 * Android 10 MCU dev-status reply `0x8305`, as delivered to the JNI callback.
 * The wire log shows a big-endian short (`830500760076`), but
 * `McuProtocolLibDevStatus::mcuBattValue` stores that short in host order before
 * the callback, so bytes 2–3 arrive little-endian (`830576000076` → 118 → 11.8 V).
 */
internal object BoardVoltageCodec {
    const val MIN_VOLTS = 6f
    const val MAX_VOLTS = 18f

    fun decode(payload: ByteArray, length: Int = payload.size): Float? {
        val size = length.coerceIn(0, payload.size)
        if (size < 4) return null
        if ((payload[0].toInt() and 0xFF) != 0x83) return null
        if ((payload[1].toInt() and 0xFF) != 0x05) return null
        val raw = (payload[2].toInt() and 0xFF) or ((payload[3].toInt() and 0xFF) shl 8)
        if (raw == 0 || raw == 0xFFFF) return null
        val volts = raw / 10f
        if (volts < MIN_VOLTS || volts > MAX_VOLTS) return null
        return volts
    }
}
