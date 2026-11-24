package vad.dashing.mqtt.mqttclient

import com.hivemq.client.mqtt.MqttClientState
import com.hivemq.client.mqtt.MqttClientSslConfig
import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.exceptions.MqttClientStateException
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client
import vad.dashing.mqtt.settings.MqttSettings
import vad.dashing.mqtt.wireguard.ProbeTunnelPlan
import vad.dashing.mqtt.wireguard.TunnelEndpoint
import vad.dashing.mqtt.wireguard.WgRouteException
import vad.dashing.mqtt.wireguard.WgSupersededException
import vad.dashing.mqtt.wireguard.WgTunnel
import vad.dashing.mqtt.wireguard.planProbeTunnel
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.TrustManagerFactory

data class Outgoing(
    val topic: String,
    val payload: String,
    val retain: Boolean,
)

class HiveMqttSession {
    private val tunnelOwner = WgTunnel.newOwner()
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

    @Volatile
    var lastPublishAtMs: Long = 0L
        private set

    fun ensureConnected(
        settings: MqttSettings,
        statusTopic: String,
        onConnected: () -> Unit,
        onDisconnected: () -> Unit,
        onMessage: (topic: String, payload: String, retained: Boolean) -> Unit,
        stillActive: () -> Boolean = { true },
    ) {
        if (!stillActive()) return
        val normalized = settings.normalized()
        val base = normalized.connectionKey() + "|" + statusTopic
        val turningOff = !normalized.wireguardEnabled || normalized.wireguardConf.isBlank()
        // Drop the old session while its tunnel is still up, so offline is a clean DISCONNECT.
        if (turningOff && client != null && connectionKey.substringBeforeLast('|') != base) {
            closeQuietly(publishOffline = true)
        }
        val tunnel = try {
            WgTunnel.route(normalized, tunnelOwner, stillActive, takeover = true)
        } catch (_: WgSupersededException) {
            return
        }
        if (!stillActive()) return
        val key = base + "|" + (tunnel?.port ?: 0)
        if (client != null && connectionKey == key) {
            if (!connected && connectExisting(willTopic)) {
                onConnected()
            }
            return
        }
        closeQuietly(publishOffline = true)
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
                if (generation.get() != own || !stillActive()) {
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
                if (generation.get() != own || !stillActive()) {
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

    /** False when the client is down or the broker does not confirm the publish. */
    fun publish(topic: String, payload: String, retain: Boolean): Boolean =
        publishAll(listOf(Outgoing(topic, payload, retain)), timeoutSeconds = 8).contains(topic)

    fun publishAll(items: List<Outgoing>, timeoutSeconds: Long = PUBLISH_BATCH_TIMEOUT_S): Set<String> {
        val current = client ?: return emptySet()
        if (!connected || items.isEmpty()) return emptySet()
        val futures = items.mapNotNull { item ->
            try {
                val future = current.publishWith()
                    .topic(item.topic)
                    .payload(item.payload.toByteArray(StandardCharsets.UTF_8))
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(item.retain)
                    .send()
                item.topic to future
            } catch (_: Exception) {
                null
            }
        }
        if (futures.isEmpty()) return emptySet()
        try {
            CompletableFuture.allOf(*futures.map { it.second }.toTypedArray()).get(timeoutSeconds, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Individual futures say which topics actually landed.
        }
        val done = futures.filter { (_, future) ->
            future.isDone && !future.isCompletedExceptionally && !future.isCancelled
        }.map { it.first }.toSet()
        if (done.isNotEmpty()) lastPublishAtMs = System.currentTimeMillis()
        return done
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

    /**
     * @param publishOffline false when a newer instance already owns the status topic.
     * The tunnel stops only if this session still owns it.
     */
    fun disconnect(publishOffline: Boolean = true) {
        closeQuietly(publishOffline)
        WgTunnel.release(tunnelOwner)
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

    private fun closeQuietly(publishOffline: Boolean) {
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
        if (publishOffline && wasConnected && oldWill != null) {
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
        private const val PUBLISH_BATCH_TIMEOUT_S = 20L

        fun probe(settings: MqttSettings): String? {
            val normalized = settings.normalized()
            if (normalized.brokerHost.isBlank()) return "Укажите адрес брокера"
            val plan = planProbeTunnel(WgTunnel.identity(normalized), WgTunnel.held())
            var probeOwner = 0L
            val tunnel = try {
                when (plan) {
                    is ProbeTunnelPlan.Refuse -> return plan.message
                    is ProbeTunnelPlan.Direct -> null
                    is ProbeTunnelPlan.Reuse -> WgTunnel.endpointFor(normalized.brokerHost)
                    ProbeTunnelPlan.Start -> {
                        probeOwner = WgTunnel.newOwner()
                        WgTunnel.route(normalized, probeOwner, stillActive = { true }, takeover = false)
                    }
                }
            } catch (error: WgRouteException) {
                if (probeOwner != 0L) WgTunnel.release(probeOwner)
                return error.message
            }
            var client: Mqtt3AsyncClient? = null
            val note = (plan as? ProbeTunnelPlan.Direct)?.note
            return try {
                val builder = Mqtt3Client.builder()
                    .identifier(clientId(normalized) + "-check")
                builder.transportConfig()
                    .socketConnectTimeout(8, TimeUnit.SECONDS)
                    .mqttConnectTimeout(10, TimeUnit.SECONDS)
                    .applyTransportConfig()
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
                val connect = built.connectWith().keepAlive(30).cleanSession(true).send()
                try {
                    connect.get(12, TimeUnit.SECONDS)
                } catch (error: TimeoutException) {
                    connect.whenComplete { _, _ -> runCatching { built.disconnect() } }
                    throw error
                }
                if (note.isNullOrBlank()) null else "$note. Брокер отвечает"
            } catch (error: Exception) {
                val text = brokerFailureText(error)
                if (note.isNullOrBlank()) text else "$note. $text"
            } finally {
                client?.let { runCatching { it.disconnect().get(4, TimeUnit.SECONDS) } }
                if (probeOwner != 0L) WgTunnel.release(probeOwner)
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
            vad.dashing.mqtt.ha.Topics.mqttClientId(
                settings.mqttClientId,
                settings.deviceId,
                settings.clientSuffix,
            )

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
