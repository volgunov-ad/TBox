package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SlaCommandRegistryTest {
    @Test
    fun tsrSwitch_allowsOffOnAndRefreshesSlaSignal() {
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH)
        assertNotNull(spec)
        val policy = spec!!.policy as MbCanCommandPolicy.SetExact
        assertEquals(
            setOf(SlaSpeedLimitDomain.SLA_SWITCH_OFF, SlaSpeedLimitDomain.SLA_SWITCH_ON),
            policy.allowedValues,
        )
        assertEquals(MbCanSignal.SlaSpeedLimit, spec.refreshSignal)
        assertEquals(18, MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH)
    }

    @Test
    fun speedLimiterSwitch_allowsOffOnAndRefreshesLimiterSignal() {
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH)
        assertNotNull(spec)
        val policy = spec!!.policy as MbCanCommandPolicy.SetExact
        assertEquals(
            setOf(
                SlaSpeedLimitDomain.SPEED_LIMITER_SWITCH_OFF,
                SlaSpeedLimitDomain.SPEED_LIMITER_SWITCH_ON,
            ),
            policy.allowedValues,
        )
        assertEquals(MbCanSignal.SpeedLimiter, spec.refreshSignal)
        assertEquals(254, MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_SWITCH)
    }

    @Test
    fun speedLimiterTarget_allowsZeroTo150AndRefreshesLimiterSignal() {
        val spec = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET)
        assertNotNull(spec)
        val policy = spec!!.policy as MbCanCommandPolicy.SetExact
        assertTrue(policy.allowedValues.contains(0))
        assertTrue(policy.allowedValues.contains(150))
        assertEquals(151, policy.allowedValues.size)
        assertEquals(MbCanSignal.SpeedLimiter, spec.refreshSignal)
        assertEquals(253, MbCanKnownVehiclePropertyId.VEHICLE_SPEEDLIMIT_VALUESET)
    }

    @Test
    fun firmwareMapper_resolvesSlaTsrSwitchReadAndWriteIds() {
        assertEquals(
            FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_REQ,
            FirmwareVehicleJsonMapper.resolveWritePropertyId(MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH),
        )
        assertEquals(
            FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS,
            FirmwareVehicleJsonMapper.resolveReadPropertyId(MbCanKnownVehiclePropertyId.VEHICLE_TSR_SWITCH),
        )
        assertEquals(289_415_947, FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_REQ)
        assertEquals(289_415_709, FirmwareVehicleJsonMapper.VHAL_SLA_ON_OFF_STATUS)
    }
}
