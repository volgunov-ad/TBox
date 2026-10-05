package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class OlaxReqprocStatusMapperTest {

    private fun load(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("wifimodem/olax_f95/$name")
            ?: error("missing fixture wifimodem/olax_f95/$name")
        return stream.readBytes().toString(StandardCharsets.UTF_8)
    }

    @Test
    fun mapsHarHomePollConnectedLte() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("home_poll_connected_lte.json"))
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals("4G", snap.netState.netStatus)
        assertEquals(4, snap.netState.signalLevel) // signalbar 5 → 4
        assertEquals("BeeLine/Vimp", snap.netValues.operator)
        assertTrue(snap.apnStatus)
        assertEquals("SIM готова", snap.netState.simStatus)
        assertEquals("домашняя сеть", snap.netState.regStatus)
        assertEquals(368304L, snap.netState.downloadSpeedBps)
        assertEquals(12684L, snap.netState.uploadSpeedBps)
    }

    @Test
    fun mapsHarDeviceRadioDbmAndImei() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("status_device.json"))
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals("REDACTED_IMEI", snap.netValues.imei)
        assertEquals("REDACTED_SIM_IMSI", snap.netValues.imsi)
        assertEquals("REDACTED_ZICCID", snap.netValues.iccid)
        assertEquals(-83, snap.netState.signalDbm)
        assertEquals(-83, snap.rssiDbm)
        assertEquals(-83, snap.rsrpDbm)
        assertTrue(snap.firmware.contains("F95SW"))
        assertTrue(snap.apnStatus)
    }

    @Test
    fun mapsMergedHomeAndDeviceHasDbmAndThrpt() {
        val home = ZteReqprocStatusMapper.fieldsFromJsonObject(load("home_poll_connected_lte.json"))
        val device = ZteReqprocStatusMapper.fieldsFromJsonObject(load("status_device.json"))
        val fields = OlaxReqprocStatusCmds.mergePreferNonBlank(home, device)
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals(-83, snap.netState.signalDbm)
        assertEquals(368304L, snap.netState.downloadSpeedBps)
        assertEquals(12684L, snap.netState.uploadSpeedBps)
        assertEquals("REDACTED_IMEI", snap.netValues.imei)
        assertEquals("4G", snap.netState.netStatus)
        assertEquals(4, snap.netState.signalLevel)
    }

    @Test
    fun mapsHarNetworkInfoRadio() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("network_info_lte.json"))
        val snap = ZteReqprocStatusMapper.map(fields)
        assertEquals(-83, snap.netState.signalDbm)
        assertEquals(13, snap.rsrqDb)
        assertEquals(6, snap.sinrDb)
        assertEquals("20", snap.lteBand)
        assertEquals("197387779", snap.cellId)
    }

    @Test
    fun statusCmdListsCoverCoreKeys() {
        assertTrue(OlaxReqprocStatusCmds.HOME.contains("signalbar"))
        assertTrue(OlaxReqprocStatusCmds.HOME.contains("realtime_rx_thrpt"))
        assertTrue(OlaxReqprocStatusCmds.RADIO.contains("lte_rsrp"))
        assertTrue(OlaxReqprocStatusCmds.DEVICE.contains("imei"))
        assertEquals("/reqproc/proc_get", OlaxReqprocStatusCmds.GET_PATH)
        assertEquals("/reqproc/proc_post", OlaxReqprocStatusCmds.POST_PATH)
    }

    @Test
    fun mergePreferNonBlankKeepsHomeThrpt() {
        val home = mapOf("realtime_rx_thrpt" to "100", "signalbar" to "5")
        val radio = mapOf("realtime_rx_thrpt" to "", "rssi" to "-80")
        val merged = OlaxReqprocStatusCmds.mergePreferNonBlank(home, radio)
        assertEquals("100", merged["realtime_rx_thrpt"])
        assertEquals("-80", merged["rssi"])
        assertFalse(merged["realtime_rx_thrpt"].isNullOrBlank())
    }
}
