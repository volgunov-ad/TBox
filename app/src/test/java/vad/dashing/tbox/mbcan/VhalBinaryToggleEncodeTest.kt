package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Table-driven checks for [VhalBinaryToggleCodec] (stock HVAC / CarSettings encodings).
 */
class VhalBinaryToggleEncodeTest {

    @Test
    fun knownToggleProperties_areRecognized() {
        val known = listOf(
            MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
            MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
            MbCanKnownVehiclePropertyId.HVAC_POWER,
            MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
            MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
            MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
        )
        known.forEach { id ->
            assertTrue("expected toggle property $id", VhalBinaryToggleCodec.isVhalBinaryToggleProperty(id))
        }
        assertFalse(VhalBinaryToggleCodec.isVhalBinaryToggleProperty(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT))
        assertFalse(
            VhalBinaryToggleCodec.isVhalBinaryToggleProperty(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH),
        )
    }

    @Test
    fun encode_steeringAndWiperAndFrontOff_useOneOnTwoOff() {
        listOf(
            MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_FRONT_OFF,
            MbCanKnownVehiclePropertyId.HVAC_BLOWER_DELAY,
        ).forEach { id ->
            assertEquals(1, VhalBinaryToggleCodec.encodeWriteValue(id, targetOn = true))
            assertEquals(2, VhalBinaryToggleCodec.encodeWriteValue(id, targetOn = false))
        }
    }

    @Test
    fun encode_parkingRadarWindshieldHvacPowerAuto_useTwoOnOneOff() {
        listOf(
            MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH,
            MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH,
            MbCanKnownVehiclePropertyId.HVAC_POWER,
            MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE,
        ).forEach { id ->
            assertEquals(2, VhalBinaryToggleCodec.encodeWriteValue(id, targetOn = true))
            assertEquals(1, VhalBinaryToggleCodec.encodeWriteValue(id, targetOn = false))
        }
    }

    @Test
    fun encode_recirculation_oneInsideTwoOutside() {
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_ON,
            VhalBinaryToggleCodec.encodeWriteValue(
                MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
                targetOn = true,
            ),
        )
        assertEquals(
            MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION_VALUE_OFF,
            VhalBinaryToggleCodec.encodeWriteValue(
                MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION,
                targetOn = false,
            ),
        )
    }

    @Test
    fun encode_sync_matchesDomain() {
        assertEquals(
            HvacClimateDomain.encodeHvacSyncVhalWrite(true),
            VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH, true),
        )
        assertEquals(
            HvacClimateDomain.encodeHvacSyncVhalWrite(false),
            VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH, false),
        )
    }

    @Test
    fun encode_unmappedProperty_returnsNull() {
        assertNull(VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.SYSTEM_REBOOT, true))
        assertNull(
            VhalBinaryToggleCodec.encodeWriteValue(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH, true),
        )
    }
}
