package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderStartRetryTest {

    @Test
    fun pausesGrowAndCoverTheBootRush() {
        val pauses = RecorderStartRetry.PAUSES_MS.toList()
        assertTrue(pauses.zipWithNext().all { (a, b) -> b >= a })
        assertTrue(pauses.sum() >= 120_000)
        assertTrue(pauses.sum() <= 300_000)
    }

    @Test
    fun messageSaysWhenTheNextTryComes() {
        assertEquals(
            "Не удалось запустить граббер: mb_daemon не ответил — повтор через 10 с",
            RecorderStartRetry.message("mb_daemon не ответил", 10_000),
        )
        assertTrue(RecorderStartRetry.message("x", null).contains("попытки исчерпаны"))
    }
}
