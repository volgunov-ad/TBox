package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.FallbackContract
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

class FallbackContractTest {
    @Test
    fun unavailableFallsBack() {
        assertTrue(
            FallbackContract.shouldFallback(
                RootSvcResult.unavailable("down"),
                RootSvcProtocol.CMD_SVC_CTL,
            ),
        )
    }

    @Test
    fun unsupportedFallsBack() {
        assertTrue(
            FallbackContract.shouldFallback(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_UNSUPPORTED, null, null),
                RootSvcProtocol.CMD_USB_UNLOCK,
            ),
        )
    }

    @Test
    fun forbiddenDoesNotFallBack() {
        assertFalse(
            FallbackContract.shouldFallback(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_FORBIDDEN, "cap", null),
                RootSvcProtocol.CMD_SCRIPT_RUN,
            ),
        )
    }

    @Test
    fun failedDoesNotFallBack() {
        assertFalse(
            FallbackContract.shouldFallback(
                RootSvcResult.failed("1", 1, null, "x", 10L, null, null),
                RootSvcProtocol.CMD_SVC_CTL,
            ),
        )
    }

    @Test
    fun unknownOutcomeOnlyIdempotent() {
        val unknown = RootSvcResult.unknownOutcome("1", "result_timeout")
        assertTrue(FallbackContract.shouldFallback(unknown, RootSvcProtocol.CMD_USB_UNLOCK))
        assertTrue(FallbackContract.shouldFallback(unknown, RootSvcProtocol.CMD_HELPER_ENSURE))
        assertFalse(FallbackContract.shouldFallback(unknown, RootSvcProtocol.CMD_SCRIPT_RUN))
        assertFalse(FallbackContract.shouldFallback(unknown, RootSvcProtocol.CMD_POWER_REBOOT))
    }

    @Test
    fun scriptTimeoutCapped() {
        assertEquals(
            RootSvcProtocol.SCRIPT_MAX_TIMEOUT_MS,
            FallbackContract.resultTimeoutFor(RootSvcProtocol.CMD_SCRIPT_RUN, 999_999L),
        )
        assertEquals(
            RootSvcProtocol.HELPER_TIMEOUT_MS,
            FallbackContract.resultTimeoutFor(RootSvcProtocol.CMD_HELPER_ENSURE, 1_000L),
        )
    }
}
