package vad.dashing.tbox.adb

import java.io.File

/**
 * Single owner for ephemeral localhost ADB shell sessions.
 *
 * When the ADB tab ([AdbRepository]) already holds a live TCP session to the same
 * host:port, reuses that [AdbConnection] under the repository mutex instead of
 * opening a second client (OEM `adbd` can then emit `ADB CLSE local id mismatch`).
 * Otherwise opens a short-lived TCP connection that does not replace USB/other sessions.
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
