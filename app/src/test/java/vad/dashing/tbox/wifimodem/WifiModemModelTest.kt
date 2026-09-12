package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiModemModelTest {

    @Test
    fun fromStorageDefaultsToZteMf79u() {
        assertEquals(WifiModemModel.ZTE_MF79U, WifiModemModel.fromStorage(null))
        assertEquals(WifiModemModel.ZTE_MF79U, WifiModemModel.fromStorage(""))
        assertEquals(WifiModemModel.ZTE_MF79U, WifiModemModel.fromStorage("unknown"))
    }

    @Test
    fun fromStorageRecognizesOlaxF95() {
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage("olax_f95"))
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage("OLAX_F95"))
        assertEquals("192.168.0.1", WifiModemModel.OLAX_F95.defaultHost)
    }

    @Test
    fun fromStorageRecognizesZteMf79u() {
        assertEquals(WifiModemModel.ZTE_MF79U, WifiModemModel.fromStorage("zte_mf79u"))
        assertEquals(WifiModemModel.ZTE_MF79U, WifiModemModel.fromStorage("ZTE_MF79U"))
        assertEquals("192.168.0.1", WifiModemModel.ZTE_MF79U.defaultHost)
    }
}
