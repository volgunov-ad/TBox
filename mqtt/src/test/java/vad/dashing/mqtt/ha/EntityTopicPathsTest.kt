package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntityTopicPathsTest {
    @Test
    fun writableEntityGetsStateAndSet() {
        val paths = entityTopicPaths("tbox", "Dashing", "hvac_power", writable = true, HaComponent.SWITCH)
        assertEquals("tbox/dashing/hvac_power/state", paths.stateTopic)
        assertEquals("tbox/dashing/hvac_power/set", paths.commandTopic)
        assertEquals(
            "состояние: tbox/dashing/hvac_power/state\nкоманда: tbox/dashing/hvac_power/set",
            formatEntityTopicCaption(paths),
        )
    }

    @Test
    fun readOnlySensorOmitsCommand() {
        val paths = entityTopicPaths(
            "tbox",
            "dashing",
            "outside_temperature",
            writable = false,
            HaComponent.SENSOR,
        )
        assertEquals("tbox/dashing/outside_temperature/state", paths.stateTopic)
        assertNull(paths.commandTopic)
        assertEquals(
            "состояние: tbox/dashing/outside_temperature/state\nкоманда: нет",
            formatEntityTopicCaption(paths),
        )
    }

    @Test
    fun deviceTrackerUsesAttributesPath() {
        val paths = entityTopicPaths(
            "tbox",
            "dashing",
            "geo_position",
            writable = false,
            HaComponent.DEVICE_TRACKER,
        )
        assertEquals("tbox/dashing/geo_position/attributes", paths.stateTopic)
        assertNull(paths.commandTopic)
    }
}
