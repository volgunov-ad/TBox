package vad.dashing.tbox.esp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EspCompanionRepositoryBleTest {
    @Before
    fun reset() {
        EspCompanionRepository.updateConnected(true)
        EspCompanionRepository.updateConnected(false)
        EspCompanionRepository.clearOtaUiState()
    }

    @Test
    fun applyBleBtn_updatesPerMacBatteryAndLastEvent() {
        EspCompanionRepository.updateConnected(true)
        EspCompanionRepository.applyBleStatus(
            on = true,
            learn = false,
            macs = listOf("AA:BB:CC:DD:EE:01", "aa:bb:cc:dd:ee:02"),
        )
        EspCompanionRepository.applyBleBtn(
            EspBleBtnEvent(
                mac = "AA:BB:CC:DD:EE:01",
                btn = 1,
                act = "press",
                bat = 88,
                rssi = -55,
            ),
        )
        val d1 = EspCompanionRepository.bleDevices.value["aa:bb:cc:dd:ee:01"]
        assertEquals(88, d1?.batteryPercent)
        assertEquals(1, d1?.lastBtn)
        assertEquals("press", d1?.lastAct)
        assertEquals(-55, d1?.lastRssi)
        assertNull(EspCompanionRepository.bleDevices.value["aa:bb:cc:dd:ee:02"]?.batteryPercent)
    }

    @Test
    fun applyBleStatus_lastMacUpdatesBattery() {
        EspCompanionRepository.updateConnected(true)
        EspCompanionRepository.applyBleStatus(
            on = true,
            learn = false,
            macs = listOf("11:22:33:44:55:66"),
            lastBat = 42,
            lastRssi = -70,
            lastMac = "11:22:33:44:55:66",
        )
        assertEquals(
            42,
            EspCompanionRepository.bleDevices.value["11:22:33:44:55:66"]?.batteryPercent,
        )
        assertEquals(
            -70,
            EspCompanionRepository.bleDevices.value["11:22:33:44:55:66"]?.lastRssi,
        )
    }

    @Test
    fun finishOta_successIncrementsEpochAndClearResetsProgress() {
        val before = EspCompanionRepository.otaSuccessEpoch.value
        EspCompanionRepository.beginOta()
        EspCompanionRepository.updateOtaProgress(50)
        EspCompanionRepository.finishOta(null)
        assertEquals(100, EspCompanionRepository.otaProgress.value)
        assertTrue(EspCompanionRepository.otaSuccessEpoch.value > before)
        EspCompanionRepository.clearOtaUiState()
        assertEquals(0, EspCompanionRepository.otaProgress.value)
        assertNull(EspCompanionRepository.otaError.value)
    }

    @Test
    fun namesCodec_roundTrip() {
        val encoded = EspBleDeviceNamesCodec.encode(
            mapOf("AA:BB:CC:DD:EE:FF" to "  Kitchen  ", "" to "x"),
        )
        val decoded = EspBleDeviceNamesCodec.decode(encoded)
        assertEquals(mapOf("aa:bb:cc:dd:ee:ff" to "Kitchen"), decoded)
        assertEquals(
            "Kitchen (aa:bb:cc:dd:ee:ff)",
            EspBleDeviceNamesCodec.label("AA:BB:CC:DD:EE:FF", decoded),
        )
    }
}
