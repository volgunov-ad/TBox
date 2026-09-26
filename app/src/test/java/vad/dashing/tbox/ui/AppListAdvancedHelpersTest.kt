package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.adb.PackageAdbActions

class AppListAdvancedHelpersTest {

    @Test
    fun formatAppListAdbStatusLine_joinsKnownFlags() {
        val line = formatAppListAdbStatusLine(
            status = PackageAdbActions.PackageStatus(
                packageName = "com.example",
                systemHidden = true,
                disabled = true,
                running = false,
            ),
            hiddenLabel = "скрыт",
            disabledLabel = "отключён",
            runningLabel = "запущен",
        )
        assertEquals("скрыт · отключён", line)
    }

    @Test
    fun formatAppListAdbStatusLine_nullWhenEmpty() {
        assertNull(
            formatAppListAdbStatusLine(
                status = PackageAdbActions.PackageStatus("com.example"),
                hiddenLabel = "h",
                disabledLabel = "d",
                runningLabel = "r",
            ),
        )
        assertNull(
            formatAppListAdbStatusLine(
                status = null,
                hiddenLabel = "h",
                disabledLabel = "d",
                runningLabel = "r",
            ),
        )
    }

    @Test
    fun adbToggleAction_picksOpposite() {
        val hidden = PackageAdbActions.PackageStatus("x", systemHidden = true)
        val disabled = PackageAdbActions.PackageStatus("x", disabled = true)
        assertEquals(
            PackageAdbActions.Action.Unhide,
            adbToggleAction(hideOrUnhide = true, status = hidden),
        )
        assertEquals(
            PackageAdbActions.Action.Hide,
            adbToggleAction(hideOrUnhide = true, status = null),
        )
        assertEquals(
            PackageAdbActions.Action.Enable,
            adbToggleAction(hideOrUnhide = false, status = disabled),
        )
        assertEquals(
            PackageAdbActions.Action.Disable,
            adbToggleAction(hideOrUnhide = false, status = null),
        )
    }
}
