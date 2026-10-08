package vad.dashing.tbox.uda

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** JX65 ECU ids from `libJX65_n720_CFG.so` (`Ecu_JX65_*_Cfg`). */
data class UdaEcu(
    val id: Int,
    val name: String,
)

object UdaEcuCatalog {
    val all: List<UdaEcu> = listOf(
        UdaEcu(3, "EMS"),
        UdaEcu(10, "TCU"),
        UdaEcu(45, "GSM"),
        UdaEcu(46, "GBC"),
        UdaEcu(5, "EPS"),
        UdaEcu(20, "ESP"),
        UdaEcu(86, "HUD"),
        UdaEcu(37, "BSD"),
        UdaEcu(57, "FCM"),
        UdaEcu(54, "FRM"),
        UdaEcu(83, "WSD"),
        UdaEcu(6, "ICM"),
        UdaEcu(81, "DHM"),
        UdaEcu(24, "PLG"),
        UdaEcu(82, "RFR"),
        UdaEcu(93, "PEPS"),
        UdaEcu(36, "APM"),
        UdaEcu(48, "WPC"),
        UdaEcu(84, "AVC_1"),
        UdaEcu(85, "AVC_2"),
        UdaEcu(0, "ABM"),
        UdaEcu(19, "SAM"),
        UdaEcu(63, "MFS"),
        UdaEcu(9, "RRM"),
        UdaEcu(47, "IMMO"),
        UdaEcu(8, "RADAR"),
        UdaEcu(87, "RBCM"),
        UdaEcu(14, "T_BOX"),
        UdaEcu(94, "FL_RAD"),
        UdaEcu(95, "FR_RAD"),
        UdaEcu(96, "RL_RAD"),
        UdaEcu(97, "RR_RAD"),
        UdaEcu(98, "FC_RAD"),
        UdaEcu(13, "BMS"),
        UdaEcu(55, "APA"),
        UdaEcu(88, "ZDC"),
        UdaEcu(89, "RLC"),
    )

    fun find(id: Int): UdaEcu? = all.firstOrNull { it.id == id }

    fun nameOf(id: Int): String = find(id)?.name ?: "ECU $id"
}

/** One row in the Info DTC window. Reading another block appends; it does not replace. */
sealed class UdaDtcEntry {
    abstract val ecuName: String

    data class Code(override val ecuName: String, val code: String) : UdaDtcEntry()
    data class None(override val ecuName: String) : UdaDtcEntry()
    data class Failure(override val ecuName: String, val detail: String) : UdaDtcEntry()
    data class Timeout(override val ecuName: String) : UdaDtcEntry()
}

/**
 * `CMD 0x86` body: `Uda_DiagResultResp_t` (header 53 bytes, then `dataLen` bytes).
 * `resultCode` is `ret | 0x80000000`; the low 31 bits are the UDE status.
 */
data class UdaDiagResult(
    val type: Int,
    val ecuId: Int,
    val endCode: Int,
    val resultCode: Int,
    val resultInfo: String,
    val data: ByteArray,
) {
    val udeCode: Int get() = resultCode and 0x7FFFFFFF
    val ok: Boolean get() = udeCode == 0
}

object UdaDiag {
    const val RESULT_HEADER_SIZE = 53
    /** UDS 19 02 status mask: all DTCs. Second byte is unused by the script's first field. */
    val READ_DTC_PAYLOAD = byteArrayOf(0xFF.toByte(), 0x00)

    fun parseResult(data: ByteArray): UdaDiagResult? {
        if (data.size < RESULT_HEADER_SIZE) return null
        val info = data.copyOfRange(17, 49)
        val end = info.indexOf(0)
        val text = String(info, 0, if (end < 0) info.size else end, Charsets.UTF_8).trim()
        val dataLen = le32(data, 49).coerceAtLeast(0)
        val available = data.size - RESULT_HEADER_SIZE
        val n = if (dataLen <= available) dataLen else available
        val payload = if (n == 0) ByteArray(0) else data.copyOfRange(RESULT_HEADER_SIZE, RESULT_HEADER_SIZE + n)
        return UdaDiagResult(
            type = data[0].toInt() and 0xFF,
            ecuId = le32(data, 1),
            endCode = le32(data, 5),
            resultCode = le32(data, 9),
            resultInfo = text,
            data = payload,
        )
    }

    /** `Uda_DiagResp_t`: type, ecuId, resp. `resp == 0` means the request was accepted. */
    fun parseAckResp(data: ByteArray): Int? {
        if (data.size < 9) return null
        return le32(data, 5)
    }

    fun entriesFor(ecuName: String, result: UdaDiagResult): List<UdaDtcEntry> {
        if (!result.ok) {
            val detail = result.resultInfo.ifBlank { "UDE 0x${result.udeCode.toString(16)}" }
            return listOf(UdaDtcEntry.Failure(ecuName, detail))
        }
        val codes = parseDtcCodes(result.data)
        if (codes.isNotEmpty()) return codes.map { UdaDtcEntry.Code(ecuName, it) }
        if (result.data.isNotEmpty()) {
            return listOf(UdaDtcEntry.Failure(ecuName, toHex(result.data)))
        }
        return listOf(UdaDtcEntry.None(ecuName))
    }

    /**
     * DTC bytes from `Diag_ReadDtc.elbin` (UDS `19 02`): optional `59 02 avail`,
     * then 3-byte DTC plus optional status byte.
     */
    fun parseDtcCodes(payload: ByteArray): List<String> {
        if (payload.isEmpty()) return emptyList()
        var body = payload
        if (body.size >= 3 && (body[0].toInt() and 0xFF) == 0x59 && (body[1].toInt() and 0xFF) == 0x02) {
            body = body.copyOfRange(3, body.size)
        }
        val width = when {
            body.size >= 4 && body.size % 4 == 0 -> 4
            body.size >= 3 && body.size % 3 == 0 -> 3
            else -> return emptyList()
        }
        val codes = ArrayList<String>()
        var i = 0
        while (i + width <= body.size) {
            val b0 = body[i].toInt() and 0xFF
            val b1 = body[i + 1].toInt() and 0xFF
            val b2 = body[i + 2].toInt() and 0xFF
            val status = if (width == 4) body[i + 3].toInt() and 0xFF else 0
            if (b0 or b1 or b2 or status != 0) {
                val code = formatDtc(b0, b1, b2)
                codes.add(if (width == 4 && status != 0) "$code/%02X".format(status) else code)
            }
            i += width
        }
        return codes
    }

    private fun formatDtc(b0: Int, b1: Int, b2: Int): String {
        val letter = when ((b0 shr 6) and 0x03) {
            0 -> 'P'
            1 -> 'C'
            2 -> 'B'
            else -> 'U'
        }
        val hex = "0123456789ABCDEF"
        return buildString(8) {
            append(letter)
            append(hex[(b0 shr 4) and 0x03])
            append(hex[b0 and 0x0F])
            append(hex[(b1 shr 4) and 0x0F])
            append(hex[b1 and 0x0F])
            append('-')
            append(hex[(b2 shr 4) and 0x0F])
            append(hex[b2 and 0x0F])
        }
    }

    private fun le32(data: ByteArray, offset: Int): Int =
        ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).int

    private fun toHex(data: ByteArray): String {
        val n = minOf(data.size, 24)
        return buildString {
            for (i in 0 until n) {
                if (i > 0) append(' ')
                append("%02X".format(data[i].toInt() and 0xFF))
            }
            if (data.size > n) append(" …")
        }
    }
}

/** Accumulated DTC rows for the Info dialog. Survives closing the window. */
object UdaDtcSession {
    private val lock = Any()
    private val _entries = MutableStateFlow<List<UdaDtcEntry>>(emptyList())
    val entries: StateFlow<List<UdaDtcEntry>> = _entries.asStateFlow()

    private val _pendingEcuId = MutableStateFlow<Int?>(null)
    val pendingEcuId: StateFlow<Int?> = _pendingEcuId.asStateFlow()

    fun markPending(ecuId: Int) {
        _pendingEcuId.value = ecuId
    }

    fun finishPending() {
        _pendingEcuId.value = null
    }

    fun append(entries: List<UdaDtcEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            _entries.value = _entries.value + entries
        }
    }

    fun clear() {
        synchronized(lock) {
            _entries.value = emptyList()
        }
    }
}
