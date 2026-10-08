package vad.dashing.mqtt.settings

import vad.dashing.mqtt.ha.Topics

data class MqttSettings(
    val apiPort: Int = 8765,
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
) {
    fun normalized(): MqttSettings = copy(
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
    )

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
    )

    val ready: Boolean
        get() = accessToken.isNotBlank() && brokerHost.isNotBlank()

    fun connectionKey(): String = listOf(
        brokerHost,
        brokerPort.toString(),
        username,
        password,
        tlsEnabled.toString(),
        caPem,
        Topics.mqttClientId(mqttClientId, deviceId),
    ).joinToString("|")
}
