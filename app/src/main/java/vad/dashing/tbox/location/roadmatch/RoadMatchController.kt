package vad.dashing.tbox.location.roadmatch

import android.util.Log
import java.io.File
import vad.dashing.tbox.location.GeoDisplayState

/**
 * Single process-wide [RoadMatchRuntime] used both to snap the DR shadow and to
 * feed the OSM speed-limit widget. Callers decide whether to apply the returned pose.
 */
class RoadMatchController(
    mapsDir: () -> File,
) {
    internal val runtime = RoadMatchRuntime(mapsDir = mapsDir)

    /**
     * @return corrected pose when a match ran, even if the caller should not apply it.
     * Null means skip / low confidence / no coverage (caller keeps previous pose).
     */
    fun tick(
        demand: RoadMatchDemand,
        pose: RoadMatchPose?,
        speedKmh: Float,
        nowElapsedMs: Long,
        allowAgainstOneway: Boolean = false,
        turnHint: RoadMapMatcher.TurnHint? = null,
    ): RoadMatchPose? {
        if (!demand.matchNeeded) {
            reset()
            return null
        }
        if (pose == null) {
            publish(demand, allowAgainstOneway)
            return null
        }
        val matched = try {
            runtime.maybeCorrect(
                enabled = true,
                pose = pose,
                speedKmh = speedKmh,
                nowElapsedMs = nowElapsedMs,
                allowAgainstOneway = allowAgainstOneway,
                turnHint = turnHint,
            )
        } catch (oom: OutOfMemoryError) {
            Log.e(TAG, "road match OOM", oom)
            RoadGraphStore.clear()
            runtime.reset()
            publish(demand, allowAgainstOneway)
            return null
        } catch (t: Throwable) {
            Log.e(TAG, "road match failed", t)
            publish(demand, allowAgainstOneway)
            return null
        }
        RoadMatchRuntimeDebug.publish(runtime.debug)
        publish(demand, allowAgainstOneway)
        return matched
    }

    fun reset() {
        runtime.reset()
        RoadMatchRuntimeDebug.clear()
        RoadMatchAnchorRepository.clear()
    }

    private fun publish(demand: RoadMatchDemand, allowAgainstOneway: Boolean) {
        val debug = runtime.debug
        val against = runtime.travelAgainstCoords()
        val lookahead = SpeedLimitLookahead.compute(
            graphs = RoadGraphStore.cachedGraphs(),
            regionId = debug.regionId,
            edgeId = debug.edgeId,
            alongTrackM = debug.alongTrackM,
            travelAgainstCoords = against,
            allowAgainstOneway = allowAgainstOneway,
        )
        RoadMatchAnchorRepository.publish(
            RoadMatchAnchorState.from(
                demand = demand,
                debug = debug,
                travelAgainstCoords = against,
                lookahead = lookahead,
            ),
        )
    }

    companion object {
        private const val TAG = "RoadMatchController"

        fun poseFromDisplay(state: GeoDisplayState): RoadMatchPose? {
            if (!state.latitude.isFinite() || !state.longitude.isFinite()) return null
            if (state.latitude == 0.0 && state.longitude == 0.0) return null
            val bearing = state.bearingDeg ?: return null
            if (!bearing.isFinite()) return null
            return RoadMatchPose(state.latitude, state.longitude, bearing)
        }
    }
}
