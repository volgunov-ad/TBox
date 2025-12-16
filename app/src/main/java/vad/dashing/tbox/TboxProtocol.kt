package vad.dashing.tbox

fun fillHeader(dataLength: Int,
               tid: Byte,
               sid: Byte,
               param: Byte): ByteArray {
    val header = ByteArray(13)
    header[0] = 0x8E.toByte()      // Стартовый байт
    header[1] = 0x5D.toByte()      // Идентификатор протокола
    header[2] = (dataLength + 10 shr 8).toByte()  // Длина данных (старший байт)
    header[3] = (dataLength + 10 and 0xFF).toByte() // Длина данных (младший байт)
    header[4] = 0x00               // Sequence number
    header[5] = 0x00               // Reserved
    header[6] = 0x01               // Версия протокола
    header[7] = 0x00               // Reserved
    header[8] = tid                // ID целевого модуля
    header[9] = sid                // ID исходного модуля
    header[10] = (dataLength shr 8).toByte()  // Длина данных (старший байт)
    header[11] = (dataLength and 0xFF).toByte() // Длина данных (младший байт)
    header[12] = param             // Команда
    return header
}

fun checkPacket(data: ByteArray): Boolean {
    if (data.isEmpty() || data.size < 14) {
        return false
    }
    if (data[0] != 0x8E.toByte() || data[1] != 0x5D.toByte()) {
        return false
    }
    return true
}

fun extractDataLength(data: ByteArray): Int {
    return ((data[10].toInt() and 0xFF) shl 8) or (data[11].toInt() and 0xFF)
}

fun checkLength(data: ByteArray, length: Int): Boolean {
    return data.size - 14 >= length
}

fun extractData(data: ByteArray, length: Int): ByteArray {
    if (xorSum(data.copyOfRange(0, 13+length)) != data[13+length]) {
        return ByteArray(0)
    }
    return data.copyOfRange(13, 13+length)
}

fun xorSum(data: ByteArray): Byte {
    if (data.isEmpty() || data.size < 9) {
        return 0
    }

    var checksum: Byte = 0
    // Начинаем с 10-го байта (индекс 9, так как индексация с 0)
    for (i in 9 until data.size) {
        checksum = (checksum.toInt() xor data[i].toInt()).toByte()
    }
    return checksum
}

fun toHexString(data: ByteArray, separator: String = " "): String {
    return data.joinToString(separator) { "%02X".format(it) }
}