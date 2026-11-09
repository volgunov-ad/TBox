package vad.dashing.tbox.adb

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

/**
 * Actual ADB TCP listen fact for automations (port accepting connections), distinct from
 * [HuAdbControl.state] which reflects property/toggle state.
 *
 * No dedicated polling loop: callers probe only while evaluating a rule condition.
 * A short TTL cache avoids repeated 250 ms probes during condition-wait windows.
 */
object AdbTcpListenFact {
    private const val CACHE_TTL_MS = 500L

    fun interface Probe {
        fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean
    }

    private data class Cached(val listening: Boolean, val atElapsedMs: Long)

    private val cache = AtomicReference<Cached?>(null)

    @Volatile
    private var probeOverride: Probe? = null

    @Volatile
    private var nowElapsedMs: () -> Long = { android.os.SystemClock.elapsedRealtime() }

    /**
     * Whether localhost:[HuAdbControl.TCP_ENABLED_PORT] currently accepts TCP connections.
     */
    fun isListening(): Boolean {
        val now = nowElapsedMs()
        cache.get()?.let { cached ->
            if (now - cached.atElapsedMs in 0 until CACHE_TTL_MS) {
                return cached.listening
            }
        }
        val listening = activeProbe().isPortOpen(
            LocalhostAdbSession.LOCAL_HOST,
            HuAdbControl.TCP_ENABLED_PORT,
            LocalhostAdbSession.TCP_PROBE_TIMEOUT_MS,
        )
        cache.set(Cached(listening, now))
        return listening
    }

    /** Remember a known listen result (e.g. after a successful localhost session probe). */
    fun remember(listening: Boolean, atElapsedMs: Long = nowElapsedMs()) {
        cache.set(Cached(listening, atElapsedMs))
    }

    internal fun setProbeForTests(probe: Probe?) {
        probeOverride = probe
        cache.set(null)
    }

    internal fun setNowElapsedMsForTests(now: () -> Long) {
        nowElapsedMs = now
    }

    internal fun resetForTests() {
        probeOverride = null
        cache.set(null)
        nowElapsedMs = { android.os.SystemClock.elapsedRealtime() }
    }

    private fun activeProbe(): Probe = probeOverride ?: DefaultProbe

    private object DefaultProbe : Probe {
        override fun isPortOpen(host: String, port: Int, timeoutMs: Int): Boolean =
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
                true
            } catch (_: Exception) {
                false
            }
    }
}
