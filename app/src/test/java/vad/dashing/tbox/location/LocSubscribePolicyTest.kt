package vad.dashing.tbox.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocSubscribePolicyTest {

    @Test
    fun periodicResubscribesWhenTboxLocStaleAndNotSuspended() {
        assertTrue(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                locSuspended = false,
                tboxConnected = true,
                locationStaleMs = 11_000L,
                sinceLastSubscribeMs = 11_000L,
            ),
        )
    }

    @Test
    fun periodicSkipsWhenAutoSuspendLocEnabled() {
        assertFalse(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = true,
                locSuspended = false,
                tboxConnected = true,
                locationStaleMs = 11_000L,
                sinceLastSubscribeMs = 11_000L,
            ),
        )
    }

    @Test
    fun periodicSkipsWhenLocConfirmedSuspended() {
        assertFalse(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                locSuspended = true,
                tboxConnected = true,
                locationStaleMs = 11_000L,
                sinceLastSubscribeMs = 11_000L,
            ),
        )
    }

    @Test
    fun periodicSkipsWhenSimulatedSourceLossEnabled() {
        assertFalse(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                locSuspended = false,
                tboxConnected = true,
                locationStaleMs = 11_000L,
                sinceLastSubscribeMs = 11_000L,
                simulatedSourceLoss = true,
            ),
        )
    }

    @Test
    fun periodicSkipsWhenFreshOrCooldown() {
        assertFalse(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                locSuspended = false,
                tboxConnected = true,
                locationStaleMs = 5_000L,
                sinceLastSubscribeMs = 11_000L,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldPeriodicResubscribe(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                locSuspended = false,
                tboxConnected = true,
                locationStaleMs = 11_000L,
                sinceLastSubscribeMs = 5_000L,
            ),
        )
    }

    @Test
    fun connectSkipsSubscribeWhenAutoSuspendOn() {
        assertFalse(
            LocSubscribePolicy.shouldSubscribeOnConnect(
                wantTboxLoc = true,
                noTboxConnect = false,
                autoSuspendLoc = true,
            ),
        )
        assertTrue(
            LocSubscribePolicy.shouldSubscribeOnConnect(
                wantTboxLoc = true,
                noTboxConnect = false,
                autoSuspendLoc = false,
            ),
        )
    }

    @Test
    fun connectSkipsSubscribeWhenSimulatedSourceLossOn() {
        assertFalse(
            LocSubscribePolicy.shouldSubscribeOnConnect(
                wantTboxLoc = true,
                noTboxConnect = false,
                autoSuspendLoc = false,
                simulatedSourceLoss = true,
            ),
        )
    }

    @Test
    fun resumeOnlyOnAutoSuspendFallingEdge() {
        assertTrue(
            LocSubscribePolicy.shouldResumeOnAutoSuspendChange(
                wasEnabled = true,
                enabled = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldResumeOnAutoSuspendChange(
                wasEnabled = false,
                enabled = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldResumeOnAutoSuspendChange(
                wasEnabled = false,
                enabled = true,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldResumeOnAutoSuspendChange(
                wasEnabled = true,
                enabled = true,
            ),
        )
    }

    @Test
    fun subscribeAfterResumeOnlyWhenTboxSourceAndNotAutoSuspend() {
        assertTrue(
            LocSubscribePolicy.shouldSubscribeAfterLocResume(
                wantTboxLoc = true,
                autoSuspendLoc = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeAfterLocResume(
                wantTboxLoc = true,
                autoSuspendLoc = true,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeAfterLocResume(
                wantTboxLoc = false,
                autoSuspendLoc = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeAfterLocResume(
                wantTboxLoc = true,
                autoSuspendLoc = false,
                simulatedSourceLoss = true,
            ),
        )
    }

    @Test
    fun simulatedLossUnsubscribesOnlyForTboxSource() {
        assertTrue(
            LocSubscribePolicy.shouldUnsubscribeOnSimulatedLoss(
                wantTboxLoc = true,
                simulatedLossEnabled = true,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldUnsubscribeOnSimulatedLoss(
                wantTboxLoc = true,
                simulatedLossEnabled = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldUnsubscribeOnSimulatedLoss(
                wantTboxLoc = false,
                simulatedLossEnabled = true,
            ),
        )
    }

    @Test
    fun simulatedLossEndSubscribesWhenTboxAndNotAutoSuspend() {
        assertTrue(
            LocSubscribePolicy.shouldSubscribeOnSimulatedLossEnd(
                wantTboxLoc = true,
                simulatedLossEnabled = false,
                autoSuspendLoc = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeOnSimulatedLossEnd(
                wantTboxLoc = true,
                simulatedLossEnabled = true,
                autoSuspendLoc = false,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeOnSimulatedLossEnd(
                wantTboxLoc = true,
                simulatedLossEnabled = false,
                autoSuspendLoc = true,
            ),
        )
        assertFalse(
            LocSubscribePolicy.shouldSubscribeOnSimulatedLossEnd(
                wantTboxLoc = false,
                simulatedLossEnabled = false,
                autoSuspendLoc = false,
            ),
        )
    }
}
