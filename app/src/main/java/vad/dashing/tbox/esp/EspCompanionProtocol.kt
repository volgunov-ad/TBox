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

    val UM980_BAUD_OPTIONS: List<Int> = listOf(
        9600, 19200, 38400, 57600, 115200, 230400, 460800,
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
}
