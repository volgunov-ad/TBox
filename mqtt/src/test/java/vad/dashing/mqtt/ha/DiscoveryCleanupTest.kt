package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.mqtt.service.publishedTopicsNeedReload

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

    @Test
    fun failedClearStaysRememberedAndASelectedMissIsKept() {
        val cleanup = DiscoveryCleanup(
            setOf(
                "homeassistant/sensor/tbox_dashing/speed/config",
                "homeassistant/sensor/tbox_dashing/old/config",
            ),
        )
        val next = setOf("homeassistant/sensor/tbox_dashing/speed/config")
        val stale = cleanup.plan(next, keepObjectIds = setOf("old"))
        assertEquals(emptyList<String>(), stale)
        val confirmed = setOf("homeassistant/sensor/tbox_dashing/speed/config")
        cleanup.commit(cleanup.remembered(confirmed, emptySet()))
        assertTrue(cleanup.publishedTopics().contains("homeassistant/sensor/tbox_dashing/old/config"))
        assertTrue(cleanup.publishedTopics().contains("homeassistant/sensor/tbox_dashing/speed/config"))
        val dropped = cleanup.plan(next)
        assertEquals(listOf("homeassistant/sensor/tbox_dashing/old/config"), dropped)
    }

    @Test
    fun topicsForgottenByTheErrorStoreComeBackWhenItRecovers() {
        assertFalse(publishedTopicsNeedReload(hadStorageError = false, storageError = null))
        assertFalse(publishedTopicsNeedReload(hadStorageError = true, storageError = "unavailable"))
        assertTrue(publishedTopicsNeedReload(hadStorageError = true, storageError = null))

        val saved = setOf("homeassistant/sensor/tbox_dashing/old/config")
        val cleanup = DiscoveryCleanup()
        cleanup.commit(emptySet())
        if (publishedTopicsNeedReload(hadStorageError = true, storageError = null)) {
            cleanup.commit(saved)
        }
        val next = setOf("homeassistant/sensor/tbox_dashing/new/config")
        assertEquals(listOf("homeassistant/sensor/tbox_dashing/old/config"), cleanup.plan(next))
    }
}
