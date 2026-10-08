package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryCleanupTest {
    @Test
    fun removedConfigIsClearedAndKeptTopicsStay() {
        val cleanup = DiscoveryCleanup(setOf("homeassistant/sensor/tbox_dashing/old/config"))
        val stale = cleanup.plan(setOf("homeassistant/sensor/tbox_dashing/new/config"))
        assertEquals(listOf("homeassistant/sensor/tbox_dashing/old/config"), stale)
        assertEquals(setOf("homeassistant/sensor/tbox_dashing/new/config"), cleanup.publishedTopics())
        assertEquals(emptyList<String>(), cleanup.plan(setOf("homeassistant/sensor/tbox_dashing/new/config")))
    }
}
