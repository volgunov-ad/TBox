package vad.dashing.tbox.adb

import android.content.Context
import vad.dashing.tbox.findActivityOrNull

/**
 * Classifies ADB I/O failures that are expected when a session is torn down
 * (our [AdbConnection.close], peer EOF after `ctl.restart adbd`, process death),
 * and flaky first-connect errors after enabling TCP on a cold head unit.
 */
internal object AdbIoErrors {
    private val BENIGN_DISCONNECT_MARKERS = listOf(
        "transport closed",
        "adb usb transport closed",
    )

    /**
     * Low-level connect / first-shell failures that often recover on a single retry
     * (adbd still coming up after `ctl.restart`, stale reused tab socket, brief EOF).
     * English exception text must not be shown as «ADB канал не открыт» — ephemeral
     * [LocalhostAdbSession] does not require the interactive ADB tab to be connected.
     */
    private val RETRYABLE_CONNECT_MARKERS = listOf(
        "transport closed",
        "adb usb transport closed",
        "connection reset",
        "broken pipe",
        "connection is not established",
        "not established",
        "connection refused",
        "failed to connect",
        "connect timed out",
    )

    private val SESSION_OPEN_FAILURE_RU_MARKERS = listOf(
        "не удалось подключиться к localhost adb",
        "не удалось включить adb tcp",
        "adb tcp не поднялся",
    )

    const val USER_FACING_CONNECT_FAILED_RU =
        "Не удалось подключиться к localhost ADB"

    fun isBenignDisconnectMessage(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return BENIGN_DISCONNECT_MARKERS.any { marker -> marker in normalized }
    }

    fun isBenignDisconnect(error: Throwable?): Boolean =
        isBenignDisconnectMessage(error?.message)

    fun isRetryableConnectFailure(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return RETRYABLE_CONNECT_MARKERS.any { marker -> marker in normalized }
    }

    fun isRetryableConnectFailure(error: Throwable?): Boolean =
        isRetryableConnectFailure(error?.message)

    /**
     * Replace raw English transport / connect exceptions with a stable RU string so
     * Toasts do not imply the ADB Settings tab channel must already be open.
     */
    fun userFacingAdbConnectDetail(detail: String?): String {
        val trimmed = detail?.trim().orEmpty()
        if (trimmed.isEmpty() || isRetryableConnectFailure(trimmed)) {
            return USER_FACING_CONNECT_FAILED_RU
        }
        return trimmed
    }

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

    /**
     * Best-effort ADB close (app-shortcut double-tap force-stop): suppress Toasts when
     * the ephemeral session could not open. Success stays silent; real shell failures
     * (e.g. Permission denial) still surface. Journal keeps ERROR from [LocalhostAdbSession].
     */
    fun shouldSuppressBestEffortAdbCloseFailure(
        message: String?,
        context: Context? = null,
    ): Boolean {
        if (shouldSuppressUserFacingFailure(message, context)) return true
        if (isRetryableConnectFailure(message)) return true
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return SESSION_OPEN_FAILURE_RU_MARKERS.any { marker -> marker in normalized }
    }
}
