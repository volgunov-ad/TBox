package vad.dashing.camdbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.RootSvcProtocol
import vad.dashing.rootsvc.client.RootSvcResult

class CamdBenchRootBridgeTest {
    @Test
    fun scriptRunUnavailableFallsBack() {
        assertEquals(
            CamdBenchRootBridge.Outcome.FALLBACK,
            CamdBenchRootBridge.outcome(
                RootSvcResult.unavailable("connect"),
                RootSvcProtocol.CMD_SCRIPT_RUN,
            ),
        )
    }

    @Test
    fun scriptRunUnknownDoesNotFallBack() {
        assertEquals(
            CamdBenchRootBridge.Outcome.FAIL,
            CamdBenchRootBridge.outcome(
                RootSvcResult.unknownOutcome("1", "result_timeout"),
                RootSvcProtocol.CMD_SCRIPT_RUN,
            ),
        )
    }

    @Test
    fun busyRetries() {
        assertEquals(
            CamdBenchRootBridge.Outcome.RETRY_BUSY,
            CamdBenchRootBridge.outcome(
                RootSvcResult.rejected("1", RootSvcProtocol.ERR_BUSY, null, null),
                RootSvcProtocol.CMD_SCRIPT_RUN,
            ),
        )
    }

    @Test
    fun ownMarkerInExecAutorun() {
        assertFalse(
            BenchScripts.isForeignAutorun(
                "#!/system/bin/sh\nexec /system/bin/sh '/sdcard/camdbench_arm.sh'\n",
            ),
        )
        assertTrue(BenchScripts.isForeignAutorun("#!/system/bin/sh\necho other\n"))
    }
}
