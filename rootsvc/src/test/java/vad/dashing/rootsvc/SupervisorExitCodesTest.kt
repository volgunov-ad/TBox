package vad.dashing.rootsvc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.rootsvc.client.RootSvcProtocol
import java.io.File

/** Documents deliberate daemon exit codes handled by assets/supervisor.sh. */
class SupervisorExitCodesTest {
    @Test
    fun upgradeAndRestartCodesAreDistinctFromCrash() {
        assertEquals(42, RootSvcProtocol.EXIT_UPGRADE)
        assertEquals(43, RootSvcProtocol.EXIT_RESTART)
        assertEquals("1.0.2", RootSvcProtocol.SERVER_VERSION)
    }

    @Test
    fun supervisorScriptHandlesRestartCodeWithoutFailCounter() {
        val script = File("src/main/assets/supervisor.sh").readText()
            .replace("\r\n", "\n")
        assertTrue(script.contains("code\" -eq 42"))
        assertTrue(script.contains("code\" -eq 43"))
        assertTrue(script.contains("admin.restart"))
        // Crashloop path must stay after the deliberate-exit branches.
        val idx43 = script.indexOf("code\" -eq 43")
        val idxFail = script.indexOf("FAILS=\$((FAILS + 1))")
        assertTrue(idx43 >= 0 && idxFail > idx43)
    }
}
