package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CanAutoBindPolicyTest {
    @Test
    fun disabledSkipsProbeEvenWhenUnlocked() {
        assertEquals(
            CanAutoBindPolicy.Startup.Disabled,
            CanAutoBindPolicy.decide(
                enabled = false,
                locked = false,
                lastResult = "",
                current = HeadUnitCanMode.Android10Vhal,
            ).startup,
        )
    }

    @Test
    fun lockPinsTheSavedMode() {
        val decision = CanAutoBindPolicy.decide(
            enabled = true,
            locked = true,
            lastResult = "",
            current = HeadUnitCanMode.Android10Vhal,
        )
        assertEquals(CanAutoBindPolicy.Startup.Pinned, decision.startup)
        assertEquals(HeadUnitCanMode.Android10Vhal, decision.mode)
    }

    @Test
    fun lockLeftByAFailedProbeDoesNotPin() {
        val decision = CanAutoBindPolicy.decide(
            enabled = true,
            locked = true,
            lastResult = "locked_after_fail:android9_mbcan|android10_vhal",
            current = HeadUnitCanMode.Android9MbCan,
        )
        assertEquals(CanAutoBindPolicy.Startup.ProbeWithFallback, decision.startup)
    }

    @Test
    fun previousSuccessPinsThatModeEvenIfSettingsWereOverwritten() {
        val decision = CanAutoBindPolicy.decide(
            enabled = true,
            locked = false,
            lastResult = "primary_ok:android10_vhal:attempt=1",
            current = HeadUnitCanMode.Android9MbCan,
        )
        assertEquals(CanAutoBindPolicy.Startup.Pinned, decision.startup)
        assertEquals(HeadUnitCanMode.Android10Vhal, decision.mode)
    }

    @Test
    fun manualChoiceAndAlternativeSuccessNameThePinnedMode() {
        assertEquals(
            HeadUnitCanMode.Android10Vhal,
            CanAutoBindPolicy.modeFromSuccessfulResult("user:android10_vhal"),
        )
        assertEquals(
            HeadUnitCanMode.Android9MbCan,
            CanAutoBindPolicy.modeFromSuccessfulResult("alternative_ok:android9_mbcan:attempt=2"),
        )
        assertEquals(
            HeadUnitCanMode.Android10Vhal,
            CanAutoBindPolicy.modeFromSuccessfulResult("pinned_ok:android10_vhal:attempt=3"),
        )
    }

    @Test
    fun unknownCarStillProbesBothStacks() {
        assertNull(CanAutoBindPolicy.modeFromSuccessfulResult(""))
        assertNull(
            CanAutoBindPolicy.modeFromSuccessfulResult(
                "locked_after_fail:android10_vhal|android9_mbcan",
            ),
        )
        assertNull(
            CanAutoBindPolicy.modeFromSuccessfulResult(
                "pinned_unavailable:android10_vhal:timeout",
            ),
        )
        val decision = CanAutoBindPolicy.decide(
            enabled = true,
            locked = false,
            lastResult = "",
            current = HeadUnitCanMode.Android9MbCan,
        )
        assertEquals(CanAutoBindPolicy.Startup.ProbeWithFallback, decision.startup)
        assertEquals(HeadUnitCanMode.Android9MbCan, decision.mode)
    }
}
