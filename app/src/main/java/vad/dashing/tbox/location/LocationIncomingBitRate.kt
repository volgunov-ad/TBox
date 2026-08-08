package vad.dashing.tbox.location

import android.os.SystemClock
import vad.dashing.tbox.esp.LocationSource

/**
 * Measures actual inbound payload rate (bits/s) for geoposition transports.
 * Call [noteBytes] from RX paths; sample with [bitsPerSec] (UI typically every 1 s).
 */
object LocationIncomingBitRate {
    private const val WINDOW_MS = 1_000L
    /** After this quiet period with no bytes, reported rate drops to 0. */
    private const val STALE_MS = 2_000L

    private val tbox = RollingBitRateMeter(WINDOW_MS, STALE_MS)
    private val esp = RollingBitRateMeter(WINDOW_MS, STALE_MS)
    private val usb = RollingBitRateMeter(WINDOW_MS, STALE_MS)

    fun noteBytes(source: LocationSource, byteCount: Int, nowElapsedMs: Long = SystemClock.elapsedRealtime()) {
        if (byteCount <= 0) return
        meter(source)?.note(byteCount, nowElapsedMs)
    }

    /**
     * Instantaneous / last-window bits per second for [source], or `null` when not applicable
     * (Android GPS has no serial/UDP payload meter).
     */
    fun bitsPerSec(source: LocationSource, nowElapsedMs: Long = SystemClock.elapsedRealtime()): Long? {
        val m = meter(source) ?: return null
        return m.sample(nowElapsedMs)
    }

    fun formatBitsPerSec(bps: Long?): String {
        if (bps == null) return "\u2014"
        return bps.toString()
    }

    private fun meter(source: LocationSource): RollingBitRateMeter? = when (source) {
        LocationSource.TBOX -> tbox
        LocationSource.ESP32 -> esp
        LocationSource.USB -> usb
        LocationSource.ANDROID -> null
    }

    /** Test-only: clear all meters. */
    fun resetForTests() {
        tbox.reset()
        esp.reset()
        usb.reset()
    }
}

/**
 * Accumulates bytes and reports average bits/s over successive [windowMs] windows.
 */
class RollingBitRateMeter(
    private val windowMs: Long = 1_000L,
    private val staleMs: Long = 2_000L,
) {
    private val lock = Any()
    private var windowOpen: Boolean = false
    private var windowStartMs: Long = 0L
    private var bytesInWindow: Long = 0L
    private var lastActivityMs: Long = 0L
    private var hasActivity: Boolean = false
    private var lastBps: Long = 0L

    fun note(byteCount: Int, nowElapsedMs: Long) {
        if (byteCount <= 0) return
        synchronized(lock) {
            hasActivity = true
            lastActivityMs = nowElapsedMs
            if (!windowOpen) {
                windowOpen = true
                windowStartMs = nowElapsedMs
            }
            bytesInWindow += byteCount.toLong()
            closeWindowIfDue(nowElapsedMs)
        }
    }

    fun sample(nowElapsedMs: Long): Long {
        synchronized(lock) {
            if (hasActivity && nowElapsedMs - lastActivityMs >= staleMs) {
                bytesInWindow = 0L
                windowOpen = false
                lastBps = 0L
                return 0L
            }
            closeWindowIfDue(nowElapsedMs)
            return lastBps
        }
    }

    fun reset() {
        synchronized(lock) {
            windowOpen = false
            windowStartMs = 0L
            bytesInWindow = 0L
            lastActivityMs = 0L
            hasActivity = false
            lastBps = 0L
        }
    }

    private fun closeWindowIfDue(nowElapsedMs: Long) {
        if (!windowOpen) return
        val elapsed = nowElapsedMs - windowStartMs
        if (elapsed < windowMs) return
        lastBps = (bytesInWindow * 8_000L) / elapsed.coerceAtLeast(1L)
        bytesInWindow = 0L
        windowStartMs = nowElapsedMs
        // Keep window open so a quiet period still ages via [staleMs] from lastActivity.
    }
}
