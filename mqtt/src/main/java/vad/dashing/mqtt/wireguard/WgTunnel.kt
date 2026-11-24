package vad.dashing.mqtt.wireguard

import android.content.Context
import vad.dashing.mqtt.settings.MqttSettings
import java.net.InetAddress
import java.net.InetSocketAddress

class WgRouteException(message: String) : Exception(message)

/** The caller lost the race with a newer bridge instance. The tunnel was not touched. */
class WgSupersededException : Exception("Туннель перешёл к новому мосту")

/** Local TCP socket that carries the broker name for TLS, while the bytes go to 127.0.0.1. */
class TunnelEndpoint(
    val port: Int,
    val socket: InetSocketAddress,
)

internal data class WgHeld(
    val identity: String,
    val owner: Long,
    val port: Int,
)

internal sealed class ProbeTunnelPlan {
    data class Direct(val note: String?) : ProbeTunnelPlan()
    data class Reuse(val port: Int) : ProbeTunnelPlan()
    data class Refuse(val message: String) : ProbeTunnelPlan()
    data object Start : ProbeTunnelPlan()
}

internal interface WgBackend {
    fun start(ipc: String, addresses: String, dns: String, mtu: Int, broker: String): Int
    fun stop()
    fun lastError(): String
}

/**
 * One process-wide tunnel. Each caller holds an owner token from [newOwner].
 * [release] stops the tunnel only when that caller still owns it, so a probe or a
 * dying service instance cannot tear down the live bridge.
 */
object WgTunnel {
    private val lock = Any()
    private var runningIdentity: String? = null
    private var runningPort: Int = 0
    private var ownerToken: Long = 0L
    private var nextToken: Long = 1L
    private var libraryReady = false
    private var libraryFailure: String? = null

    internal var backend: WgBackend = NativeWgBackend

    internal fun blockedByLibrary(): String? = libraryFailure

    internal fun libraryLoaded(): Boolean = libraryReady

    internal fun rememberLibraryFailure(message: String) {
        libraryFailure = message
    }

    fun newOwner(): Long = synchronized(lock) { nextToken++ }

    /** Stable identity of the draft, or null when WireGuard is off. No DNS. */
    fun identity(settings: MqttSettings): String? {
        val normalized = settings.normalized()
        if (!normalized.wireguardEnabled || normalized.wireguardConf.isBlank()) return null
        return normalized.wireguardConf + "\n" + normalized.brokerHost + ":" + normalized.brokerPort
    }

    internal fun held(): WgHeld? = synchronized(lock) {
        val identity = runningIdentity ?: return null
        if (runningPort == 0 || ownerToken == 0L) return null
        WgHeld(identity, ownerToken, runningPort)
    }

    fun endpointFor(brokerHost: String): TunnelEndpoint? = synchronized(lock) {
        if (runningPort == 0) return null
        endpoint(brokerHost, runningPort)
    }

    /**
     * @param takeover a live bridge may replace someone else's tunnel. A probe must not.
     */
    fun route(
        settings: MqttSettings,
        owner: Long,
        stillActive: () -> Boolean = { true },
        takeover: Boolean = true,
    ): TunnelEndpoint? {
        synchronized(lock) {
            if (!stillActive()) throw WgSupersededException()
            val normalized = settings.normalized()
            val draft = identity(normalized)
            if (draft == null) {
                if (ownerToken == owner && runningIdentity != null) stopLocked()
                return null
            }
            if (draft == runningIdentity && runningPort != 0) {
                // A new bridge instance adopts the live tunnel without restarting it.
                if (takeover && ownerToken != owner) {
                    if (!stillActive()) throw WgSupersededException()
                    ownerToken = owner
                }
                return endpoint(normalized.brokerHost, runningPort)
            }
            if (runningIdentity != null && ownerToken != owner && !takeover) {
                throw WgRouteException(PROBE_OTHER_TUNNEL_MESSAGE)
            }
            if (!stillActive()) throw WgSupersededException()
            if (normalized.brokerHost.isBlank()) throw WgRouteException("Укажите адрес брокера")
            val conf = parseWgConf(normalized.wireguardConf).getOrElse {
                throw WgRouteException(it.message ?: "Файл WireGuard не разобран")
            }
            val target = brokerTarget(conf, normalized.brokerHost, normalized.brokerPort)
            val endpoints = conf.peers.map { peer ->
                resolveEndpoint(peer.endpointHost, peer.endpointPort)
            }
            if (!stillActive()) throw WgSupersededException()
            val ipc = conf.toIpc(endpoints)
            stopLocked()
            val port = backend.start(ipc, conf.addressCsv(), conf.dnsCsv(), conf.mtu, target)
            runningIdentity = draft
            runningPort = port
            ownerToken = owner
            return endpoint(normalized.brokerHost, port)
        }
    }

    /** Stops the tunnel only when [owner] is still the one that started it. */
    fun release(owner: Long) {
        synchronized(lock) {
            if (ownerToken != owner) return
            stopLocked()
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    /**
     * Last failed dial from the local port into the tunnel. The native side never clears it,
     * so it is only meaningful while the broker is unreachable.
     */
    fun lastDialError(): String {
        synchronized(lock) {
            if (runningIdentity == null) return ""
            return backend.lastError()
        }
    }

    internal fun resetForTest() {
        synchronized(lock) {
            runningIdentity = null
            runningPort = 0
            ownerToken = 0L
            nextToken = 1L
            backend = NativeWgBackend
        }
    }

    private fun stopLocked() {
        runningIdentity = null
        runningPort = 0
        ownerToken = 0L
        runCatching { backend.stop() }
    }

    private fun endpoint(host: String, port: Int): TunnelEndpoint {
        // Hostname stays on the address so TLS checks the broker name, not 127.0.0.1.
        val loopback = InetAddress.getByAddress(host, byteArrayOf(127, 0, 0, 1))
        return TunnelEndpoint(port, InetSocketAddress(loopback, port))
    }

    /** Load libgojni on the main thread, before a broker check touches the class. */
    fun prepare(context: Context) {
        synchronized(lock) {
            if (libraryReady || libraryFailure != null) return
            try {
                System.loadLibrary("gojni")
                go.Seq.setContext(context.applicationContext)
                libraryReady = true
            } catch (error: Throwable) {
                libraryFailure = wireguardFailure(error)
            }
        }
    }

    private object NativeWgBackend : WgBackend {
        override fun start(ipc: String, addresses: String, dns: String, mtu: Int, broker: String): Int {
            blockedByLibrary()?.let { throw WgRouteException(it) }
            if (!libraryLoaded()) {
                throw WgRouteException("Не удалось загрузить библиотеку WireGuard")
            }
            return startNative(ipc, addresses, dns, mtu, broker)
        }

        override fun stop() {
            if (!libraryLoaded()) return
            runCatching { wgstack.Wgstack.stop() }
        }

        override fun lastError(): String {
            if (!libraryLoaded()) return ""
            return runCatching { wgstack.Wgstack.lastError().orEmpty().trim() }.getOrDefault("")
        }
    }
}

internal const val PROBE_OTHER_TUNNEL_MESSAGE =
    "Мост уже держит другой туннель WireGuard. Сохраните настройки или остановите мост и проверьте снова."

internal fun planProbeTunnel(draftIdentity: String?, held: WgHeld?): ProbeTunnelPlan {
    if (draftIdentity == null) {
        val note = if (held != null) {
            "Проверка без WireGuard: в черновике туннель выключен, рабочий туннель не останавливала"
        } else {
            null
        }
        return ProbeTunnelPlan.Direct(note)
    }
    if (held == null) return ProbeTunnelPlan.Start
    if (held.identity == draftIdentity) return ProbeTunnelPlan.Reuse(held.port)
    return ProbeTunnelPlan.Refuse(PROBE_OTHER_TUNNEL_MESSAGE)
}

private fun brokerTarget(conf: WgConf, host: String, port: Int): String {
    if (isLiteralIp(host)) {
        if (!conf.allows(host)) {
            throw WgRouteException("Адрес брокера не входит в AllowedIPs")
        }
        return formatEndpoint(host, port)
    }
    if (conf.dns.isNotEmpty()) return formatEndpoint(host, port)
    val resolved = try {
        InetAddress.getAllByName(host).firstOrNull()
    } catch (_: Exception) {
        null
    } ?: throw WgRouteException("Имя брокера не открывается, а в файле нет DNS")
    val ip = resolved.hostAddress ?: throw WgRouteException("Имя брокера не открывается, а в файле нет DNS")
    if (!conf.allows(ip)) throw WgRouteException("Адрес брокера не входит в AllowedIPs")
    return formatEndpoint(ip, port)
}

private fun resolveEndpoint(host: String, port: Int): String {
    if (isLiteralIp(host)) return formatEndpoint(host, port)
    val resolved = try {
        InetAddress.getAllByName(host).firstOrNull()
    } catch (_: Exception) {
        null
    } ?: throw WgRouteException("Сервер WireGuard не найден")
    val ip = resolved.hostAddress ?: throw WgRouteException("Сервер WireGuard не найден")
    return formatEndpoint(ip, port)
}

private fun startNative(ipc: String, addresses: String, dns: String, mtu: Int, broker: String): Int {
    try {
        return wgstack.Wgstack.start(ipc, addresses, dns, mtu, broker)
    } catch (error: Throwable) {
        val message = wireguardFailure(error)
        val brokenLibrary = generateSequence(error) { it.cause }.any {
            it is LinkageError || it is ClassNotFoundException
        }
        if (brokenLibrary) WgTunnel.rememberLibraryFailure(message)
        throw WgRouteException(message)
    }
}

/** Class-init failures surface as "wgstack.Wgstack". Keep the linker text instead. */
internal fun wireguardFailure(error: Throwable): String {
    val messages = generateSequence(error) { it.cause }
        .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
        .filterNot { it == "wgstack.Wgstack" || it.startsWith("wgstack.") }
        .toList()
    val detail = messages.lastOrNull()
    val brokenLibrary = generateSequence(error) { it.cause }.any {
        it is LinkageError || it is ClassNotFoundException
    }
    if (brokenLibrary) {
        return if (detail == null) {
            "Не удалось загрузить библиотеку WireGuard"
        } else {
            "Не удалось загрузить библиотеку WireGuard: $detail"
        }
    }
    return detail ?: "Не удалось поднять WireGuard"
}
