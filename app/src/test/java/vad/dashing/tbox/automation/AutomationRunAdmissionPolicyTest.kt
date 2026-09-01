package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomationRunAdmissionPolicyTest {
    @Test
    fun single_launchesWhenIdleAndSkipsWhenBusy() {
        assertEquals(
            AutomationRunAdmission.LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.SINGLE, 0, 0, 1),
        )
        assertEquals(
            AutomationRunAdmission.SKIP,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.SINGLE, 1, 0, 1),
        )
    }

    @Test
    fun restart_alwaysCancelsAndLaunches() {
        assertEquals(
            AutomationRunAdmission.CANCEL_ACTIVE_AND_LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.RESTART, 0, 0, 10),
        )
        assertEquals(
            AutomationRunAdmission.CANCEL_ACTIVE_AND_LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.RESTART, 2, 4, 10),
        )
    }

    @Test
    fun queued_launchesIdleThenEnqueuesUpToMaxRuns() {
        assertEquals(
            AutomationRunAdmission.LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.QUEUED, 0, 3, 3),
        )
        assertEquals(
            AutomationRunAdmission.ENQUEUE,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.QUEUED, 1, 1, 3),
        )
        assertEquals(
            AutomationRunAdmission.SKIP,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.QUEUED, 1, 2, 3),
        )
        assertEquals(
            AutomationRunAdmission.SKIP,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.QUEUED, 2, 1, 3),
        )
    }

    @Test
    fun parallel_launchesUntilActiveHitsMaxRuns() {
        assertEquals(
            AutomationRunAdmission.LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.PARALLEL, 0, 0, 2),
        )
        assertEquals(
            AutomationRunAdmission.LAUNCH,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.PARALLEL, 1, 99, 2),
        )
        assertEquals(
            AutomationRunAdmission.SKIP,
            AutomationRunAdmissionPolicy.decide(AutomationRunMode.PARALLEL, 2, 0, 2),
        )
    }

    @Test
    fun queuedDrain_onlyWhenEnabledIdleAndQueueNotEmpty() {
        assertTrue(
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = AutomationRunMode.QUEUED,
                enabled = true,
                activeCount = 0,
                queuedCount = 1,
            ),
        )
        assertFalse(
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = AutomationRunMode.QUEUED,
                enabled = false,
                activeCount = 0,
                queuedCount = 1,
            ),
        )
        assertFalse(
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = AutomationRunMode.QUEUED,
                enabled = true,
                activeCount = 1,
                queuedCount = 1,
            ),
        )
        assertFalse(
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = AutomationRunMode.QUEUED,
                enabled = true,
                activeCount = 0,
                queuedCount = 0,
            ),
        )
        assertFalse(
            AutomationRunAdmissionPolicy.shouldLaunchQueuedNext(
                runMode = AutomationRunMode.RESTART,
                enabled = true,
                activeCount = 0,
                queuedCount = 2,
            ),
        )
    }
}
