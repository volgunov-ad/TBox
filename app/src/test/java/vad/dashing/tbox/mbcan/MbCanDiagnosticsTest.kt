package vad.dashing.tbox.mbcan

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MbCanDiagnosticsTest {
    @Test
    fun shouldEmit_debugOnlyWhenDiagnosticsEnabled() {
        assertFalse(MbCanDiagnostics.shouldEmit("DEBUG", diagnosticsEnabled = false))
        assertTrue(MbCanDiagnostics.shouldEmit("DEBUG", diagnosticsEnabled = true))
        assertTrue(MbCanDiagnostics.shouldEmit("debug", diagnosticsEnabled = true))
    }

    @Test
    fun shouldEmit_errorWarnInfoAlways() {
        for (level in listOf("ERROR", "WARN", "INFO", "error", " warn ", "Info")) {
            assertTrue(
                "level=$level must emit with diagnostics off",
                MbCanDiagnostics.shouldEmit(level, diagnosticsEnabled = false),
            )
            assertTrue(
                "level=$level must emit with diagnostics on",
                MbCanDiagnostics.shouldEmit(level, diagnosticsEnabled = true),
            )
        }
    }

    @Test
    fun shouldEmit_unknownLevelAlways() {
        // Unknown levels are not suppressed by the diagnostics gate (global min level may still filter).
        assertTrue(MbCanDiagnostics.shouldEmit("TRACE", diagnosticsEnabled = false))
    }
}
