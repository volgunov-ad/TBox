package vad.dashing.tbox.fuellevelcalibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.min

class FuelSmartEstimatorTest {

    private fun estimatorForTank(cap: Double): FuelSmartEstimator {
        val sensorMax = min(cap * 48.0 / 50.0, cap - 1e-3)
        return FuelSmartEstimator(
            tankCapacity = cap,
            sensorMin = cap * 2.0 / 50.0,
            sensorMax = sensorMax,
            onCalibrationPersist = {},
        )
    }

    private fun sensorLitersFromPercent(cap: Double, percent: Double): Double =
        cap * percent / 100.0

    @Test
    fun upperRamp_atSensorMax_matchesUncorrectedZoneVolume() {
        val cap = 57.0
        val est = estimatorForTank(cap)
        val sensorMax = cap * 48.0 / 50.0
        val atRampStart = est.getCorrectedLiters(sensorMax, 15.0).litersStandard
        val justBelow = est.getCorrectedLiters(sensorMax - 0.01, 15.0).litersStandard
        assertEquals(atRampStart, justBelow, 0.05)
    }

    @Test
    fun upperRamp_atFullTank_isTankCapacity() {
        val cap = 57.0
        val est = estimatorForTank(cap)
        val full = est.getCorrectedLiters(cap, 15.0).litersStandard
        assertEquals(cap, full, 1e-6)
    }

    @Test
    fun upperRamp_midRange_interpolatesBetweenRampStartAndFull() {
        val cap = 57.0
        val est = estimatorForTank(cap)
        val sensorMax = cap * 48.0 / 50.0
        val low = est.getCorrectedLiters(sensorMax, 15.0).litersStandard
        val midSensor = sensorMax + (cap - sensorMax) * 0.5
        val mid = est.getCorrectedLiters(midSensor, 15.0).litersStandard
        assertTrue(mid > low)
        assertTrue(mid < cap)
        assertEquals(low + (cap - low) * 0.5, mid, 0.05)
    }

    @Test
    fun upperRamp_monotonicInDeadZone() {
        val cap = 57.0
        val est = estimatorForTank(cap)
        val sensorMax = cap * 48.0 / 50.0
        var prev = est.getCorrectedLiters(sensorMax, 15.0).litersStandard
        var sensor = sensorMax + 0.2
        while (sensor < cap) {
            val cur = est.getCorrectedLiters(sensor, 15.0).litersStandard
            assertTrue(cur >= prev - 1e-6)
            prev = cur
            sensor += 0.2
        }
    }

    @Test
    fun upperRamp_noCliffFrom96To95Percent_on57LiterTank() {
        val cap = 57.0
        val est = estimatorForTank(cap)
        val at96 = est.getCorrectedLiters(sensorLitersFromPercent(cap, 96.0), 15.0).litersStandard
        val at95 = est.getCorrectedLiters(sensorLitersFromPercent(cap, 95.0), 15.0).litersStandard
        val drop = at96 - at95
        assertTrue("drop $drop should be less than old cliff ~2.85L", drop < 2.0)
        assertTrue(drop > 0.0)
    }
}
