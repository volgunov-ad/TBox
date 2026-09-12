package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Test

class WifiModemModelTest {

    @Test
    fun fromStorageDefaultsToOlaxF95() {
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage(null))
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage(""))
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage("unknown"))
    }

    @Test
    fun fromStorageRecognizesIds() {
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage("olax_f95"))
        assertEquals(WifiModemModel.OLAX_F95, WifiModemModel.fromStorage("OLAX_F95"))
        assertEquals("192.168.0.1", WifiModemModel.OLAX_F95.defaultHost)
    }
}
