package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.internet.HuInternetStatus

class ModemConnectionCheckTest {

    @Test
    fun cellularChannel_requiresNetAndApn() {
        assertTrue(ModemConnectionCheck.isCellularChannelUp("4G", true))
        assertTrue(ModemConnectionCheck.isCellularChannelUp("3G", true))
        assertTrue(ModemConnectionCheck.isCellularChannelUp("2G", true))
        assertFalse(ModemConnectionCheck.isCellularChannelUp("4G", false))
        assertFalse(ModemConnectionCheck.isCellularChannelUp("No service", true))
        assertFalse(ModemConnectionCheck.isCellularChannelUp(null, true))
        assertFalse(ModemConnectionCheck.isCellularChannelUp("", false))
    }

    @Test
    fun healthy_withoutInternetProbe_followsChannelOnly() {
        assertTrue(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = true,
                internetProbeEnabled = false,
                huInternetStatus = HuInternetStatus.OFFLINE,
            ),
        )
        assertFalse(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "No service",
                apnStatus = true,
                internetProbeEnabled = false,
                huInternetStatus = HuInternetStatus.ONLINE,
            ),
        )
    }

    @Test
    fun healthy_withInternetProbe_failsOnlyOnOffline() {
        assertTrue(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = true,
                internetProbeEnabled = true,
                huInternetStatus = HuInternetStatus.ONLINE,
            ),
        )
        assertTrue(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = true,
                internetProbeEnabled = true,
                huInternetStatus = HuInternetStatus.UNKNOWN,
            ),
        )
        assertTrue(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = true,
                internetProbeEnabled = true,
                huInternetStatus = HuInternetStatus.CHECKING,
            ),
        )
        assertFalse(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = true,
                internetProbeEnabled = true,
                huInternetStatus = HuInternetStatus.OFFLINE,
            ),
        )
    }

    @Test
    fun healthy_channelDown_unhealthyRegardlessOfInternet() {
        assertFalse(
            ModemConnectionCheck.isNetworkHealthy(
                netStatus = "4G",
                apnStatus = false,
                internetProbeEnabled = true,
                huInternetStatus = HuInternetStatus.ONLINE,
            ),
        )
    }
}
