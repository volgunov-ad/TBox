package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingEntityPolicyTest {
    @Test
    fun oneMissIsKeptAndThreeOrHalfAnHourDrops() {
        assertTrue(MissingEntityPolicy.keep(1, 0L))
        assertTrue(MissingEntityPolicy.keep(2, 60_000L))
        assertFalse(MissingEntityPolicy.keep(MissingEntityPolicy.RELOADS, 0L))
        assertFalse(MissingEntityPolicy.keep(1, MissingEntityPolicy.GRACE_MS))
    }

    @Test
    fun configTopicYieldsTheObjectId() {
        assertEquals(
            "drive_mode",
            discoveryObjectId("homeassistant/sensor/tbox_dashing/drive_mode/config"),
        )
        assertEquals(null, discoveryObjectId("tbox/dashing/status"))
    }
}
