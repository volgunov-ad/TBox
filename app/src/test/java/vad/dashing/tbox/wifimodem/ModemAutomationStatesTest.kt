package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.NetState

class ModemAutomationStatesTest {
    @Test
    fun linkStatusKey_mapsAllEnumValues() {
        assertEquals("idle", ModemAutomationStates.linkStatusKey(WifiModemLinkStatus.IDLE))
        assertEquals("ok", ModemAutomationStates.linkStatusKey(WifiModemLinkStatus.OK))
        assertEquals(
            "auth_failed",
            ModemAutomationStates.linkStatusKey(WifiModemLinkStatus.AUTH_FAILED),
        )
        assertEquals(
            "unreachable",
            ModemAutomationStates.linkStatusKey(WifiModemLinkStatus.UNREACHABLE),
        )
        assertEquals("error", ModemAutomationStates.linkStatusKey(WifiModemLinkStatus.ERROR))
    }

    @Test
    fun mobileDataKey_isBinary() {
        assertEquals("on", ModemAutomationStates.mobileDataKey(true))
        assertEquals("off", ModemAutomationStates.mobileDataKey(false))
    }

    @Test
    fun netTypeKey_mapsRatAndEmpty() {
        assertEquals("2g", ModemAutomationStates.netTypeKey("2G"))
        assertEquals("3g", ModemAutomationStates.netTypeKey("3G"))
        assertEquals("4g", ModemAutomationStates.netTypeKey("4G"))
        assertEquals("none", ModemAutomationStates.netTypeKey("нет сети"))
        assertEquals("none", ModemAutomationStates.netTypeKey("-"))
        assertEquals("none", ModemAutomationStates.netTypeKey(null))
        assertEquals("none", ModemAutomationStates.netTypeKey(""))
    }

    @Test
    fun simStatusKey_mapsRussianUiStrings() {
        assertEquals("none", ModemAutomationStates.simStatusKey("нет SIM"))
        assertEquals("ready", ModemAutomationStates.simStatusKey("SIM готова"))
        assertEquals("pin", ModemAutomationStates.simStatusKey("требуется PIN"))
        assertEquals("error", ModemAutomationStates.simStatusKey("ошибка SIM"))
        assertEquals("unknown", ModemAutomationStates.simStatusKey(""))
        assertEquals("unknown", ModemAutomationStates.simStatusKey("-"))
        assertEquals("unknown", ModemAutomationStates.simStatusKey(null))
    }

    @Test
    fun registeredKey_homeAndRoamingAreOn() {
        assertEquals("on", ModemAutomationStates.registeredKey("домашняя сеть"))
        assertEquals("on", ModemAutomationStates.registeredKey("роуминг"))
        assertEquals("off", ModemAutomationStates.registeredKey("нет"))
        assertEquals("off", ModemAutomationStates.registeredKey("поиск сети"))
        assertEquals("off", ModemAutomationStates.registeredKey("-"))
        assertEquals("off", ModemAutomationStates.registeredKey(null))
    }

    @Test
    fun modemSourceKey_mapsEnum() {
        assertEquals("tbox", ModemAutomationStates.modemSourceKey(ModemSource.TBOX))
        assertEquals("wifi_http", ModemAutomationStates.modemSourceKey(ModemSource.WIFI_HTTP))
    }

    @Test
    fun rssiDbmOrNull_prefersSignalDbmThenCsq() {
        assertEquals(
            -85,
            ModemAutomationStates.rssiDbmOrNull(NetState(signalDbm = -85, csq = 10)),
        )
        assertEquals(
            -93,
            ModemAutomationStates.rssiDbmOrNull(NetState(csq = 10)),
        )
        assertNull(ModemAutomationStates.rssiDbmOrNull(NetState(csq = 99)))
        assertNull(ModemAutomationStates.rssiDbmOrNull(NetState()))
    }

    @Test
    fun operatorOrNull_skipsPlaceholders() {
        assertEquals("МТС", ModemAutomationStates.operatorOrNull("МТС"))
        assertNull(ModemAutomationStates.operatorOrNull("-"))
        assertNull(ModemAutomationStates.operatorOrNull(""))
        assertNull(ModemAutomationStates.operatorOrNull(null))
    }

    @Test
    fun throughputKbpsOrNull_convertsBytesPerSec() {
        assertEquals(8.0, ModemAutomationStates.throughputKbpsOrNull(1000L)!!, 0.0001)
        assertNull(ModemAutomationStates.throughputKbpsOrNull(null))
        assertNull(ModemAutomationStates.throughputKbpsOrNull(-1L))
    }
}
