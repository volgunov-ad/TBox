package vad.dashing.tbox.internet

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.InetAddress

class HuSystemNetworksTest {
    @Test
    fun fromLink_keepsIpv4AndDropsIpv6() {
        val row = HuSystemNetworks.fromLink(
            interfaceName = " wlan0 ",
            addresses = listOf(
                InetAddress.getByName("192.168.1.128"),
                InetAddress.getByName("fe80::1"),
                InetAddress.getByName("0.0.0.0"),
            ),
            wifi = true,
            ethernet = false,
            cellular = false,
            vpn = false,
            validated = true,
            isDefault = true,
        )
        assertEquals("wlan0", row.interfaceName)
        assertEquals(listOf(HuNetTransport.WIFI), row.transports)
        assertEquals(listOf("192.168.1.128"), row.ipv4)
        assertEquals(true, row.validated)
        assertEquals(true, row.isDefault)
    }

    @Test
    fun fromLink_ethernetWithoutValidation() {
        val row = HuSystemNetworks.fromLink(
            interfaceName = "usb0",
            addresses = listOf(InetAddress.getByName("192.168.225.60")),
            wifi = false,
            ethernet = true,
            cellular = false,
            vpn = false,
            validated = false,
            isDefault = false,
        )
        assertEquals(listOf(HuNetTransport.ETHERNET), row.transports)
        assertEquals(listOf("192.168.225.60"), row.ipv4)
        assertEquals(false, row.validated)
    }

    @Test
    fun fromLink_blankInterfaceBecomesDashAndOtherTransport() {
        val row = HuSystemNetworks.fromLink(
            interfaceName = "  ",
            addresses = emptyList(),
            wifi = false,
            ethernet = false,
            cellular = false,
            vpn = false,
            validated = false,
            isDefault = false,
        )
        assertEquals("—", row.interfaceName)
        assertEquals(listOf(HuNetTransport.OTHER), row.transports)
        assertEquals(emptyList<String>(), row.ipv4)
    }

    @Test
    fun merge_addsSoftApThatConnectivityDoesNotTrack() {
        val sta = HuSystemNetworks.fromLink(
            interfaceName = "wlan0",
            addresses = listOf(InetAddress.getByName("192.168.1.128")),
            wifi = true,
            ethernet = false,
            cellular = false,
            vpn = false,
            validated = true,
            isDefault = true,
        )
        val merged = HuSystemNetworks.merge(
            connectivity = listOf(sta),
            local = listOf(
                HuLocalInterface("wlan0", listOf(InetAddress.getByName("192.168.1.128"))),
                HuLocalInterface("wlan1", listOf(InetAddress.getByName("192.168.42.38"))),
                HuLocalInterface("lo", listOf(InetAddress.getByName("127.0.0.1"))),
            ),
        )
        assertEquals(listOf("wlan0", "lo", "wlan1"), merged.map { it.interfaceName })
        val ap = merged.first { it.interfaceName == "wlan1" }
        assertEquals(listOf(HuNetTransport.HOTSPOT), ap.transports)
        assertEquals(listOf("192.168.42.38"), ap.ipv4)
        assertEquals(false, ap.validated)
        assertEquals(listOf(HuNetTransport.OTHER), merged.first { it.interfaceName == "lo" }.transports)
    }

    @Test
    fun looksLikeSoftAp_matchesTetherPoolOutsideKnownNames() {
        assertEquals(
            true,
            HuSystemNetworks.looksLikeSoftAp("swlan3", listOf(InetAddress.getByName("192.168.43.1"))),
        )
        assertEquals(
            false,
            HuSystemNetworks.looksLikeSoftAp("eth0", listOf(InetAddress.getByName("10.0.0.2"))),
        )
    }

    @Test
    fun sort_defaultThenValidatedThenName() {
        val wifi = net("wlan0", validated = true, isDefault = true)
        val usb = net("usb0", validated = false, isDefault = false)
        val other = net("wlan2", validated = true, isDefault = false)
        val sorted = HuSystemNetworks.sort(listOf(usb, other, wifi))
        assertEquals(listOf("wlan0", "wlan2", "usb0"), sorted.map { it.interfaceName })
    }

    private fun net(name: String, validated: Boolean, isDefault: Boolean) =
        HuSystemNetwork(
            interfaceName = name,
            transports = listOf(HuNetTransport.WIFI),
            ipv4 = emptyList(),
            validated = validated,
            isDefault = isDefault,
        )
}
