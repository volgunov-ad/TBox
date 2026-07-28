package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.OutsideTemperatureDomain

class OutsideTemperatureDomainTest {

    @Test
    fun decodeVhalRaw_matchesReportedDashboardValues() {
        // Signed delivery of unsigned CAN raw (138 → −118 as Int8); °C = raw×0.5−40
        assertEquals(34.0f, OutsideTemperatureDomain.decodeVhalRaw(-108)!!, 0.001f)
        assertEquals(30.0f, OutsideTemperatureDomain.decodeVhalRaw(-116)!!, 0.001f)
        assertEquals(29.5f, OutsideTemperatureDomain.decodeVhalRaw(-117)!!, 0.001f)
        assertEquals(29.0f, OutsideTemperatureDomain.decodeVhalRaw(-118)!!, 0.001f)
        assertEquals(28.5f, OutsideTemperatureDomain.decodeVhalRaw(-119)!!, 0.001f)
        assertEquals(28.0f, OutsideTemperatureDomain.decodeVhalRaw(-120)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_acceptsUnsignedIntDelivery() {
        assertEquals(34.0f, OutsideTemperatureDomain.decodeVhalRaw(148)!!, 0.001f)
        assertEquals(30.0f, OutsideTemperatureDomain.decodeVhalRaw(140)!!, 0.001f)
        assertEquals(29.5f, OutsideTemperatureDomain.decodeVhalRaw(139)!!, 0.001f)
        assertEquals(29.0f, OutsideTemperatureDomain.decodeVhalRaw(138)!!, 0.001f)
        assertEquals(3.0f, OutsideTemperatureDomain.decodeVhalRaw(86)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_rejectsOutOfRange() {
        // 254 → 87 °C (invalid upper bound); 255 → 87.5
        assertNull(OutsideTemperatureDomain.decodeVhalRaw(254))
        assertNull(OutsideTemperatureDomain.decodeVhalRaw(255))
        assertNull(OutsideTemperatureDomain.decodeVhalRaw(-1))
    }

    @Test
    fun decodeMbCanCelsiusRaw_treats87AsInvalid() {
        assertNull(OutsideTemperatureDomain.decodeMbCanCelsiusRaw(87))
        assertEquals(25.0f, OutsideTemperatureDomain.decodeMbCanCelsiusRaw(25)!!, 0.001f)
        assertEquals(-5.0f, OutsideTemperatureDomain.decodeMbCanCelsiusRaw(-5)!!, 0.001f)
    }
}
