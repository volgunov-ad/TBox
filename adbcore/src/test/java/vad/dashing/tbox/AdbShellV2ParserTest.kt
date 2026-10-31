package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vad.dashing.tbox.adb.AdbShellV2
import vad.dashing.tbox.adb.AdbShellV2Parser
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AdbShellV2ParserTest {

    private fun chunk(id: Int, data: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + data.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(id.toByte())
        buffer.putInt(data.size)
        buffer.put(data)
        return buffer.array()
    }

    @Test
    fun parsesSingleChunk() {
        val parser = AdbShellV2Parser()
        val chunks = parser.feed(chunk(AdbShellV2.CHUNK_STDOUT, "hello".toByteArray()))
        assertEquals(1, chunks.size)
        assertEquals(AdbShellV2.CHUNK_STDOUT, chunks[0].id)
        assertEquals("hello", String(chunks[0].data, Charsets.UTF_8))
    }

    @Test
    fun parsesMultipleChunksInOneFeed() {
        val parser = AdbShellV2Parser()
        val stream = chunk(AdbShellV2.CHUNK_STDOUT, "a".toByteArray()) +
            chunk(AdbShellV2.CHUNK_STDERR, "b".toByteArray()) +
            chunk(AdbShellV2.CHUNK_EXIT, byteArrayOf(0))
        val chunks = parser.feed(stream)
        assertEquals(3, chunks.size)
        assertEquals(AdbShellV2.CHUNK_STDOUT, chunks[0].id)
        assertEquals("a", String(chunks[0].data, Charsets.UTF_8))
        assertEquals(AdbShellV2.CHUNK_STDERR, chunks[1].id)
        assertEquals("b", String(chunks[1].data, Charsets.UTF_8))
        assertEquals(AdbShellV2.CHUNK_EXIT, chunks[2].id)
        assertEquals(1, chunks[2].data.size)
        assertEquals(0, chunks[2].data[0].toInt())
    }

    @Test
    fun reassemblesChunkFedByteByByte() {
        val parser = AdbShellV2Parser()
        val bytes = chunk(AdbShellV2.CHUNK_STDOUT, "stream".toByteArray())
        val collected = ArrayList<vad.dashing.tbox.adb.AdbShellV2Chunk>()
        for (b in bytes) {
            collected.addAll(parser.feed(byteArrayOf(b), 1))
        }
        assertEquals(1, collected.size)
        assertEquals("stream", String(collected[0].data, Charsets.UTF_8))
        assertTrue(parser.feed(ByteArray(0)).isEmpty())
    }

    @Test
    fun partialFeedHoldsBytesUntilComplete() {
        val parser = AdbShellV2Parser()
        val bytes = chunk(AdbShellV2.CHUNK_STDOUT, "xy".toByteArray())
        assertTrue(parser.feed(bytes, 4).isEmpty())
        val chunks = parser.feed(bytes.copyOfRange(4, bytes.size))
        assertEquals(1, chunks.size)
        assertEquals("xy", String(chunks[0].data, Charsets.UTF_8))
    }

    @Test
    fun rejectsOversizedChunkLength() {
        val parser = AdbShellV2Parser()
        val buffer = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(AdbShellV2.CHUNK_STDOUT.toByte())
        buffer.putInt(AdbShellV2.MAX_CHUNK + 1)
        try {
            parser.feed(buffer.array())
            fail("expected IOException")
        } catch (expected: IOException) {
        }
    }

    @Test
    fun handlesLargeValidChunk() {
        val parser = AdbShellV2Parser()
        val data = ByteArray(64 * 1024) { 'z'.code.toByte() }
        val chunks = parser.feed(chunk(AdbShellV2.CHUNK_STDOUT, data))
        assertEquals(1, chunks.size)
        assertEquals(data.size, chunks[0].data.size)
    }
}
