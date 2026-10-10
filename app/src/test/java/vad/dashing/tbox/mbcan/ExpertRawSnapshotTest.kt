package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpertRawSnapshotTest {

    private val vehicle = ExpertRawCanParam("HVAC_POWER", 7, ExpertRawCanBus.Vehicle, null, null)
    private val bcm = ExpertRawCanParam("eMBCAN_VEHICLE_BCM_STATUS", 21, ExpertRawCanBus.MbCanObject, null, null)

    @Test
    fun `entries flatten object fields under the row key`() {
        val result = ExpertRawGetResult(
            success = true,
            message = "ok",
            fields = listOf("stLightSts.nHighBeamSts" to "1", "nWiperSts" to "0"),
        )
        assertEquals(
            listOf(
                "MbCanObject/eMBCAN_VEHICLE_BCM_STATUS(21).stLightSts.nHighBeamSts" to "1",
                "MbCanObject/eMBCAN_VEHICLE_BCM_STATUS(21).nWiperSts" to "0",
            ),
            ExpertRawSnapshot.entries(bcm, result),
        )
        assertEquals(
            listOf("Vehicle/HVAC_POWER(7)" to ExpertRawSnapshot.FAILED_VALUE),
            ExpertRawSnapshot.entries(vehicle, ExpertRawGetResult(false, message = "null")),
        )
    }

    @Test
    fun `diff keeps only changed, appeared and vanished keys in order`() {
        val before = ExpertRawSnapshot.toMap(
            listOf(
                vehicle to ExpertRawGetResult(true, rawValue = 1, message = "ok"),
                bcm to ExpertRawGetResult(true, message = "ok", fields = listOf("a" to "1", "b" to "5")),
            ),
        )
        val after = ExpertRawSnapshot.toMap(
            listOf(
                vehicle to ExpertRawGetResult(true, rawValue = 2, message = "ok"),
                bcm to ExpertRawGetResult(true, message = "ok", fields = listOf("a" to "1", "c" to "9")),
            ),
        )
        val changes = ExpertRawSnapshot.diff(before, after)
        assertEquals(
            listOf(
                ExpertRawSnapshot.Change("Vehicle/HVAC_POWER(7)", "1", "2"),
                ExpertRawSnapshot.Change("MbCanObject/eMBCAN_VEHICLE_BCM_STATUS(21).b", "5", null),
                ExpertRawSnapshot.Change("MbCanObject/eMBCAN_VEHICLE_BCM_STATUS(21).c", null, "9"),
            ),
            changes,
        )
        assertEquals("Vehicle/HVAC_POWER(7): 1 → 2", ExpertRawSnapshot.formatChange(changes[0]))
        assertTrue(ExpertRawSnapshot.diff(before, before).isEmpty())
        assertEquals(3, ExpertRawSnapshot.okCount(before))
    }

    @Test
    fun `entries keep the int and the byte array as separate keys`() {
        val result = ExpertRawGetResult(
            success = true,
            rawValue = 2,
            message = "ok",
            fields = listOf("bytes" to "[2,0,15]"),
        )
        assertEquals(
            listOf(
                "Vehicle/HVAC_POWER(7)" to "2",
                "Vehicle/HVAC_POWER(7).bytes" to "[2,0,15]",
            ),
            ExpertRawSnapshot.entries(vehicle, result),
        )
    }
}
