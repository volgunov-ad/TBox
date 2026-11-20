package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class UyvyFrameTest {
    @Test
    fun readsAPublishedSlot() {
        val slot = readUyvySlot(header(width = 1280, height = 800, seq = 3, slot = 1))
        assertEquals(1280, slot?.width)
        assertEquals(800, slot?.height)
        assertEquals(1, slot?.slot)
        assertEquals(1280 * 2 * 800, slot?.slotBytes)
        assertEquals(3, slot?.seq)
    }

    @Test
    fun rejectsAnUnpublishedOrTornHeader() {
        assertNull(readUyvySlot(header(width = 1280, height = 800, seq = 0, slot = 0)))
        assertNull(readUyvySlot(header(width = 1280, height = 800, seq = 2, slot = 2)))
        assertNull(readUyvySlot(header(width = 1487, height = 616, seq = 1, slot = 0)))
        assertNull(readUyvySlot(ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)))
    }

    @Test
    fun renderPacingIsFastWhileFramesArrive() {
        assertEquals(8L, UyvyRenderer.RenderPacing.sleepMs(0))
        assertEquals(8L, UyvyRenderer.RenderPacing.sleepMs(-1))
    }

    @Test
    fun renderPacingBacksOffWhileIdle() {
        val first = UyvyRenderer.RenderPacing.sleepMs(1)
        val mid = UyvyRenderer.RenderPacing.sleepMs(3)
        val saturated = UyvyRenderer.RenderPacing.sleepMs(50)
        assertEquals(12L, first)
        assertTrue(mid > first)
        assertEquals(40L, saturated)
        assertEquals(40L, UyvyRenderer.RenderPacing.sleepMs(500))
    }

    @Test
    fun renderPacingNeverExceedsTheCap() {
        for (idle in 0..60) {
            val ms = UyvyRenderer.RenderPacing.sleepMs(idle)
            assertTrue("idle=$idle ms=$ms", ms >= 8L && ms <= 40L)
        }
    }

    private fun header(width: Int, height: Int, seq: Int, slot: Int): ByteBuffer {
        val stride = width * 2
        val slotBytes = stride * height
        val buffer = ByteBuffer.allocate(UYVY_HEADER + slotBytes * 2).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(0, UYVY_MAGIC)
        buffer.putInt(4, width)
        buffer.putInt(8, height)
        buffer.putInt(12, stride)
        buffer.putInt(16, seq)
        buffer.putInt(20, slot)
        buffer.putInt(24, slotBytes)
        return buffer
    }
}
