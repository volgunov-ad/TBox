package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.AccCruiseDomain
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

    @Test
    fun parseHardkey_mapsLeftJoystickToResSet() {
        assertEquals(
            CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
            CcsRememberedSetpoint.parseHardkeyCruiseKey(29),
        )
        assertEquals(
            CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
            CcsRememberedSetpoint.parseHardkeyCruiseKey(30),
        )
        assertEquals(null, CcsRememberedSetpoint.parseHardkeyCruiseKey(115))
        assertEquals(null, CcsRememberedSetpoint.parseHardkeyCruiseKey(0))
    }

    @Test
    fun decideHardkey_standbyResKeeps_setCaptures() {
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.KeepRemembered,
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
                AccCruiseDomain.CCS_STATUS_STANDBY,
            ),
        )
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.CaptureSpeed("hardkey_set"),
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
                AccCruiseDomain.CCS_STATUS_STANDBY,
            ),
        )
    }

    @Test
    fun decideHardkey_activeNudgesByOne() {
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.Nudge(1, "hardkey_res"),
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
                AccCruiseDomain.CCS_STATUS_ACTIVE,
            ),
        )
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.Nudge(-1, "hardkey_set"),
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
                AccCruiseDomain.CCS_STATUS_ACTIVE,
            ),
        )
    }

    @Test
    fun decideHardkey_offIgnores() {
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.Ignore,
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.ResPlus,
                null,
            ),
        )
        assertEquals(
            CcsRememberedSetpoint.HardkeySetpointDecision.Ignore,
            CcsRememberedSetpoint.decideHardkey(
                CcsRememberedSetpoint.HardkeyCruiseKey.SetMinus,
                0,
            ),
        )
    }

    @Test
    fun shouldReconcileStableSpeed_requiresHoldAndDelta() {
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 95,
                stableForMs = 500L,
                withinOurPulse = false,
            ),
        )
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 91,
                stableForMs = CcsRememberedSetpoint.STABLE_RECONCILE_MS,
                withinOurPulse = false,
            ),
        )
        assertTrue(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 95,
                stableForMs = CcsRememberedSetpoint.STABLE_RECONCILE_MS,
                withinOurPulse = false,
            ),
        )
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = 90,
                roundedSpeedKmh = 95,
                stableForMs = CcsRememberedSetpoint.STABLE_RECONCILE_MS,
                withinOurPulse = true,
            ),
        )
        assertFalse(
            CcsRememberedSetpoint.shouldReconcileStableSpeed(
                rememberedKmh = null,
                roundedSpeedKmh = 95,
                stableForMs = CcsRememberedSetpoint.STABLE_RECONCILE_MS,
                withinOurPulse = false,
            ),
        )
    }
}
