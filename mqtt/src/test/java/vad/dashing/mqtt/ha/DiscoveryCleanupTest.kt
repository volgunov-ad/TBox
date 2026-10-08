package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryCleanupTest {
    @Test
    fun removedConfigIsClearedAndKeptTopicsStay() {
        val cleanup = DiscoveryCleanup(setOf("homeassistant/sensor/tbox_dashing/old/config"))
        val next = setOf("homeassistant/sensor/tbox_dashing/new/config")
        val stale = cleanup.plan(next)
        assertEquals(listOf("homeassistant/sensor/tbox_dashing/old/config"), stale)
        assertEquals(setOf("homeassistant/sensor/tbox_dashing/old/config"), cleanup.publishedTopics())
        cleanup.commit(next)
        assertEquals(next, cleanup.publishedTopics())
        assertEquals(emptyList<String>(), cleanup.plan(next))
    }
}
