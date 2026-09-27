package vad.dashing.tbox.adb

import java.io.File

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
