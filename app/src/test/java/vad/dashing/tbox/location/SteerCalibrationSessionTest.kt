package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import vad.dashing.tbox.LocValues
import kotlin.math.abs

class SteerCalibrationSessionTest {

    @Before
    fun resetStore() {
        SteerCalibrationStore.reset()
        SteerCalibrationStore.update(SteerCalibrationOffsets(deadzoneDeg = 2f))
    }

    private fun live(speed: Float, course: Float) = LocValues(
        locateStatus = true,
        latitude = 55.0,
        longitude = 37.0,
        speed = speed,
        trueDirection = if (course == 0f) 0.1f else course,
    )

    private fun syntheticArc(
        wheelDeg: Float,
        speedKmh: Float,
        scale: Float,
        startBearing: Float,
        startMs: Long,
    ): List<SteerCalibrationMath.SteerSample> {
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = startMs
        var bearing = startBearing
        samples.add(SteerCalibrationMath.SteerSample(wheelDeg, bearing, speedKmh, t))
        val dtMs = 100L
        repeat(40) {
            t += dtMs
            val dPsi = SteerHeadingIntegrator.yawDeltaDeg(
                centeredWheelDeg = wheelDeg,
                speedMps = speedKmh / 3.6f,
                dtSec = dtMs / 1000.0,
                scale = scale,
                sign = 1,
                applyInternalDeadzone = true,
                deadzoneDeg = 2f,
            )
            bearing += dPsi
            samples.add(SteerCalibrationMath.SteerSample(wheelDeg, bearing, speedKmh, t))
        }
        return samples
    }

    private fun feed(
        session: SteerCalibrationSession,
        samples: List<SteerCalibrationMath.SteerSample>,
    ) {
        for (s in samples) {
            session.onTick(
                elapsedMs = s.elapsedMs,
                liveUsable = true,
                live = live(s.speedKmh, s.bearingDeg),
                canKmh = s.speedKmh,
                centeredSteerDeg = s.centeredSteerDeg,
                horizontalAccuracyM = 5f,
            )
        }
    }

    @Test
    fun missingWheelDoesNotPauseSession() {
        val session = SteerCalibrationSession()
        session.start(0L)
        assertFalse(
            session.onTick(
                elapsedMs = 1_000L,
                liveUsable = true,
                live = live(40f, 90f),
                canKmh = 40f,
                centeredSteerDeg = null,
                horizontalAccuracyM = 5f,
            ),
        )
        assertEquals(SteerCalibrationSession.Phase.RUNNING, session.uiState().phase)
        assertEquals(DriveCalibrationMath.PauseKind.NONE, session.uiState().pause)
    }

    @Test
    fun reverseClearsBuffer() {
        val session = SteerCalibrationSession()
        session.start(0L)
        assertFalse(
            session.onTick(
                elapsedMs = 1_000L,
                liveUsable = true,
                live = live(40f, 90f),
                canKmh = 40f,
                centeredSteerDeg = 20f,
                horizontalAccuracyM = 5f,
                reverseEngaged = true,
            ),
        )
        assertEquals(DriveCalibrationMath.PauseKind.REVERSE, session.uiState().pause)
    }

    @Test
    fun mergeKeepsZeroDeadzoneWheelbase() {
        val prev = SteerCalibrationOffsets(
            zeroDeg = 3.5f,
            deadzoneDeg = 4f,
            wheelbaseM = 2.9f,
            scaleProfile = SteerScaleProfile.uniform(0.05f),
        )
        val est = SteerCalibrationMath.SteerScaleEstimate(
            sign = 1,
            scaleProfile = SteerScaleProfile.uniform(0.08f),
            segmentCount = 10,
            leftCount = 5,
            rightCount = 5,
        )
        val merged = SteerCalibrationMath.mergeWithPrevious(est, prev, 99L)
        assertEquals(3.5f, merged.zeroDeg, 0f)
        assertEquals(4f, merged.deadzoneDeg, 0f)
        assertEquals(2.9f, merged.wheelbaseM, 0f)
        assertEquals(0.08f, merged.scaleProfile.at40Kmh, 1e-4f)
        assertTrue(merged.scaleEstimated)
    }

    @Test
    fun fittedArcsMakeSessionAutoReady() {
        val session = SteerCalibrationSession()
        session.start(1_000L)
        val trueScale = 0.08f
        val samples = ArrayList<SteerCalibrationMath.SteerSample>()
        var t = 1_000L
        repeat(5) { idx ->
            val speed = listOf(20f, 40f, 60f, 80f, 40f)[idx]
            val arc = syntheticArc(
                wheelDeg = 90f,
                speedKmh = speed,
                scale = trueScale,
                startBearing = 90f - idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        repeat(5) { idx ->
            val speed = listOf(20f, 40f, 60f, 80f, 60f)[idx]
            val arc = syntheticArc(
                wheelDeg = -90f,
                speedKmh = speed,
                scale = trueScale,
                startBearing = -90f + idx * 40f,
                startMs = t,
            )
            samples.addAll(arc)
            t = arc.last().elapsedMs + 2_000L
        }
        feed(session, samples)
        session.onTick(
            elapsedMs = t + 1_000L,
            liveUsable = true,
            live = live(40f, 45f),
            canKmh = 40f,
            centeredSteerDeg = 0f,
            horizontalAccuracyM = 5f,
        )
        assertTrue(
            "ready L=${session.uiState().fittedLeft} R=${session.uiState().fittedRight} " +
                "buckets=${session.uiState().profileSpeedBuckets}",
            session.isAutoReady(),
        )
        val off = session.finishToPreview(1_000L, SteerCalibrationStore.offsets)
        assertNotNull(off)
        assertTrue(off!!.scaleEstimated)
        assertEquals(1, off.sign)
        assertTrue(abs(off.scaleProfile.at40Kmh - trueScale) < 0.03f)
        assertEquals(SteerCalibrationSession.Phase.PREVIEW, session.uiState().phase)
    }

    @Test
    fun timeoutFollowsDriveWindow() {
        val session = SteerCalibrationSession()
        session.start(1_000L)
        assertFalse(session.isTimedOut(1_000L + SteerCalibrationSession.SESSION_TIMEOUT_MS - 1L))
        assertTrue(session.isTimedOut(1_000L + SteerCalibrationSession.SESSION_TIMEOUT_MS))
    }
}
