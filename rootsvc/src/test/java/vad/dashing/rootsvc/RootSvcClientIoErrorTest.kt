package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.FallbackContract
import vad.dashing.rootsvc.client.RootSvcClient
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

class RootSvcClientIoErrorTest {
    @Test
    fun afterAckMapsToUnknownOutcomeNotUnavailable() {
        val result = RootSvcClient.mapIoError(
            true,
            "abcd1234",
            IllegalStateException("line_too_long"),
        )
        assertEquals(RootSvcResult.Kind.UNKNOWN_OUTCOME, result.kind)
        assertEquals("abcd1234", result.id)
        assertTrue(result.reason!!.contains("line_too_long"))
        assertFalse(
            FallbackContract.shouldFallback(result, RootSvcProtocol.CMD_SCRIPT_RUN),
        )
    }

    @Test
    fun beforeAckMapsToUnavailable() {
        val result = RootSvcClient.mapIoError(
            false,
            "abcd1234",
            java.io.IOException("broken pipe"),
        )
        assertEquals(RootSvcResult.Kind.UNAVAILABLE, result.kind)
        assertTrue(FallbackContract.shouldFallback(result, RootSvcProtocol.CMD_SCRIPT_RUN))
    }
}
