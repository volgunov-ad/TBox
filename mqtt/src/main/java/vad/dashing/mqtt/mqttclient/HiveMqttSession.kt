package vad.dashing.mqtt.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.wireguard.TunnelEndpoint
import vad.dashing.mqtt.wireguard.WgRouteException
import vad.dashing.mqtt.wireguard.WgTunnel
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.TrustManagerFactory

class HiveMqttSession {
    private var client: Mqtt3AsyncClient? = null
    private val subscribed = mutableSetOf<String>()
    private var connectionKey: String = ""
    private var willTopic: String? = null
    private val generation = AtomicInteger(0)

    @Volatile
    var connected: Boolean = false
        private set

    /** Why the broker link is down; empty while connected. */
    @Volatile
    var lastFailure: String = ""
        private set

    fun ensureConnected(
        settings: MqttSettings,
        statusTopic: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onMessage: (topic: String, payload: String, retained: Boolean) -> Unit,
    ) {
        val normalized = settings.normalized()
        val base = normalized.connectionKey() + "|" + statusTopic
        val turningOff = !normalized.wireguardEnabled || normalized.wireguardConf.isBlank()
        // Drop the old session while its tunnel is still up, so offline is a clean DISCONNECT.
        if (turningOff && client != null && connectionKey.substringBeforeLast('|') != base) {
            closeQuietly()
        }
        val tunnel = WgTunnel.route(normalized)
        val key = base + "|" + (tunnel?.port ?: 0)
        if (client != null && connectionKey == key) {
            if (!connected && connectExisting(willTopic)) {
                onConnected()
            }
            return
        }
        closeQuietly()
        connectionKey = key
        val own = generation.incrementAndGet()
        val holder = AtomicReference<Mqtt3AsyncClient?>(null)
        val builder = Mqtt3Client.builder()
            .identifier(clientId(normalized))
        bindBroker(builder, normalized, tunnel)
        // Default backoff grows to 2 min; after a long outage the car would stay offline that long.
        builder.automaticReconnect()
            .initialDelay(1, TimeUnit.SECONDS)
            .maxDelay(RECONNECT_MAX_DELAY_S, TimeUnit.SECONDS)
            .applyAutomaticReconnect()
            .addConnectedListener {
                if (generation.get() != own) {
                    // Same client id: a late stale connect would kick the live client off the broker.
                    // disconnect() returns a hot future; waiting here would stall the client loop.
                    holder.get()?.disconnect()
                    return@addConnectedListener
                }
                connected = true
                lastFailure = ""
                onConnected()
            }
            .addDisconnectedListener { context ->
                if (generation.get() != own) {
                    // disconnect() is a no-op between reconnect attempts; stop the loop here.
                    context.reconnector.reconnect(false)
                    return@addDisconnectedListener
                }
                connected = false
                lastFailure = brokerFailureText(context.cause)
                onDisconnected()
            }
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
        holder.set(created)
        client = created
        willTopic = statusTopic
        created.publishes(MqttGlobalPublishFilter.ALL) { publish ->
            if (generation.get() != own) return@publishes
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

    /** Clean session drops broker subscriptions. The next replace sends them again. */
    fun forgetSubscriptions() {
        subscribed.clear()
    }

    fun replaceSubscriptions(topics: Set<String>) {
        val current = client ?: return
        if (!connected) return
        val remove = subscribed - topics
        val add = topics - subscribed
        remove.forEach { topic ->
            current.unsubscribeWith().topicFilter(topic).send().get(8, TimeUnit.SECONDS)
            subscribed.remove(topic)
        }
        add.forEach { topic ->
            current.subscribeWith()
                .topicFilter(topic)
                .qos(MqttQos.AT_LEAST_ONCE)
                .send()
                .get(8, TimeUnit.SECONDS)
            subscribed.add(topic)
        }
    }

    fun disconnect() {
        closeQuietly()
        WgTunnel.stop()
    }

    /**
     * @return true when the client is already connected and the caller should publish again.
     * A fresh connect notifies through the connected listener instead.
     */
    private fun connectExisting(statusTopic: String? = null): Boolean {
        val current = client ?: return false
        when (mqttConnectStep(current.config.state)) {
            MqttConnectStep.ALREADY_UP -> {
                connected = true
                subscribed.clear()
                return true
            }
            MqttConnectStep.WAIT -> return false
            MqttConnectStep.CONNECT -> Unit
        }
        val connect = current.connectWith().keepAlive(30).cleanSession(true)
        if (statusTopic != null) {
            connect.willPublish()
                .topic(statusTopic)
                .payload("offline".toByteArray(StandardCharsets.UTF_8))
                .qos(MqttQos.AT_LEAST_ONCE)
                .retain(true)
                .applyWillPublish()
        }
        try {
            connect.send().get(15, TimeUnit.SECONDS)
        } catch (error: Exception) {
            if (error.hasCause<MqttClientStateException>()) {
                if (current.config.state.isConnected) {
                    connected = true
                    subscribed.clear()
                    return true
                }
                return false
            }
            lastFailure = brokerFailureText(error)
            throw error
        }
        connected = true
        lastFailure = ""
        subscribed.clear()
        return false
    }

    private fun closeQuietly() {
        generation.incrementAndGet()
        val current = client
        val oldWill = willTopic
        val wasConnected = connected
        client = null
        connected = false
        lastFailure = ""
        subscribed.clear()
        connectionKey = ""
        willTopic = null
        if (current == null) return
        // A clean DISCONNECT suppresses the will, so the old status topic would stay "online".
        if (wasConnected && oldWill != null) {
            runCatching {
                current.publishWith()
                    .topic(oldWill)
                    .payload("offline".toByteArray(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(true)
                    .send()
                    .get(4, TimeUnit.SECONDS)
            }
        }
        runCatching { current.disconnect().get(4, TimeUnit.SECONDS) }
    }

    companion object {
        private const val RECONNECT_MAX_DELAY_S = 30L

        fun probe(settings: MqttSettings): String? {
            val normalized = settings.normalized()
            if (normalized.brokerHost.isBlank()) return "Укажите адрес брокера"
            val tunnel = try {
                WgTunnel.route(normalized)
            } catch (error: WgRouteException) {
                return error.message
            }
            var client: Mqtt3AsyncClient? = null
            return try {
                val builder = Mqtt3Client.builder()
                    .identifier(clientId(normalized) + "-check")
                bindBroker(builder, normalized, tunnel)
                if (normalized.tlsEnabled) builder.sslConfig(sslConfig(normalized.caPem))
                if (normalized.username.isNotBlank()) {
                    builder.simpleAuth()
                        .username(normalized.username)
                        .password(normalized.password.toByteArray(StandardCharsets.UTF_8))
                        .applySimpleAuth()
                }
                val built = builder.buildAsync()
                client = built
                built.connectWith().keepAlive(30).cleanSession(true).send().get(12, TimeUnit.SECONDS)
                null
            } catch (error: Exception) {
                brokerFailureText(error)
            } finally {
                client?.let { runCatching { it.disconnect().get(4, TimeUnit.SECONDS) } }
            }
        }

        private fun bindBroker(
            builder: com.hivemq.client.mqtt.mqtt3.Mqtt3ClientBuilder,
            settings: MqttSettings,
            tunnel: TunnelEndpoint?,
        ) {
            if (tunnel == null) {
                builder.serverHost(settings.brokerHost).serverPort(settings.brokerPort)
            } else {
                builder.serverAddress(tunnel.socket)
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

internal enum class MqttConnectStep {
    CONNECT,
    WAIT,
    ALREADY_UP,
}

/** Automatic reconnect already owns every state except a fully stopped client. */
internal fun mqttConnectStep(state: MqttClientState): MqttConnectStep = when (state) {
    MqttClientState.DISCONNECTED -> MqttConnectStep.CONNECT
    MqttClientState.CONNECTED -> MqttConnectStep.ALREADY_UP
    MqttClientState.CONNECTING,
    MqttClientState.CONNECTING_RECONNECT,
    MqttClientState.DISCONNECTED_RECONNECT,
    -> MqttConnectStep.WAIT
}

/** Future wrappers carry no text of their own; a bare timeout has no message at all. */
internal fun brokerFailureText(error: Throwable): String {
    val cause = generateSequence(error) { it.cause }
        .firstOrNull { it !is ExecutionException && it !is CompletionException }
        ?: error
    if (cause is TimeoutException) return "Брокер не ответил вовремя"
    return cause.message?.trim()?.takeIf { it.isNotEmpty() } ?: cause.javaClass.simpleName
}

private inline fun <reified T : Throwable> Throwable.hasCause(): Boolean {
    var current: Throwable? = this
    while (current != null) {
        if (current is T) return true
        current = current.cause
    }
    return false
}
