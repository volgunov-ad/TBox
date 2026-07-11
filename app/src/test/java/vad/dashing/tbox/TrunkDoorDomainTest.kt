package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TrunkDoorDisplayState
import vad.dashing.tbox.mbcan.TrunkDoorDomain

class TrunkDoorDomainTest {
    @Test
    fun decodeBinaryOpen_mapsStockDoorStatus() {
        assertEquals(false, TrunkDoorDomain.decodeBinaryOpen(0))
        assertEquals(true, TrunkDoorDomain.decodeBinaryOpen(1))
        assertNull(TrunkDoorDomain.decodeBinaryOpen(2))
        assertNull(TrunkDoorDomain.decodeBinaryOpen(null))
    }

    @Test
    fun displayState_iconColor_openClosedAndMoving() {
        val closed = TrunkDoorDisplayState(isOpen = false, isMoving = false)
        val open = TrunkDoorDisplayState(isOpen = true, isMoving = false)
        val movingClosed = TrunkDoorDisplayState(isOpen = false, isMoving = true)
        val movingOpen = TrunkDoorDisplayState(isOpen = true, isMoving = true)

        assertFalse(TrunkDoorDomain.iconUsesActiveColor(closed))
        assertTrue(TrunkDoorDomain.iconUsesActiveColor(open))
        assertTrue(TrunkDoorDomain.iconUsesActiveColor(movingClosed))
        assertTrue(TrunkDoorDomain.iconUsesActiveColor(movingOpen))
    }

    @Test
    fun pulseTargets_whenStopped() {
        val closed = TrunkDoorDisplayState(isOpen = false, isMoving = false)
        val unknown = TrunkDoorDisplayState(isOpen = null, isMoving = false)
        val open = TrunkDoorDisplayState(isOpen = true, isMoving = false)

        assertTrue(TrunkDoorDomain.shouldPulseOpen(closed))
        assertTrue(TrunkDoorDomain.shouldPulseOpen(unknown))
        assertFalse(TrunkDoorDomain.shouldPulseClose(closed))

        assertTrue(TrunkDoorDomain.shouldPulseClose(open))
        assertFalse(TrunkDoorDomain.shouldPulseOpen(open))
    }

    @Test
    fun pulseStop_onlyWhileMoving() {
        val moving = TrunkDoorDisplayState(isOpen = false, isMoving = true)
        val stopped = TrunkDoorDisplayState(isOpen = true, isMoving = false)

        assertTrue(TrunkDoorDomain.shouldPulseStop(moving))
        assertFalse(TrunkDoorDomain.shouldPulseStop(stopped))
    }

    @Test
    fun moveDirActive_matchesStockRearDoorView() {
        assertTrue(TrunkDoorDomain.isMoveDirActive(0))
        assertTrue(TrunkDoorDomain.isMoveDirActive(1))
        assertFalse(TrunkDoorDomain.isMoveDirActive(2))
    }
}
