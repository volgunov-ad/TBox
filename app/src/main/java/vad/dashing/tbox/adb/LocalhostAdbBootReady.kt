package vad.dashing.tbox.adb

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.delay

/**
 * Bounded “wait for localhost ADB” backoff for one-shot boot paths.
 *
 * Used by SoftAP router bring-up, WRITE_SECURE_SETTINGS auto-grant, and ADB automation
 * actions in the first minutes after [markServiceReady]. Not an endless loop: delays are
 * fixed and short (same spirit as DSP/USB-audio startup series).
 */
internal object LocalhostAdbBootReady {
    /** Immediate, then 5 / 10 / 20 / 30 s — five attempts, ~65 s wall if every try fails fast. */
    val ATTEMPT_DELAYS_MS: List<Long> = listOf(0L, 5_000L, 10_000L, 20_000L, 30_000L)

    /** How long after [markServiceReady] ADB automation actions keep using boot backoff. */
    const val BOOT_RETRY_WINDOW_MS = 180_000L

    private val serviceReadyElapsedMs = AtomicLong(-1L)

    fun markServiceReady(elapsedMs: Long? = null) {
        serviceReadyElapsedMs.set(elapsedMs ?: SystemClock.elapsedRealtime())
    }

    /** Test seam: clear the boot window. */
    fun resetForTests() {
        serviceReadyElapsedMs.set(-1L)
    }

    fun inBootWindow(
        nowElapsedMs: Long? = null,
        windowMs: Long = BOOT_RETRY_WINDOW_MS,
    ): Boolean {
        val ready = serviceReadyElapsedMs.get()
        // Check ready before touching SystemClock so JVM unit tests stay mock-free.
        if (ready < 0L) return false
        val now = nowElapsedMs ?: SystemClock.elapsedRealtime()
        val age = now - ready
        return age in 0L until windowMs
    }

    fun isRetryableSessionReason(reason: LocalhostAdbSession.Reason): Boolean = when (reason) {
        LocalhostAdbSession.Reason.TcpEnableFailed,
        LocalhostAdbSession.Reason.TcpNotReady,
        LocalhostAdbSession.Reason.AdbConnectFailed,
        -> true
    }

    fun isRetryableAdbErrorCode(code: String?): Boolean {
        if (code.isNullOrBlank()) return false
        return LocalhostAdbReasons.fromSessionReasonName(code) != null
    }

    /**
     * Runs [attempt] once per entry in [delaysMs]. Waits the listed delay *before* each try
     * (first entry is usually 0). Stops early when [shouldRetry] is false.
     */
    suspend fun <T> runAttempts(
        delaysMs: List<Long> = ATTEMPT_DELAYS_MS,
        pause: suspend (Long) -> Unit,
        shouldRetry: (T) -> Boolean,
        attempt: suspend () -> T,
    ): T {
        require(delaysMs.isNotEmpty()) { "delaysMs must not be empty" }
        var index = 0
        while (true) {
            val wait = delaysMs[index]
            if (wait > 0L) pause(wait)
            val value = attempt()
            if (!shouldRetry(value) || index >= delaysMs.lastIndex) return value
            index += 1
        }
    }

    suspend fun <T> runAttempts(
        delaysMs: List<Long> = ATTEMPT_DELAYS_MS,
        shouldRetry: (T) -> Boolean,
        attempt: suspend () -> T,
    ): T = runAttempts(
        delaysMs = delaysMs,
        pause = { ms -> delay(ms) },
        shouldRetry = shouldRetry,
        attempt = attempt,
    )

    suspend fun <T> runSessionAttempts(
        delaysMs: List<Long> = ATTEMPT_DELAYS_MS,
        pause: suspend (Long) -> Unit,
        attempt: suspend () -> LocalhostAdbSession.Result<T>,
    ): LocalhostAdbSession.Result<T> = runAttempts(
        delaysMs = delaysMs,
        pause = pause,
        shouldRetry = { result ->
            result is LocalhostAdbSession.Result.Failed &&
                isRetryableSessionReason(result.reason)
        },
        attempt = attempt,
    )

    /** Delays for ADB automation / VD during the boot window; single shot otherwise. */
    fun delaysForCurrentWindow(nowElapsedMs: Long? = null): List<Long> =
        if (inBootWindow(nowElapsedMs)) ATTEMPT_DELAYS_MS else listOf(0L)
}
