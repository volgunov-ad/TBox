package vad.dashing.tbox.adb

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tracks intentional ADB teardown so benign disconnect errors ("transport closed")
 * are not surfaced to the user as Toasts / ERROR status.
 *
 * Real failures during user-initiated ADB work while the app is alive still surface.
 */
internal object AdbShutdownGate {
    private val shuttingDown = AtomicBoolean(false)
    private val intentionalCloseDepth = AtomicInteger(0)
    /** Living [vad.dashing.tbox.MainActivity] instances, including one that is finishing. */
    private val liveMainActivities = AtomicInteger(0)

    /** Process / activity is finishing — suppress benign disconnect UI. */
    fun markAppShuttingDown() {
        shuttingDown.set(true)
    }

    /**
     * The process outlives a finished [vad.dashing.tbox.MainActivity] (BackgroundService keeps it),
     * so a new activity / service instance must clear the flag or every later logcat capture
     * and script run stops before its first command.
     */
    fun markAppAlive() {
        shuttingDown.set(false)
    }

    /** A MainActivity exists. Clears a shutdown latched by an older instance that already finished. */
    fun onMainActivityCreated() {
        liveMainActivities.incrementAndGet()
        shuttingDown.set(false)
    }

    /**
     * The activity is pausing because it called [android.app.Activity.finish].
     * Suppress transport-closed toasts only when it is the last MainActivity.
     * On A10 an external launch finishes the display-0 instance after the stock-window
     * instance is already up; that finish must not abort logcat for the window that stays.
     */
    fun onMainActivityFinishing() {
        if (liveMainActivities.get() <= 1) {
            shuttingDown.set(true)
        }
    }

    /**
     * @return true when [isFinishing] and no MainActivity remains, so the shared ADB
     * tab session should be dropped. A config-change destroy and a finished trampoline
     * that leaves the windowed instance alive both return false.
     */
    fun onMainActivityDestroyed(isFinishing: Boolean): Boolean {
        val left = liveMainActivities.updateAndGet { current -> (current - 1).coerceAtLeast(0) }
        if (!isFinishing || left > 0) return false
        shuttingDown.set(true)
        return true
    }

    fun isAppShuttingDown(): Boolean = shuttingDown.get()

    /**
     * Runs [block] while peer/socket closes from our side are expected
     * (tab disconnect, TCP restore via `ctl.restart adbd`, etc.).
     */
    fun <T> withIntentionalTransportClose(block: () -> T): T {
        intentionalCloseDepth.incrementAndGet()
        try {
            return block()
        } finally {
            intentionalCloseDepth.decrementAndGet()
        }
    }

    /** Suspending variant for TCP restore / other ADB teardown that awaits IO. */
    suspend fun <T> withIntentionalTransportCloseSuspending(block: suspend () -> T): T {
        intentionalCloseDepth.incrementAndGet()
        try {
            return block()
        } finally {
            intentionalCloseDepth.decrementAndGet()
        }
    }

    fun isIntentionalTransportClose(): Boolean = intentionalCloseDepth.get() > 0

    fun shouldSuppressBenignDisconnect(): Boolean =
        shuttingDown.get() || intentionalCloseDepth.get() > 0

    /** Test seam. */
    internal fun resetForTests() {
        shuttingDown.set(false)
        intentionalCloseDepth.set(0)
        liveMainActivities.set(0)
    }
}
