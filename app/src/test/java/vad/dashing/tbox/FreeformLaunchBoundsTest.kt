package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FreeformLaunchBoundsTest {

    @Test
    fun normalizePercent_clampsAndSnapsToStep() {
        assertEquals(20, FreeformLaunchBounds.normalizePercent(10))
        assertEquals(80, FreeformLaunchBounds.normalizePercent(90))
        assertEquals(50, FreeformLaunchBounds.normalizePercent(50))
        assertEquals(40, FreeformLaunchBounds.normalizePercent(35))
        assertEquals(30, FreeformLaunchBounds.normalizePercent(34))
    }

    @Test
    fun left_splitsWidthForApp() {
        val (app, tbox) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayWidth = 1000,
            displayHeight = 600,
            side = FreeformLaunchSide.LEFT,
            percent = 40,
        )
        assertEquals(0, app.left)
        assertEquals(0, app.top)
        assertEquals(400, app.right)
        assertEquals(600, app.bottom)
        assertEquals(400, tbox.left)
        assertEquals(1000, tbox.right)
        assertEquals(600, tbox.bottom)
    }

    @Test
    fun right_splitsWidthForApp() {
        val (app, tbox) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayWidth = 1000,
            displayHeight = 600,
            side = FreeformLaunchSide.RIGHT,
            percent = 40,
        )
        assertEquals(600, app.left)
        assertEquals(1000, app.right)
        assertEquals(0, tbox.left)
        assertEquals(600, tbox.right)
    }

    @Test
    fun top_splitsHeightForApp() {
        val (app, tbox) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayWidth = 800,
            displayHeight = 1000,
            side = FreeformLaunchSide.TOP,
            percent = 30,
        )
        assertEquals(0, app.top)
        assertEquals(300, app.bottom)
        assertEquals(300, tbox.top)
        assertEquals(1000, tbox.bottom)
    }

    @Test
    fun bottom_splitsHeightForApp() {
        val (app, tbox) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayWidth = 800,
            displayHeight = 1000,
            side = FreeformLaunchSide.BOTTOM,
            percent = 30,
        )
        assertEquals(700, app.top)
        assertEquals(1000, app.bottom)
        assertEquals(0, tbox.top)
        assertEquals(700, tbox.bottom)
    }

    @Test
    fun sideFromStorageKey_defaultsToRight() {
        assertEquals(FreeformLaunchSide.LEFT, FreeformLaunchSide.fromStorageKey("left"))
        assertEquals(FreeformLaunchSide.RIGHT, FreeformLaunchSide.fromStorageKey("unknown"))
        assertEquals(FreeformLaunchSide.RIGHT, FreeformLaunchSide.fromStorageKey(null))
    }
}
