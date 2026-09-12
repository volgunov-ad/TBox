package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZteReqprocStatusMapperTest {

    @Test
    fun mapsTypicalOlaxLteConnectedStatus() {
        val json = """
            {
              "network_type":"LTE",
              "sub_network_type":"FDD_LTE",
              "rssi":"-71",
              "signalbar":"4",
              "lte_rsrp":"-95",
              "lte_rsrq":"-10",
              "lte_snr":"12",
              "lte_band":"3",
              "cell_id":"123456",
              "network_provider":"MTS",
              "imei":"860000000000001",
              "sim_imsi":"250010000000001",
              "ziccid":"89701000000000000001",
              "ppp_status":"ppp_connected",
              "modem_main_state":"modem_init_complete",
              "simcard_roam":"Home",
              "wan_ipaddr":"10.20.30.40",
              "cr_version":"F95_FAKE_FOR_TEST"
            }
        """.trimIndent()

        val snap = ZteReqprocStatusMapper.map(ZteReqprocStatusMapper.fieldsFromJsonObject(json))

        assertEquals("4G", snap.netState.netStatus)
        assertEquals(4, snap.netState.signalLevel)
        assertEquals(21, snap.netState.csq) // (-71+113)/2 = 21
        assertEquals("домашняя сеть", snap.netState.regStatus)
        assertEquals("SIM готова", snap.netState.simStatus)
        assertEquals("MTS", snap.netValues.operator)
        assertEquals("860000000000001", snap.netValues.imei)
        assertEquals("250010000000001", snap.netValues.imsi)
        assertEquals("89701000000000000001", snap.netValues.iccid)
        assertTrue(snap.apnStatus)
        assertEquals("10.20.30.40", snap.apnState.apnIP)
        assertEquals(-71, snap.rssiDbm)
        assertEquals(-95, snap.rsrpDbm)
        assertEquals("3", snap.lteBand)
        assertEquals("F95_FAKE_FOR_TEST", snap.firmware)
    }

    @Test
    fun signalBarFiveClampsToFour() {
        assertEquals(4, ZteReqprocStatusMapper.signalBarToLevel(5))
        assertEquals(0, ZteReqprocStatusMapper.signalBarToLevel(0))
        assertEquals(1, ZteReqprocStatusMapper.signalBarToLevel(1))
    }

    @Test
    fun networkTypeAliases() {
        assertEquals("4G", ZteReqprocStatusMapper.mapNetworkType("FDD_LTE"))
        assertEquals("3G", ZteReqprocStatusMapper.mapNetworkType("WCDMA"))
        assertEquals("2G", ZteReqprocStatusMapper.mapNetworkType("GSM"))
        assertEquals("-", ZteReqprocStatusMapper.mapNetworkType(""))
    }

    @Test
    fun pppDisconnectedClearsApn() {
        val fields = mapOf(
            "network_type" to "LTE",
            "signalbar" to "2",
            "ppp_status" to "ppp_disconnected",
            "modem_main_state" to "modem_init_complete",
            "sim_imsi" to "25001",
            "simcard_roam" to "Home",
        )
        val snap = ZteReqprocStatusMapper.map(fields)
        assertFalse(snap.apnStatus)
        assertEquals(false, snap.apnState.apnStatus)
        assertEquals(2, snap.netState.signalLevel)
    }

    @Test
    fun roamingAndNoSim() {
        val roam = ZteReqprocStatusMapper.map(
            mapOf(
                "network_type" to "LTE",
                "signalbar" to "3",
                "ppp_status" to "ppp_connected",
                "modem_main_state" to "modem_init_complete",
                "sim_imsi" to "25099",
                "simcard_roam" to "Roaming",
            ),
        )
        assertEquals("роуминг", roam.netState.regStatus)

        val noSim = ZteReqprocStatusMapper.map(
            mapOf(
                "modem_main_state" to "modem_sim_undetected",
                "ppp_status" to "",
            ),
        )
        assertEquals("нет SIM", noSim.netState.simStatus)
    }

    @Test
    fun fallsBackToCsqFromRssiWhenNoSignalbar() {
        val snap = ZteReqprocStatusMapper.map(
            mapOf(
                "network_type" to "LTE",
                "rssi" to "-85",
                "ppp_status" to "ppp_connected",
                "modem_main_state" to "modem_init_complete",
                "sim_imsi" to "1",
                "simcard_roam" to "Home",
            ),
        )
        // (-85+113)/2 = 14 → level 2
        assertEquals(14, snap.netState.csq)
        assertEquals(2, snap.netState.signalLevel)
    }

    @Test
    fun statusCmdsIncludeCoreKeys() {
        val cmds = ZteReqprocStatusMapper.STATUS_CMDS
        assertTrue(cmds.contains("signalbar"))
        assertTrue(cmds.contains("ppp_status"))
        assertTrue(cmds.contains("imei"))
    }
}
