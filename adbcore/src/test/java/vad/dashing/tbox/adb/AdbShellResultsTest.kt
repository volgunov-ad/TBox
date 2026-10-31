package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdbShellResultsTest {
    @Test
    fun failureDetail_nonZeroExit() {
        assertEquals(
            "Permission denial",
            AdbShellResults.failureDetail(
                AdbShellResult("", "Permission denial", 255, true),
            ),
        )
    }

    @Test
    fun failureDetail_successExitNullDetail() {
        assertNull(
            AdbShellResults.failureDetail(
                AdbShellResult("ok", "", 0, true),
            ),
        )
    }

    @Test
    fun failureDetail_legacyErrorStdout() {
        assertEquals(
            "Error: Activity not started",
            AdbShellResults.failureDetail(
                AdbShellResult("Error: Activity not started", "", null, false),
            ),
        )
    }
}
