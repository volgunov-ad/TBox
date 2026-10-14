package vad.dashing.dataresize

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JournalTest {
    private fun statuses(steps: List<StepUi>) = steps.map { it.status }

    @Test
    fun runningStep_marksEarlierDone() {
        val lines = listOf(
            "2026-10-14 22:39:03 @check проверка",
            "2026-10-14 22:39:03 @stop остановка Android",
            "2026-10-14 22:39:12 @freeze заморозка /data",
            "2026-10-14 22:39:12 @copy копирование 2048 МиБ",
            "2147483648 bytes transferred in 24.892 secs (86272041 bytes/sec)",
        )
        val (steps, failure) = Journal.steps(Journal.MOVE, lines)
        assertNull(failure)
        assertEquals(List(3) { StepStatus.Ok } + StepStatus.Running + List(7) { StepStatus.Pending }, statuses(steps))
        assertEquals("22:39:12  копирование 2048 МиБ\n2147483648 bytes transferred in 24.892 secs (86272041 bytes/sec)", steps[3].detail)
    }

    @Test
    fun failure_marksRunningStepFailed() {
        val lines = listOf(
            "2026-10-14 22:21:00 @check проверка",
            "2026-10-14 22:21:13 @verify сверка копии",
            "2026-10-14 22:21:13 !FAIL копия не совпала, Android запущен обратно",
        )
        val (steps, failure) = Journal.steps(Journal.MOVE, lines)
        assertEquals("копия не совпала, Android запущен обратно", failure)
        assertEquals(StepStatus.Failed, steps[4].status)
        assertEquals(StepStatus.Ok, steps[3].status)
        assertEquals(StepStatus.Pending, steps[5].status)
    }

    @Test
    fun rebootConfirmed_completesLastStep() {
        val lines = Journal.MOVE.map { "2026-10-14 22:40:27 @${it.id} x" }
        assertEquals(StepStatus.Running, Journal.steps(Journal.MOVE, lines).first.last().status)
        assertEquals(List(11) { StepStatus.Ok }, statuses(Journal.steps(Journal.MOVE, lines, completed = true).first))
    }

    @Test
    fun stage2Done_isComplete() {
        val lines = Journal.STAGE2.map { "2026-10-14 22:42:45 @${it.id} x" }
        assertEquals(List(4) { StepStatus.Ok }, statuses(Journal.steps(Journal.STAGE2, lines).first))
    }

    @Test
    fun emptyJournal_allPending() {
        assertEquals(List(5) { StepStatus.Pending }, statuses(Journal.steps(Journal.ROLLBACK, emptyList()).first))
    }
}
