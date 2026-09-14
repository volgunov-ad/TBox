package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyPressDiagnosticsTest {
    @Test
    fun log_keepsNewestEntriesWithinLimit() {
        val result = (0..KEY_PRESS_DIAGNOSTIC_LOG_LIMIT).fold(KeyPressDiagnosticLog()) { log, index ->
            log.append("event-$index")
        }

        assertEquals(KEY_PRESS_DIAGNOSTIC_LOG_LIMIT, result.lines.size)
        assertFalse(result.lines.contains("event-0"))
        assertEquals("event-${KEY_PRESS_DIAGNOSTIC_LOG_LIMIT}", result.lines.last())
    }

    @Test
    fun mbCanFormat_includesAllRawFieldsAndKnownName() {
        val line = KeyPressDiagnosticFormat.mbCan(keyCode = 115, keyStatus = 1, keyType = 2)

        assertTrue(line.contains("keyCode=115"))
        assertTrue(line.contains("name=VOLUME_UP"))
        assertTrue(line.contains("keyStatus=1"))
        assertTrue(line.contains("keyType=2"))
    }

    @Test
    fun vhalFormat_includesRawMetadataAndArrayValue() {
        val line = KeyPressDiagnosticFormat.vhal(
            VhalKeyDiagnosticEvent(
                propertyId = 289475088,
                areaId = 3,
                value = intArrayOf(10, 20),
                valueType = "[I",
                timestampNanos = 123L,
                status = 0,
            )
        )

        assertTrue(line.contains("propertyId=289475088"))
        assertTrue(line.contains("areaId=3"))
        assertTrue(line.contains("value=[10, 20]"))
        assertTrue(line.contains("type=[I"))
        assertTrue(line.contains("timestamp=123"))
        assertTrue(line.contains("status=0"))
    }

    @Test
    fun androidFormat_includesActionAndCode() {
        val line = KeyPressDiagnosticFormat.android(action = "DOWN", keyCode = 24L)

        assertTrue(line.contains("action=DOWN"))
        assertTrue(line.contains("keyCode=24"))
    }
}
