package vad.dashing.tbox.mbcan

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class HvacClimateCanRepositoryTest {

    @After
    fun tearDown() {
        HvacClimateCanRepository.clearAll()
    }

    @Test
    fun advanceFanSpeed_successiveSteps_useOptimisticLiveBase() {
        HvacClimateCanRepository.applyFanSpeed(2)
        assertEquals(3, HvacClimateCanRepository.advanceFanSpeed(increase = true))
        assertEquals(4, HvacClimateCanRepository.advanceFanSpeed(increase = true))
        assertEquals(5, HvacClimateCanRepository.advanceFanSpeed(increase = true))
        assertEquals(5, HvacClimateCanRepository.hvacFanSpeed.value)
    }

    @Test
    fun advanceTempLeft_successiveSteps_useOptimisticLiveBase() {
        HvacClimateCanRepository.applyTempLeftMbCan(220)
        assertEquals(22.5f, HvacClimateCanRepository.advanceTempLeft(increase = true), 0.001f)
        assertEquals(23.0f, HvacClimateCanRepository.advanceTempLeft(increase = true), 0.001f)
        assertEquals(23.0f, HvacClimateCanRepository.hvacTempLeftCelsius.value!!, 0.001f)
    }
}
