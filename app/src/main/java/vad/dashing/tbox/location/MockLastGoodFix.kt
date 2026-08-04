package vad.dashing.tbox.location

import org.json.JSONObject
import vad.dashing.tbox.LocValues

/**
 * Last accepted live GNSS point for mock cold-start seed (disk-backed).
 */
data class MockLastGoodFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    /** Degrees; 0 means unknown / do not restore as heading. */
    val bearingDeg: Float,
    val savedAtEpochMs: Long,
) {
    fun toLocValues(): LocValues = LocValues(
        locateStatus = true,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        trueDirection = bearingDeg,
        speed = 0f,
    )

    fun isFresh(nowEpochMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean {
        if (savedAtEpochMs <= 0L || nowEpochMs < savedAtEpochMs) return false
        return nowEpochMs - savedAtEpochMs <= maxAgeMs
    }

    fun toJson(): String = JSONObject()
        .put("lat", latitude)
        .put("lon", longitude)
        .put("alt", altitude)
        .put("bearing", bearingDeg.toDouble())
        .put("savedAt", savedAtEpochMs)
        .toString()

    companion object {
        /** Do not cold-start from a point older than this. */
        const val MAX_AGE_MS = 24L * 60L * 60L * 1_000L

        fun fromLive(
            loc: LocValues,
            savedAtEpochMs: Long,
            bearingOverride: Float? = null,
        ): MockLastGoodFix? {
            if (!MockLocationJob.hasValidCoordinates(loc)) return null
            val bearing = when {
                bearingOverride != null && bearingOverride != 0f -> bearingOverride
                loc.trueDirection != 0f -> loc.trueDirection
                else -> 0f
            }
            return MockLastGoodFix(
                latitude = loc.latitude,
                longitude = loc.longitude,
                altitude = loc.altitude,
                bearingDeg = bearing,
                savedAtEpochMs = savedAtEpochMs,
            )
        }

        fun fromJson(raw: String?): MockLastGoodFix? {
            if (raw.isNullOrBlank()) return null
            return try {
                val o = JSONObject(raw)
                val lat = o.getDouble("lat")
                val lon = o.getDouble("lon")
                val fix = MockLastGoodFix(
                    latitude = lat,
                    longitude = lon,
                    altitude = o.optDouble("alt", 0.0),
                    bearingDeg = o.optDouble("bearing", 0.0).toFloat(),
                    savedAtEpochMs = o.optLong("savedAt", 0L),
                )
                if (!MockLocationJob.hasValidCoordinates(fix.toLocValues())) null else fix
            } catch (_: Exception) {
                null
            }
        }

        fun canUseForColdStart(mode: MockCanSpeedMode): Boolean =
            mode == MockCanSpeedMode.ALWAYS ||
                mode == MockCanSpeedMode.WHEN_FIX_LOST ||
                mode == MockCanSpeedMode.CONSTANT
    }
}

/**
 * Debounces disk writes for [MockLastGoodFix] (default 60 s).
 */
class MockLastGoodFixDebouncer(
    private val debounceMs: Long = DEBOUNCE_MS,
) {
    companion object {
        const val DEBOUNCE_MS = 60_000L
    }

    var pending: MockLastGoodFix? = null
        private set
    /** ElapsedRealtime of last disk write; null if never written this session. */
    private var lastWriteElapsedMs: Long? = null

    /**
     * Remember [fix]. Returns it when a disk write should happen now; otherwise null
     * (caller must [takeFlush] on shutdown).
     */
    fun note(fix: MockLastGoodFix, nowElapsedMs: Long): MockLastGoodFix? {
        pending = fix
        val last = lastWriteElapsedMs
        if (last == null || nowElapsedMs - last >= debounceMs) {
            lastWriteElapsedMs = nowElapsedMs
            pending = null
            return fix
        }
        return null
    }

    /** Pending fix not yet written, if any. */
    fun takeFlush(): MockLastGoodFix? {
        val p = pending
        pending = null
        return p
    }
}
