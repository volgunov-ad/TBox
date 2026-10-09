package vad.dashing.mqtt.settings

import vad.dashing.mqtt.ha.Topics
import java.security.MessageDigest

data class MqttSettings(
    val apiPort: Int = 8765,
    val brokerEnabled: Boolean = true,
    val brokerHost: String = "",
    val brokerPort: Int = 1883,
    val username: String = "",
    val password: String = "",
    val tlsEnabled: Boolean = false,
    val caPem: String = "",
    val topicPrefix: String = Topics.DEFAULT_TOPIC_PREFIX,
    val discoveryPrefix: String = Topics.DEFAULT_DISCOVERY_PREFIX,
    val deviceId: String = Topics.DEFAULT_DEVICE_ID,
    val deviceName: String = Topics.DEFAULT_DEVICE_NAME,
    val mqttClientId: String = "",
    val discoveryEnabled: Boolean = true,
    val acceptCommands: Boolean = true,
    val autostart: Boolean = true,
    val pollSeconds: Int = 3,
    val repeatMinutes: Int = 5,
    val fastPublishSeconds: Int = 5,
    val accessToken: String = "",
    val selectedObjectIds: Set<String> = emptySet(),
    val wireguardEnabled: Boolean = false,
    val wireguardConf: String = "",
    val wireguardFileName: String = "",
) {
    fun normalized(): MqttSettings {
        val conf = wireguardConf.trim().removePrefix("\uFEFF").replace("\r\n", "\n")
        return copy(
            apiPort = apiPort.coerceIn(1, 65535),
            brokerPort = brokerPort.coerceIn(1, 65535),
            brokerHost = brokerHost.trim(),
            username = username.trim(),
            topicPrefix = Topics.normalizePrefix(topicPrefix, Topics.DEFAULT_TOPIC_PREFIX),
            discoveryPrefix = Topics.normalizePrefix(discoveryPrefix, Topics.DEFAULT_DISCOVERY_PREFIX),
            deviceId = Topics.normalizeDeviceId(deviceId),
            deviceName = Topics.deviceName(deviceName),
            pollSeconds = pollSeconds.coerceIn(1, 60),
            repeatMinutes = repeatMinutes.coerceIn(0, 60),
            fastPublishSeconds = fastPublishSeconds.coerceIn(0, 60),
            wireguardConf = conf,
            wireguardFileName = wireguardFileName.trim(),
            wireguardEnabled = wireguardEnabled && conf.isNotBlank(),
        )
    }

    /** Broker fields from [from]. Token, entities and the Monitor port stay on this copy. */
    fun applyingConnection(from: MqttSettings): MqttSettings = copy(
        brokerHost = from.brokerHost,
        brokerPort = from.brokerPort,
        username = from.username,
        password = from.password,
        tlsEnabled = from.tlsEnabled,
        caPem = from.caPem,
        topicPrefix = from.topicPrefix,
        discoveryPrefix = from.discoveryPrefix,
        deviceId = from.deviceId,
        mqttClientId = from.mqttClientId,
        autostart = from.autostart,
        pollSeconds = from.pollSeconds,
        repeatMinutes = from.repeatMinutes,
        fastPublishSeconds = from.fastPublishSeconds,
        wireguardEnabled = from.wireguardEnabled,
        wireguardConf = from.wireguardConf,
        wireguardFileName = from.wireguardFileName,
    )

    val ready: Boolean
        get() = accessToken.isNotBlank() && brokerHost.isNotBlank()

    /** The bridge has nothing to do without the broker, so the switch stops the whole service. */
    val active: Boolean
        get() = ready && brokerEnabled

    fun connectionKey(): String = listOf(
        brokerHost,
        brokerPort.toString(),
        username,
        password,
        tlsEnabled.toString(),
        caPem,
        Topics.mqttClientId(mqttClientId, deviceId),
        wireguardEnabled.toString(),
        sha256(wireguardConf),
    ).joinToString("|")
}

private fun sha256(text: String): String {
    if (text.isEmpty()) return ""
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
