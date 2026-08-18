package vad.dashing.tbox.location

/**
 * Hidden GNSS truth for geo-debug / field replay: live receiver even when mock
 * is in CONSTANT / simulatedLoss and [LocValues] is treated as untruthful.
 *
 * Priority: this-tick NMEA RMC → published locValues with coords → Android
 * last-known → previously cached fix (with growing [Fix.ageMs]).
 */
object GeoDebugHiddenTruth {
    data class Fix(
        val lat: Double,
        val lon: Double,
        val courseDeg: Float? = null,
        val src: String,
        val accM: Float? = null,
        val ageMs: Long = 0L,
    )

    fun isValidCoord(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && (lat != 0.0 || lon != 0.0)

    fun parseRmc(sentence: String): Fix? {
        val raw = sentence.trim().removePrefix("nmea|")
        if (raw.isEmpty()) return null
        val star = raw.indexOf('*')
        val body = if (star >= 0) raw.substring(0, star) else raw
        val parts = body.split(',')
        val talker = parts.getOrNull(0) ?: return null
        if (!talker.endsWith("RMC")) return null
        if (parts.getOrNull(2) != "A") return null
        val lat = nmeaDegMin(parts.getOrNull(3).orEmpty(), parts.getOrNull(4).orEmpty())
            ?: return null
        val lon = nmeaDegMin(parts.getOrNull(5).orEmpty(), parts.getOrNull(6).orEmpty())
            ?: return null
        if (!isValidCoord(lat, lon)) return null
        val course = parts.getOrNull(8)?.toFloatOrNull()?.takeIf { it.isFinite() }
        return Fix(lat = lat, lon = lon, courseDeg = course, src = "nmea", ageMs = 0L)
    }

    fun firstValidRmc(sentences: Iterable<String>): Fix? =
        sentences.asSequence().mapNotNull(::parseRmc).firstOrNull()

    fun fromPublished(
        lat: Double,
        lon: Double,
        courseDeg: Float?,
        src: String,
        accM: Float?,
        ageMs: Long,
    ): Fix? {
        if (!isValidCoord(lat, lon)) return null
        return Fix(
            lat = lat,
            lon = lon,
            courseDeg = courseDeg?.takeIf { it.isFinite() },
            src = src,
            accM = accM?.takeIf { it.isFinite() && it > 0f },
            ageMs = ageMs.coerceAtLeast(0L),
        )
    }

    fun select(
        nmea: Fix?,
        locValues: Fix?,
        lastKnown: Fix?,
        cached: Fix?,
        nowElapsedMs: Long,
        cachedAtElapsedMs: Long?,
    ): Fix? {
        nmea?.let { return it.copy(src = "nmea", ageMs = 0L) }
        locValues?.let { return it }
        lastKnown?.let { return it }
        if (cached != null && cachedAtElapsedMs != null) {
            return cached.copy(ageMs = (nowElapsedMs - cachedAtElapsedMs).coerceAtLeast(0L))
        }
        return null
    }

    fun replayPose(
        preMatchLat: Double?,
        preMatchLon: Double?,
        preMatchBearing: Float?,
        mockLat: Double?,
        mockLon: Double?,
        mockBearing: Float?,
    ): Triple<Double, Double, Float>? {
        val lat = preMatchLat ?: mockLat ?: return null
        val lon = preMatchLon ?: mockLon ?: return null
        val bearing = preMatchBearing ?: mockBearing ?: return null
        if (!isValidCoord(lat, lon) || !bearing.isFinite()) return null
        return Triple(lat, lon, bearing)
    }

    fun replayTruth(
        truthLat: Double?,
        truthLon: Double?,
        truthCourse: Float?,
        nmea: Fix?,
    ): Fix? {
        if (truthLat != null && truthLon != null && isValidCoord(truthLat, truthLon)) {
            return Fix(
                lat = truthLat,
                lon = truthLon,
                courseDeg = truthCourse,
                src = "truth",
            )
        }
        return nmea
    }

    internal fun nmeaDegMin(raw: String, hemi: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        val deg = (value / 100.0).toInt()
        val minutes = value - deg * 100.0
        var out = deg + minutes / 60.0
        if (hemi == "S" || hemi == "W") out = -out
        return out
    }
}
