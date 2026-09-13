package vad.dashing.tbox.wifimodem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuaweiHilinkStatusMapperTest {
    @Test
    fun mapsOnlineLteSnapshot() {
        val information = mapOf(
            "Imei" to "860000000000001",
            "Imsi" to "250000000000001",
            "Iccid" to "89701000000000000001",
            "WanIPAddress" to "10.1.2.3",
            "SoftwareVersion" to "3.0.2.62",
            "FullName" to "MTS",
        )
        val monitoring = mapOf(
            "ConnectionStatus" to "901",
            "SignalIcon" to "4",
            "SimStatus" to "1",
            "RoamingStatus" to "0",
        )
        val signal = mapOf(
            "rssi" to "-70dBm",
            "rsrp" to "-95dBm",
            "mode" to "7",
            "cell_id" to "12345",
        )
        val traffic = mapOf(
            "CurrentDownloadRate" to "153600",
            "CurrentUploadRate" to "20480",
        )
        val snap = HuaweiHilinkStatusMapper.map(
            information = information,
            monitoring = monitoring,
            signal = signal,
            previous = null,
            traffic = traffic,
        )
        assertTrue(snap.apnStatus)
        assertEquals("4G", snap.netState.netStatus)
        assertEquals("860000000000001", snap.netValues.imei)
        assertEquals("10.1.2.3", snap.apnState.apnIP)
        assertEquals(4, snap.netState.signalLevel)
        assertEquals(-70, snap.netState.signalDbm)
        assertEquals(-70, snap.rssiDbm)
        assertEquals(-95, snap.rsrpDbm)
        assertEquals(153600L, snap.netState.downloadSpeedBps)
        assertEquals(20480L, snap.netState.uploadSpeedBps)
    }

    @Test
    fun mapsDbmFromRsrpWhenRssiMissing() {
        val snap = HuaweiHilinkStatusMapper.map(
            information = emptyMap(),
            monitoring = mapOf("ConnectionStatus" to "901", "SignalIcon" to "3"),
            signal = mapOf("rsrp" to "-102dBm", "mode" to "7"),
            previous = null,
        )
        assertEquals(-102, snap.netState.signalDbm)
        assertEquals(-102, snap.rsrpDbm)
    }

    @Test
    fun mapsOfflineAfterDataswitch() {
        val snap = HuaweiHilinkStatusMapper.map(
            information = emptyMap(),
            monitoring = mapOf("ConnectionStatus" to "902", "SimStatus" to "1"),
            signal = emptyMap(),
            previous = null,
        )
        assertEquals(false, snap.apnStatus)
    }
}
