package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuTelemetryDecodeTest {

    @Test
    fun instantFuel_rawDividedByTen() {
        assertEquals(8.5f, InstantFuelConsumptionDomain.decodeRawCounter(85)!!, 0.001f)
        assertNull(InstantFuelConsumptionDomain.decodeRawCounter(0))
        assertNull(InstantFuelConsumptionDomain.decodeRawCounter(-1))
    }

    @Test
    fun instantFuel_shortCounterUsesUnsigned() {
        // OEM getFuelRollingCounter is short; high bit must not become signed-negative Int.
        assertEquals(8.5f, InstantFuelConsumptionDomain.decodeRawCounter(85.toShort())!!, 0.001f)
        assertNull(InstantFuelConsumptionDomain.decodeRawCounter(0.toShort()))
    }

    @Test
    fun accCruise_jobManagerDoesNotOwnFrmOrGaspedSubscribe() {
        assertTrue(MbCanSignal.AccCruise.subscribeDataTypes.isEmpty())
    }

    @Test
    fun distanceToEmpty_rejectsNonPositive() {
        assertEquals(120f, DistanceToEmptyDomain.decodeKm(120f)!!, 0.001f)
        assertNull(DistanceToEmptyDomain.decodeKm(0f))
        assertEquals(120u, DistanceToEmptyDomain.decodeKm(120))
        assertNull(DistanceToEmptyDomain.decodeKm(0))
    }

    @Test
    fun maintenanceTips_allowsZeroRejectsNegative() {
        assertEquals(0u, MaintenanceTipsDomain.decodeKm(0))
        assertEquals(1999u, MaintenanceTipsDomain.decodeKm(1999))
        assertNull(MaintenanceTipsDomain.decodeKm(-1))
    }

    @Test
    fun pm25_densityBounds() {
        assertEquals(75u, Pm25AirQualityDomain.decodeDensity(75))
        assertNull(Pm25AirQualityDomain.decodeDensity(0))
        assertNull(Pm25AirQualityDomain.decodeDensity(65535))
    }
}
