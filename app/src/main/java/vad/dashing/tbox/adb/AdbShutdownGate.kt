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

    /** Process / activity is finishing — suppress benign disconnect UI. */
    fun markAppShuttingDown() {
        shuttingDown.set(true)
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
    }
}
