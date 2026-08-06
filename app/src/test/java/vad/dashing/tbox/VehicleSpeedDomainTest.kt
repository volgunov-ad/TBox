package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.VehicleSpeedDomain
import vad.dashing.tbox.mbcan.VehicleSpeedDomain.DisplaySpeedUnwrapState

class VehicleSpeedDomainTest {

    @Test
    fun decodeVhalRaw_matchesReportedSpeeds() {
        // км/ч = UINT16(raw) / 16
        assertEquals(41.0f, VehicleSpeedDomain.decodeVhalRaw(656)!!, 0.001f)
        assertEquals(42.0f, VehicleSpeedDomain.decodeVhalRaw(672)!!, 0.001f)
        assertEquals(48.0f, VehicleSpeedDomain.decodeVhalRaw(768)!!, 0.001f)
        assertEquals(0.0f, VehicleSpeedDomain.decodeVhalRaw(0)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_acceptsFloatDelivery() {
        assertEquals(41.0f, VehicleSpeedDomain.decodeVhalRaw(656f)!!, 0.001f)
        assertEquals(12.5f, VehicleSpeedDomain.decodeVhalRaw(200f)!!, 0.001f)
    }

    @Test
    fun decodeVhalRaw_rejectsInvalid() {
        assertNull(VehicleSpeedDomain.decodeVhalRaw(-1))
        assertNull(VehicleSpeedDomain.decodeVhalRaw(Float.NaN))
    }

    @Test
    fun resolvePreferredKmh_prefersVsoWhenRawPositive() {
        val result = VehicleSpeedDomain.resolvePreferredKmh(vsoRaw = 656, displayRaw = 800)
        assertEquals(41.0f, result.kmh!!, 0.001f)
    }

    @Test
    fun resolvePreferredKmh_usesDisplayWhenVsoZeroOrMissing() {
        // Full-width display raw (>255) still /16
        assertEquals(
            50.0f,
            VehicleSpeedDomain.resolvePreferredKmh(vsoRaw = 0, displayRaw = 800).kmh!!,
            0.001f,
        )
        assertEquals(
            50.0f,
            VehicleSpeedDomain.resolvePreferredKmh(vsoRaw = null, displayRaw = 800).kmh!!,
            0.001f,
        )
    }

    @Test
    fun resolvePreferredKmh_bothZero_returnsZero() {
        assertEquals(
            0.0f,
            VehicleSpeedDomain.resolvePreferredKmh(vsoRaw = 0, displayRaw = 0).kmh!!,
            0.001f,
        )
    }

    @Test
    fun resolvePreferredKmh_bothNull_returnsNull() {
        assertNull(VehicleSpeedDomain.resolvePreferredKmh(vsoRaw = null, displayRaw = null).kmh)
    }

    @Test
    fun displayUnwrap_climbsThroughWrap_15to16() {
        var state = DisplaySpeedUnwrapState()
        val steps = listOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240, 0)
        val expected = (0..16).map { it.toFloat() }
        steps.zip(expected).forEach { (raw, kmh) ->
            val result = VehicleSpeedDomain.decodeDisplayTruncatedRaw(raw, state)
            assertEquals("raw=$raw", kmh, result.kmh!!, 0.001f)
            state = result.unwrapState
        }
    }

    @Test
    fun displayUnwrap_descendsThroughWrap_16to15() {
        var state = DisplaySpeedUnwrapState.fromAbsoluteKmh(16f)
        val result = VehicleSpeedDomain.decodeDisplayTruncatedRaw(240, state)
        assertEquals(15.0f, result.kmh!!, 0.001f)
    }

    @Test
    fun displayUnwrap_continuesPast32() {
        var state = DisplaySpeedUnwrapState()
        // 0..15 then wrap to 16..20 via low-byte sequence after first wrap
        val afterWrap = listOf(0, 16, 32, 48, 64) // residues 0..4 with offset 16 → 16..20
        // First bring to 15
        for (raw in listOf(0, 16, 32, 48, 64, 80, 96, 112, 128, 144, 160, 176, 192, 208, 224, 240)) {
            state = VehicleSpeedDomain.decodeDisplayTruncatedRaw(raw, state).unwrapState
        }
        afterWrap.zip(listOf(16f, 17f, 18f, 19f, 20f)).forEach { (raw, kmh) ->
            val result = VehicleSpeedDomain.decodeDisplayTruncatedRaw(raw, state)
            assertEquals("raw=$raw", kmh, result.kmh!!, 0.001f)
            state = result.unwrapState
        }
    }

    @Test
    fun displayUnwrap_fullWidthResyncs() {
        val result = VehicleSpeedDomain.decodeDisplayTruncatedRaw(
            656,
            DisplaySpeedUnwrapState(lastResidue = 3f, unwrapOffset = 16f),
        )
        assertEquals(41.0f, result.kmh!!, 0.001f)
        assertEquals(41f % 16f, result.unwrapState.lastResidue!!, 0.001f)
    }
}
