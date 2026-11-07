package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalApiLocalClientsTest {
    @Test
    fun nameOf_knownCompanions() {
        assertEquals("TBox MQTT", ExternalApiLocalClients.nameOf("vad.dashing.mqtt"))
        assertEquals("VAD Voice", ExternalApiLocalClients.nameOf("vad.dashing.voice"))
        assertEquals(
            "Dashing Club Launcher",
            ExternalApiLocalClients.nameOf("ras.dashing.tbox.launcher"),
        )
        assertNull(ExternalApiLocalClients.nameOf("com.example.unknown"))
    }

    @Test
    fun clientId_usesLocalPrefix() {
        assertEquals(
            "local:ras.dashing.tbox.launcher",
            ExternalApiLocalClients.clientId("ras.dashing.tbox.launcher"),
        )
    }
}
