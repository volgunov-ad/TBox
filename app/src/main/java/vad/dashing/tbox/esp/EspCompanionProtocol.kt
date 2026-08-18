package vad.dashing.tbox.esp

import org.json.JSONArray
import org.json.JSONObject
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.UtcTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.CRC32

/** NDJSON protocol v1 between HU (USB Host) and ESP32-S3 companion. */
object EspCompanionProtocol {
    const val PROTOCOL_VERSION = 1
    const val HEARTBEAT_TIMEOUT_MS = 5_000L
    const val UM980_ONLINE_TIMEOUT_MS = 3_000L

    /**
     * Link quiet too long: either no RX since last message, or never received
     * anything after open (connectedAt grace uses the same timeout).
     */
    fun shouldForceReopenLink(
        connected: Boolean,
        lastMessageAtMs: Long,
        connectedAtMs: Long,
        nowMs: Long,
        timeoutMs: Long = HEARTBEAT_TIMEOUT_MS,
    ): Boolean {
        if (!connected) return false
        return when {
            lastMessageAtMs > 0L -> nowMs - lastMessageAtMs > timeoutMs
            connectedAtMs > 0L -> nowMs - connectedAtMs > timeoutMs
            else -> false
        }
    }

    /** Matches ota_0/ota_1 size in companion partitions.csv. */
    const val OTA_MAX_IMAGE_SIZE = 0x180000L
    const val OTA_IMAGE_MAGIC: Int = 0xE9
    const val OTA_CHUNK_MAX = 1024
    private const val OTA_FRAME_MAGIC0: Byte = 0xA5.toByte()
    private const val OTA_FRAME_MAGIC1: Byte = 0x5A.toByte()

    const val TYPE_HELLO = "hello"
    const val TYPE_HB = "hb"
    const val TYPE_GPS = "gps"
    const val TYPE_GPIO = "gpio"
    const val TYPE_GPIO_EVENT = "gpioEvent"
    const val TYPE_RELAY = "relay"
    const val TYPE_RELAY_SET = "relaySet"
    const val TYPE_UM980_CMD = "um980Cmd"
    const val TYPE_UM980_RSP = "um980Rsp"
    const val TYPE_UM980_BAUD = "um980Baud"
    const val TYPE_REBOOT = "reboot"
    const val TYPE_REBOOT_ACK = "rebootAck"
    const val TYPE_OTA_BEGIN = "otaBegin"
    const val TYPE_OTA_END = "otaEnd"
    const val TYPE_OTA_ACK = "otaAck"
    const val TYPE_OTA_DONE = "otaDone"
    const val TYPE_UM980_BRIDGE_BEGIN = "um980BridgeBegin"
    const val TYPE_UM980_BRIDGE_END = "um980BridgeEnd"
    const val TYPE_UM980_BRIDGE_ACK = "um980BridgeAck"
    const val TYPE_CAN_TX = "canTx"
    const val TYPE_CAN_BAUD = "canBaud"
    const val TYPE_CAN_FILTER = "canFilter"
    const val TYPE_CAN_LIGHT_BEGIN = "canLightBegin"
    const val TYPE_CAN_LIGHT_END = "canLightEnd"
    const val TYPE_CAN_ACK = "canAck"
    const val TYPE_CAN_RX = "canRx"

    const val CAN_LIGHT_FRAME_LEN = 14
    const val CAN_LIGHT_FLAG_EXT = 0x01
    const val CAN_LIGHT_FLAG_RTR = 0x02
    const val CAN_LIGHT_FLAG_TX = 0x04

    val UM980_BAUD_OPTIONS: List<Int> = listOf(
        9600, 19200, 38400, 57600, 115200, 230400, 460800,
    )

    val CAN_BAUD_OPTIONS: List<Int> = listOf(
        100_000, 125_000, 250_000, 500_000, 1_000_000,
    )

    fun encodeHello(): String = line(TYPE_HELLO)

    fun encodeRelaySet(mask: Int): String =
        line(TYPE_RELAY_SET, mapOf("mask" to (mask and 0xFF)))

    fun encodeUm980Cmd(cmd: String): String =
        line(TYPE_UM980_CMD, mapOf("cmd" to cmd))

    fun encodeUm980Baud(baud: Int): String =
        line(TYPE_UM980_BAUD, mapOf("baud" to baud))

    fun encodeReboot(): String = line(TYPE_REBOOT)

    fun encodeOtaBegin(size: Long, crc32: Long): String =
        line(
            TYPE_OTA_BEGIN,
            mapOf(
                "size" to size,
                "crc32" to (crc32 and 0xFFFF_FFFFL),
            ),
        )

    fun encodeOtaEnd(): String = line(TYPE_OTA_END)

    fun encodeUm980BridgeBegin(): String = line(TYPE_UM980_BRIDGE_BEGIN)

    fun encodeUm980BridgeEnd(): String = line(TYPE_UM980_BRIDGE_END)

    fun encodeCanTx(
        id: Int,
        ext: Boolean,
        data: ByteArray,
        rtr: Boolean = false,
        dlc: Int? = null,
    ): String {
        val n = (dlc ?: data.size).coerceIn(0, 8)
        val extras = mutableMapOf<String, Any>(
            "id" to formatCanIdHex(id, ext),
            "ext" to ext,
            "data" to formatCanDataHex(data.copyOf(n.coerceAtMost(data.size)), separator = ""),
            "rtr" to rtr,
            "dlc" to n,
        )
        return line(TYPE_CAN_TX, extras)
    }

    fun encodeCanBaud(baud: Int): String =
        line(TYPE_CAN_BAUD, mapOf("baud" to baud))

    fun encodeCanFilter(acceptAll: Boolean): String =
        line(TYPE_CAN_FILTER, mapOf("acceptAll" to acceptAll))

    fun encodeCanFilter(filters: List<CanFilterSpec>): String {
        val arr = JSONArray()
        for (f in filters) {
            val o = JSONObject()
            o.put("id", formatCanIdHex(f.id, f.ext))
            f.mask?.let { o.put("mask", formatCanIdHex(it, f.ext)) }
            o.put("ext", f.ext)
            arr.put(o)
        }
        return line(TYPE_CAN_FILTER, mapOf("filters" to arr))
    }

    fun encodeCanLightBegin(): String = line(TYPE_CAN_LIGHT_BEGIN)

    fun encodeCanLightEnd(): String = line(TYPE_CAN_LIGHT_END)

    /**
     * Compact light-mode records: N × 14 bytes (not OTA-wrapped).
     * Host→Device TX should set [tx]; Device→Host RX leaves it clear.
     */
    fun encodeCanLightFrames(frames: List<CanFrame>, tx: Boolean = false): ByteArray {
        val out = ByteArray(frames.size * CAN_LIGHT_FRAME_LEN)
        var off = 0
        for (frame in frames) {
            val dlc = frame.dlc
            var flags = 0
            if (frame.ext) flags = flags or CAN_LIGHT_FLAG_EXT
            if (frame.rtr) flags = flags or CAN_LIGHT_FLAG_RTR
            if (tx) flags = flags or CAN_LIGHT_FLAG_TX
            out[off] = flags.toByte()
            val id = if (frame.ext) frame.id and 0x1FFF_FFFF else frame.id and 0x7FF
            out[off + 1] = ((id ushr 24) and 0xFF).toByte()
            out[off + 2] = ((id ushr 16) and 0xFF).toByte()
            out[off + 3] = ((id ushr 8) and 0xFF).toByte()
            out[off + 4] = (id and 0xFF).toByte()
            out[off + 5] = dlc.toByte()
            val copy = dlc.coerceAtMost(frame.data.size)
            if (copy > 0) {
                System.arraycopy(frame.data, 0, out, off + 6, copy)
            }
            off += CAN_LIGHT_FRAME_LEN
        }
        return out
    }

    fun decodeCanLightPayload(payload: ByteArray): List<CanFrame> {
        if (payload.size < CAN_LIGHT_FRAME_LEN) return emptyList()
        val n = payload.size / CAN_LIGHT_FRAME_LEN
        return buildList(n) {
            var off = 0
            repeat(n) {
                val flags = payload[off].toInt() and 0xFF
                val id = ((payload[off + 1].toInt() and 0xFF) shl 24) or
                    ((payload[off + 2].toInt() and 0xFF) shl 16) or
                    ((payload[off + 3].toInt() and 0xFF) shl 8) or
                    (payload[off + 4].toInt() and 0xFF)
                val dlc = (payload[off + 5].toInt() and 0xFF).coerceIn(0, 8)
                val data = if (dlc == 0) {
                    ByteArray(0)
                } else {
                    payload.copyOfRange(off + 6, off + 6 + dlc)
                }
                add(
                    CanFrame(
                        id = id,
                        ext = flags and CAN_LIGHT_FLAG_EXT != 0,
                        rtr = flags and CAN_LIGHT_FLAG_RTR != 0,
                        data = data,
                        tx = flags and CAN_LIGHT_FLAG_TX != 0,
                    ),
                )
                off += CAN_LIGHT_FRAME_LEN
            }
        }
    }

    fun formatCanIdHex(id: Int, ext: Boolean): String {
        val v = if (ext) id and 0x1FFF_FFFF else id and 0x7FF
        return if (ext) {
            String.format(Locale.US, "0x%08X", v)
        } else {
            String.format(Locale.US, "0x%03X", v)
        }
    }

    fun formatCanDataHex(data: ByteArray, separator: String = " "): String {
        if (data.isEmpty()) return ""
        val sb = StringBuilder(data.size * (2 + separator.length))
        for (i in data.indices) {
            if (i > 0 && separator.isNotEmpty()) sb.append(separator)
            sb.append(String.format(Locale.US, "%02X", data[i].toInt() and 0xFF))
        }
        return sb.toString()
    }

    fun formatCanFrame(frame: CanFrame): String {
        val dir = if (frame.tx) "TX" else "RX"
        val flags = buildString {
            if (frame.ext) append(" ext")
            if (frame.rtr) append(" rtr")
        }
        val data = formatCanDataHex(frame.data)
        return "CAN $dir ${formatCanIdHex(frame.id, frame.ext)}$flags [${frame.dlc}] $data".trimEnd()
    }

    /** Hex id with optional `0x` prefix; bare digits are hex (CAN UI). */
    fun parseHexId(text: String): Int? {
        val t = text.trim().replace(" ", "")
            .removePrefix("0x")
            .removePrefix("0X")
        if (t.isEmpty()) return null
        if (t.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        return t.toLongOrNull(radix = 16)?.toInt()
    }

    /** Data hex; ignores spaces, colons, dashes. */
    fun parseHexData(text: String): ByteArray? {
        val sb = StringBuilder(text.length)
        for (c in text) {
            when (c) {
                ' ', ':', '-', '\t', '\n', '\r' -> Unit
                in '0'..'9', in 'a'..'f', in 'A'..'F' -> sb.append(c)
                else -> return null
            }
        }
        if (sb.length % 2 != 0) return null
        if (sb.isEmpty()) return ByteArray(0)
        val n = sb.length / 2
        if (n > 8) return null
        val out = ByteArray(n)
        var i = 0
        while (i < n) {
            val hi = sb[i * 2].digitToInt(16)
            val lo = sb[i * 2 + 1].digitToInt(16)
            out[i] = ((hi shl 4) or lo).toByte()
            i++
        }
        return out
    }

    /** Same framing as OTA chunks — reused for UM980 UART bridge and CAN light. */
    fun encodeBridgeChunkFrame(payload: ByteArray): ByteArray = encodeOtaChunkFrame(payload)

    /** Binary OTA frame: `0xA5 0x5A | u16be len | payload | u32be crc32(payload)`. */
    fun encodeOtaChunkFrame(payload: ByteArray): ByteArray {
        require(payload.isNotEmpty() && payload.size <= OTA_CHUNK_MAX) {
            "OTA chunk size ${payload.size}"
        }
        val out = ByteArray(4 + payload.size + 4)
        out[0] = OTA_FRAME_MAGIC0
        out[1] = OTA_FRAME_MAGIC1
        out[2] = ((payload.size shr 8) and 0xFF).toByte()
        out[3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, 4, payload.size)
        val crc = crc32Ieee(payload)
        val crcOff = 4 + payload.size
        out[crcOff] = ((crc shr 24) and 0xFF).toByte()
        out[crcOff + 1] = ((crc shr 16) and 0xFF).toByte()
        out[crcOff + 2] = ((crc shr 8) and 0xFF).toByte()
        out[crcOff + 3] = (crc and 0xFF).toByte()
        return out
    }

    fun crc32Ieee(data: ByteArray, offset: Int = 0, length: Int = data.size): Long {
        val crc = CRC32()
        crc.update(data, offset, length)
        return crc.value
    }

    /**
     * Pre-flight check before OTA. Returns null if OK, otherwise a stable error code:
     * `empty`, `too_large`, `bad_magic`.
     */
    fun validateFirmwareImage(size: Long, firstByte: Int): String? {
        if (size <= 0L) return "empty"
        if (size > OTA_MAX_IMAGE_SIZE) return "too_large"
        if ((firstByte and 0xFF) != OTA_IMAGE_MAGIC) return "bad_magic"
        return null
    }

    fun parseLine(line: String): EspMessage? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        return try {
            val o = JSONObject(trimmed)
            val v = o.optInt("v", PROTOCOL_VERSION)
            if (v != PROTOCOL_VERSION) return null
            when (o.optString("t")) {
                TYPE_HELLO -> EspMessage.Hello(
                    fw = o.optString("fw", ""),
                    gpioInCount = o.optInt("gpioIn", 0),
                    relayCount = o.optInt("relays", 0),
                    um980 = o.optBoolean("um980", false),
                    baud = o.optInt("baud", 115200),
                    can = o.optBoolean("can", false),
                    canBackend = o.optString("canBackend", "").ifBlank { null },
                    canBaud = o.optInt("canBaud", 0),
                    canLight = o.optBoolean("canLight", false),
                )
                TYPE_HB -> EspMessage.Heartbeat(uptimeMs = o.optLong("uptimeMs", 0L))
                TYPE_GPS -> EspMessage.Gps(
                    fix = o.optInt("fix", 0),
                    lat = o.optDouble("lat", 0.0),
                    lon = o.optDouble("lon", 0.0),
                    alt = o.optDouble("alt", 0.0),
                    speedKmh = o.optDouble("speedKmh", 0.0).toFloat(),
                    course = o.optDouble("course", 0.0).toFloat(),
                    satsUsed = o.optInt("satsUsed", 0),
                    satsVis = o.optInt("satsVis", 0),
                    utc = o.optString("utc", "").ifBlank { null },
                    hdop = o.optDouble("hdop", 0.0).toFloat().takeIf { it > 0f },
                    pdop = o.optDouble("pdop", 0.0).toFloat().takeIf { it > 0f },
                    vdop = o.optDouble("vdop", 0.0).toFloat().takeIf { it > 0f },
                    hrms = o.optDouble("hrms", 0.0).toFloat().takeIf { it > 0f },
                    vrms = o.optDouble("vrms", 0.0).toFloat().takeIf { it > 0f },
                    diffAge = o.optDouble("diffAge", -1.0).toFloat().takeIf { it >= 0f },
                    raw = trimmed,
                )
                TYPE_GPIO -> EspMessage.Gpio(
                    mask = o.optInt("mask", 0),
                    ms = o.optLong("ms", 0L),
                )
                TYPE_GPIO_EVENT -> EspMessage.GpioEvent(
                    channel = o.optInt("ch", 0),
                    level = o.optInt("level", 0) != 0,
                    ms = o.optLong("ms", 0L),
                )
                TYPE_RELAY -> EspMessage.Relay(mask = o.optInt("mask", 0))
                TYPE_UM980_RSP -> {
                    val arr = o.optJSONArray("lines") ?: JSONArray()
                    val lines = buildList {
                        for (i in 0 until arr.length()) {
                            add(arr.optString(i, ""))
                        }
                    }
                    EspMessage.Um980Rsp(
                        cmd = o.optString("cmd", ""),
                        lines = lines,
                        ok = o.optBoolean("ok", false),
                    )
                }
                TYPE_UM980_BAUD -> EspMessage.Um980Baud(
                    baud = o.optInt("baud", 0),
                    ok = o.optBoolean("ok", false),
                )
                TYPE_REBOOT_ACK -> EspMessage.RebootAck
                TYPE_OTA_ACK -> EspMessage.OtaAck(
                    phase = o.optString("phase", ""),
                    offset = o.optLong("offset", 0L),
                    ok = o.optBoolean("ok", false),
                    err = o.optString("err", "").ifBlank { null },
                )
                TYPE_OTA_DONE -> EspMessage.OtaDone(
                    ok = o.optBoolean("ok", false),
                    err = o.optString("err", "").ifBlank { null },
                )
                TYPE_UM980_BRIDGE_ACK -> EspMessage.Um980BridgeAck(
                    phase = o.optString("phase", ""),
                    ok = o.optBoolean("ok", false),
                    err = o.optString("err", "").ifBlank { null },
                )
                TYPE_CAN_ACK -> EspMessage.CanAck(
                    phase = o.optString("phase", ""),
                    ok = o.optBoolean("ok", false),
                    err = o.optString("err", "").ifBlank { null },
                )
                TYPE_CAN_BAUD -> EspMessage.CanBaud(
                    baud = o.optInt("baud", 0),
                    ok = o.optBoolean("ok", false),
                )
                TYPE_CAN_RX -> {
                    val idText = o.optString("id", "")
                    val id = parseHexId(idText) ?: o.optInt("id", 0)
                    val dataHex = o.optString("data", "")
                    val data = parseHexData(dataHex) ?: ByteArray(0)
                    EspMessage.CanRx(
                        frame = CanFrame(
                            id = id,
                            ext = o.optBoolean("ext", false),
                            rtr = o.optBoolean("rtr", false),
                            data = data,
                            tx = false,
                        ),
                    )
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun gpsToLocValues(gps: EspMessage.Gps, now: Date = Date()): LocValues {
        val locateStatus = gps.fix > 0 && !(gps.lat == 0.0 && gps.lon == 0.0)
        return LocValues(
            rawValue = gps.raw,
            locateStatus = locateStatus,
            utcTime = parseUtc(gps.utc),
            longitude = gps.lon,
            latitude = gps.lat,
            altitude = gps.alt,
            visibleSatellites = gps.satsVis,
            usingSatellites = gps.satsUsed,
            speed = gps.speedKmh,
            trueDirection = gps.course,
            magneticDirection = gps.course,
            hdop = gps.hdop,
            pdop = gps.pdop,
            vdop = gps.vdop,
            hrms = gps.hrms,
            vrms = gps.vrms,
            fixQuality = gps.fix.takeIf { it >= 0 },
            diffAgeSec = gps.diffAge,
            updateTime = now,
        )
    }

    private fun parseUtc(utc: String?): UtcTime? {
        if (utc.isNullOrBlank()) return null
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val date = fmt.parse(utc) ?: return null
            val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                time = date
            }
            UtcTime(
                year = cal.get(java.util.Calendar.YEAR) % 100,
                month = cal.get(java.util.Calendar.MONTH) + 1,
                day = cal.get(java.util.Calendar.DAY_OF_MONTH),
                hour = cal.get(java.util.Calendar.HOUR_OF_DAY),
                minute = cal.get(java.util.Calendar.MINUTE),
                second = cal.get(java.util.Calendar.SECOND),
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun line(type: String, extras: Map<String, Any> = emptyMap()): String {
        val o = JSONObject()
        o.put("v", PROTOCOL_VERSION)
        o.put("t", type)
        for ((k, v) in extras) {
            o.put(k, v)
        }
        return o.toString() + "\n"
    }
}

sealed class EspMessage {
    data class Hello(
        val fw: String,
        val gpioInCount: Int,
        val relayCount: Int,
        val um980: Boolean,
        val baud: Int = 115200,
        val can: Boolean = false,
        val canBackend: String? = null,
        val canBaud: Int = 0,
        val canLight: Boolean = false,
    ) : EspMessage()

    data class Heartbeat(val uptimeMs: Long) : EspMessage()

    data class Gps(
        val fix: Int,
        val lat: Double,
        val lon: Double,
        val alt: Double,
        val speedKmh: Float,
        val course: Float,
        val satsUsed: Int,
        val satsVis: Int,
        val utc: String?,
        val hdop: Float? = null,
        val pdop: Float? = null,
        val vdop: Float? = null,
        val hrms: Float? = null,
        val vrms: Float? = null,
        /** Age of DGPS/RTK corrections from GGA, seconds. */
        val diffAge: Float? = null,
        val raw: String,
    ) : EspMessage()

    data class Gpio(val mask: Int, val ms: Long) : EspMessage()

    data class GpioEvent(val channel: Int, val level: Boolean, val ms: Long) : EspMessage()

    data class Relay(val mask: Int) : EspMessage()

    data class Um980Rsp(
        val cmd: String,
        val lines: List<String>,
        val ok: Boolean,
    ) : EspMessage()

    data class Um980Baud(
        val baud: Int,
        val ok: Boolean,
    ) : EspMessage()

    data object RebootAck : EspMessage()

    data class OtaAck(
        val phase: String,
        val offset: Long,
        val ok: Boolean,
        val err: String?,
    ) : EspMessage()

    data class OtaDone(
        val ok: Boolean,
        val err: String?,
    ) : EspMessage()

    data class Um980BridgeAck(
        val phase: String,
        val ok: Boolean,
        val err: String?,
    ) : EspMessage()

    data class CanAck(
        val phase: String,
        val ok: Boolean,
        val err: String?,
    ) : EspMessage()

    data class CanBaud(
        val baud: Int,
        val ok: Boolean,
    ) : EspMessage()

    data class CanRx(
        val frame: CanFrame,
    ) : EspMessage()
}

data class CanFilterSpec(
    val id: Int,
    val mask: Int? = null,
    val ext: Boolean = false,
)

data class CanFrame(
    val id: Int,
    val ext: Boolean,
    val rtr: Boolean = false,
    val data: ByteArray,
    val tx: Boolean = false,
) {
    val dlc: Int get() = data.size.coerceIn(0, 8)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CanFrame) return false
        return id == other.id &&
            ext == other.ext &&
            rtr == other.rtr &&
            tx == other.tx &&
            data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var h = id
        h = 31 * h + ext.hashCode()
        h = 31 * h + rtr.hashCode()
        h = 31 * h + tx.hashCode()
        h = 31 * h + data.contentHashCode()
        return h
    }
}

/**
 * Accumulates USB chunks into OTA/light binary frames:
 * `0xA5 0x5A | u16be len | payload | u32be crc32(payload)`.
 */
class EspOtaFrameDecoder(
    private val maxPayload: Int = EspCompanionProtocol.OTA_CHUNK_MAX,
) {
    private val buf = ByteArray(4 + maxPayload + 4)
    private var len: Int = 0

    fun reset() {
        len = 0
    }

    fun push(chunk: ByteArray, offset: Int = 0, length: Int = chunk.size): List<ByteArray> {
        val out = ArrayList<ByteArray>(2)
        var i = offset
        val end = (offset + length).coerceAtMost(chunk.size)
        while (i < end) {
            val b = chunk[i]
            i++
            if (len == 0) {
                if (b == OTA_MAGIC0) {
                    buf[0] = b
                    len = 1
                }
                continue
            }
            if (len == 1) {
                if (b == OTA_MAGIC1) {
                    buf[1] = b
                    len = 2
                } else if (b == OTA_MAGIC0) {
                    buf[0] = b
                    len = 1
                } else {
                    len = 0
                }
                continue
            }
            if (len >= buf.size) {
                len = 0
                continue
            }
            buf[len] = b
            len++
            if (len < 4) continue
            val plen = ((buf[2].toInt() and 0xFF) shl 8) or (buf[3].toInt() and 0xFF)
            if (plen <= 0 || plen > maxPayload) {
                len = 0
                continue
            }
            val need = 4 + plen + 4
            if (len < need) continue
            val payload = buf.copyOfRange(4, 4 + plen)
            val crcOff = 4 + plen
            val got = ((buf[crcOff].toLong() and 0xFF) shl 24) or
                ((buf[crcOff + 1].toLong() and 0xFF) shl 16) or
                ((buf[crcOff + 2].toLong() and 0xFF) shl 8) or
                (buf[crcOff + 3].toLong() and 0xFF)
            val expect = EspCompanionProtocol.crc32Ieee(payload)
            if (got == expect) {
                out += payload
            }
            len = 0
        }
        return out
    }

    private companion object {
        private val OTA_MAGIC0: Byte = 0xA5.toByte()
        private val OTA_MAGIC1: Byte = 0x5A.toByte()
    }
}
