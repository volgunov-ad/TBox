package vad.dashing.voice.nlu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceSlotParserTest {
    @Test
    fun relativeWarmerAndCooler() {
        assertEquals(VoiceSlotParser.Slot.Relative(+1), VoiceSlotParser.parse("сделай потеплее"))
        assertEquals(VoiceSlotParser.Slot.Relative(-1), VoiceSlotParser.parse("похолоднее"))
    }

    @Test
    fun relativeVolume() {
        assertEquals(VoiceSlotParser.Slot.Relative(+1), VoiceSlotParser.parse("громче"))
        assertEquals(VoiceSlotParser.Slot.Relative(-1), VoiceSlotParser.parse("сделай тише"))
    }

    @Test
    fun binaryOnOff() {
        assertEquals(VoiceSlotParser.Slot.Binary(true), VoiceSlotParser.parse("включи климат"))
        assertEquals(VoiceSlotParser.Slot.Binary(false), VoiceSlotParser.parse("выключи климат"))
    }

    @Test
    fun pulseOpenClose() {
        assertEquals(VoiceSlotParser.Slot.Pulse(true), VoiceSlotParser.parse("открой багажник"))
        assertEquals(VoiceSlotParser.Slot.Pulse(false), VoiceSlotParser.parse("закрой багажник"))
    }

    @Test
    fun absoluteNumber() {
        val slot = VoiceSlotParser.parse("температура 22.5")
        assertTrue(slot is VoiceSlotParser.Slot.AbsoluteNumber)
        assertEquals(22.5, (slot as VoiceSlotParser.Slot.AbsoluteNumber).value, 1e-6)
    }

    @Test
    fun absoluteVolumeInt() {
        val slot = VoiceSlotParser.parse("громкость 15")
        assertTrue(slot is VoiceSlotParser.Slot.AbsoluteNumber)
        assertEquals(15.0, (slot as VoiceSlotParser.Slot.AbsoluteNumber).value, 1e-6)
    }
}
