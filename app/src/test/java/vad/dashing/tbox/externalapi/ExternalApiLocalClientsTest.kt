package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.dcl.DclLauncherApiContract

class ExternalApiLocalClientsTest {
    @Test
    fun nameOf_knownCompanions() {
        assertEquals("TBox MQTT", ExternalApiLocalClients.nameOf("vad.dashing.mqtt"))
        assertEquals("VAD Voice", ExternalApiLocalClients.nameOf("vad.dashing.voice"))
        assertEquals(
            "Dashing Club Launcher",
            ExternalApiLocalClients.nameOf(DclLauncherApiContract.LAUNCHER_PACKAGE),
        )
        assertNull(ExternalApiLocalClients.nameOf("com.example.unknown"))
    }

    @Test
    fun clientId_usesLocalPrefix() {
        assertEquals(
            "local:${DclLauncherApiContract.LAUNCHER_PACKAGE}",
            ExternalApiLocalClients.clientId(DclLauncherApiContract.LAUNCHER_PACKAGE),
        )
    }

    @Test
    fun shouldEnableDclEnvironmentOnApprove_onlyDclLauncher() {
        assertTrue(
            ExternalApiLocalClients.shouldEnableDclEnvironmentOnApprove(
                DclLauncherApiContract.LAUNCHER_PACKAGE,
            ),
        )
        assertFalse(ExternalApiLocalClients.shouldEnableDclEnvironmentOnApprove("vad.dashing.mqtt"))
        assertFalse(ExternalApiLocalClients.shouldEnableDclEnvironmentOnApprove("vad.dashing.voice"))
        assertFalse(
            ExternalApiLocalClients.shouldEnableDclEnvironmentOnApprove("com.example.unknown"),
        )
    }
}
