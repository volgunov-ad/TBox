package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenBootOpenPolicyTest {

    @Test
    fun delayBeforeAttempt_matchesRetrySchedule() {
        assertEquals(0L, MainScreenBootOpenPolicy.delayBeforeAttemptMs(0))
        assertEquals(2_000L, MainScreenBootOpenPolicy.delayBeforeAttemptMs(1))
        assertEquals(5_000L, MainScreenBootOpenPolicy.delayBeforeAttemptMs(2))
        assertEquals(15_000L, MainScreenBootOpenPolicy.delayBeforeAttemptMs(3))
        assertEquals(30_000L, MainScreenBootOpenPolicy.delayBeforeAttemptMs(4))
        assertNull(MainScreenBootOpenPolicy.delayBeforeAttemptMs(5))
        assertNull(MainScreenBootOpenPolicy.delayBeforeAttemptMs(-1))
    }

    @Test
    fun episodeDeadline_expiresAfterBudget() {
        val start = 10_000L
        val deadline = MainScreenBootOpenPolicy.newDeadlineElapsedRealtimeMs(
            nowElapsedRealtimeMs = start,
            maxEpisodeMs = MainScreenBootOpenPolicy.MAX_EPISODE_MS,
        )
        assertEquals(start + MainScreenBootOpenPolicy.MAX_EPISODE_MS, deadline)
        assertFalse(MainScreenBootOpenPolicy.isEpisodeExpired(start + 1_000L, deadline))
        assertTrue(
            MainScreenBootOpenPolicy.isEpisodeExpired(
                start + MainScreenBootOpenPolicy.MAX_EPISODE_MS,
                deadline,
            ),
        )
    }

    @Test
    fun episodeDeadline_includesInitialDelay() {
        val start = 10_000L
        val delayMs = 60_000L
        val deadline = MainScreenBootOpenPolicy.newDeadlineWithInitialDelayMs(
            initialDelayMs = delayMs,
            nowElapsedRealtimeMs = start,
        )
        assertEquals(start + delayMs + MainScreenBootOpenPolicy.MAX_EPISODE_MS, deadline)

        // First attempt happens right after the initial delay — must not be expired yet.
        assertFalse(
            MainScreenBootOpenPolicy.isEpisodeExpired(start + delayMs + 1_000L, deadline),
        )
        assertTrue(
            MainScreenBootOpenPolicy.isEpisodeExpired(
                start + delayMs + MainScreenBootOpenPolicy.MAX_EPISODE_MS,
                deadline,
            ),
        )
    }

    @Test
    fun episodeDeadline_initialDelay_neverNegative() {
        assertEquals(
            5_000L + MainScreenBootOpenPolicy.MAX_EPISODE_MS,
            MainScreenBootOpenPolicy.newDeadlineWithInitialDelayMs(
                initialDelayMs = -100L,
                nowElapsedRealtimeMs = 5_000L,
            ),
        )
    }
}
