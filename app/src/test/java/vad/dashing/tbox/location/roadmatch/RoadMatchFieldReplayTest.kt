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
        val truthBearingDeg: Float? = null,
        /** Logged map-match yaw on this tick; used to recover DR heading. */
        val loggedBearingDeltaDeg: Float = 0f,
        val loggedHighway: String? = null,
        val dDistM: Double? = null,
        val dYawDebDeg: Float? = null,
        val yawScale: Float? = null,
        val yawSign: Float = 1f,
        val turnHint: RoadMapMatcher.TurnHint? = null,
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
        val kinematicMode = System.getenv("TBOX_ROADMATCH_REPLAY_KINEMATIC").orEmpty()
        val kinematic = kinematicMode == "1" || kinematicMode == "strip" || kinematicMode == "gyro"
        assumeTrue("external replay inputs not configured", mapsDir?.isDirectory == true && logs.isNotEmpty())

        val reports = JSONArray()
        for (log in logs) {
            assertTrue("missing replay log: $log", log.isFile)
            reports.put(replay(log, mapsDir!!, kinematicMode))
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

    private fun replay(log: File, mapsDir: File, kinematicMode: String): JSONObject {
        val kinematic = kinematicMode == "1" || kinematicMode == "strip" || kinematicMode == "gyro"
        val gyroOnly = kinematicMode == "gyro"
        val ticks = parseTicks(log)
        assertTrue("no replayable mock ticks in $log", ticks.size >= 2)
        val runtime = RoadMatchRuntime(mapsDir = { mapsDir })
        var sim = RoadMatchPose(ticks.first().lat, ticks.first().lon, ticks.first().bearingDeg)
        var previousRaw = ticks.first()
        val resetAtElapsedMs = System.getenv("TBOX_ROADMATCH_REPLAY_RESET_AT_ELAPSED")?.toLongOrNull()
        var didWindowReset = false
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
        var lagAt190430: Double? = null
        var lagAt190435: Double? = null
        var lagAt190440: Double? = null
        var linkFastCatchups = 0
        var leashBreaks = 0
        var freePromotes = 0
        var leashStretchTicks = 0
        var junctionTicks = 0
        val headingErrs = ArrayList<Double>()
        val traceFile = System.getenv("TBOX_ROADMATCH_REPLAY_TRACE")?.let(::File)
        val resetUseNmea = System.getenv("TBOX_ROADMATCH_REPLAY_RESET_USE_NMEA") == "1"
        val trace = if (traceFile != null) {
            StringBuilder("elapsedMs,edgeId,highway,hint,conf,lagM,lat,lon,bearing\n")
        } else {
            null
        }
        val ringEdges = linkedSetOf<Long>()
        var ringHintTicks = 0
        var ringLagMaxM = 0.0
        var tookEastExit = false

        for ((index, tick) in ticks.withIndex()) {
            if (index > 0) {
                val loggedYaw = signedAngleDelta(previousRaw.bearingDeg, tick.bearingDeg)
                if (kinematic) {
                    val scale = (tick.yawScale ?: 1f) * tick.yawSign
                    val drYaw = if (gyroOnly && tick.dYawDebDeg != null) {
                        tick.dYawDebDeg * scale
                    } else {
                        // Keep hybrid/steer from the field; strip this tick's match yaw.
                        loggedYaw - tick.loggedBearingDeltaDeg
                    }
                    val heading = normalizeDeg(sim.bearingDeg + drYaw)
                    val pathM = tick.dDistM
                        ?: RoadGraph.haversineM(
                            previousRaw.lat, previousRaw.lon, tick.lat, tick.lon,
                        )
                    val dest = destination(sim.lat, sim.lon, heading, pathM)
                    sim = RoadMatchPose(dest.first, dest.second, heading)
                } else {
                    // Replay recorded trajectory deltas on top of the corrected simulation pose.
                    sim = sim.copy(
                        lat = sim.lat + (tick.lat - previousRaw.lat),
                        lon = sim.lon + (tick.lon - previousRaw.lon),
                        bearingDeg = normalizeDeg(sim.bearingDeg + loggedYaw),
                    )
                }
            }
            if (tick.hardResync) {
                // Production hard-resync snaps pose and clears matcher state.
                sim = RoadMatchPose(tick.lat, tick.lon, tick.bearingDeg)
                runtime.reset()
            }
            if (!didWindowReset && resetAtElapsedMs != null && tick.elapsedMs >= resetAtElapsedMs) {
                val seedLat = if (resetUseNmea) tick.truthLat ?: tick.lat else tick.lat
                val seedLon = if (resetUseNmea) tick.truthLon ?: tick.lon else tick.lon
                val seedBrg = if (resetUseNmea) tick.truthBearingDeg ?: tick.bearingDeg else tick.bearingDeg
                sim = RoadMatchPose(seedLat, seedLon, seedBrg)
                runtime.reset()
                didWindowReset = true
            }
            val result = runtime.maybeCorrect(
                enabled = true,
                pose = sim,
                speedKmh = tick.speedKmh,
                nowElapsedMs = tick.elapsedMs,
                allowAgainstOneway = tick.reverse,
                turnHint = tick.turnHint,
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
                if (debug.highwayClass?.endsWith("_link") == true) {
                    linkFastCatchups++
                }
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
            if (debug.rejectReason == "leash_break") leashBreaks++
            if (debug.leash == "stretch") leashStretchTicks++
            if (debug.freePromoted) freePromotes++
            if (debug.junction) junctionTicks++
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
            val ringDist = RoadGraph.haversineM(sim.lat, sim.lon, 56.2576, 43.4676)
            if (ringDist <= 280.0) {
                debug.edgeId?.let(ringEdges::add)
                if (debug.turnHint != null) ringHintTicks++
                if (debug.edgeId == 20609L || debug.edgeId == 20623L || debug.edgeId == 20622L) {
                    tookEastExit = true
                }
            }
            val tLag = if (tLat != null && tLon != null) {
                RoadGraph.haversineM(sim.lat, sim.lon, tLat, tLon)
            } else {
                null
            }
            if (tLag != null && ringDist <= 280.0 && tick.speedKmh >= 5f) {
                ringLagMaxM = max(ringLagMaxM, tLag)
            }
            if (trace != null) {
                trace.append(tick.elapsedMs).append(',')
                    .append(debug.edgeId ?: "").append(',')
                    .append(debug.highwayClass ?: "").append(',')
                    .append(debug.turnHint ?: "").append(',')
                    .append(debug.confidence ?: "").append(',')
                    .append(tLag ?: "").append(',')
                    .append(sim.lat).append(',')
                    .append(sim.lon).append(',')
                    .append(sim.bearingDeg).append('\n')
            }
            val truthBrg = tick.truthBearingDeg
            if (truthBrg != null && tick.speedKmh >= 15f) {
                headingErrs.add(
                    RoadMapMatcher.smallestAngleDeg(sim.bearingDeg, truthBrg).toDouble(),
                )
            }
            previousRaw = tick
        }

        assertTrue(
            "no graph loaded for ${log.name}; check bundle install suffix/coverage",
            noGraph < ticks.size,
        )
        val sortedLags = truthLags.sorted()
        val sortedHeading = headingErrs.sorted()
        fun percentile(values: List<Double>, p: Double): Double? {
            if (values.isEmpty()) return null
            val idx = ((values.size - 1) * p).toInt().coerceIn(0, values.lastIndex)
            return values[idx]
        }
        return JSONObject()
            .put("file", log.name)
            .put("kinematic", kinematic)
            .put("kinematicMode", if (kinematicMode.isEmpty()) "off" else kinematicMode)
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
            .put("linkFastCatchups", linkFastCatchups)
            .put("maxBearingCorrectionDeg", maxBearingCorrectionDeg.toDouble())
            .put("maxMovingNoCorrectionTicks", maxMovingNoCorrectionTicks)
            .put("rejectReasons", JSONObject(rejectReasons as Map<*, *>))
            .put("truthLagSamples", truthLags.size)
            .put("truthLagMeanM", if (truthLags.isEmpty()) JSONObject.NULL else truthLags.average())
            .put("truthLagP50M", percentile(sortedLags, 0.50) ?: JSONObject.NULL)
            .put("truthLagP95M", percentile(sortedLags, 0.95) ?: JSONObject.NULL)
            .put("truthLagMaxM", if (truthLags.isEmpty()) JSONObject.NULL else sortedLags.last())
            .put("truthLagAt190430M", lagAt190430 ?: JSONObject.NULL)
            .put("truthLagAt190435M", lagAt190435 ?: JSONObject.NULL)
            .put("truthLagAt190440M", lagAt190440 ?: JSONObject.NULL)
            .put("headingErrSamples", headingErrs.size)
            .put("headingErrMeanDeg", if (headingErrs.isEmpty()) JSONObject.NULL else headingErrs.average())
            .put("headingErrP50Deg", percentile(sortedHeading, 0.50) ?: JSONObject.NULL)
            .put("headingErrP95Deg", percentile(sortedHeading, 0.95) ?: JSONObject.NULL)
            .put("headingErrMaxDeg", if (headingErrs.isEmpty()) JSONObject.NULL else sortedHeading.last())
            .put("finalLat", sim.lat)
            .put("finalLon", sim.lon)
            .put("finalBearingDeg", sim.bearingDeg.toDouble())
            .put("ringHintTicks", ringHintTicks)
            .put("ringLagMaxM", ringLagMaxM)
            .put("ringTookEastExit", tookEastExit)
            .put("ringEdges", JSONArray(ringEdges.toList()))
            .put("leashBreaks", leashBreaks)
            .put("leashStretchTicks", leashStretchTicks)
            .put("freePromotes", freePromotes)
            .put("junctionTicks", junctionTicks)
            .also {
                if (traceFile != null && trace != null) {
                    traceFile.parentFile?.mkdirs()
                    traceFile.writeText(trace.toString())
                }
            }
    }

    private fun parseTicks(file: File): List<Tick> {
        val out = ArrayList<Tick>()
        var elapsedMs: Long? = null
        var mockLat: Double? = null
        var mockLon: Double? = null
        var mockBearing: Float? = null
        var preLat: Double? = null
        var preLon: Double? = null
        var preBearing: Float? = null
        var speed: Float? = null
        var reverse = false
        var hardResync = false
        var truthLineLat: Double? = null
        var truthLineLon: Double? = null
        var truthLineCourse: Float? = null
        var nmeaFix: vad.dashing.tbox.location.GeoDebugHiddenTruth.Fix? = null
        var loggedBearingDelta = 0f
        var loggedHighway: String? = null
        var dDistM: Double? = null
        var dYawDebDeg: Float? = null
        var yawScale: Float? = null
        var yawSign = 1f
        var turnHint: RoadMapMatcher.TurnHint? = null
        val replayLatch = vad.dashing.tbox.mbcan.TurnSignalsLatch()

        fun flush() {
            val e = elapsedMs
            val pose = vad.dashing.tbox.location.GeoDebugHiddenTruth.replayPose(
                preMatchLat = preLat,
                preMatchLon = preLon,
                preMatchBearing = preBearing,
                mockLat = mockLat,
                mockLon = mockLon,
                mockBearing = mockBearing,
            )
            if (e != null && pose != null) {
                val truth = vad.dashing.tbox.location.GeoDebugHiddenTruth.replayTruth(
                    truthLat = truthLineLat,
                    truthLon = truthLineLon,
                    truthCourse = truthLineCourse,
                    nmea = nmeaFix,
                )
                out.add(
                    Tick(
                        e, pose.first, pose.second, pose.third, speed ?: 0f, reverse, hardResync,
                        truthLat = truth?.lat,
                        truthLon = truth?.lon,
                        truthBearingDeg = truth?.courseDeg,
                        loggedBearingDeltaDeg = loggedBearingDelta,
                        loggedHighway = loggedHighway,
                        dDistM = dDistM,
                        dYawDebDeg = dYawDebDeg,
                        yawScale = yawScale,
                        yawSign = yawSign,
                        turnHint = turnHint,
                    ),
                )
            }
            mockLat = null
            mockLon = null
            mockBearing = null
            preLat = null
            preLon = null
            preBearing = null
            speed = null
            reverse = false
            hardResync = false
            truthLineLat = null
            truthLineLon = null
            truthLineCourse = null
            nmeaFix = null
            loggedBearingDelta = 0f
            loggedHighway = null
            dDistM = null
            dYawDebDeg = null
            yawScale = null
            yawSign = 1f
            turnHint = null
        }

        file.forEachLine { line ->
            if (line.startsWith("--- ")) {
                flush()
                elapsedMs = Regex("""elapsedMs=(\d+)""").find(line)?.groupValues?.get(1)?.toLong()
            } else if (line.startsWith("mock.lat=")) {
                mockLat = value(line, "mock.lat")?.toDoubleOrNull()
                mockLon = value(line, "lon")?.toDoubleOrNull()
                mockBearing = value(line, "bearing")?.toFloatOrNull()
                speed = value(line, "speedKmh")?.toFloatOrNull()
            } else if (line.startsWith("preMatch.lat=")) {
                preLat = value(line, "preMatch.lat")?.toDoubleOrNull()
                preLon = value(line, "preMatch.lon")?.toDoubleOrNull()
                preBearing = value(line, "preMatch.bearing")?.toFloatOrNull()
            } else if (line.startsWith("truth.lat=")) {
                truthLineLat = value(line, "truth.lat")?.toDoubleOrNull()
                truthLineLon = value(line, "truth.lon")?.toDoubleOrNull()
                truthLineCourse = value(line, "truth.course")?.toFloatOrNull()
            } else if (line.startsWith("can.accountingKmh=")) {
                speed = value(line, "can.accountingKmh")?.toFloatOrNull() ?: speed
            } else if (line.startsWith("constant.shadowDistM=")) {
                hardResync = value(line, "hardResync") == "true"
            } else if (line.startsWith("reverse.consider=")) {
                reverse = value(line, "huPrnd") == "R" || value(line, "tboxPrnd") == "R"
            } else if (line.startsWith("integ.distM=")) {
                dDistM = value(line, "dDistM")?.toDoubleOrNull()
                dYawDebDeg = value(line, "dYawDebDeg")?.toFloatOrNull()
            } else if (line.startsWith("calib.biasYaw=")) {
                yawScale = value(line, "yawScale")?.toFloatOrNull()
                yawSign = value(line, "yawSign")?.toFloatOrNull() ?: 1f
            } else if (line.startsWith("mapMatch.active=")) {
                loggedBearingDelta = value(line, "bearingDeltaDeg")?.toFloatOrNull() ?: 0f
                loggedHighway = value(line, "highway")?.takeIf { it != "-" }
            } else if (line.startsWith("turn.left=")) {
                val now = elapsedMs ?: 0L
                val loggedLatched = value(line, "turn.latched")
                turnHint = if (loggedLatched != null) {
                    when (loggedLatched) {
                        "L" -> RoadMapMatcher.TurnHint.Left
                        "R" -> RoadMapMatcher.TurnHint.Right
                        else -> null
                    }
                } else {
                    val state = vad.dashing.tbox.mbcan.TurnSignalsState(
                        leftActive = triBool(value(line, "turn.left")),
                        rightActive = triBool(value(line, "turn.right") ?: value(line, "right")),
                        hazardActive = triBool(value(line, "turn.hazard") ?: value(line, "hazard")),
                    )
                    when (replayLatch.onState(state, now)) {
                        vad.dashing.tbox.mbcan.TurnSignalSide.Left ->
                            RoadMapMatcher.TurnHint.Left
                        vad.dashing.tbox.mbcan.TurnSignalSide.Right ->
                            RoadMapMatcher.TurnHint.Right
                        else -> null
                    }
                }
            } else if (line.startsWith("nmea|") && nmeaFix == null) {
                nmeaFix = vad.dashing.tbox.location.GeoDebugHiddenTruth.parseRmc(line)
            }
        }
        flush()
        return out
    }

    private fun destination(
        lat: Double,
        lon: Double,
        bearingDeg: Float,
        distM: Double,
    ): Pair<Double, Double> {
        if (distM < 1e-6) return lat to lon
        val br = Math.toRadians(bearingDeg.toDouble())
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val ang = distM / 6_371_000.0
        val lat2 = kotlin.math.asin(
            kotlin.math.sin(lat1) * kotlin.math.cos(ang) +
                kotlin.math.cos(lat1) * kotlin.math.sin(ang) * kotlin.math.cos(br),
        )
        val lon2 = lon1 + kotlin.math.atan2(
            kotlin.math.sin(br) * kotlin.math.sin(ang) * kotlin.math.cos(lat1),
            kotlin.math.cos(ang) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2),
        )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

    private fun value(line: String, key: String): String? =
        Regex("""(?:^|\s)${Regex.escape(key)}=([^\s]+)""").find(line)?.groupValues?.get(1)

    private fun triBool(raw: String?): Boolean? = when (raw) {
        "true" -> true
        "false" -> false
        else -> null
    }

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
