package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
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

    @Test fun ldwSensitivity_normalizesInvertedVhalRead() {
        assertEquals(LdwSensitivity.High, CarSettingsAdasDomain.decodeLdwSensitivityMbCan(1))
        assertEquals(LdwSensitivity.High, CarSettingsAdasDomain.decodeLdwSensitivityVhal(0))
        assertEquals(0, CarSettingsAdasDomain.encodeLdwSensitivityVhal(LdwSensitivity.Low))
    }
}
