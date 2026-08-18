package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.utils.GEARBOX_MODE_CURRENT_GEAR_DATA_KEY

class RequiresTboxConnectionTest {

    @Test
    fun denylist_coversNineteenTboxOnlyWidgetKeys() {
        val expected = setOf(
            "voltage",
            "carSpeedAccurate",
            "cruiseSetSpeed",
            "breakingForce",
            "gearBoxOilTemperature",
            "gearBoxCurrentGear",
            "gearBoxPreparedGear",
            "gearBoxChangeGear",
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
        assertEquals(19, expected.size)
        for (key in expected) {
            assertTrue(key, WidgetsRepository.requiresTboxConnection(key))
            assertFalse(key, WidgetsRepository.isWidgetOfferedWhenNoTbox(key))
        }
    }

    @Test
    fun huCapableAndLocalKeys_notInDenylist() {
        assertFalse(WidgetsRepository.requiresTboxConnection("engineRPM"))
        assertFalse(WidgetsRepository.requiresTboxConnection("carSpeed"))
        assertFalse(WidgetsRepository.requiresTboxConnection("gearBoxMode"))
        assertFalse(WidgetsRepository.requiresTboxConnection("odometer"))
        assertFalse(WidgetsRepository.requiresTboxConnection("fuelLevelPercentage"))
        assertFalse(WidgetsRepository.requiresTboxConnection("gnssSpeed"))
        assertFalse(WidgetsRepository.requiresTboxConnection("locWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection("timeWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection(CPU_USAGE_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.requiresTboxConnection(FREE_RAM_PERCENT_WIDGET_DATA_KEY))
        assertFalse(WidgetsRepository.requiresTboxConnection("hvacAcWidget"))
        assertFalse(WidgetsRepository.requiresTboxConnection(""))
    }

    @Test
    fun gearBoxMode_supportsUseMbCanVhal() {
        assertTrue(WidgetsRepository.supportsUseMbCanVhal("gearBoxMode"))
        assertTrue(WidgetsRepository.isWidgetOfferedWhenNoTbox("gearBoxMode"))
    }

    @Test
    fun preferUseMbCanVhalOnConfigs_onlyWhenNoTbox() {
        val rpm = FloatingDashboardWidgetConfig(dataKey = "engineRPM", useMbCanVhal = false)
        val gear = FloatingDashboardWidgetConfig(dataKey = "gearBoxMode", useMbCanVhal = false)
        val voltage = FloatingDashboardWidgetConfig(dataKey = "voltage", useMbCanVhal = false)
        val off = WidgetsRepository.preferUseMbCanVhalOnConfigs(listOf(rpm, gear, voltage), noTboxConnect = false)
        assertFalse(off[0].useMbCanVhal)
        assertFalse(off[1].useMbCanVhal)
        assertFalse(off[2].useMbCanVhal)
        val on = WidgetsRepository.preferUseMbCanVhalOnConfigs(listOf(rpm, gear, voltage), noTboxConnect = true)
        assertTrue(on[0].useMbCanVhal)
        assertTrue(on[1].useMbCanVhal)
        assertFalse(on[2].useMbCanVhal)
    }
}
