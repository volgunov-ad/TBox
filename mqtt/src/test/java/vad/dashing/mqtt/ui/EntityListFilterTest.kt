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
}
