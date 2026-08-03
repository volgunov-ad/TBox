package vad.dashing.tbox.location

import vad.dashing.tbox.LocValues

/**
 * Builds [GeoDisplayState] for the navigation / geoposition widgets when mock is **not**
 * pushing: show source data, but if junk detection is on and the live point is junk,
 * keep showing the last usable point (speed / bearing / sats) with [GeoDisplayState.liveUsable]=false.
 */
object GeoDisplaySourcePassthrough {
    data class Result(
        val state: GeoDisplayState,
        val lastUsable: LocValues?,
    )

    fun next(
        live: LocValues,
        liveUsable: Boolean,
        junkFilterOn: Boolean,
        lastUsable: LocValues?,
    ): Result {
        if (liveUsable) {
            return Result(
                state = GeoDisplayState.fromLive(live, liveUsable = true, mockActive = false),
                lastUsable = live,
            )
        }
        val junk = MockLocationJob.isJunkLive(live, junkFilterOn, liveUsable)
        if (junk && lastUsable != null && MockLocationJob.hasValidCoordinates(lastUsable)) {
            return Result(
                state = GeoDisplayState.fromLive(
                    loc = lastUsable,
                    liveUsable = false,
                    mockActive = false,
                ).copy(
                    // Keep live satellite counts when source still reports them.
                    visibleSatellites = live.visibleSatellites.takeIf { it > 0 }
                        ?: lastUsable.visibleSatellites,
                    usingSatellites = live.usingSatellites.takeIf { it > 0 }
                        ?: lastUsable.usingSatellites,
                ),
                lastUsable = lastUsable,
            )
        }
        return Result(
            state = GeoDisplayState.fromLive(live, liveUsable = false, mockActive = false),
            lastUsable = lastUsable,
        )
    }
}
