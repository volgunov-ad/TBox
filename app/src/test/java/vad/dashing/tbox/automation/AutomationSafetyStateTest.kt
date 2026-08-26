package vad.dashing.tbox.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationSafetyStateTest {
    @Test
    fun emptySources_failClosed() {
        assertFalse(
            AutomationSafetyState.isStationaryInPark(
                availableSources = emptySet(),
                samples = parkedSamples(AutomationSignalSource.TBOX),
            ),
        )
    }

    @Test
    fun missingSpeedOrGear_failClosed() {
        val speedOnly = mapOf(
            AutomationSignalKey(AutomationSignalId.CAR_SPEED, AutomationSignalSource.TBOX) to
                sample(
                    AutomationSignalId.CAR_SPEED,
                    AutomationSignalSource.TBOX,
                    AutomationSignalValue.Number(0.0),
                ),
        )
        assertFalse(
            AutomationSafetyState.isStationaryInPark(
                availableSources = setOf(AutomationSignalSource.TBOX),
                samples = speedOnly,
            ),
        )
    }

    @Test
    fun movingOrDrive_failClosed() {
        assertFalse(
            AutomationSafetyState.isStationaryInPark(
                availableSources = setOf(AutomationSignalSource.TBOX),
                samples = samples(
                    AutomationSignalSource.TBOX,
                    speed = 1.0,
                    gear = "P",
                ),
            ),
        )
        assertFalse(
            AutomationSafetyState.isStationaryInPark(
                availableSources = setOf(AutomationSignalSource.TBOX),
                samples = samples(
                    AutomationSignalSource.TBOX,
                    speed = 0.0,
                    gear = "D",
                ),
            ),
        )
    }

    @Test
    fun allLiveSourcesMustConfirmPark() {
        val tboxParked = parkedSamples(AutomationSignalSource.TBOX)
        val headUnitMoving = samples(
            AutomationSignalSource.HEAD_UNIT,
            speed = 5.0,
            gear = "P",
        )
        assertFalse(
            AutomationSafetyState.isStationaryInPark(
                availableSources = setOf(
                    AutomationSignalSource.TBOX,
                    AutomationSignalSource.HEAD_UNIT,
                ),
                samples = tboxParked + headUnitMoving,
            ),
        )
        assertTrue(
            AutomationSafetyState.isStationaryInPark(
                availableSources = setOf(
                    AutomationSignalSource.TBOX,
                    AutomationSignalSource.HEAD_UNIT,
                ),
                samples = parkedSamples(AutomationSignalSource.TBOX) +
                    parkedSamples(AutomationSignalSource.HEAD_UNIT),
            ),
        )
    }

    @Test
    fun unavailableSample_isRemovedFromSnapshot() {
        AutomationSafetyState.clear()
        val key = AutomationSignalKey(AutomationSignalId.CAR_SPEED, AutomationSignalSource.TBOX)
        AutomationSafetyState.update(
            AutomationSignalSample(key, AutomationSignalValue.Number(0.0), 0L),
        )
        assertTrue(key in AutomationSafetyState.snapshot())
        AutomationSafetyState.update(
            AutomationSignalSample(key, AutomationSignalValue.Unavailable, 1L),
        )
        assertFalse(key in AutomationSafetyState.snapshot())
        AutomationSafetyState.clear()
    }

    private fun parkedSamples(source: AutomationSignalSource) =
        samples(source, speed = 0.0, gear = "P")

    private fun samples(
        source: AutomationSignalSource,
        speed: Double,
        gear: String,
    ): Map<AutomationSignalKey, AutomationSignalSample> = mapOf(
        AutomationSignalKey(AutomationSignalId.CAR_SPEED, source) to
            sample(AutomationSignalId.CAR_SPEED, source, AutomationSignalValue.Number(speed)),
        AutomationSignalKey(AutomationSignalId.GEAR_MODE, source) to
            sample(AutomationSignalId.GEAR_MODE, source, AutomationSignalValue.State(gear)),
    )

    private fun sample(
        signal: AutomationSignalId,
        source: AutomationSignalSource,
        value: AutomationSignalValue,
    ) = AutomationSignalSample(
        AutomationSignalKey(signal, source),
        value,
        0L,
    )
}
