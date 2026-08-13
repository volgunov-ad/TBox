package vad.dashing.tbox.location.roadmatch

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.math.max

/**
 * Optional deterministic replay of field geo-debug logs through the production matcher.
 *
 * The test is skipped in normal unit runs. [tools/run_road_match_replay.py] supplies
 * external logs + an installed bundle and collects the JSON report.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchFieldReplayTest {
    private data class Tick(
        val elapsedMs: Long,
        val lat: Double,
        val lon: Double,
        val bearingDeg: Float,
        val speedKmh: Float,
        val reverse: Boolean,
        val hardResync: Boolean,
    )

    @Test
    fun replayExternalGeoDebugLogs() {
        val mapsDir = System.getenv("TBOX_ROADMATCH_REPLAY_MAPS_DIR")?.let(::File)
        val logs = System.getenv("TBOX_ROADMATCH_REPLAY_LOGS")
            ?.split(File.pathSeparator)
            ?.filter { it.isNotBlank() }
            ?.map(::File)
            .orEmpty()
        val reportFile = System.getenv("TBOX_ROADMATCH_REPLAY_REPORT")?.let(::File)
        assumeTrue("external replay inputs not configured", mapsDir?.isDirectory == true && logs.isNotEmpty())

        val reports = JSONArray()
        for (log in logs) {
            assertTrue("missing replay log: $log", log.isFile)
            reports.put(replay(log, mapsDir!!))
            RoadGraphStore.clear()
        }
        val root = JSONObject()
            .put("format", 1)
            .put("mapsDir", mapsDir!!.absolutePath)
            .put("logs", reports)
        if (reportFile != null) {
            reportFile.parentFile?.mkdirs()
            reportFile.writeText(root.toString(2))
        }
        println("ROAD_MATCH_REPLAY=${root}")
    }

    private fun replay(log: File, mapsDir: File): JSONObject {
        val ticks = parseTicks(log)
        assertTrue("no replayable mock ticks in $log", ticks.size >= 2)
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir })
        var sim = RoadMatchPose(ticks.first().lat, ticks.first().lon, ticks.first().bearingDeg)
        var previousRaw = ticks.first()
        var corrected = 0
        var switches = 0
        var high = 0
        var medium = 0
        var holdEdge = 0
        var connectedCorridor = 0
        var low = 0
        var noCandidate = 0
        var noGraph = 0
        var nearRejected = 0
        var fastBearingCatchups = 0
        var maxBearingCorrectionDeg = 0f
        var maxMovingNoCorrectionTicks = 0
        var movingNoCorrectionTicks = 0
        val edgeIds = linkedSetOf<Long>()
        val rejectReasons = linkedMapOf<String, Int>()

        for ((index, tick) in ticks.withIndex()) {
            if (index > 0) {
                // Replay recorded trajectory deltas on top of the corrected simulation pose.
                sim = sim.copy(
                    lat = sim.lat + (tick.lat - previousRaw.lat),
                    lon = sim.lon + (tick.lon - previousRaw.lon),
                    bearingDeg = normalizeDeg(
                        sim.bearingDeg + signedAngleDelta(previousRaw.bearingDeg, tick.bearingDeg),
                    ),
                )
            }
            if (tick.hardResync) {
                // Production hard-resync snaps pose and clears matcher state.
                sim = RoadMatchPose(tick.lat, tick.lon, tick.bearingDeg)
                runtime.reset()
            }
            val result = runtime.maybeCorrect(
                enabled = true,
                pose = sim,
                speedKmh = tick.speedKmh,
                nowElapsedMs = tick.elapsedMs,
                allowAgainstOneway = tick.reverse,
            )
            if (result != null) {
                sim = result
                corrected++
                movingNoCorrectionTicks = 0
            } else if (tick.speedKmh >= 5f) {
                movingNoCorrectionTicks++
                maxMovingNoCorrectionTicks = max(
                    maxMovingNoCorrectionTicks,
                    movingNoCorrectionTicks,
                )
            }
            val debug = runtime.debug
            debug.edgeId?.let(edgeIds::add)
            if (debug.switchedEdge) switches++
            val bearingCorrection = kotlin.math.abs(debug.bearingDeltaDeg ?: 0f)
            if (bearingCorrection > RoadMapMatcher.MAX_BEARING_STEP_DEG + 0.05f) {
                fastBearingCatchups++
            }
            maxBearingCorrectionDeg = max(maxBearingCorrectionDeg, bearingCorrection)
            when (debug.confidence) {
                RoadMatchConfidence.HIGH.name -> high++
                RoadMatchConfidence.MEDIUM.name -> medium++
                "HOLD_EDGE" -> holdEdge++
                "CONNECTED_CORRIDOR" -> connectedCorridor++
                RoadMatchConfidence.LOW.name -> low++
                RoadMatchConfidence.NONE.name -> noCandidate++
            }
            if (debug.skippedReason == "no_graph") noGraph++
            val rejected = debug.skippedReason == "low_confidence" ||
                debug.skippedReason == "switch_rejected" ||
                debug.skippedReason == "switch_pending"
            if (rejected && (debug.candidateCrossTrackM ?: Double.POSITIVE_INFINITY) <= 20.0) {
                nearRejected++
            }
            debug.rejectReason?.let { reason ->
                rejectReasons[reason] = (rejectReasons[reason] ?: 0) + 1
            }
            previousRaw = tick
        }

        assertTrue(
            "no graph loaded for ${log.name}; check bundle install suffix/coverage",
            noGraph < ticks.size,
        )
        return JSONObject()
            .put("file", log.name)
            .put("ticks", ticks.size)
            .put("corrections", corrected)
            .put("correctionRate", corrected.toDouble() / ticks.size)
            .put("high", high)
            .put("medium", medium)
            .put("holdEdge", holdEdge)
            .put("connectedCorridor", connectedCorridor)
            .put("low", low)
            .put("noCandidate", noCandidate)
            .put("noGraph", noGraph)
            .put("switches", switches)
            .put("uniqueEdges", edgeIds.size)
            .put("nearRejected", nearRejected)
            .put("fastBearingCatchups", fastBearingCatchups)
            .put("maxBearingCorrectionDeg", maxBearingCorrectionDeg.toDouble())
            .put("maxMovingNoCorrectionTicks", maxMovingNoCorrectionTicks)
            .put("rejectReasons", JSONObject(rejectReasons as Map<*, *>))
    }

    private fun parseTicks(file: File): List<Tick> {
        val out = ArrayList<Tick>()
        var elapsedMs: Long? = null
        var lat: Double? = null
        var lon: Double? = null
        var bearing: Float? = null
        var speed: Float? = null
        var reverse = false
        var hardResync = false

        fun flush() {
            val e = elapsedMs
            val la = lat
            val lo = lon
            val b = bearing
            if (e != null && la != null && lo != null && b != null) {
                out.add(Tick(e, la, lo, b, speed ?: 0f, reverse, hardResync))
            }
            lat = null
            lon = null
            bearing = null
            speed = null
            reverse = false
            hardResync = false
        }

        file.forEachLine { line ->
            if (line.startsWith("--- ")) {
                flush()
                elapsedMs = Regex("""elapsedMs=(\d+)""").find(line)?.groupValues?.get(1)?.toLong()
            } else if (line.startsWith("mock.lat=")) {
                lat = value(line, "mock.lat")?.toDoubleOrNull()
                lon = value(line, "lon")?.toDoubleOrNull()
                bearing = value(line, "bearing")?.toFloatOrNull()
                speed = value(line, "speedKmh")?.toFloatOrNull()
            } else if (line.startsWith("can.accountingKmh=")) {
                speed = value(line, "can.accountingKmh")?.toFloatOrNull() ?: speed
            } else if (line.startsWith("constant.shadowDistM=")) {
                hardResync = value(line, "hardResync") == "true"
            } else if (line.startsWith("reverse.consider=")) {
                reverse = value(line, "huPrnd") == "R" || value(line, "tboxPrnd") == "R"
            }
        }
        flush()
        return out
    }

    private fun value(line: String, key: String): String? =
        Regex("""(?:^|\s)${Regex.escape(key)}=([^\s]+)""").find(line)?.groupValues?.get(1)

    private fun signedAngleDelta(from: Float, to: Float): Float {
        var delta = (to - from) % 360f
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f
        return delta
    }

    private fun normalizeDeg(value: Float): Float {
        var out = value % 360f
        if (out < 0f) out += 360f
        return out
    }
}
