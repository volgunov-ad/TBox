package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.AccCruiseDomain

class AccCruiseDomainTest {
    @Test
    fun isEngaged_matchesLauncherModes() {
        assertFalse(AccCruiseDomain.isEngaged(null))
        assertFalse(AccCruiseDomain.isEngaged(0))
        assertFalse(AccCruiseDomain.isEngaged(1))
        assertFalse(AccCruiseDomain.isEngaged(2))
        assertTrue(AccCruiseDomain.isEngaged(3))
        assertTrue(AccCruiseDomain.isEngaged(4))
        assertTrue(AccCruiseDomain.isEngaged(5))
        assertFalse(AccCruiseDomain.isEngaged(6))
    }

    @Test
    fun isStandbyReadyForSet_matchesStandbyModes() {
        assertTrue(AccCruiseDomain.isStandbyReadyForSet(2))
        assertTrue(AccCruiseDomain.isStandbyReadyForSet(6))
        assertFalse(AccCruiseDomain.isStandbyReadyForSet(3))
    }

    @Test
    fun isStandbyDisplay_matchesStockAdasCardModes() {
        assertTrue(AccCruiseDomain.isStandbyDisplay(1))
        assertTrue(AccCruiseDomain.isStandbyDisplay(2))
        assertTrue(AccCruiseDomain.isStandbyDisplay(6))
        assertTrue(AccCruiseDomain.isStandbyDisplay(7))
        assertFalse(AccCruiseDomain.isStandbyDisplay(3))
        assertFalse(AccCruiseDomain.isStandbyDisplay(0))
        assertFalse(AccCruiseDomain.isStandbyDisplay(null))
    }

    @Test
    fun shouldShowAccSetpoint_forEngagedOrStandbyDisplay() {
        assertTrue(AccCruiseDomain.shouldShowAccSetpoint(3))
        assertTrue(AccCruiseDomain.shouldShowAccSetpoint(1))
        assertFalse(AccCruiseDomain.shouldShowAccSetpoint(0))
        assertFalse(AccCruiseDomain.shouldShowAccSetpoint(9))
        assertFalse(AccCruiseDomain.shouldShowAccSetpoint(null))
    }

    @Test
    fun isAccFullyOff_includesNullZeroFault() {
        assertTrue(AccCruiseDomain.isAccFullyOff(null))
        assertTrue(AccCruiseDomain.isAccFullyOff(0))
        assertTrue(AccCruiseDomain.isAccFullyOff(9))
        assertFalse(AccCruiseDomain.isAccFullyOff(2))
        assertFalse(AccCruiseDomain.isAccFullyOff(3))
    }

    @Test
    fun isActiveAtTarget_requiresEngagedAndMatchingSpeed() {
        assertTrue(AccCruiseDomain.isActiveAtTarget(3, 90, 90))
        assertFalse(AccCruiseDomain.isActiveAtTarget(3, 80, 90))
        assertFalse(AccCruiseDomain.isActiveAtTarget(0, 90, 90))
        assertFalse(AccCruiseDomain.isActiveAtTarget(3, null, 90))
    }

    @Test
    fun isCcsEngaged_matchesStockGaspedStatuses() {
        assertFalse(AccCruiseDomain.isCcsEngaged(null))
        assertFalse(AccCruiseDomain.isCcsEngaged(0))
        assertTrue(AccCruiseDomain.isCcsEngaged(1))
        assertTrue(AccCruiseDomain.isCcsEngaged(2))
        assertFalse(AccCruiseDomain.isCcsEngaged(3))
    }

    @Test
    fun isVehicleSpeedAtTarget_usesOneKmhTolerance() {
        assertTrue(AccCruiseDomain.isVehicleSpeedAtTarget(90f, 90))
        assertTrue(AccCruiseDomain.isVehicleSpeedAtTarget(89.4f, 90))
        assertTrue(AccCruiseDomain.isVehicleSpeedAtTarget(90.6f, 90))
        assertFalse(AccCruiseDomain.isVehicleSpeedAtTarget(88f, 90))
        assertFalse(AccCruiseDomain.isVehicleSpeedAtTarget(null, 90))
    }

    @Test
    fun ccsStepDeltaAndBatchSteps() {
        assertEquals(null, AccCruiseDomain.ccsStepDelta(null, 90))
        assertEquals(10, AccCruiseDomain.ccsStepDelta(80f, 90))
        assertEquals(-5, AccCruiseDomain.ccsStepDelta(95f, 90))
        assertEquals(0, AccCruiseDomain.ccsBatchSteps(0))
        assertEquals(0, AccCruiseDomain.ccsBatchSteps(1))
        assertEquals(0, AccCruiseDomain.ccsBatchSteps(-1))
        assertEquals(2, AccCruiseDomain.ccsBatchSteps(2))
        assertEquals(5, AccCruiseDomain.ccsBatchSteps(12))
        assertEquals(5, AccCruiseDomain.ccsBatchSteps(-9))
        assertEquals(3, AccCruiseDomain.ccsBatchSteps(-3))
    }

    @Test
    fun ccsOvershot_respectsDirectionAndBand() {
        assertFalse(AccCruiseDomain.ccsOvershot(90f, 90, increasing = true))
        assertFalse(AccCruiseDomain.ccsOvershot(91f, 90, increasing = true))
        assertTrue(AccCruiseDomain.ccsOvershot(92f, 90, increasing = true))
        assertFalse(AccCruiseDomain.ccsOvershot(89f, 90, increasing = false))
        assertTrue(AccCruiseDomain.ccsOvershot(88f, 90, increasing = false))
        assertFalse(AccCruiseDomain.ccsOvershot(null, 90, increasing = true))
    }

    @Test
    fun ccsSpeedUnchanged_usesOneKmhWindow() {
        assertTrue(AccCruiseDomain.ccsSpeedUnchanged(90f, 90.4f))
        assertTrue(AccCruiseDomain.ccsSpeedUnchanged(90f, 89.1f))
        assertFalse(AccCruiseDomain.ccsSpeedUnchanged(90f, 91.5f))
        assertFalse(AccCruiseDomain.ccsSpeedUnchanged(null, 90f))
        assertFalse(AccCruiseDomain.ccsSpeedUnchanged(90f, null))
    }

    @Test
    fun isCcsActiveAtTarget_requiresStatusAndSpeed() {
        assertTrue(AccCruiseDomain.isCcsActiveAtTarget(1, 90f, 90))
        assertFalse(AccCruiseDomain.isCcsActiveAtTarget(0, 90f, 90))
        assertFalse(AccCruiseDomain.isCcsActiveAtTarget(1, 80f, 90))
        assertFalse(AccCruiseDomain.isCcsActiveAtTarget(null, 90f, 90))
    }

    @Test
    fun shouldUseAccPath_respectsCruiseControlType() {
        assertFalse(AccCruiseDomain.shouldUseAccPath(false))
        assertTrue(AccCruiseDomain.shouldUseAccPath(true))
        assertTrue(AccCruiseDomain.shouldUseAccPath(false, CruiseControlType.ACC))
        assertFalse(AccCruiseDomain.shouldUseAccPath(true, CruiseControlType.CCS))
        assertTrue(AccCruiseDomain.shouldUseAccPath(true, CruiseControlType.AUTO))
        assertFalse(AccCruiseDomain.shouldUseAccPath(false, CruiseControlType.AUTO))
    }

    @Test
    fun shouldUseAccPath_requiresFrmFeedback() {
        assertFalse(AccCruiseDomain.shouldUseAccPath(false))
        assertTrue(AccCruiseDomain.shouldUseAccPath(true))
    }

    @Test
    fun decodeMbCanVSetDis_isUnsignedByteKmh() {
        assertEquals(90, AccCruiseDomain.decodeMbCanVSetDisKmh(90))
        assertEquals(255, AccCruiseDomain.decodeMbCanVSetDisKmh(-1))
    }

    @Test
    fun decodeVhalVSetDis_usesHalfScaleCeil() {
        assertEquals(0, AccCruiseDomain.decodeVhalVSetDisKmh(0))
        assertEquals(1, AccCruiseDomain.decodeVhalVSetDisKmh(1))
        assertEquals(45, AccCruiseDomain.decodeVhalVSetDisKmh(90))
        assertEquals(90, AccCruiseDomain.decodeVhalVSetDisKmh(180))
        assertEquals(91, AccCruiseDomain.decodeVhalVSetDisKmh(181))
    }

    @Test
    fun clampTargetAndIntervals() {
        assertEquals(30, AccCruiseDomain.clampTargetKmh(10))
        assertEquals(150, AccCruiseDomain.clampTargetKmh(200))
        assertEquals(90, AccCruiseDomain.clampTargetKmh(90))
        assertEquals(50, AccCruiseDomain.clampStepIntervalMs(10))
        assertEquals(1500, AccCruiseDomain.clampStepIntervalMs(5000))
        assertEquals(150, AccCruiseDomain.clampStepIntervalMs(150))
    }
}
