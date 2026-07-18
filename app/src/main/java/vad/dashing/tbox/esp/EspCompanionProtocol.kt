package vad.dashing.tbox.esp

import org.json.JSONObject
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.UtcTime
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** NDJSON protocol v1 between HU (USB Host) and ESP32-S3 companion. */
object EspCompanionProtocol {
    const val PROTOCOL_VERSION = 1
    const val HEARTBEAT_TIMEOUT_MS = 5_000L

    const val TYPE_HELLO = "hello"
    const val TYPE_HB = "hb"
    const val TYPE_GPS = "gps"
    const val TYPE_GPIO = "gpio"
    const val TYPE_GPIO_EVENT = "gpioEvent"
    const val TYPE_RELAY = "relay"
    const val TYPE_RELAY_SET = "relaySet"

    fun encodeHello(): String = line(TYPE_HELLO)

    fun encodeRelaySet(mask: Int): String =
        line(TYPE_RELAY_SET, mapOf("mask" to (mask and 0xFF)))

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
}
