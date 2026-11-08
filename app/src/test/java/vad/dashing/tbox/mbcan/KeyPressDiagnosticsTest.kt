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
        assertTrue(line.contains("name=WHEEL_RIGHT_JOY_UP"))
        assertTrue(line.contains("keyStatus=1"))
        assertTrue(line.contains("state=RELEASED"))
        assertTrue(line.contains("keyType=2"))
    }

    @Test
    fun mbCanFormat_decodesVerifiedWheelAndDoorKeyNames() {
        assertTrue(KeyPressDiagnosticFormat.mbCan(29, 0, 0).contains("name=WHEEL_LEFT_JOY_UP"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(30, 0, 0).contains("name=WHEEL_LEFT_JOY_DOWN"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(31, 0, 0).contains("name=WHEEL_LEFT_JOY_LEFT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(32, 0, 0).contains("name=WHEEL_LEFT_JOY_RIGHT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(33, 0, 0).contains("name=WHEEL_LEFT_JOY_PRESS"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(216, 0, 0).contains("name=WHEEL_LEFT_BTN_TOP"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(316, 1, 0).contains("name=WHEEL_LEFT_BTN_BOTTOM"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(114, 0, 0).contains("name=WHEEL_RIGHT_JOY_DOWN"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(163, 0, 0).contains("name=WHEEL_RIGHT_JOY_LEFT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(165, 0, 0).contains("name=WHEEL_RIGHT_JOY_RIGHT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(28, 1, 0).contains("name=WHEEL_RIGHT_JOY_PRESS"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(158, 0, 0).contains("name=WHEEL_RIGHT_BTN_TOP"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(582, 0, 0).contains("name=WHEEL_RIGHT_BTN_BOTTOM"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(210, 0, 0).contains("name=DOOR_FRONT_PASSENGER"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(211, 0, 0).contains("name=DOOR_REAR_RIGHT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(212, 0, 0).contains("name=DOOR_REAR_LEFT"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(999, 0, 0).contains("name=UNKNOWN"))
    }

    @Test
    fun mbCanFormat_decodesKeyStatus() {
        assertTrue(KeyPressDiagnosticFormat.mbCan(29, 0, 0).contains("state=PRESSED"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(29, 1, 0).contains("state=RELEASED"))
        assertTrue(KeyPressDiagnosticFormat.mbCan(29, 7, 0).contains("state=UNKNOWN"))
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
        assertFalse(line.contains("name="))
    }

    @Test
    fun vhalFormat_namesDoorPanelButtons() {
        val line = KeyPressDiagnosticFormat.vhal(
            VhalKeyDiagnosticEvent(
                propertyId = 289412276,
                areaId = 0,
                value = 1,
                valueType = "java.lang.Integer",
                timestampNanos = null,
                status = null,
            )
        )

        assertTrue(line.contains("propertyId=289412276"))
        assertTrue(line.contains("name=DOOR_FRONT_PASSENGER"))
        assertTrue(line.contains("value=1"))
        assertEquals("DOOR_REAR_LEFT", KeyPressDiagnosticFormat.vhalDoorName(289412309))
        assertEquals("DOOR_REAR_RIGHT", KeyPressDiagnosticFormat.vhalDoorName(289412275))
    }

    @Test
    fun adayoFormat_includesActionAndHardKey() {
        val line = KeyPressDiagnosticFormat.adayo("adayo.keyEvent.onKeyDown", "K_PTT", null)

        assertTrue(line.contains("action=adayo.keyEvent.onKeyDown"))
        assertTrue(line.contains("hardKey=K_PTT"))
        assertTrue(KeyPressDiagnosticFormat.adayo("adayo.keyEvent.onKeyUp", null, null).contains("hardKey=-"))
    }

    @Test
    fun adayoFormat_includesCarInputKeyCode() {
        val line = KeyPressDiagnosticFormat.adayo("adayo.keyEvent.onKeyDown", null, 24)

        assertTrue(line.contains("action=adayo.keyEvent.onKeyDown"))
        assertTrue(line.contains("InputKey=24"))
        assertTrue(line.contains("name=WHEEL_RIGHT_JOY_UP"))
        assertFalse(line.contains("hardKey=-"))
        assertEquals("WHEEL_LEFT_JOY_PRESS", KeyPressDiagnosticFormat.adayoInputKeyName(195))
        assertEquals("WHEEL_LEFT_BTN_BOTTOM", KeyPressDiagnosticFormat.adayoInputKeyName(189))
        assertEquals("WHEEL_RIGHT_BTN_TOP", KeyPressDiagnosticFormat.adayoInputKeyName(4))
        assertEquals("WHEEL_RIGHT_BTN_BOTTOM", KeyPressDiagnosticFormat.adayoInputKeyName(231))
        assertEquals("WHEEL_RIGHT_JOY_PRESS", KeyPressDiagnosticFormat.adayoInputKeyName(91))
        assertEquals("UNKNOWN", KeyPressDiagnosticFormat.adayoInputKeyName(999))
    }

    @Test
    fun androidFormat_includesActionAndCode() {
        val line = KeyPressDiagnosticFormat.android(action = "DOWN", keyCode = 24L, nativeEvent = null)

        assertTrue(line.contains("action=DOWN"))
        assertTrue(line.contains("keyCode=24"))
        assertFalse(line.contains("scanCode="))
    }
}
