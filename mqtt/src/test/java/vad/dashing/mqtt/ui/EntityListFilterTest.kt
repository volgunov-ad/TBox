package vad.dashing.mqtt.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityListFilterTest {
    @Test
    fun blankQueryKeepsEveryRow() {
        assertTrue(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "  "))
    }

    @Test
    fun queryMatchesLabelDescriptionKeyOrGroup() {
        assertTrue(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "скор"))
        assertTrue(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "КМ/Ч"))
        assertTrue(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "speed"))
        assertTrue(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "движ"))
        assertFalse(entityMatchesFilter("Скорость", "км/ч", "vehicle_speed", "Движение", "климат"))
    }

    @Test
    fun queryMatchesMqttTopics() {
        assertTrue(
            entityMatchesFilter(
                "Питание",
                "",
                "hvac_power",
                "Климат",
                "hvac_power/set",
                "tbox/dashing/hvac_power/state",
                "tbox/dashing/hvac_power/set",
            ),
        )
        assertFalse(
            entityMatchesFilter(
                "Питание",
                "",
                "hvac_power",
                "Климат",
                "fuel_level",
                "tbox/dashing/hvac_power/state",
                "tbox/dashing/hvac_power/set",
            ),
        )
    }
}
