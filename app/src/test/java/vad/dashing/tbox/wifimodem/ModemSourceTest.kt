package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Test

class ModemSourceTest {

    @Test
    fun fromStorage() {
        assertEquals(ModemSource.TBOX, ModemSource.fromStorage(null))
        assertEquals(ModemSource.TBOX, ModemSource.fromStorage(""))
        assertEquals(ModemSource.TBOX, ModemSource.fromStorage("TBOX"))
        assertEquals(ModemSource.WIFI_HTTP, ModemSource.fromStorage("WIFI_HTTP"))
        assertEquals(ModemSource.WIFI_HTTP, ModemSource.fromStorage("wifi"))
        assertEquals(ModemSource.WIFI_HTTP, ModemSource.fromStorage("http"))
    }
}
