package vad.dashing.tbox.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.LocValues
import vad.dashing.tbox.esp.LocationSource

class MockLocationJobTest {

    @Test
    fun doesNotPushWhenMockDisabled() {
        assertFalse(MockLocationJob.shouldPushMock(false, LocationSource.TBOX))
        assertFalse(MockLocationJob.shouldPushMock(false, LocationSource.ESP32))
    }

    @Test
    fun doesNotPushWhenAndroidSource() {
        assertFalse(MockLocationJob.shouldPushMock(true, LocationSource.ANDROID))
    }

    @Test
    fun pushesWhenMockEnabledAndNotAndroid() {
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.TBOX))
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.ESP32))
        assertTrue(MockLocationJob.shouldPushMock(true, LocationSource.USB))
    }

    @Test
    fun hasValidCoordinates_rejectsZeroZero() {
        assertFalse(MockLocationJob.hasValidCoordinates(LocValues()))
        assertTrue(
            MockLocationJob.hasValidCoordinates(
                LocValues(latitude = 55.0, longitude = 37.0, locateStatus = true),
            ),
        )
    }

    @Test
    fun fixRetentionIsTwoMinutes() {
        assertTrue(MockLocationJob.FIX_RETENTION_MS == 120_000L)
    }
}
