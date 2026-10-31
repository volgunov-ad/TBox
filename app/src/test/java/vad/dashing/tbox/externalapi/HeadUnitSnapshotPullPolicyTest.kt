package vad.dashing.tbox.externalapi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanSignal

class HeadUnitSnapshotPullPolicyTest {
    @Test
    fun sameSetDoesNotPullAgain() {
        val signals = setOf(MbCanSignal.HvacTempLeft, MbCanSignal.HvacFanSpeed)
        assertFalse(ExternalApiSignalReader.headUnitPullRequired(signals, signals))
    }

    @Test
    fun changedSetPullsOnce() {
        val subscribed = setOf(MbCanSignal.HvacTempLeft)
        val requested = setOf(MbCanSignal.HvacTempLeft, MbCanSignal.HvacFanSpeed)
        assertTrue(ExternalApiSignalReader.headUnitPullRequired(subscribed, requested))
        assertTrue(ExternalApiSignalReader.headUnitPullRequired(emptySet(), requested))
    }

    @Test
    fun emptyRequestDoesNotPull() {
        assertFalse(
            ExternalApiSignalReader.headUnitPullRequired(
                setOf(MbCanSignal.HvacTempLeft),
                emptySet(),
            ),
        )
    }
}
