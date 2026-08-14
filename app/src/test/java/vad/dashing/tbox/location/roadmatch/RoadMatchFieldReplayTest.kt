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
        /** Hidden / live GNSS truth from NMEA when present in the journal. */
        val truthLat: Double? = null,
        val truthLon: Double? = null,
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
        val truthLags = ArrayList<Double>()
        val posNudges = ArrayList<Double>()
        val crossTracks = ArrayList<Double>()
        var posNudgeTurnSumM = 0.0
        var turnActiveTicks = 0
        var lagAt190430: Double? = null
        var lagAt190435: Double? = null
        var lagAt190440: Double? = null
        // Reshetikha sharp-turn window on 2026-08-14 074349 (elapsed ≈ 07:46:53–07:47:15).
        var reshetikhaNudgeSumM = 0.0
        var reshetikhaEastM = 0.0
        var reshetikhaRawEastM = 0.0

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
            val before = sim
            val result = runtime.maybeCorrect(
                enabled = true,
                pose = sim,
                speedKmh = tick.speedKmh,
                nowElapsedMs = tick.elapsedMs,
                allowAgainstOneway = tick.reverse,
            )
            if (result != null) {
                val nudge = RoadGraph.haversineM(before.lat, before.lon, result.lat, result.lon)
                posNudges.add(nudge)
                if (runtime.debug.turnActive == true) {
                    posNudgeTurnSumM += nudge
                }
                if (tick.elapsedMs in 504_000L..527_000L) {
                    reshetikhaNudgeSumM += nudge
                    reshetikhaEastM += eastMeters(before.lat, before.lon, result.lat, result.lon)
                }
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
            if (debug.turnActive == true) turnActiveTicks++
            debug.crossTrackM?.let(crossTracks::add)
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
            val tLat = tick.truthLat
            val tLon = tick.truthLon
            if (tLat != null && tLon != null && tick.speedKmh >= 5f) {
                val lag = RoadGraph.haversineM(sim.lat, sim.lon, tLat, tLon)
                truthLags.add(lag)
                // Roundabout entry markers from the 2026-08-13 Nizhny field log.
                if (tick.elapsedMs in 685_000L..686_500L) lagAt190430 = lag
                if (tick.elapsedMs in 690_000L..691_500L) lagAt190435 = lag
                if (tick.elapsedMs in 695_000L..696_500L) lagAt190440 = lag
            }
            if (index > 0 && tick.elapsedMs in 504_000L..527_000L) {
                reshetikhaRawEastM += eastMeters(
                    previousRaw.lat, previousRaw.lon, tick.lat, tick.lon,
                )
            }
            previousRaw = tick
        }

        assertTrue(
            "no graph loaded for ${log.name}; check bundle install suffix/coverage",
            noGraph < ticks.size,
        )
        val sortedLags = truthLags.sorted()
        val sortedNudges = posNudges.sorted()
        val sortedXt = crossTracks.sorted()
        fun percentile(values: List<Double>, p: Double): Double? {
            if (values.isEmpty()) return null
            val idx = ((values.size - 1) * p).toInt().coerceIn(0, values.lastIndex)
            return values[idx]
        }
        fun percentile(p: Double): Double? = percentile(sortedLags, p)
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
            .put("truthLagSamples", truthLags.size)
            .put("truthLagMeanM", if (truthLags.isEmpty()) JSONObject.NULL else truthLags.average())
            .put("truthLagP50M", percentile(0.50) ?: JSONObject.NULL)
            .put("truthLagP95M", percentile(0.95) ?: JSONObject.NULL)
            .put("truthLagMaxM", if (truthLags.isEmpty()) JSONObject.NULL else sortedLags.last())
            .put("truthLagAt190430M", lagAt190430 ?: JSONObject.NULL)
            .put("truthLagAt190435M", lagAt190435 ?: JSONObject.NULL)
            .put("truthLagAt190440M", lagAt190440 ?: JSONObject.NULL)
            .put("posNudgeMeanM", if (posNudges.isEmpty()) JSONObject.NULL else posNudges.average())
            .put("posNudgeP95M", percentile(sortedNudges, 0.95) ?: JSONObject.NULL)
            .put("posNudgeTurnSumM", posNudgeTurnSumM)
            .put("crossTrackMeanM", if (crossTracks.isEmpty()) JSONObject.NULL else crossTracks.average())
            .put("crossTrackP95M", percentile(sortedXt, 0.95) ?: JSONObject.NULL)
            .put("turnActiveTicks", turnActiveTicks)
            .put("reshetikhaNudgeSumM", reshetikhaNudgeSumM)
            .put("reshetikhaEastM", reshetikhaEastM)
            .put("reshetikhaRawEastM", reshetikhaRawEastM)
    }

    private fun eastMeters(fromLat: Double, fromLon: Double, toLat: Double, toLon: Double): Double {
        val meanLat = Math.toRadians((fromLat + toLat) / 2.0)
        return (toLon - fromLon) * 111_320.0 * kotlin.math.cos(meanLat)
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
        var truthLat: Double? = null
        var truthLon: Double? = null

        fun flush() {
            val e = elapsedMs
            val la = lat
            val lo = lon
            val b = bearing
            if (e != null && la != null && lo != null && b != null) {
                out.add(
                    Tick(
                        e, la, lo, b, speed ?: 0f, reverse, hardResync,
                        truthLat = truthLat,
                        truthLon = truthLon,
                    ),
                )
            }
            lat = null
            lon = null
            bearing = null
            speed = null
            reverse = false
            hardResync = false
            truthLat = null
            truthLon = null
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
            } else if (line.startsWith("nmea|\$GNRMC") && truthLat == null) {
                parseNmeaLatLon(line)?.let { (tLat, tLon) ->
                    truthLat = tLat
                    truthLon = tLon
                }
            }
        }
        flush()
        return out
    }

    private fun parseNmeaLatLon(line: String): Pair<Double, Double>? {
        // $GNRMC,hhmmss.ss,A,ddmm.mmmm,N,dddmm.mmmm,E,...
        val parts = line.substringAfter("nmea|", line).split(',')
        if (parts.size < 7) return null
        if (parts.getOrNull(2) != "A") return null
        val lat = nmeaDegMin(parts[3], parts[4]) ?: return null
        val lon = nmeaDegMin(parts[5], parts[6]) ?: return null
        return lat to lon
    }

    private fun nmeaDegMin(raw: String, hemi: String): Double? {
        val value = raw.toDoubleOrNull() ?: return null
        val deg = (value / 100.0).toInt()
        val minutes = value - deg * 100.0
        var out = deg + minutes / 60.0
        if (hemi == "S" || hemi == "W") out = -out
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
