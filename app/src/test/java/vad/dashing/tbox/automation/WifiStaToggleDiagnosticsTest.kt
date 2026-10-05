package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiStaToggleDiagnosticsTest {
    @Test
    fun failureMessage_prefersAirplaneThenSoftApThenApi() {
        assertTrue(
            WifiStaToggleDiagnostics.failureMessage(
                airplane = true,
                softApEnabled = true,
                sdkInt = 29,
            ).contains("полёта"),
        )
        assertTrue(
            WifiStaToggleDiagnostics.failureMessage(
                airplane = false,
                softApEnabled = true,
                sdkInt = 28,
            ).contains("SoftAP"),
        )
        assertTrue(
            WifiStaToggleDiagnostics.failureMessage(
                airplane = false,
                softApEnabled = false,
                sdkInt = 29,
            ).contains("API 29"),
        )
        assertEquals(
            "Не удалось переключить Wi-Fi",
            WifiStaToggleDiagnostics.failureMessage(
                airplane = false,
                softApEnabled = false,
                sdkInt = 28,
            ),
        )
    }
}
