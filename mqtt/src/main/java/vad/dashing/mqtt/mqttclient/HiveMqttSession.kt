package vad.dashing.mqtt.mqttclient

import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import vad.dashing.mqtt.settings.MqttSettings
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.TimeUnit
import javax.net.ssl.TrustManagerFactory

class HiveMqttSession {
    private var client: Mqtt3AsyncClient? = null
    private val subscribed = mutableSetOf<String>()
    private var connectionKey: String = ""
    private var willTopic: String? = null

    @Volatile
    var connected: Boolean = false
        private set

    fun ensureConnected(
        settings: MqttSettings,
        statusTopic: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onMessage: (topic: String, payload: String, retained: Boolean) -> Unit,
    ) {
        val key = settings.connectionKey() + "|" + statusTopic
        if (client != null && connectionKey == key) {
            if (!connected) {
                connectExisting(willTopic)
            }
            return
        }
        closeQuietly()
        connectionKey = key
        val normalized = settings.normalized()
        val builder = Mqtt3Client.builder()
            .identifier(clientId(normalized))
            .serverHost(normalized.brokerHost)
            .serverPort(normalized.brokerPort)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener { onConnected() }
            .addDisconnectedListener { connected = false; onDisconnected() }
        if (normalized.tlsEnabled) {
            builder.sslConfig(sslConfig(normalized.caPem))
        }
        if (normalized.username.isNotBlank()) {
            builder.simpleAuth()
                .username(normalized.username)
                .password(normalized.password.toByteArray(StandardCharsets.UTF_8))
                .applySimpleAuth()
        }
        val created = builder.buildAsync()
        client = created
        willTopic = statusTopic
        created.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            val bytes = publish.payload.map { buffer ->
                val copy = ByteArray(buffer.remaining())
                buffer.get(copy)
                copy
            }.orElse(ByteArray(0))
            onMessage(
                publish.topic.toString(),
                String(bytes, StandardCharsets.UTF_8),
                publish.isRetain,
            )
        }
        connectExisting(statusTopic)
    }

    fun publish(topic: String, payload: String, retain: Boolean) {
        val current = client ?: return
        if (!connected) return
        current.publishWith()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .qos(MqttQos.AT_LEAST_ONCE)
            .retain(retain)
            .send()
            .get(8, TimeUnit.SECONDS)
    }

    fun replaceSubscriptions(topics: Set<String>) {
        val current = client ?: return
        if (!connected) return
        val remove = subscribed - topics
        val add = topics - subscribed
        remove.forEach { topic ->
            current.unsubscribeWith().topicFilter(topic).send().get(8, TimeUnit.SECONDS)
        }
        add.forEach { topic ->
            current.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .get(8, TimeUnit.SECONDS)
        }
        subscribed.clear()
        subscribed.addAll(topics)
    }

    fun disconnect() {
        closeQuietly()
    }

    private fun connectExisting(statusTopic: String? = null) {
        val current = client ?: return
        val connect = current.connectWith().keepAlive(30).cleanSession(true)
        if (statusTopic != null) {
            connect.willPublish()
                .topic(statusTopic)
                .payload("offline".toByteArray(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(true)
                .applyWillPublish()
        }
        connect.send().get(15, TimeUnit.SECONDS)
        connected = true
        subscribed.clear()
    }

    private fun closeQuietly() {
        val current = client
        client = null
        connected = false
        subscribed.clear()
        connectionKey = ""
        willTopic = null
        if (current != null) {
            runCatching { current.disconnect().get(4, TimeUnit.SECONDS) }
        }
    }

    companion object {
        fun probe(settings: MqttSettings): String? {
            val normalized = settings.normalized()
            if (normalized.brokerHost.isBlank()) return "Укажите адрес брокера"
            val builder = Mqtt3Client.builder()
                .identifier(clientId(normalized) + "-check")
                .serverHost(normalized.brokerHost)
                .serverPort(normalized.brokerPort)
            if (normalized.tlsEnabled) builder.sslConfig(sslConfig(normalized.caPem))
            if (normalized.username.isNotBlank()) {
                builder.simpleAuth()
                    .username(normalized.username)
                    .password(normalized.password.toByteArray(StandardCharsets.UTF_8))
                    .applySimpleAuth()
            }
            val client = builder.buildAsync()
            return try {
                client.connectWith().keepAlive(30).cleanSession(true).send().get(12, TimeUnit.SECONDS)
                runCatching { client.disconnect().get(4, TimeUnit.SECONDS) }
                null
            } catch (error: Exception) {
                runCatching { client.disconnect() }
                error.message ?: "Не удалось подключиться"
            }
        }

        private fun clientId(settings: MqttSettings): String =
            vad.dashing.mqtt.ha.Topics.mqttClientId(settings.mqttClientId, settings.deviceId)

        private fun sslConfig(pem: String): MqttClientSslConfig {
            val builder = MqttClientSslConfig.builder()
            val trimmed = pem.trim()
            if (trimmed.isNotEmpty()) {
                val factory = CertificateFactory.getInstance("X.509")
                val certificates = factory.generateCertificates(ByteArrayInputStream(trimmed.toByteArray(StandardCharsets.UTF_8)))
                val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
                keyStore.load(null, null)
                certificates.forEachIndexed { index, certificate ->
                    keyStore.setCertificateEntry("ca$index", certificate)
                }
                val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                trust.init(keyStore)
                builder.trustManagerFactory(trust)
            }
            return builder.build()
        }
    }
}
