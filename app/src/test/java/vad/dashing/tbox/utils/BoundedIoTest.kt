package vad.dashing.tbox.utils

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class BoundedIoTest {

    @Test
    fun readsStreamAtExactLimit() {
        val data = ByteArray(10_000) { it.toByte() }
        assertArrayEquals(data, data.inputStream().readBytesAtMost(data.size.toLong()))
    }

    @Test(expected = SizeLimitExceededException::class)
    fun rejectsStreamOverLimit() {
        ByteArray(10_001).inputStream().readBytesAtMost(10_000)
    }

    @Test
    fun copyStopsBeforeWritingPastLimit() {
        val out = ByteArrayOutputStream()
        try {
            ByteArray(3 * DEFAULT_BUFFER_SIZE).inputStream().copyToAtMost(out, DEFAULT_BUFFER_SIZE + 1L)
        } catch (_: SizeLimitExceededException) {
        }
        assertEquals(DEFAULT_BUFFER_SIZE, out.size())
    }
}
