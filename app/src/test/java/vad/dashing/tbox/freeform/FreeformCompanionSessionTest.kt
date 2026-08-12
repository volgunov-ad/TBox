package vad.dashing.tbox.freeform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FreeformCompanionSessionTest {

    @Before
    fun clearSession() {
        FreeformCompanionSession.clear()
    }

    @Test
    fun overlayBehind_defaultsFalse() {
        FreeformCompanionSession.set(
            packageName = "com.example.app",
            side = FreeformLaunchSide.LEFT,
            percent = 40,
            activityDisplayWidth = 1280,
            activityDisplayHeight = 720,
            activityDisplayId = 0,
        )
        assertTrue(FreeformCompanionSession.isActive)
        assertFalse(FreeformCompanionSession.isOverlayBehind)
    }

    @Test
    fun overlayBehind_flagTracked() {
        FreeformCompanionSession.set(
            packageName = "com.example.app",
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
            activityDisplayWidth = 1280,
            activityDisplayHeight = 720,
            activityDisplayId = 5,
            overlayBehind = true,
        )
        assertTrue(FreeformCompanionSession.isOverlayBehind)
        assertTrue(FreeformCompanionSession.isActiveFor("com.example.app"))
        FreeformCompanionSession.clear()
        assertFalse(FreeformCompanionSession.isOverlayBehind)
        assertFalse(FreeformCompanionSession.isActive)
    }
}
