package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues

enum class LocIndicatorState {
    /** No fix / invalid coords and not retaining. */
    NONE,
    /** Live GNSS point accepted (same gate as mock). */
    LIVE,
    /** Mock retention / DR holding last point. */
    RETAINING,
    /** Live point lost, mock not retaining. */
    LOST,
}

enum class GeoSpeedSource {
    GNSS,
    CAN,
    RETENTION,
}

enum class GeoBearingSource {
    GNSS,
    RETENTION,
    HELD,
}

/**
 * Published geoposition snapshot for widgets / indicator / truth.
 * When mock is on, matches what is pushed to the Android mock provider.
 *
 * [liveUsable] drives the green/blue arrow (mock blend / passthrough).
 * [gnssTruthful] is receiver trust (junk-[MockLocationJob.isLiveUsable]) for the
 * «Правдивость» row — independent of CONSTANT soft-blend weight.
 */
data class GeoDisplayState(
    val liveUsable: Boolean = false,
    val retaining: Boolean = false,
    val locateStatus: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Double = 0.0,
    val speedKmh: Float = 0f,
    val speedSource: GeoSpeedSource = GeoSpeedSource.GNSS,
    val bearingDeg: Float? = null,
    val bearingSource: GeoBearingSource = GeoBearingSource.HELD,
    val hasReliableBearing: Boolean = false,
    val visibleSatellites: Int = 0,
    val usingSatellites: Int = 0,
    val mockActive: Boolean = false,
    /** GNSS receiver trustworthy (fix + coords + optional junk filter). */
    val gnssTruthful: Boolean = false,
) {
    val indicator: LocIndicatorState
        get() = when {
            liveUsable -> LocIndicatorState.LIVE
            retaining -> LocIndicatorState.RETAINING
            !locateStatus && latitude == 0.0 && longitude == 0.0 -> LocIndicatorState.NONE
            else -> LocIndicatorState.LOST
        }

    /** Menu «правдивость» = GNSS trust, not arrow color / blend gate. */
    val isTruthful: Boolean get() = gnssTruthful

    fun toLocValues(): LocValues = LocValues(
        locateStatus = locateStatus,
        latitude = latitude,
        longitude = longitude,
        altitude = altitude,
        speed = speedKmh,
        trueDirection = bearingDeg ?: 0f,
        visibleSatellites = visibleSatellites,
        usingSatellites = usingSatellites,
    )

    companion object {
        val EMPTY = GeoDisplayState()

        fun fromLive(
            loc: LocValues,
            liveUsable: Boolean,
            speedKmh: Float = loc.speed,
            speedSource: GeoSpeedSource = GeoSpeedSource.GNSS,
            bearingDeg: Float? = loc.trueDirection.takeIf { it != 0f },
            bearingSource: GeoBearingSource = if (bearingDeg != null) {
                GeoBearingSource.GNSS
            } else {
                GeoBearingSource.HELD
            },
            mockActive: Boolean = false,
            gnssTruthful: Boolean = liveUsable,
        ): GeoDisplayState = GeoDisplayState(
            liveUsable = liveUsable,
            retaining = false,
            locateStatus = loc.locateStatus,
            latitude = loc.latitude,
            longitude = loc.longitude,
            altitude = loc.altitude,
            speedKmh = speedKmh,
            speedSource = speedSource,
            bearingDeg = bearingDeg,
            bearingSource = bearingSource,
            hasReliableBearing = bearingDeg != null,
            visibleSatellites = loc.visibleSatellites,
            usingSatellites = loc.usingSatellites,
            mockActive = mockActive,
            gnssTruthful = gnssTruthful,
        )
    }
}
