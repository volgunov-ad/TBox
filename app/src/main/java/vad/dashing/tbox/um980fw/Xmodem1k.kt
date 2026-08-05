package vad.dashing.tbox.um980fw

/**
 * XMODEM-1K sender matching Unicore N4 BootLoader (UPrecise capture):
 * STX | blk | ~blk | 1024 data | checksum (sum & 0xFF); ACK/NAK; EOT.
 * Also accepts CRC-16 mode if the receiver starts with 'C'.
 */
object Xmodem1k {
    const val SOH: Byte = 0x01
    const val STX: Byte = 0x02
    const val EOT: Byte = 0x04
    const val ACK: Byte = 0x06
    const val NAK: Byte = 0x15
    const val CAN: Byte = 0x18
    const val CRC_LETTER: Byte = 'C'.code.toByte()

    const val BLOCK_SIZE: Int = 1024
    const val MAX_RETRIES: Int = 10

    enum class CheckMode { CHECKSUM, CRC16 }

    fun checksum(data: ByteArray, off: Int = 0, len: Int = data.size - off): Int {
        var s = 0
        for (i in off until off + len) {
            s = (s + (data[i].toInt() and 0xFF)) and 0xFF
        }
        return s
    }

    /** CRC-16/XMODEM (poly 0x1021, init 0). */
    fun crc16(data: ByteArray, off: Int = 0, len: Int = data.size - off): Int {
        var crc = 0
        for (i in off until off + len) {
            crc = crc xor ((data[i].toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if ((crc and 0x8000) != 0) {
                    ((crc shl 1) xor 0x1021) and 0xFFFF
                } else {
                    (crc shl 1) and 0xFFFF
                }
            }
        }
        return crc
    }

    fun buildBlock(seq: Int, payload: ByteArray, mode: CheckMode): ByteArray {
        require(seq in 1..255)
        require(payload.size <= BLOCK_SIZE)
        val data = ByteArray(BLOCK_SIZE)
        System.arraycopy(payload, 0, data, 0, payload.size)
        // pad 0x1A (CTRL-Z) for short last block — classic XMODEM
        if (payload.size < BLOCK_SIZE) {
            data.fill(0x1A, payload.size, BLOCK_SIZE)
        }
        val blk = seq and 0xFF
        val inv = blk.inv() and 0xFF
        return when (mode) {
            CheckMode.CHECKSUM -> {
                val out = ByteArray(3 + BLOCK_SIZE + 1)
                out[0] = STX
                out[1] = blk.toByte()
                out[2] = inv.toByte()
                System.arraycopy(data, 0, out, 3, BLOCK_SIZE)
                out[3 + BLOCK_SIZE] = checksum(data).toByte()
                out
            }
            CheckMode.CRC16 -> {
                val c = crc16(data)
                val out = ByteArray(3 + BLOCK_SIZE + 2)
                out[0] = STX
                out[1] = blk.toByte()
                out[2] = inv.toByte()
                System.arraycopy(data, 0, out, 3, BLOCK_SIZE)
                out[3 + BLOCK_SIZE] = ((c shr 8) and 0xFF).toByte()
                out[3 + BLOCK_SIZE + 1] = (c and 0xFF).toByte()
                out
            }
        }
    }
}
