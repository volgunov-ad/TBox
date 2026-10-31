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
    fun formatAppListAdbStatusLine_showsDexoptAndSize() {
        val status = PackageAdbActions.PackageStatus(
            packageName = "vad.dashing.tbox",
            running = true,
            dexoptFilter = "run-from-apk",
        )
        assertEquals(
            "запущен · из APK · APK 65 МБ",
            formatAppListAdbStatusLine(
                status = status,
                hiddenLabel = "h",
                disabledLabel = "d",
                runningLabel = "запущен",
                fromApkLabel = "из APK",
                apkSizeLabel = "APK 65 МБ",
            ),
        )
        assertEquals(
            "компиляция → speed…",
            formatAppListAdbStatusLine(
                status = status.copy(running = false),
                hiddenLabel = "h",
                disabledLabel = "d",
                runningLabel = "r",
                compilingLabel = "компиляция → speed…",
            ),
        )
    }

    @Test
    fun canCompileSpeed_blocksLargeApk() {
        assertTrue(canCompileSpeed(null))
        assertTrue(canCompileSpeed(68L * 1024 * 1024))
        assertFalse(canCompileSpeed(320L * 1024 * 1024))
        assertEquals(65, bytesToMb(68_389_641L))
    }

    @Test
    fun awaitDexoptFilter_reachedTimeoutAborted() = runBlocking {
        var checks = 0
        assertEquals(
            DexoptWaitResult.Reached,
            awaitDexoptFilter(
                check = { checks++; checks >= 3 },
                isSessionActive = { true },
                delayFn = {},
            ),
        )
        assertEquals(3, checks)
        assertEquals(
            DexoptWaitResult.Timeout,
            awaitDexoptFilter(
                check = { false },
                isSessionActive = { true },
                delayFn = {},
                firstPollMs = 10,
                intervalMs = 10,
                timeoutMs = 30,
            ),
        )
        assertEquals(
            DexoptWaitResult.Aborted,
            awaitDexoptFilter(check = { null }, isSessionActive = { false }, delayFn = {}),
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
