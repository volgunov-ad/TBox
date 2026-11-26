package vad.dashing.camd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

class CamdRootBridgeTest {
    @Test
    fun unavailableFallsBack() {
        assertEquals(
            CamdRootBridge.Outcome.FALLBACK,
            CamdRootBridge.outcome(
                RootSvcResult.unavailable("down"),
                RootSvcProtocol.CMD_SVC_CTL,
            ),
        )
    }

    @Test
    fun unsupportedFallsBack() {
        assertEquals(
            CamdRootBridge.Outcome.FALLBACK,
            CamdRootBridge.outcome(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_UNSUPPORTED, null, null),
                RootSvcProtocol.CMD_HELPER_ENSURE,
            ),
        )
    }

    @Test
    fun busyRetries() {
        assertEquals(
            CamdRootBridge.Outcome.RETRY_BUSY,
            CamdRootBridge.outcome(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_BUSY, "queue", null),
                RootSvcProtocol.CMD_SVC_CTL,
            ),
        )
    }

    @Test
    fun forbiddenFails() {
        assertEquals(
            CamdRootBridge.Outcome.FAIL,
            CamdRootBridge.outcome(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_FORBIDDEN, "cap", null),
                RootSvcProtocol.CMD_PKG_APPOPS,
            ),
        )
    }

    @Test
    fun failedDoesNotFallBack() {
        assertEquals(
            CamdRootBridge.Outcome.FAIL,
            CamdRootBridge.outcome(
                RootSvcResult.failed("1", 1, null, "x", 10L, null, null),
                RootSvcProtocol.CMD_SVC_CTL,
            ),
        )
    }

    @Test
    fun okPasses() {
        assertEquals(
            CamdRootBridge.Outcome.OK,
            CamdRootBridge.outcome(
                RootSvcResult.ok("1", 0, null, null, 5L, null),
                RootSvcProtocol.CMD_SETTINGS_A11Y_ADD,
            ),
        )
    }

    @Test
    fun unknownOutcomeIdempotentFallsBack() {
        val unknown = RootSvcResult.unknownOutcome("1", "result_timeout")
        assertEquals(
            CamdRootBridge.Outcome.FALLBACK,
            CamdRootBridge.outcome(unknown, RootSvcProtocol.CMD_HELPER_ENSURE),
        )
        assertEquals(
            CamdRootBridge.Outcome.FALLBACK,
            CamdRootBridge.outcome(unknown, RootSvcProtocol.CMD_SVC_CTL),
        )
        assertEquals(
            CamdRootBridge.Outcome.FAIL,
            CamdRootBridge.outcome(unknown, RootSvcProtocol.CMD_SCRIPT_RUN),
        )
    }

    @Test
    fun grabVersionOkReadsNeitherFileAsFalse() {
        // Neither helpers nor tmp stamp exists in the unit-test JVM.
        assertFalse(GrabberScripts.grabVersionOk())
    }

    @Test
    fun ownMarkerRecognizedInExecAutorun() {
        assertFalse(
            DaemonScripts.isForeignAutorun(
                "#!/system/bin/sh\nexec /system/bin/sh '/sdcard/camd_grab_arm.sh'\n",
            ),
        )
        assertTrue(DaemonScripts.isForeignAutorun("#!/system/bin/sh\necho foreign\n"))
    }
}
