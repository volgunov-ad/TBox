package vad.dashing.mqtt.ha

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DiscoverySignatureTest {
    @Test
    fun optionChangeChangesTheSignature() {
        val base = sample()
        val first = DiscoverySignature.compute(
            entities = listOf(base),
            topicPrefix = "tbox",
            discoveryPrefix = "homeassistant",
            deviceId = "dashing",
            deviceName = "Jetour Dashing",
            swVersion = "0.1.1",
            discoveryEnabled = true,
            acceptCommands = true,
        )
        val widened = base.copy(options = listOf("ECO", "NOR", "SPT", "SNOW"))
        val second = DiscoverySignature.compute(
            entities = listOf(widened),
            topicPrefix = "tbox",
            discoveryPrefix = "homeassistant",
            deviceId = "dashing",
            deviceName = "Jetour Dashing",
            swVersion = "0.1.1",
            discoveryEnabled = true,
            acceptCommands = true,
        )
        assertNotEquals(first, second)
        assertEquals(
            first,
            DiscoverySignature.compute(
                entities = listOf(base),
                topicPrefix = "tbox",
                discoveryPrefix = "homeassistant",
                deviceId = "dashing",
                deviceName = "Jetour Dashing",
                swVersion = "0.1.1",
                discoveryEnabled = true,
                acceptCommands = true,
            ),
        )
    }

    private fun sample() = CatalogEntity(
        objectId = "drive_mode",
        label = "Режим вождения",
        description = "",
        group = EntityGroup.MOTION,
        component = HaComponent.SELECT,
        writable = true,
        options = listOf("ECO", "NOR", "SPT"),
    )
}
