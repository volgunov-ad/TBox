package vad.dashing.tbox.adb

import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Coordination for ephemeral localhost ADB shell vs the interactive ADB tab.
 *
 * When [AdbRepository] already holds a live TCP session to the same host:port, reuses
 * that [AdbConnection] under the repository mutex (avoids a second TCP client to the
 * same `adbd`). Otherwise opens a short-lived TCP connection — **every** grant / VD /
 * automation / AppList consumer can still start a session when the tab is disconnected.
 *
 * Note: `ADB CLSE local id mismatch` also happens on a **single** connection when late
 * CLSE/OKAY from a previous shell is mis-attributed; that is fixed in [AdbConnection].
 * A cached live connection that died meanwhile (stale `CONNECTED` state) is detected at
 * reuse time and retried once on a fresh ephemeral client — see
 * [shouldFallbackToEphemeral].
 *
 * All production [LocalhostAdbSession.AndroidGateway] shell I/O goes through here.
 */
internal object AdbExclusiveTcpShell {

    enum class Mode {
        /** Reuse [AdbRepository]'s live TCP [AdbConnection]. */
        ReuseLiveTcp,
        /** Open a new short-lived TCP client. */
        Ephemeral,
    }

    private val DEAD_TRANSPORT_MARKERS = listOf("connection reset", "broken pipe")

    /**
     * Whether [error] from a reused live TCP connection means the cached transport died
     * before the block completed (peer EOF after an adbd restart / idle drop), so a
     * single retry on a fresh ephemeral client is safe. Unrelated protocol or consumer
     * errors, and cancellation, must not be retried.
     */
    fun shouldFallbackToEphemeral(error: Throwable): Boolean {
        if (error is CancellationException) return false
        val normalized = error.message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return AdbIoErrors.isBenignDisconnectMessage(normalized) ||
            DEAD_TRANSPORT_MARKERS.any { it in normalized }
    }

    /**
     * Decision helper (unit-tested). [hasConnection] must reflect a non-null live connection.
     */
    fun selectMode(
        repositoryInitialized: Boolean,
        phase: AdbRepository.Phase?,
        transport: AdbRepository.TransportType?,
        endpoint: String?,
        hasConnection: Boolean,
        host: String,
        port: Int,
    ): Mode {
        if (!repositoryInitialized || !hasConnection) return Mode.Ephemeral
        if (phase != AdbRepository.Phase.CONNECTED) return Mode.Ephemeral
        if (transport != AdbRepository.TransportType.TCP) return Mode.Ephemeral
        if (endpoint.isNullOrBlank()) return Mode.Ephemeral
        if (!AdbEndpoints.matches(endpoint, host, port)) return Mode.Ephemeral
        return Mode.ReuseLiveTcp
    }

    fun <T> openEphemeral(
        host: String,
        port: Int,
        connectTimeoutMs: Int,
        sessionTimeoutMs: Int,
        keysDir: File,
        clientName: String,
        block: (execute: (String) -> AdbShellResult) -> T,
    ): T {
        val transport = AdbTcpTransport.connect(
            host,
            port,
            timeoutMs = maxOf(connectTimeoutMs, sessionTimeoutMs),
        )
        return transport.use { tcp ->
            AdbConnection(
                tcp,
                AdbAuthKeys.loadOrCreate(keysDir),
                clientName,
            ).use { connection ->
                connection.connect()
                block { command -> connection.execute(command) }
            }
        }
    }
}
