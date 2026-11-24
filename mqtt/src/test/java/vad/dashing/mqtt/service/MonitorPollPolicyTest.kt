package vad.dashing.mqtt.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitorPollPolicyTest {
    @Test
    fun threeFailuresOrThirtySecondsMarkMonitorDown() {
        assertFalse(MonitorPollPolicy.shouldMarkDown(1, 0L))
        assertFalse(MonitorPollPolicy.shouldMarkDown(2, 5_000L))
        assertTrue(MonitorPollPolicy.shouldMarkDown(MonitorPollPolicy.FAIL_LIMIT, 1L))
        assertTrue(MonitorPollPolicy.shouldMarkDown(1, MonitorPollPolicy.FAIL_WINDOW_MS))
        assertFalse(MonitorPollPolicy.shouldMarkDown(0, MonitorPollPolicy.FAIL_WINDOW_MS))
    }
}
