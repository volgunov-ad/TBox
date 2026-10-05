package vad.dashing.tbox.adb

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * Classifies ADB I/O failures that are expected when a session is torn down
 * (our [AdbConnection.close], peer EOF after `ctl.restart adbd`, process death).
 */
internal object AdbIoErrors {
    private val BENIGN_DISCONNECT_MARKERS = listOf(
        "transport closed",
        "adb usb transport closed",
    )

    fun isBenignDisconnectMessage(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return BENIGN_DISCONNECT_MARKERS.any { marker -> marker in normalized }
    }

    fun isBenignDisconnect(error: Throwable?): Boolean =
        isBenignDisconnectMessage(error?.message)

    /**
     * Whether a failure detail should be shown as Toast / alert.
     * Benign "transport closed" is hidden during app shutdown or intentional close;
     * the same message still surfaces for live user-initiated ADB actions.
     */
    fun shouldSuppressUserFacingFailure(
        message: String?,
        context: Context? = null,
    ): Boolean {
        if (!isBenignDisconnectMessage(message)) return false
        if (AdbShutdownGate.shouldSuppressBenignDisconnect()) return true
        val activity = context?.findActivityOrNull()
        return activity != null && (activity.isFinishing || activity.isDestroyed)
    }

    private fun Context.findActivityOrNull(): Activity? {
        var current: Context? = this
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return current as? Activity
    }
}
