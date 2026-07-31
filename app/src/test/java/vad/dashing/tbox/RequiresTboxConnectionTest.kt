package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY

class RequiresTboxConnectionTest {

    @Test
    fun denylist_coversTwentyTboxOnlyWidgetKeys() {
        val expected = setOf(
            "voltage",
            "carSpeedAccurate",
            "cruiseSetSpeed",
            "breakingForce",
            "gearBoxOilTemperature",
            "gearBoxCurrentGear",
            "gearBoxPreparedGear",
            "gearBoxChangeGear",
            "gearBoxMode",
            "gearBoxDriveMode",
            "gearBoxWork",
            "gearBoxWidget",
            GEARBOX_MODE_CURRENT_GEAR_DATA_KEY,
            "insideTemperature",
            "voltage+engineTemperatureWidget",
            "tempInOutWidget",
            "netWidget",
            "netWidgetNew",
            "netWidgetColored",
            "restartTbox",
        )
        assertEquals(20, expected.size)
        for (key in expected) {
            assertTrue(key, WidgetsRepository.requiresTboxConnection(key))
            assertFalse(key, WidgetsRepository.isWidgetOfferedWhenNoTbox(key))
        }
    }

    @Test
    fun huCapableAndLocalKeys_notInDenylist() {
        assertFalse(WidgetsRepository.requiresTboxConnection("engineRPM"))
        assertFalse(WidgetsRepository.requiresTboxConnection("carSpeed"))
        assertFalse(WidgetsRepository.requiresTboxConnection("odometer"))
        assertFalse(WidgetsRepository.requiresTboxConnection("fuelLevelPercentage"))
        assertFalse(WidgetsRepository.requiresTboxConnection("gnssSpeed"))
        assertFalse(WidgetsRepository.requiresTboxConnection("locWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection("timeWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection("hvacAcWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection(""))
    }

    @Test
    fun preferUseMbCanVhalOnConfigs_onlyWhenNoTbox() {
        val rpm = FloatingDashboardWidgetConfig(dataKey = "engineRPM", useMbCanVhal = false)
        val voltage = FloatingDashboardWidgetConfig(dataKey = "voltage", useMbCanVhal = false)
        val off = WidgetsRepository.preferUseMbCanVhalOnConfigs(listOf(rpm, voltage), noTboxConnect = false)
        assertFalse(off[0].useMbCanVhal)
        assertFalse(off[1].useMbCanVhal)
        val on = WidgetsRepository.preferUseMbCanVhalOnConfigs(listOf(rpm, voltage), noTboxConnect = true)
        assertTrue(on[0].useMbCanVhal)
        assertFalse(on[1].useMbCanVhal)
    }
}
