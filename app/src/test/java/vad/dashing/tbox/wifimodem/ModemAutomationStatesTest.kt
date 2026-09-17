package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Test

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
}
