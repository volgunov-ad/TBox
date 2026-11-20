package vad.dashing.tbox.adb

import android.content.Context
import vad.dashing.tbox.R

/**
 * Single mapping from [LocalhostAdbSession.Reason] to user-facing text.
 *
 * UI / toasts / cards must use [message] with a [Context] so RU/EN flavors resolve
 * correctly. JVM unit tests and journal-only paths may pass `context = null` and get
 * the Russian fallbacks (same wording as `main/res` strings).
 */
internal object LocalhostAdbReasons {
    /** Matches [R.string.localhost_adb_reason_tcp_enable_failed] in `main`. */
    const val FALLBACK_TCP_ENABLE_FAILED = "Не удалось включить ADB TCP на ГУ"

    /** Matches [R.string.localhost_adb_reason_tcp_not_ready] in `main`. */
    const val FALLBACK_TCP_NOT_READY = "ADB TCP не поднялся вовремя"

    /** Matches [R.string.localhost_adb_reason_connect_failed] in `main`. */
    const val FALLBACK_CONNECT_FAILED = "Не удалось подключиться к localhost ADB"

    fun stringRes(reason: LocalhostAdbSession.Reason): Int = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed ->
            R.string.localhost_adb_reason_tcp_enable_failed
        LocalhostAdbSession.Reason.TcpNotReady ->
            R.string.localhost_adb_reason_tcp_not_ready
        LocalhostAdbSession.Reason.AdbConnectFailed ->
            R.string.localhost_adb_reason_connect_failed
    }

    fun fallback(reason: LocalhostAdbSession.Reason): String = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed -> FALLBACK_TCP_ENABLE_FAILED
        LocalhostAdbSession.Reason.TcpNotReady -> FALLBACK_TCP_NOT_READY
        LocalhostAdbSession.Reason.AdbConnectFailed -> FALLBACK_CONNECT_FAILED
    }

    /**
     * Human text for [reason]. Raw English connect/transport noise is replaced for
     * [LocalhostAdbSession.Reason.AdbConnectFailed]; other reasons ignore empty detail.
     */
    fun message(
        context: Context?,
        reason: LocalhostAdbSession.Reason,
        detail: String = "",
    ): String {
        val base = context?.getString(stringRes(reason)) ?: fallback(reason)
        val trimmed = detail.trim()
        return when (reason) {
            LocalhostAdbSession.Reason.AdbConnectFailed -> when {
                trimmed.isEmpty() || AdbIoErrors.isRetryableConnectFailure(trimmed) -> base
                trimmed == base -> base
                else -> trimmed
            }
            else -> if (trimmed.isEmpty() || trimmed == reason.name) base else base
        }
    }

    fun fromSessionReasonName(name: String): LocalhostAdbSession.Reason? = when (name) {
        LocalhostAdbSession.Reason.TcpEnableFailed.name ->
            LocalhostAdbSession.Reason.TcpEnableFailed
        LocalhostAdbSession.Reason.TcpNotReady.name ->
            LocalhostAdbSession.Reason.TcpNotReady
        LocalhostAdbSession.Reason.AdbConnectFailed.name ->
            LocalhostAdbSession.Reason.AdbConnectFailed
        else -> null
    }

    fun forVirtualDisplay(
        context: Context?,
        reason: VirtualDisplayAdb.Reason,
        detail: String = "",
    ): String {
        val session = when (reason) {
            VirtualDisplayAdb.Reason.TcpEnableFailed ->
                LocalhostAdbSession.Reason.TcpEnableFailed
            VirtualDisplayAdb.Reason.TcpNotReady ->
                LocalhostAdbSession.Reason.TcpNotReady
            VirtualDisplayAdb.Reason.AdbConnectFailed ->
                LocalhostAdbSession.Reason.AdbConnectFailed
            else -> null
        }
        return if (session != null) {
            message(context, session, detail)
        } else {
            detail.ifBlank { reason.name }
        }
    }

    fun forPackageAdb(
        context: Context?,
        reason: PackageAdbActions.Reason,
        detail: String = "",
    ): String {
        val session = when (reason) {
            PackageAdbActions.Reason.TcpEnableFailed ->
                LocalhostAdbSession.Reason.TcpEnableFailed
            PackageAdbActions.Reason.TcpNotReady ->
                LocalhostAdbSession.Reason.TcpNotReady
            PackageAdbActions.Reason.AdbConnectFailed ->
                LocalhostAdbSession.Reason.AdbConnectFailed
            else -> null
        }
        return if (session != null) {
            message(context, session, detail)
        } else {
            detail.ifBlank { reason.name }
        }
    }
}
