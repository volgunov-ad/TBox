package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdasHvacStockDecodeTest {

    @Test
    fun lasMode_acceptsOneTwoThree() {
        assertEquals(1, MbCanSignalStateEngine.decodeLasModeRaw(1))
        assertEquals(2, MbCanSignalStateEngine.decodeLasModeRaw(2))
        assertEquals(3, MbCanSignalStateEngine.decodeLasModeRaw(3))
        assertNull(MbCanSignalStateEngine.decodeLasModeRaw(0))
        assertNull(MbCanSignalStateEngine.decodeLasModeRaw(4))
    }

    @Test
    fun hvacAcMax_vhalOnWhenRawTwo() {
        assertEquals(MbCanBinaryState.On, MbCanSignalStateEngine.decodeHvacAcMaxVhalRaw(2))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeHvacAcMaxVhalRaw(1))
        assertEquals(MbCanBinaryState.Off, MbCanSignalStateEngine.decodeHvacAcMaxVhalRaw(0))
    }

    @Test
    fun hvacCustom_vhalRawPlusOne() {
        assertEquals(HvacCustomMode.Eco, HvacCustomMode.fromVhalRaw(0))
        assertEquals(HvacCustomMode.Comfort, HvacCustomMode.fromVhalRaw(1))
        assertEquals(HvacCustomMode.Strong, HvacCustomMode.fromVhalRaw(2))
        assertNull(HvacCustomMode.fromVhalRaw(3))
    }

    @Test
    fun hvacCustom_cyclesEcoComfortStrong() {
        assertEquals(HvacCustomMode.Comfort, HvacCustomMode.nextInCycle(HvacCustomMode.Eco))
        assertEquals(HvacCustomMode.Strong, HvacCustomMode.nextInCycle(HvacCustomMode.Comfort))
        assertEquals(HvacCustomMode.Eco, HvacCustomMode.nextInCycle(HvacCustomMode.Strong))
        assertEquals(HvacCustomMode.Eco, HvacCustomMode.nextInCycle(null))
    }
}
