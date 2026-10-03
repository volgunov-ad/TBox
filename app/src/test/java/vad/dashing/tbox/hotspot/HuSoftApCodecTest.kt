package vad.dashing.tbox.hotspot

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuSoftApCodecTest {
    @Test
    fun parseResultKeepsSsidPasswordBandAndChannel() {
        val line = result(
            ssid = "\"AndroidAP_1234\"",
            password = "ab;cd:ef",
            band = 1,
            channel = 149,
            auth = 4,
            enabled = true,
        )
        val read = HuSoftApCodec.parse("noise\n$line\n")
        assertEquals("AndroidAP_1234", read?.ssid)
        assertEquals("ab;cd:ef", read?.password)
        assertEquals(1, read?.band)
        assertEquals(149, read?.channel)
        assertEquals(4, read?.authType)
        assertEquals(5745, read?.frequencyMhz)
        assertTrue(read?.enabled == true)
    }

    @Test
    fun liveFrequencyOverridesStoredBand() {
        val line = "RESULT ok=1 enabled=1 band=0 channel=0 auth=4 " +
            "ssidB64=${b64("28adfea9")} pskB64=${b64("b6ef26be")} live=5220"
        val read = HuSoftApCodec.parse(line)
        assertEquals(5220, read?.liveMhz)
        assertEquals(5220, read?.frequencyMhz)
        assertEquals(44, HuSoftApCodec.channelOfMhz(5220))
        assertFalse(HuSoftApCodec.on24Ghz(5220))
        assertTrue(HuSoftApCodec.on24Ghz(2437))
        assertTrue(HuSoftApCodec.on24Ghz(2462))
        assertFalse(HuSoftApCodec.on24Ghz(0))
        assertEquals(6, HuSoftApCodec.channelOfMhz(2437))
        assertEquals(11, HuSoftApCodec.channelOfMhz(2462))
        assertEquals(14, HuSoftApCodec.channelOfMhz(2484))
    }

    @Test
    fun frequencyFromChannel() {
        assertEquals(2437, HuSoftApCodec.frequencyMhz(0, 6))
        assertEquals(2484, HuSoftApCodec.frequencyMhz(0, 14))
        assertEquals(5745, HuSoftApCodec.frequencyMhz(1, 149))
        assertNull(HuSoftApCodec.frequencyMhz(1, 0))
        assertNull(HuSoftApCodec.frequencyMhz(-1, 6))
    }

    @Test
    fun selectHotspotIpv4PrefersSoftApInterfaceThenTetherPool() {
        val wlan1 = HuSoftApCodec.selectHotspotIpv4(
            listOf(
                SoftApIface("wlan0", "192.168.1.20"),
                SoftApIface("wlan1", "192.168.43.1"),
            ),
        )
        assertEquals("192.168.43.1", wlan1)
        val pool = HuSoftApCodec.selectHotspotIpv4(
            listOf(
                SoftApIface("wlan0", "192.168.1.20"),
                SoftApIface("rndis0", "192.168.49.1"),
            ),
        )
        assertEquals("192.168.49.1", pool)
        assertNull(
            HuSoftApCodec.selectHotspotIpv4(listOf(SoftApIface("wlan0", "192.168.1.20"))),
        )
    }

    @Test
    fun wifiQrEscapesAndPicksSecurity() {
        assertEquals(
            "WIFI:T:WPA;S:car\\;net;P:p\\:ass;;",
            HuSoftApCodec.wifiQrPayload("car;net", "p:ass", 4),
        )
        assertEquals(
            "WIFI:T:SAE;S:ap;P:secret12;;",
            HuSoftApCodec.wifiQrPayload("ap", "secret12", 8),
        )
        assertEquals(
            "WIFI:T:nopass;S:open;;",
            HuSoftApCodec.wifiQrPayload("open", "", 0),
        )
    }

    @Test
    fun shellCommandQuotesApkAndDoesNotRewriteConfig() {
        val command = HuSoftApCodec.shellCommand("/data/app/base.apk", "up")
        assertEquals(
            "CLASSPATH='/data/app/base.apk' app_process /system/bin " +
                "vad.dashing.tbox.hotspot.HuSoftApMain up",
            command,
        )
        assertFalse(command.contains("setWifiApConfiguration"))
    }

    @Test
    fun actionAndErrorLines() {
        assertTrue(HuSoftApCodec.parseActionOk("RESULT ok=1 action=down") == true)
        assertNull(HuSoftApCodec.parse("RESULT ok=1 action=up"))
        assertEquals("denied", HuSoftApCodec.errorCode("log\nRESULT ok=0 err=denied"))
        assertNull(HuSoftApCodec.errorCode("RESULT ok=1 action=up"))
    }

    private fun result(
        ssid: String,
        password: String,
        band: Int,
        channel: Int,
        auth: Int,
        enabled: Boolean,
    ): String {
        val on = if (enabled) 1 else 0
        return "RESULT ok=1 enabled=$on band=$band channel=$channel auth=$auth " +
            "ssidB64=${b64(ssid)} pskB64=${b64(password)}"
    }

    private fun b64(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
}
