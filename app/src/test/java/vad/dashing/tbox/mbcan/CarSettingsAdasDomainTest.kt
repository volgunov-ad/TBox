package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarSettingsAdasDomainTest {
    @Test fun fcwSensitivity_usesTheSameFarStandardNearRawValuesOnBothBackends() {
        assertEquals(FcwSensitivity.Far, CarSettingsAdasDomain.decodeFcwSensitivityMbCan(3))
        assertEquals(FcwSensitivity.Near, CarSettingsAdasDomain.decodeFcwSensitivityMbCan(2))
        assertEquals(FcwSensitivity.Far, CarSettingsAdasDomain.decodeFcwSensitivityVhal(3))
        assertEquals(FcwSensitivity.Near, CarSettingsAdasDomain.decodeFcwSensitivityVhal(2))
        assertEquals(3, CarSettingsAdasDomain.encodeFcwSensitivityMbCan(FcwSensitivity.Far))
        assertEquals(2, CarSettingsAdasDomain.encodeFcwSensitivityMbCan(FcwSensitivity.Near))
        assertEquals(3, CarSettingsAdasDomain.encodeFcwSensitivityVhal(FcwSensitivity.Far))
        assertEquals(2, CarSettingsAdasDomain.encodeFcwSensitivityVhal(FcwSensitivity.Near))
        assertEquals(1, CarSettingsAdasDomain.encodeFcwSensitivityMbCan(FcwSensitivity.Standard))
    }

    /** Stock A10: raw 1 = High, 0 = Low on both the feedback (289415707) and the request (289415949). */
    @Test fun ldwSensitivity_sharesHighOneLowZeroPolarityOnBothBackends() {
        assertEquals(LdwSensitivity.High, CarSettingsAdasDomain.decodeLdwSensitivityMbCan(1))
        assertEquals(LdwSensitivity.High, CarSettingsAdasDomain.decodeLdwSensitivityVhal(1))
        assertEquals(LdwSensitivity.Low, CarSettingsAdasDomain.decodeLdwSensitivityVhal(0))
        assertNull(CarSettingsAdasDomain.decodeLdwSensitivityVhal(2))
        assertEquals(0, CarSettingsAdasDomain.encodeLdwSensitivityVhal(LdwSensitivity.Low))
        assertEquals(1, CarSettingsAdasDomain.encodeLdwSensitivityVhal(LdwSensitivity.High))
    }

    /** Values from A9 HU log: item=95 value=1 (with TJA on) and value=4 (with TJA off). */
    @Test fun accTimeGap_matchesStockRequestAndClusterStatus() {
        assertEquals(AccTimeGap.Medium, CarSettingsAdasDomain.decodeAccTimeGapStatus(0))
        assertEquals(AccTimeGap.Far, CarSettingsAdasDomain.decodeAccTimeGapStatus(1))
        assertEquals(AccTimeGap.Near, CarSettingsAdasDomain.decodeAccTimeGapStatus(2))
        assertNull(CarSettingsAdasDomain.decodeAccTimeGapStatus(3))
        assertEquals(AccTimeGap.Medium, CarSettingsAdasDomain.decodeAccTimeGapRequest(1))
        assertEquals(AccTimeGap.Far, CarSettingsAdasDomain.decodeAccTimeGapRequest(2))
        assertEquals(AccTimeGap.Near, CarSettingsAdasDomain.decodeAccTimeGapRequest(3))
        assertNull(CarSettingsAdasDomain.decodeAccTimeGapRequest(0))
        assertNull(CarSettingsAdasDomain.decodeAccTimeGapRequest(4))
        assertEquals(1, CarSettingsAdasDomain.encodeAccTimeGapRequest(AccTimeGap.Medium))
        assertEquals(2, CarSettingsAdasDomain.encodeAccTimeGapRequest(AccTimeGap.Far))
        assertEquals(3, CarSettingsAdasDomain.encodeAccTimeGapRequest(AccTimeGap.Near))
    }

    /** Values from A9 HU log: item=80 value=1 with LAS=2 (LKA), value=2 with LAS=1 (LDW). */
    @Test fun ldwSwitch_decodesA9LogOffOnWithLasCoOccurrence() {
        assertEquals(MbCanBinaryState.Off, CarSettingsAdasDomain.decodeLdwSwitchMbCan(1))
        assertEquals(MbCanBinaryState.On, CarSettingsAdasDomain.decodeLdwSwitchMbCan(2))
        assertNull(CarSettingsAdasDomain.decodeLdwSwitchMbCan(0))
        assertNull(CarSettingsAdasDomain.decodeLdwSwitchMbCan(3))
    }

    @Test fun accTimeGapAndLdwSwitch_areRegisteredInCatalogAndCommandRegistry() {
        assertEquals(95, MbCanKnownVehiclePropertyId.ACC_TIME_GAP_SET)
        assertEquals(80, MbCanKnownVehiclePropertyId.LDW_SWITCH)
        val gap = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.ACC_TIME_GAP_SET)
        assertEquals(MbCanSignal.AccTimeGap, gap?.refreshSignal)
        assertEquals(
            MbCanCommandPolicy.SetExact(allowedValues = setOf(1, 2, 3)),
            gap?.policy,
        )
        val ldw = MbCanCommandRegistry.get(MbCanKnownVehiclePropertyId.LDW_SWITCH)
        assertEquals(MbCanSignal.LdwSwitch, ldw?.refreshSignal)
        val policy = ldw?.policy as MbCanCommandPolicy.ToggleBinary
        assertEquals(1, policy.offValue)
        assertEquals(2, policy.onValue)
    }
}
