package vad.dashing.tbox

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TrunkDoorDisplayState
import vad.dashing.tbox.mbcan.TrunkDoorDomain
import vad.dashing.tbox.mbcan.TrunkIconTint
import vad.dashing.tbox.mbcan.TrunkMovement

class TrunkDoorDomainTest {
    private val idleColor = Color.White
    private val openColor = Color.Blue
    private val orangeColor = Color(0xFFF3A721)

    @Test
    fun decodeBinaryOpenVhal_mapsStockRearDoorStatus() {
        assertEquals(false, TrunkDoorDomain.decodeBinaryOpenVhal(0))
        assertEquals(true, TrunkDoorDomain.decodeBinaryOpenVhal(1))
        assertNull(TrunkDoorDomain.decodeBinaryOpenVhal(2))
        assertNull(TrunkDoorDomain.decodeBinaryOpenVhal(null))
    }

    @Test
    fun decodeBinaryOpenMbCan_mapsDashingBcmTrunkSts() {
        assertEquals(false, TrunkDoorDomain.decodeBinaryOpenMbCan(0))
        assertEquals(false, TrunkDoorDomain.decodeBinaryOpenMbCan(1))
        assertEquals(true, TrunkDoorDomain.decodeBinaryOpenMbCan(2))
        assertNull(TrunkDoorDomain.decodeBinaryOpenMbCan(3))
        assertNull(TrunkDoorDomain.decodeBinaryOpenMbCan(null))
    }

    @Test
    fun movementFromMoveDir_matchesStockRearDoorView() {
        assertEquals(TrunkMovement.Closing, TrunkDoorDomain.movementFromMoveDir(0))
        assertEquals(TrunkMovement.Opening, TrunkDoorDomain.movementFromMoveDir(1))
        assertEquals(TrunkMovement.Stopped, TrunkDoorDomain.movementFromMoveDir(2))
        assertEquals(TrunkMovement.Stopped, TrunkDoorDomain.movementFromMoveDir(null))
    }

    @Test
    fun resolveIconTint_stoppedOpenAndClosed() {
        val closed = TrunkDoorDisplayState(isOpen = false, moveDir = 2)
        val openStopped = TrunkDoorDisplayState(isOpen = true, moveDir = 2)

        assertEquals(
            TrunkIconTint.Solid(idleColor),
            TrunkDoorDomain.resolveIconTint(closed, idleColor, openColor, orangeColor),
        )
        assertEquals(
            TrunkIconTint.Solid(openColor),
            TrunkDoorDomain.resolveIconTint(openStopped, idleColor, openColor, orangeColor),
        )
    }

    @Test
    fun resolveIconTint_movingStatesPulseBetweenExpectedColors() {
        val opening = TrunkDoorDisplayState(isOpen = null, moveDir = 1)
        val closing = TrunkDoorDisplayState(isOpen = true, moveDir = 0)

        assertEquals(
            TrunkIconTint.Pulsing(from = orangeColor, to = openColor),
            TrunkDoorDomain.resolveIconTint(opening, idleColor, openColor, orangeColor),
        )
        assertEquals(
            TrunkIconTint.Pulsing(from = openColor, to = idleColor),
            TrunkDoorDomain.resolveIconTint(closing, idleColor, openColor, orangeColor),
        )
    }

    @Test
    fun pulseTargets_whenStopped() {
        val closed = TrunkDoorDisplayState(isOpen = false, moveDir = 2)
        val unknown = TrunkDoorDisplayState(isOpen = null, moveDir = 2)
        val openStopped = TrunkDoorDisplayState(isOpen = true, moveDir = 2)

        assertTrue(TrunkDoorDomain.shouldPulseOpen(closed))
        assertTrue(TrunkDoorDomain.shouldPulseOpen(unknown))
        assertFalse(TrunkDoorDomain.shouldPulseClose(closed))

        assertTrue(TrunkDoorDomain.shouldPulseClose(openStopped))
        assertFalse(TrunkDoorDomain.shouldPulseOpen(openStopped))
    }

    @Test
    fun pulseStop_onlyWhileMoving() {
        val moving = TrunkDoorDisplayState(isOpen = false, moveDir = 1)
        val stopped = TrunkDoorDisplayState(isOpen = true, moveDir = 2)

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
