package vad.dashing.mqtt.wireguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WgConfTest {
    @Test
    fun parsesANormalConfAndBuildsIpc() {
        val conf = parseWgConf(SAMPLE).getOrThrow()
        assertEquals("10.7.0.2/32", conf.addresses.single())
        assertEquals(listOf("1.1.1.1"), conf.dns)
        assertEquals(1280, conf.mtu)
        assertEquals("vpn.example.com", conf.peers.single().endpointHost)
        assertEquals(51820, conf.peers.single().endpointPort)
        assertEquals(25, conf.peers.single().keepalive)
        assertTrue(conf.allows("10.8.0.1"))
        assertFalse(conf.allows("8.8.8.8"))
        val ipc = conf.toIpc(listOf("203.0.113.5:51820"))
        assertTrue(ipc.contains("private_key=000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f"))
        assertTrue(ipc.contains("endpoint=203.0.113.5:51820"))
        assertTrue(ipc.contains("allowed_ip=10.8.0.0/24"))
        assertTrue(ipc.contains("preshared_key="))
        assertFalse(ipc.contains("AAECAwQF"))
        assertEquals("vpn.example.com:51820\n10.7.0.2/32", conf.summary())
    }

    @Test
    fun ignoresWgQuickExtrasAndSearchDomains() {
        val text = SAMPLE.replace("DNS = 1.1.1.1", "DNS = 1.1.1.1, home.arpa") +
            "\nTable = off\nPostUp = iptables -A FORWARD -j ACCEPT\n"
        val conf = parseWgConf(text).getOrThrow()
        assertEquals(listOf("1.1.1.1"), conf.dns)
    }

    @Test
    fun rejectsAFileWithoutAPeer() {
        val text = """
            [Interface]
            PrivateKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
            Address = 10.7.0.2/32
        """.trimIndent()
        assertEquals("В файле нет [Peer]", parseWgConf(text).exceptionOrNull()?.message)
    }

    @Test
    fun fullTunnelAllowsAnyV4Address() {
        assertTrue(ipInCidr("1.2.3.4", "0.0.0.0/0"))
        assertTrue(ipInCidr("10.7.0.5", "10.7.0.0/24"))
        assertFalse(ipInCidr("10.8.0.5", "10.7.0.0/24"))
        assertTrue(ipInCidr("::1", "::/0"))
    }

    private companion object {
        val SAMPLE = """
            # home
            [Interface]
            PrivateKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
            Address = 10.7.0.2/32
            DNS = 1.1.1.1
            MTU = 1280

            [Peer]
            PublicKey = ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=
            PresharedKey = BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc=
            Endpoint = vpn.example.com:51820
            AllowedIPs = 10.8.0.0/24
            PersistentKeepalive = 25
        """.trimIndent()
    }
}
