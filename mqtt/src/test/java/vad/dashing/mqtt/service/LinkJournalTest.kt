package vad.dashing.mqtt.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkJournalTest {
    @Test
    fun newestFirstAndCapped() {
        val journal = LinkJournal(capacity = 3)
        (1..5).forEach { journal.add(LinkEvent(it.toLong(), "Брокер", it % 2 == 0)) }
        assertEquals(listOf(5L, 4L, 3L), journal.snapshot().map { it.atMs })
    }
}
