package vad.dashing.tbox.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HeadingMotionDebugTest {
    @Test
    fun phoneGyroCompassIgnoresDefaultGyroFallback() {
        val debug = headingMotionDebug(
            MockHeadingSource.PHONE_GYRO_COMPASS,
            HeadingFallbackStatus(),
        )
        assertEquals(MockHeadingSource.PHONE_GYRO_COMPASS, debug.selected)
        assertEquals(MockHeadingSource.PHONE_GYRO_COMPASS, debug.effective)
        assertNull(debug.fallbackReason)
        assertEquals(setOf(HeadingMotionDebugChannel.PHONE), debug.channels)
        assertFalse(HeadingMotionDebugChannel.HU_IMU in debug.channels)
    }

    @Test
    fun phoneFallbackToSteerShowsBothChannels() {
        val debug = headingMotionDebug(
            MockHeadingSource.PHONE_GYRO,
            HeadingFallbackStatus(
                selectedSource = MockHeadingSource.PHONE_GYRO,
                effectiveSource = MockHeadingSource.STEER,
                fallbackReason = "phone_gyro_silent",
            ),
        )
        assertEquals(MockHeadingSource.STEER, debug.effective)
        assertEquals("phone_gyro_silent", debug.fallbackReason)
        assertTrue(HeadingMotionDebugChannel.PHONE in debug.channels)
        assertTrue(HeadingMotionDebugChannel.STEER in debug.channels)
        assertFalse(HeadingMotionDebugChannel.HU_IMU in debug.channels)
    }

    @Test
    fun huGyroAndSteerSelections() {
        val gyro = headingMotionDebug(MockHeadingSource.GYRO)
        assertEquals(setOf(HeadingMotionDebugChannel.HU_IMU), gyro.channels)

        val steer = headingMotionDebug(MockHeadingSource.STEER)
        assertEquals(setOf(HeadingMotionDebugChannel.STEER), steer.channels)

        val hybrid = headingMotionDebug(MockHeadingSource.GYRO_STEER)
        assertEquals(
            setOf(HeadingMotionDebugChannel.HU_IMU, HeadingMotionDebugChannel.STEER),
            hybrid.channels,
        )

        val wheels = headingMotionDebug(MockHeadingSource.WHEELS_STEER)
        assertEquals(
            setOf(HeadingMotionDebugChannel.WHEELS, HeadingMotionDebugChannel.STEER),
            wheels.channels,
        )
    }
}
