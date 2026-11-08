package vad.dashing.tbox.mbcan

import org.junit.Assert.assertNull
import org.junit.Test

class HuMcuBoardVoltageHoldTest {
    @Test
    fun setHeldOnJvmDoesNotCrashAndClearsState() {
        // Default UniversalCan mode is A9, so start() returns false without loading JNI.
        HuMcuBoardVoltage.setHeld("unit-test-hold", true)
        assertNull(HuMcuBoardVoltage.state.value)
        assertNull(HuMcuBoardVoltage.currentGear.value)
        HuMcuBoardVoltage.setHeld("unit-test-hold", false)
        assertNull(HuMcuBoardVoltage.state.value)
        assertNull(HuMcuBoardVoltage.currentGear.value)
    }

    @Test
    fun codecsTolerateEmptyAndShortPayloads() {
        assertNull(BoardVoltageCodec.decode(ByteArray(0)))
        assertNull(DevStatusGearCodec.decode(ByteArray(0)))
        assertNull(BoardVoltageCodec.decode(byteArrayOf(0x83.toByte(), 0x05)))
        assertNull(DevStatusGearCodec.decode(byteArrayOf(0x83.toByte(), 0x00, 0, 0, 0, 0)))
    }
}
