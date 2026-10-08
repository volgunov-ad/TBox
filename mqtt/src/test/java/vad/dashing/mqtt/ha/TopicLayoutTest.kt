package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TopicLayoutTest {
    @Test
    fun stateTopicsDoNotIncludeComponent() {
        val base = Topics.base("tbox", "Dashing")
        assertEquals("tbox/dashing", base)
        assertEquals("tbox/dashing/drive_mode/state", Topics.state(base, "drive_mode"))
        assertEquals("tbox/dashing/drive_mode/set", Topics.set(base, "drive_mode"))
        assertFalse(Topics.state(base, "drive_mode").contains("select"))
    }

    @Test
    fun deviceIdKeepsOnlySlugCharacters() {
        assertEquals("my_car", Topics.normalizeDeviceId(" My Car! "))
        assertEquals("dashing", Topics.normalizeDeviceId("   "))
    }

    @Test
    fun discoveryPathUsesComponentAndStableIdentifier() {
        assertEquals(
            "homeassistant/sensor/tbox_dashing/outside_temperature/config",
            Topics.discoveryConfig("homeassistant", "sensor", "dashing", "outside_temperature"),
        )
        assertEquals("tbox_dashing", Topics.deviceIdentifier("dashing"))
        assertEquals("Jetour Dashing", Topics.deviceName("  "))
    }
}
