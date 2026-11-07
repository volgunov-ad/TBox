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

/**
 * Current gear number from Android 10 dev-status `0x8300`, callback layout (17 bytes).
 * Offset 6 is the stock `McuCommMsg0x8300.gear` byte. Parked head unit reads 0.
 * The native unpack is one nibble, so 0…15 is accepted. This is not P/R/N/D.
 */
internal object DevStatusGearCodec {
    fun decode(payload: ByteArray, length: Int = payload.size): Int? {
        val size = length.coerceIn(0, payload.size)
        if (size < 7) return null
        if ((payload[0].toInt() and 0xFF) != 0x83) return null
        if ((payload[1].toInt() and 0xFF) != 0x00) return null
        val gear = payload[6].toInt() and 0xFF
        if (gear > 15) return null
        return gear
    }
}
