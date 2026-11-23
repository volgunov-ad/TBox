package vad.dashing.store

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressThrottleTest {
    @Test
    fun knownSizeEmitsOncePerPercent() {
        val throttle = ProgressThrottle()
        val total = 10_000L
        var emitted = 0
        var downloaded = 0L
        while (downloaded < total) {
            downloaded = (downloaded + 8).coerceAtMost(total)
            if (throttle.shouldEmit(downloaded, total)) emitted++
        }
        assertEquals(101, emitted)
    }

    @Test
    fun unknownSizeEmitsEvery256KiB() {
        val throttle = ProgressThrottle()
        assertTrue(throttle.shouldEmit(8_192L, null))
        assertFalse(throttle.shouldEmit(100_000L, null))
        assertTrue(throttle.shouldEmit(8_192L + 256L * 1024L, null))
        assertFalse(throttle.shouldEmit(8_192L + 300L * 1024L, null))
    }
}
