package vad.dashing.tbox.ui

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun scheduleAppListAdvancedSessionExit_skipsWhenInactive() {
        val ran = AtomicBoolean(false)
        val job = scheduleAppListAdvancedSessionExit(
            isActive = { false },
            exit = { ran.set(true) },
        )
        assertNull(job)
        assertFalse(ran.get())
    }

    @Test
    fun scheduleAppListAdvancedSessionExit_doesNotBlockCaller() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val job = scheduleAppListAdvancedSessionExit(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            isActive = { true },
            exit = { gate.await() },
        )
        assertNotNull(job)
        assertTrue(job!!.isActive)
        assertFalse(job.isCompleted)
        gate.complete(Unit)
        job.join()
        assertTrue(job.isCompleted)
    }
}
