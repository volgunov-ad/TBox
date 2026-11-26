package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.WireCodec

class ScriptOutputTruncateTest {
    @Test
    fun keepsShortText() {
        assertEquals("hello", WireCodec.truncateTail("hello", 16))
        assertEquals("", WireCodec.truncateTail(null, 16))
    }

    @Test
    fun keepsTailWhenOverLimit() {
        val text = "abcdefghij"
        assertEquals("fghij", WireCodec.truncateTail(text, 5))
    }

    @Test
    fun scriptLimitFitsUnderLineLimit() {
        val limit = RootSvcProtocol.SCRIPT_OUTPUT_LIMIT
        assertEquals(4 * 1024, limit)
        // Two capped fields + JSON envelope must stay under LINE_LIMIT.
        val overhead = 512
        assertTrue(2 * limit + overhead < RootSvcProtocol.LINE_LIMIT)
    }
}
