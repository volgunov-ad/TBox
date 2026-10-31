package vad.dashing.tbox.adb

import java.io.EOFException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdbExclusiveTcpShellTest {

    @Test
    fun selectMode_reusesLiveTcpToSameEndpoint() {
        assertEquals(
            AdbExclusiveTcpShell.Mode.ReuseLiveTcp,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = AdbRepository.Phase.CONNECTED,
                transport = AdbRepository.TransportType.TCP,
                endpoint = "127.0.0.1:5555",
                hasConnection = true,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
        assertEquals(
            AdbExclusiveTcpShell.Mode.ReuseLiveTcp,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = AdbRepository.Phase.CONNECTED,
                transport = AdbRepository.TransportType.TCP,
                endpoint = "localhost:5555",
                hasConnection = true,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
    }

    @Test
    fun selectMode_ephemeralWhenDisconnectedOrUsbOrDifferentHost() {
        assertEquals(
            AdbExclusiveTcpShell.Mode.Ephemeral,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = false,
                phase = null,
                transport = null,
                endpoint = null,
                hasConnection = false,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
        assertEquals(
            AdbExclusiveTcpShell.Mode.Ephemeral,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = AdbRepository.Phase.DISCONNECTED,
                transport = null,
                endpoint = "",
                hasConnection = false,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
        assertEquals(
            AdbExclusiveTcpShell.Mode.Ephemeral,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = AdbRepository.Phase.CONNECTED,
                transport = AdbRepository.TransportType.USB,
                endpoint = "TBox",
                hasConnection = true,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
        assertEquals(
            AdbExclusiveTcpShell.Mode.Ephemeral,
            AdbExclusiveTcpShell.selectMode(
                repositoryInitialized = true,
                phase = AdbRepository.Phase.CONNECTED,
                transport = AdbRepository.TransportType.TCP,
                endpoint = "192.168.1.50:5555",
                hasConnection = true,
                host = "127.0.0.1",
                port = 5555,
            ),
        )
    }

    @Test
    fun shouldFallbackToEphemeral_forDeadTransportErrors() {
        assertTrue(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                EOFException("ADB transport closed"),
            ),
        )
        assertTrue(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                IOException("Connection reset"),
            ),
        )
        assertTrue(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                IOException("Broken pipe"),
            ),
        )
    }

    @Test
    fun shouldFallbackToEphemeral_rejectsUnrelatedEmptyAndCancellation() {
        assertFalse(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                IOException("Bad ADB payload checksum"),
            ),
        )
        assertFalse(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                IOException(""),
            ),
        )
        assertFalse(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                IOException(null as String?),
            ),
        )
        assertFalse(
            AdbExclusiveTcpShell.shouldFallbackToEphemeral(
                CancellationException("ADB transport closed"),
            ),
        )
    }
}
