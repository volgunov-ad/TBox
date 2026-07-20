package vad.dashing.tbox.utils

import vad.dashing.tbox.LocValues
import vad.dashing.tbox.UtcTime
import vad.dashing.tbox.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Date
import kotlin.math.floor

/**
 * Parses LOC GPS payloads from TBox.
 *
 * Classic firmware packs a fixed 39-byte little-endian struct.
 * Some LOC versions instead forward raw NMEA text (`$GNRMC` / `$GNGGA`, …).
 * The binary path used to always slice bytes `[6, 45)`, which truncated NMEA
 * and produced garbage coordinates when ASCII was read as ints.
 */
object LocPayloadParser {
    private const val BINARY_GPS_SIZE = 39
    private const val KNOTS_TO_KMH = 1.852f

    fun parse(gpsPayload: ByteArray, updateTime: Date = Date()): LocValues? {
        if (gpsPayload.isEmpty()) return null
        return if (looksLikeNmea(gpsPayload)) {
            parseNmea(gpsPayload, updateTime)
        } else {
            parseBinary(gpsPayload, updateTime)
        }
    }

    fun looksLikeNmea(gpsPayload: ByteArray): Boolean {
        if (gpsPayload.isEmpty() || gpsPayload[0] != '$'.code.toByte()) return false
        val preview = String(
            gpsPayload,
            0,
            minOf(gpsPayload.size, 16),
            Charsets.US_ASCII,
        )
        return preview.contains("RMC", ignoreCase = true) ||
            preview.contains("GGA", ignoreCase = true) ||
            preview.startsWith("\$GP", ignoreCase = true) ||
            preview.startsWith("\$GN", ignoreCase = true) ||
            preview.startsWith("\$GL", ignoreCase = true) ||
            preview.startsWith("\$BD", ignoreCase = true) ||
            preview.startsWith("\$GB", ignoreCase = true)
    }

    fun parseBinary(gpsPayload: ByteArray, updateTime: Date = Date()): LocValues? {
        if (gpsPayload.size < BINARY_GPS_SIZE) return null
        val rawValue = toHexString(gpsPayload.copyOfRange(0, BINARY_GPS_SIZE))
        val buffer = ByteBuffer.wrap(gpsPayload, 0, BINARY_GPS_SIZE).order(ByteOrder.LITTLE_ENDIAN)

        val locateStatus = buffer.get().toInt() and 0xFF != 0
        val utcTime = UtcTime(
            year = buffer.get().toInt() and 0xFF,
            month = buffer.get().toInt() and 0xFF,
            day = buffer.get().toInt() and 0xFF,
            hour = buffer.get().toInt() and 0xFF,
            minute = buffer.get().toInt() and 0xFF,
            second = buffer.get().toInt() and 0xFF,
        )
        val longitudeDirection = buffer.get().toInt() and 0xFF
        val rawLongitude = buffer.int
        val longitude = rawLongitude.toDouble() / 1_000_000.0 *
            if (longitudeDirection == 1) -1 else 1
        val latitudeDirection = buffer.get().toInt() and 0xFF
        val rawLatitude = buffer.int
        val latitude = rawLatitude.toDouble() / 1_000_000.0 *
            if (latitudeDirection == 1) -1 else 1
        val rawAltitude = buffer.int
        val altitude = rawAltitude.toDouble() / 1_000_000.0
        val visibleSatellites = buffer.get().toInt() and 0xFF
        val usingSatellites = buffer.get().toInt() and 0xFF
        val speed = (buffer.short.toInt() and 0xFFFF) / 10f
        val trueDirection = (buffer.short.toInt() and 0xFFFF) / 10f
        val magneticDirection = (buffer.short.toInt() and 0xFFFF) / 10f

        return LocValues(
            rawValue = rawValue,
            locateStatus = locateStatus,
            utcTime = utcTime,
            longitude = longitude,
            latitude = latitude,
            altitude = altitude,
            visibleSatellites = visibleSatellites,
            usingSatellites = usingSatellites,
            speed = speed,
            trueDirection = trueDirection,
            magneticDirection = magneticDirection,
            updateTime = updateTime,
        )
    }

    fun parseNmea(gpsPayload: ByteArray, updateTime: Date = Date()): LocValues {
        val text = String(gpsPayload, Charsets.US_ASCII)
            .trim { it <= ' ' || it == '\u0000' }
        val rawValue = toHexString(gpsPayload)

        var locateStatus = false
        var latitude = 0.0
        var longitude = 0.0
        var altitude = 0.0
        var visibleSatellites = 0
        var usingSatellites = 0
        var speed = 0f
        var trueDirection = 0f
        var magneticDirection = 0f
        var utcTime: UtcTime? = null

        for (sentence in extractNmeaSentences(text)) {
            val fields = splitNmeaFields(sentence)
            if (fields.isEmpty()) continue
            val type = fields[0].uppercase()
            when {
                type.endsWith("RMC") -> {
                    val parsed = parseRmc(fields) ?: continue
                    locateStatus = parsed.locateStatus
                    latitude = parsed.latitude
                    longitude = parsed.longitude
                    speed = parsed.speedKmh
                    trueDirection = parsed.trueDirection
                    magneticDirection = parsed.trueDirection
                    utcTime = parsed.utcTime ?: utcTime
                }
                type.endsWith("GGA") -> {
                    val parsed = parseGga(fields) ?: continue
                    if (parsed.locateStatus) {
                        locateStatus = true
                        latitude = parsed.latitude
                        longitude = parsed.longitude
                    } else if (!locateStatus) {
                        // Keep RMC fix if GGA reports no fix.
                    }
                    altitude = parsed.altitude
                    usingSatellites = parsed.usingSatellites
                    if (visibleSatellites == 0) {
                        visibleSatellites = parsed.usingSatellites
                    }
                    utcTime = parsed.utcTime ?: utcTime
                }
            }
        }

        if ((latitude == 0.0 && longitude == 0.0 && altitude == 0.0) || !locateStatus) {
            locateStatus = false
        }

        return LocValues(
            rawValue = rawValue,
            locateStatus = locateStatus,
            utcTime = utcTime,
            longitude = longitude,
            latitude = latitude,
            altitude = altitude,
            visibleSatellites = visibleSatellites,
            usingSatellites = usingSatellites,
            speed = speed,
            trueDirection = trueDirection,
            magneticDirection = magneticDirection,
            updateTime = updateTime,
        )
    }

    internal fun extractNmeaSentences(text: String): List<String> {
        val result = ArrayList<String>()
        var start = -1
        for (i in text.indices) {
            val c = text[i]
            when {
                c == '$' -> start = i
                start >= 0 && (c == '\r' || c == '\n') -> {
                    val sentence = text.substring(start, i).trimEnd()
                    if (sentence.length > 5) result.add(sentence)
                    start = -1
                }
            }
        }
        if (start >= 0 && start < text.length) {
            val sentence = text.substring(start).trimEnd()
            if (sentence.length > 5) result.add(sentence)
        }
        return result
    }

    private fun splitNmeaFields(sentence: String): List<String> {
        val body = sentence
            .removePrefix("$")
            .substringBefore('*')
        return body.split(',')
    }

    private data class RmcParsed(
        val locateStatus: Boolean,
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Float,
        val trueDirection: Float,
        val utcTime: UtcTime?,
    )

    private data class GgaParsed(
        val locateStatus: Boolean,
        val latitude: Double,
        val longitude: Double,
        val altitude: Double,
        val usingSatellites: Int,
        val utcTime: UtcTime?,
    )

    private fun parseRmc(fields: List<String>): RmcParsed? {
        // $--RMC,time,status,lat,N/S,lon,E/W,speed,course,date,...
        if (fields.size < 10) return null
        val status = fields.getOrNull(2)?.uppercase().orEmpty()
        val locateStatus = status == "A"
        val latitude = parseNmeaCoordinate(fields.getOrNull(3), fields.getOrNull(4))
        val longitude = parseNmeaCoordinate(fields.getOrNull(5), fields.getOrNull(6))
        val speedKmh = fields.getOrNull(7)?.toFloatOrNull()?.times(KNOTS_TO_KMH) ?: 0f
        val trueDirection = fields.getOrNull(8)?.toFloatOrNull() ?: 0f
        val utcTime = parseNmeaUtc(fields.getOrNull(1), fields.getOrNull(9))
        return RmcParsed(
            locateStatus = locateStatus && latitude != null && longitude != null,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            speedKmh = speedKmh,
            trueDirection = trueDirection,
            utcTime = utcTime,
        )
    }

    private fun parseGga(fields: List<String>): GgaParsed? {
        // $--GGA,time,lat,N/S,lon,E/W,quality,numSV,HDOP,alt,M,...
        if (fields.size < 10) return null
        val quality = fields.getOrNull(6)?.toIntOrNull() ?: 0
        val locateStatus = quality > 0
        val latitude = parseNmeaCoordinate(fields.getOrNull(2), fields.getOrNull(3))
        val longitude = parseNmeaCoordinate(fields.getOrNull(4), fields.getOrNull(5))
        val usingSatellites = fields.getOrNull(7)?.toIntOrNull() ?: 0
        val altitude = fields.getOrNull(9)?.toDoubleOrNull() ?: 0.0
        val utcTime = parseNmeaUtc(fields.getOrNull(1), dateField = null)
        return GgaParsed(
            locateStatus = locateStatus && latitude != null && longitude != null,
            latitude = latitude ?: 0.0,
            longitude = longitude ?: 0.0,
            altitude = altitude,
            usingSatellites = usingSatellites,
            utcTime = utcTime,
        )
    }

    /**
     * NMEA `ddmm.mmmm` / `dddmm.mmmm` → decimal degrees.
     */
    internal fun parseNmeaCoordinate(value: String?, hemisphere: String?): Double? {
        if (value.isNullOrBlank() || hemisphere.isNullOrBlank()) return null
        val raw = value.toDoubleOrNull() ?: return null
        val absRaw = kotlin.math.abs(raw)
        val degrees = floor(absRaw / 100.0)
        val minutes = absRaw - degrees * 100.0
        if (minutes >= 60.0) return null
        var decimal = degrees + minutes / 60.0
        when (hemisphere.uppercase()) {
            "S", "W" -> decimal = -decimal
            "N", "E" -> Unit
            else -> return null
        }
        return decimal
    }

    private fun parseNmeaUtc(timeField: String?, dateField: String?): UtcTime? {
        if (timeField.isNullOrBlank() || timeField.length < 6) return null
        val hour = timeField.substring(0, 2).toIntOrNull() ?: return null
        val minute = timeField.substring(2, 4).toIntOrNull() ?: return null
        val second = timeField.substring(4, 6).toIntOrNull() ?: return null
        var day = 0
        var month = 0
        var year = 0
        if (!dateField.isNullOrBlank() && dateField.length >= 6) {
            day = dateField.substring(0, 2).toIntOrNull() ?: 0
            month = dateField.substring(2, 4).toIntOrNull() ?: 0
            year = dateField.substring(4, 6).toIntOrNull() ?: 0
        }
        return UtcTime(
            year = year,
            month = month,
            day = day,
            hour = hour,
            minute = minute,
            second = second,
        )
    }
}
