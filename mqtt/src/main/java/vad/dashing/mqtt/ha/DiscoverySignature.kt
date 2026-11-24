package vad.dashing.mqtt.ha

import java.security.MessageDigest

/**
 * Republish key for MQTT discovery. It covers the config bodies, not only the entity ids,
 * so a new option, unit or limit is published again.
 */
object DiscoverySignature {
    fun compute(
        entities: List<CatalogEntity>,
        topicPrefix: String,
        discoveryPrefix: String,
        deviceId: String,
        deviceName: String,
        swVersion: String,
        discoveryEnabled: Boolean,
        acceptCommands: Boolean,
    ): String {
        val bodies = entities.sortedBy { it.objectId }.joinToString("\n") { entity ->
            entity.objectId + "=" + DiscoveryPayload.entityConfig(
                entity,
                topicPrefix,
                discoveryPrefix,
                deviceId,
                deviceName,
                swVersion,
            )
        }
        val services = DiscoveryPayload.connectivityConfig(topicPrefix, deviceId, deviceName, swVersion) +
            "\n" + DiscoveryPayload.lastSeenConfig(topicPrefix, deviceId, deviceName, swVersion)
        val raw = listOf(
            discoveryPrefix,
            topicPrefix,
            deviceId,
            deviceName,
            swVersion,
            discoveryEnabled.toString(),
            acceptCommands.toString(),
            bodies,
            services,
        ).joinToString("\n")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
