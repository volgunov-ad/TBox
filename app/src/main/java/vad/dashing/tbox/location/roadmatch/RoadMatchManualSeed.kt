package vad.dashing.tbox.location.roadmatch

import java.util.concurrent.atomic.AtomicReference
import vad.dashing.tbox.location.ConstantDrMath

/**
 * Manual F3 seed: travel bearing (same as the green shadow tick), not nose heading.
 * [MockLocationJob] consumes this on the next CONSTANT tick — same snap path as
 * hard-resync, but the coordinates come from the tile, not GNSS.
 */
data class RoadMatchManualSeed(
    val lat: Double,
    val lon: Double,
    val travelBearingDeg: Float,
) {
    companion object {
        fun create(lat: Double, lon: Double, travelBearingDeg: Float): RoadMatchManualSeed? {
            if (!lat.isFinite() || !lon.isFinite()) return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            if (lat == 0.0 && lon == 0.0) return null
            if (!travelBearingDeg.isFinite()) return null
            return RoadMatchManualSeed(
                lat = lat,
                lon = lon,
                travelBearingDeg = ConstantDrMath.wrapBearingDeg(travelBearingDeg),
            )
        }
    }
}

object RoadMatchManualSeedRepository {
    private val pending = AtomicReference<RoadMatchManualSeed?>(null)

    fun request(seed: RoadMatchManualSeed) {
        pending.set(seed)
    }

    fun take(): RoadMatchManualSeed? = pending.getAndSet(null)

    fun clear() {
        pending.set(null)
    }

    fun peek(): RoadMatchManualSeed? = pending.get()
}
