package vad.dashing.tbox

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MainScreenWindowModeGeometryTest {

    @Test
    fun defaultForDisplay_usesLeftHalf() {
        val g = MainScreenWindowModeGeometry.defaultForDisplay(1000, 600)
        assertEquals(0, g.startX)
        assertEquals(0, g.startY)
        assertEquals(500, g.width)
        assertEquals(600, g.height)
    }

    @Test
    fun normalized_clampsNegativeAndMinSize() {
        val g = MainScreenWindowModeGeometry(-10, -5, 20, 30).normalized()
        assertEquals(0, g.startX)
        assertEquals(0, g.startY)
        assertEquals(MainScreenWindowModeGeometry.MIN_SIZE, g.width)
        assertEquals(MainScreenWindowModeGeometry.MIN_SIZE, g.height)
    }

    @Test
    fun defaultForDisplay_tinyDisplay_stillMeetsMinSize() {
        val g = MainScreenWindowModeGeometry.defaultForDisplay(50, 40)
        assertEquals(MainScreenWindowModeGeometry.MIN_SIZE, g.width)
        assertEquals(MainScreenWindowModeGeometry.MIN_SIZE, g.height)
    }

    @Test
    fun computeComplementOverlay_sameDisplay_rightCompanion() {
        val g = FreeformLaunchBounds.computeComplementOverlayGeometry(
            activityDisplayWidth = 1000,
            activityDisplayHeight = 600,
            overlayDisplayWidth = 1000,
            overlayDisplayHeight = 600,
            side = FreeformLaunchSide.RIGHT,
            percent = 40,
        )
        // Companion on right 40% → TBox left 60%
        assertEquals(0, g.startX)
        assertEquals(0, g.startY)
        assertEquals(600, g.width)
        assertEquals(600, g.height)
    }

    @Test
    fun computeComplementOverlay_mapsVirtualIntoLargerFullScreen() {
        // Virtual app display 1920x720 nested top-start in 1920x1080 overlay panel.
        val g = FreeformLaunchBounds.computeComplementOverlayGeometry(
            activityDisplayWidth = 1920,
            activityDisplayHeight = 720,
            overlayDisplayWidth = 1920,
            overlayDisplayHeight = 1080,
            side = FreeformLaunchSide.LEFT,
            percent = 50,
        )
        // Companion left half of virtual → TBox right half at y=0 (not stretched to 1080)
        assertEquals(960, g.startX)
        assertEquals(0, g.startY)
        assertEquals(960, g.width)
        assertEquals(720, g.height)
    }

    @Test
    fun mapActivityRectToOverlay_clampsToOverlay() {
        val g = FreeformLaunchBounds.mapActivityRectToOverlay(
            activityRect = Rect(100, 50, 500, 400),
            overlayDisplayWidth = 800,
            overlayDisplayHeight = 600,
        )
        assertEquals(100, g.startX)
        assertEquals(50, g.startY)
        assertEquals(400, g.width)
        assertEquals(350, g.height)
    }
}
