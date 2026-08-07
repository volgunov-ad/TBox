package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.mbcan.CcsRememberedSetpoint

class CcsRememberedSetpointTest {
    @Test
    fun stalkEnterActive_noRemembered_capturesSpeed() {
        val d = CcsRememberedSetpoint.decideStalkEnterActive(null, 72.4f)
        assertEquals(CcsRememberedSetpoint.StalkEnterActiveDecision.Capture(72), d)
    }

    @Test
    fun stalkEnterActive_withinThreshold_keeps() {
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Keep,
            CcsRememberedSetpoint.decideStalkEnterActive(90, 91.2f),
        )
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Keep,
            CcsRememberedSetpoint.decideStalkEnterActive(90, 88.0f),
        )
    }

    @Test
    fun stalkEnterActive_beyondThreshold_capturesNewSet() {
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Capture(95),
            CcsRememberedSetpoint.decideStalkEnterActive(90, 95.0f),
        )
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Capture(80),
            CcsRememberedSetpoint.decideStalkEnterActive(90, 80.4f),
        )
    }

    @Test
    fun stalkEnterActive_unknownSpeed_keeps() {
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Keep,
            CcsRememberedSetpoint.decideStalkEnterActive(90, null),
        )
        assertEquals(
            CcsRememberedSetpoint.StalkEnterActiveDecision.Keep,
            CcsRememberedSetpoint.decideStalkEnterActive(null, null),
        )
    }

    @Test
    fun stalkMatchThreshold_isTwoKmh() {
        assertEquals(2, CcsRememberedSetpoint.STALK_MATCH_THRESHOLD_KMH)
    }
}
