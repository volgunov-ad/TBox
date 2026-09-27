package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
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
}
