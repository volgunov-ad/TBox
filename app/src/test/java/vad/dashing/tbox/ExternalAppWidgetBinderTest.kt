package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.ExternalAppWidgetBinder.PickBindStatus

class ExternalAppWidgetBinderTest {

    @Test
    fun buildGrantBindAdbCommand_usesPackageAndUser() {
        val command = ExternalAppWidgetBinder.buildGrantBindAdbCommand("vad.dashing.tbox", 0)
        assertEquals(
            "adb shell cmd appwidget grantbind --package vad.dashing.tbox --user 0",
            command,
        )
    }

    @Test
    fun statusAfterPickBindAttempt_readyWhenWidgetInfoPresentEvenIfBindDenied() {
        val status = ExternalAppWidgetBinder.statusAfterPickBindAttempt(
            bindIfAllowedSucceeded = false,
            widgetInfoPresent = true,
        )
        assertEquals(PickBindStatus.ReadyToConfigure, status)
    }

    @Test
    fun statusAfterPickBindAttempt_needsPermissionWhenNeitherBindNorInfo() {
        val status = ExternalAppWidgetBinder.statusAfterPickBindAttempt(
            bindIfAllowedSucceeded = false,
            widgetInfoPresent = false,
        )
        assertEquals(PickBindStatus.NeedsBindPermission, status)
    }

    @Test
    fun statusAfterBindPermissionUi_readyWhenWidgetInfoPresentEvenIfBindDenied() {
        val status = ExternalAppWidgetBinder.statusAfterBindPermissionUi(
            bindUiResultOk = false,
            bindIfAllowedSucceeded = false,
            widgetInfoPresent = true,
        )
        assertEquals(PickBindStatus.ReadyToConfigure, status)
    }
}
