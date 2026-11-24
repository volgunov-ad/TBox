package vad.dashing.mqtt.wireguard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import vad.dashing.mqtt.settings.MqttSettings

class WgTunnelOwnershipTest {
    @After
    fun tearDown() {
        WgTunnel.resetForTest()
    }

    @Test
    fun probePlansDoNotTouchAForeignTunnel() {
        assertTrue(planProbeTunnel(null, null) is ProbeTunnelPlan.Direct)
        val direct = planProbeTunnel(null, WgHeld("conf-a", 1L, 9)) as ProbeTunnelPlan.Direct
        assertTrue(direct.note!!.contains("не останавливала"))
        assertTrue(planProbeTunnel("conf-a", null) is ProbeTunnelPlan.Start)
        assertEquals(9, (planProbeTunnel("conf-a", WgHeld("conf-a", 1L, 9)) as ProbeTunnelPlan.Reuse).port)
        val refuse = planProbeTunnel("conf-b", WgHeld("conf-a", 1L, 9)) as ProbeTunnelPlan.Refuse
        assertEquals(PROBE_OTHER_TUNNEL_MESSAGE, refuse.message)
    }

    @Test
    fun probeRouteDoesNotStopTheBridgeTunnel() {
        val backend = FakeBackend()
        WgTunnel.backend = backend
        val bridge = WgTunnel.newOwner()
        val live = settings(CONF_A)
        assertEquals(51883, WgTunnel.route(live, bridge)!!.port)
        assertEquals(1, backend.starts)
        val stopsAfterStart = backend.stops

        WgTunnel.route(
            live.copy(wireguardEnabled = false),
            WgTunnel.newOwner(),
            takeover = false,
        )
        assertEquals(bridge, WgTunnel.held()!!.owner)
        assertEquals(stopsAfterStart, backend.stops)

        try {
            WgTunnel.route(settings(CONF_B), WgTunnel.newOwner(), takeover = false)
            fail("different tunnel must be refused")
        } catch (error: WgRouteException) {
            assertEquals(PROBE_OTHER_TUNNEL_MESSAGE, error.message)
        }
        assertEquals(1, backend.starts)
        assertEquals(bridge, WgTunnel.held()!!.owner)

        try {
            WgTunnel.route(live, bridge, stillActive = { false })
            fail("inactive caller must not touch the tunnel")
        } catch (_: WgSupersededException) {
        }
        assertEquals(bridge, WgTunnel.held()!!.owner)

        val probe = WgTunnel.newOwner()
        WgTunnel.release(probe)
        assertEquals(bridge, WgTunnel.held()!!.owner)
        val replacement = WgTunnel.newOwner()
        assertEquals(51883, WgTunnel.route(live, replacement, takeover = true)!!.port)
        assertEquals(1, backend.starts)
        assertEquals(stopsAfterStart, backend.stops)
        assertEquals(replacement, WgTunnel.held()!!.owner)
        WgTunnel.release(bridge)
        assertEquals(replacement, WgTunnel.held()!!.owner)
        WgTunnel.release(replacement)
        assertNull(WgTunnel.held())
        assertEquals(stopsAfterStart + 1, backend.stops)
    }

    private fun settings(conf: String) = MqttSettings(
        brokerHost = "10.8.0.1",
        brokerPort = 1883,
        wireguardEnabled = true,
        wireguardConf = conf,
    )

    private class FakeBackend : WgBackend {
        var starts = 0
        var stops = 0

        override fun start(ipc: String, addresses: String, dns: String, mtu: Int, broker: String): Int {
            starts += 1
            return 51883
        }

        override fun stop() {
            stops += 1
        }

        override fun lastError(): String = ""
    }

    private companion object {
        val CONF_A = """
            [Interface]
            PrivateKey = AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=
            Address = 10.7.0.2/32
            DNS = 1.1.1.1
            MTU = 1280

            [Peer]
            PublicKey = ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8=
            Endpoint = 203.0.113.10:51820
            AllowedIPs = 10.8.0.0/24
        """.trimIndent()

        val CONF_B = CONF_A.replace("10.7.0.2/32", "10.7.0.3/32")
    }
}
