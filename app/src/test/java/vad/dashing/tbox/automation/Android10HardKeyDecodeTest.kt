package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.mbcan.KeyPressDiagnosticFormat

class Android10HardKeyDecodeTest {
    @Test
    fun wheelBroadcast_mapsDownAndUpOnly() {
        assertEquals(
            AutomationHardKeyStatus.PRESSED,
            Android10HardKeyDecode.wheelStatus("adayo.keyEvent.onKeyDown"),
        )
        assertEquals(
            AutomationHardKeyStatus.RELEASED,
            Android10HardKeyDecode.wheelStatus("adayo.keyEvent.onKeyUp"),
        )
        assertNull(Android10HardKeyDecode.wheelStatus("adayo.keyEvent.onKeyLongPress"))
    }

    @Test
    fun doorProperty_usesSameCodesAsA9() {
        assertEquals(210, KeyPressDiagnosticFormat.vhalDoorKeyCode(289412276))
        assertEquals(211, KeyPressDiagnosticFormat.vhalDoorKeyCode(289412275))
        assertEquals(212, KeyPressDiagnosticFormat.vhalDoorKeyCode(289412309))
        assertEquals(AutomationHardKeyStatus.PRESSED, Android10HardKeyDecode.doorStatus(1))
        assertEquals(AutomationHardKeyStatus.RELEASED, Android10HardKeyDecode.doorStatus(0))
        assertNull(Android10HardKeyDecode.doorStatus(2))
    }

    @Test
    fun voiceAssistBroadcast_isSuppressedAroundDoorHold() {
        assertTrue(
            Android10HardKeyDecode.suppressVoiceAssist(
                inputKey = 231,
                doorHeld = true,
                nowMillis = 1_000L,
                suppressUntilMillis = 0L,
            ),
        )
        assertTrue(
            Android10HardKeyDecode.suppressVoiceAssist(
                inputKey = 231,
                doorHeld = false,
                nowMillis = 1_000L,
                suppressUntilMillis = 1_200L,
            ),
        )
        assertFalse(
            Android10HardKeyDecode.suppressVoiceAssist(
                inputKey = 231,
                doorHeld = false,
                nowMillis = 2_000L,
                suppressUntilMillis = 1_200L,
            ),
        )
        assertFalse(
            Android10HardKeyDecode.suppressVoiceAssist(
                inputKey = 24,
                doorHeld = true,
                nowMillis = 1_000L,
                suppressUntilMillis = 5_000L,
            ),
        )
    }

    @Test
    fun picker_coversMeasuredWheelAndDoors() {
        val codes = Android10HardKeyDecode.pickerCodes
        assertTrue(19 in codes)
        assertTrue(195 in codes)
        assertTrue(4 in codes)
        assertTrue(231 in codes)
        assertTrue(210 in codes)
        assertTrue(211 in codes)
        assertTrue(212 in codes)
        assertFalse(29 in codes)
        assertFalse(216 in codes)
    }
}
