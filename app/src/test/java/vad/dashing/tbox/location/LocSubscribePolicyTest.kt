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
    }
}
