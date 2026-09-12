package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class ZteGoformStatusMapperTest {

    private fun load(name: String): String {
        val stream = javaClass.classLoader!!.getResourceAsStream("wifimodem/zte_mf79u/$name")
            ?: error("missing fixture wifimodem/zte_mf79u/$name")
        return stream.readBytes().toString(StandardCharsets.UTF_8)
    }

    @Test
    fun mapsHarHomePollNoSim() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("home_poll.json"))
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals("нет сети", snap.netState.netStatus)
        assertEquals("нет SIM", snap.netState.simStatus)
        assertFalse(snap.apnStatus)
        assertEquals("modem_sim_undetected", snap.modemMainStateRaw)
        assertEquals("ppp_disconnected", snap.pppStatusRaw)
        assertEquals("LIMITED_SERVICE_GSM", snap.networkTypeRaw)
    }

    @Test
    fun mapsHarDeviceInfoRssiAndImei() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("status_device.json"))
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals("REDACTED_IMEI", snap.netValues.imei)
        assertEquals("REDACTED_IMSI", snap.netValues.imsi)
        assertEquals(-87, snap.rssiDbm)
        // (-87 + 113) / 2 = 13 → level 2
        assertEquals(13, snap.netState.csq)
        assertEquals(2, snap.netState.signalLevel)
        assertEquals("нет сети", snap.netState.netStatus)
        assertFalse(snap.apnStatus)
        assertTrue(snap.firmware.contains("MF79U") || snap.firmware.contains("BD_"))
    }

    @Test
    fun mapsSyntheticConnectedLte() {
        val fields = ZteReqprocStatusMapper.fieldsFromJsonObject(load("status_connected_synthetic.json"))
        val snap = ZteReqprocStatusMapper.map(fields)

        assertEquals("4G", snap.netState.netStatus)
        assertEquals(4, snap.netState.signalLevel)
        assertEquals("MTS", snap.netValues.operator)
        assertTrue(snap.apnStatus)
        assertEquals("10.20.30.40", snap.apnState.apnIP)
        assertEquals("домашняя сеть", snap.netState.regStatus)
        assertEquals("SIM готова", snap.netState.simStatus)
    }

    @Test
    fun limitedServiceGsmIsNoNetworkNot2g() {
        assertEquals(
            "нет сети",
            ZteReqprocStatusMapper.mapNetworkType("LIMITED_SERVICE_GSM"),
        )
    }

    @Test
    fun homeCmdListContainsSignalKeys() {
        assertTrue(ZteGoformStatusCmds.HOME.contains("signalbar"))
        assertTrue(ZteGoformStatusCmds.HOME.contains("ppp_status"))
        assertTrue(ZteGoformStatusCmds.DEVICE.contains("imei"))
    }
}
