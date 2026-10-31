package vad.dashing.tbox.phoneble

import java.util.UUID

/**
 * Direct link between the phone and the head unit over Bluetooth Classic RFCOMM, without
 * the ESP32 companion. The phone must already be bonded with the head unit (HFP/A2DP).
 *
 * Same payloads as the GATT link ([PhoneBleCodec]): PAIR in clear, sealed CMD / REFRESH
 * from the phone, sealed SNAP pages back. RFCOMM is a byte stream, so every payload goes
 * in a frame: u8 length (1..[PAYLOAD_MAX]), then the payload.
 *
 * Serial Port 0x1101 is taken by the stock nForeTek service on both head units; the own
 * UUID makes SDP route the phone to TBox Monitor.
 */
object PhoneDirectWire {
    val SERVICE_UUID: UUID = UUID.fromString("7b0e5d1e-4c2a-4f3b-9e61-0d7a5b3c2e11")
    const val SERVICE_NAME: String = "TBox Phone"

    /** Largest payload: a PAIR packet with a full name. */
    const val PAYLOAD_MAX: Int = 1 + PhoneBleCodec.ID_LEN + PhoneBleCodec.KEY_LEN + PhoneBleCodec.NAME_MAX

    fun frame(payload: ByteArray): ByteArray {
        require(payload.size in 1..PAYLOAD_MAX) { "payload ${payload.size}" }
        val out = ByteArray(1 + payload.size)
        out[0] = payload.size.toByte()
        payload.copyInto(out, 1)
        return out
    }

    /**
     * Splits the incoming byte stream into payloads. A zero or oversized length means the
     * peer does not speak this protocol: [broken] turns true and the link must be closed.
     */
    class FrameReader {
        private val buffer = ByteArray(1 + PAYLOAD_MAX)
        private var filled = 0

        var broken: Boolean = false
            private set

        fun feed(data: ByteArray, count: Int = data.size): List<ByteArray> {
            if (broken) return emptyList()
            val out = ArrayList<ByteArray>(1)
            for (index in 0 until count) {
                buffer[filled++] = data[index]
                val length = buffer[0].toInt() and 0xFF
                if (length == 0 || length > PAYLOAD_MAX) {
                    broken = true
                    return out
                }
                if (filled == 1 + length) {
                    out += buffer.copyOfRange(1, filled)
                    filled = 0
                }
            }
            return out
        }
    }
}
