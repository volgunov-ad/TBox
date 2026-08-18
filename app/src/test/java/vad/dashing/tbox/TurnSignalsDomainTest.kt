package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.TurnSignalSide
import vad.dashing.tbox.mbcan.TurnSignalsDomain
import vad.dashing.tbox.mbcan.TurnSignalsState

class TurnSignalsDomainTest {

    @Test
    fun decodeMbCanTurnLightActive_stockAutoMapValues() {
        assertTrue(TurnSignalsDomain.decodeMbCanTurnLightActive(2)!!)
        assertFalse(TurnSignalsDomain.decodeMbCanTurnLightActive(0)!!)
        assertFalse(TurnSignalsDomain.decodeMbCanTurnLightActive(1)!!)
        assertNull(TurnSignalsDomain.decodeMbCanTurnLightActive(3))
        assertNull(TurnSignalsDomain.decodeMbCanTurnLightActive(-1))
    }

    @Test
    fun decodeCemBinaryActive_oneIsOn() {
        assertTrue(TurnSignalsDomain.decodeCemBinaryActive(1)!!)
        assertFalse(TurnSignalsDomain.decodeCemBinaryActive(0)!!)
        assertNull(TurnSignalsDomain.decodeCemBinaryActive(2))
    }

    @Test
    fun fromMbCanTurnLightRaw_hazardWhenBothActive() {
        val hazard = TurnSignalsDomain.fromMbCanTurnLightRaw(2, 2)
        assertEquals(true, hazard.leftActive)
        assertEquals(true, hazard.rightActive)
        assertEquals(true, hazard.hazardActive)

        val left = TurnSignalsDomain.fromMbCanTurnLightRaw(2, 0)
        assertEquals(true, left.leftActive)
        assertEquals(false, left.rightActive)
        assertEquals(false, left.hazardActive)

        val off = TurnSignalsDomain.fromMbCanTurnLightRaw(0, 0)
        assertEquals(false, off.hazardActive)
    }

    @Test
    fun effectiveSide_hazardWins() {
        assertEquals(
            TurnSignalSide.Hazard,
            TurnSignalsDomain.effectiveSide(
                TurnSignalsState(leftActive = true, rightActive = true, hazardActive = true),
            ),
        )
        assertEquals(
            TurnSignalSide.Left,
            TurnSignalsDomain.effectiveSide(
                TurnSignalsState(leftActive = true, rightActive = false, hazardActive = false),
            ),
        )
        assertEquals(
            TurnSignalSide.Right,
            TurnSignalsDomain.effectiveSide(
                TurnSignalsState(leftActive = false, rightActive = true, hazardActive = false),
            ),
        )
        assertNull(
            TurnSignalsDomain.effectiveSide(
                TurnSignalsState(leftActive = true, rightActive = true, hazardActive = false),
            ),
        )
    }

    @Test
    fun forkHintSide_leftRightOnlyHazardIgnored() {
        assertEquals(
            TurnSignalSide.Left,
            TurnSignalsDomain.forkHintSide(
                TurnSignalsState(leftActive = true, rightActive = false, hazardActive = false),
            ),
        )
        assertEquals(
            TurnSignalSide.Right,
            TurnSignalsDomain.forkHintSide(
                TurnSignalsState(leftActive = false, rightActive = true, hazardActive = false),
            ),
        )
        assertNull(
            TurnSignalsDomain.forkHintSide(
                TurnSignalsState(leftActive = true, rightActive = true, hazardActive = true),
            ),
        )
        assertNull(
            TurnSignalsDomain.forkHintSide(
                TurnSignalsState(leftActive = false, rightActive = false, hazardActive = false),
            ),
        )
        assertNull(
            TurnSignalsDomain.forkHintSide(TurnSignalsState()),
        )
    }
}
