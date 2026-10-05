package vad.dashing.tbox

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import vad.dashing.tbox.freeform.FreeformCompanionSession
import vad.dashing.tbox.freeform.FreeformLaunchHelper
import vad.dashing.tbox.freeform.FreeformLaunchSide
import vad.dashing.tbox.ui.openAppListDialog

/**
 * App-list Dialog requires a focusable Activity. In window mode the main screen lives in a
 * non-focusable overlay, so openAppListDialog must exit to fullscreen before requesting show.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AppListDialogOverlayExitTest {

    @Before
    fun setUp() {
        FreeformCompanionSession.clear()
        AppListDialogRequestBus.dismiss()
        FreeformLaunchHelper.markExitFinished()
    }

    @After
    fun tearDown() {
        FreeformCompanionSession.clear()
        AppListDialogRequestBus.dismiss()
        FreeformLaunchHelper.markExitFinished()
    }

    @Test
    fun openAppListDialog_withoutWindowMode_requestsShowImmediately() {
        openAppListDialog(RuntimeEnvironment.getApplication())
        assertTrue(AppListDialogRequestBus.visible.value)
    }

    @Test
    fun openAppListDialog_whileWindowModeActive_defersDialogRequest() {
        FreeformCompanionSession.set(
            packageName = "com.example.maps",
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
            activityDisplayWidth = 1920,
            activityDisplayHeight = 720,
            activityDisplayId = 0,
        )
        assertTrue(FreeformCompanionSession.isActive)

        openAppListDialog(RuntimeEnvironment.getApplication())

        // Must not flip the bus while still in overlay — Dialog would crash there.
        assertFalse(AppListDialogRequestBus.visible.value)
    }
}
