package vad.dashing.tbox.mbcan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarSettingsHudDomainTest {
    @Test fun overspeed_roundTripsStockFormula() {
        assertEquals(30, CarSettingsHudDomain.decodeOverspeedKmh(0))
        assertEquals(100, CarSettingsHudDomain.decodeOverspeedKmh(14))
        assertEquals(14, CarSettingsHudDomain.encodeOverspeedKmh(100))
        assertNull(CarSettingsHudDomain.encodeOverspeedKmh(101))
    }

    @Test fun hudModesAndLevels_areBounded() {
        assertEquals(1, CarSettingsHudDomain.HUD_MODE_STANDARD)
        assertEquals(2, CarSettingsHudDomain.HUD_MODE_SNOW)
        assertEquals(1, CarSettingsHudDomain.HUD_LEVEL_MIN)
        assertEquals(10, CarSettingsHudDomain.HUD_LEVEL_MAX)
    }
}
