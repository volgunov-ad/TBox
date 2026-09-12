package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModemConnectionCheckTest {

    @Test
    fun tbox_requiresCellularAndApn() {
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "4G", true),
        )
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "3G", true),
        )
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "2G", true),
        )
        assertFalse(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "4G", false),
        )
        assertFalse(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "No service", true),
        )
        assertFalse(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, null, true),
        )
        assertFalse(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.TBOX, "", false),
        )
    }

    @Test
    fun wifiHttp_neverTriggersRestartEvenIfOffline() {
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.WIFI_HTTP, "No service", false),
        )
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.WIFI_HTTP, null, false),
        )
        assertTrue(
            ModemConnectionCheck.isTboxCellularUp(ModemSource.WIFI_HTTP, "4G", true),
        )
    }
}
