package vad.dashing.mqtt.wireguard

import android.content.Context
import vad.dashing.mqtt.settings.MqttSettings
import java.net.InetAddress
import java.net.InetSocketAddress

class WgRouteException(message: String) : Exception(message)

/** Local TCP socket that carries the broker name for TLS, while the bytes go to 127.0.0.1. */
class TunnelEndpoint(
    val port: Int,
    val socket: InetSocketAddress,
)

object WgTunnel {
    private val lock = Any()
    private var runningKey: String? = null
    private var runningPort: Int = 0
    private var libraryReady = false
    private var libraryFailure: String? = null

    internal fun blockedByLibrary(): String? = libraryFailure

    internal fun libraryLoaded(): Boolean = libraryReady

    internal fun rememberLibraryFailure(message: String) {
        libraryFailure = message
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

    /** Null means the broker is dialed directly. */
    fun route(settings: MqttSettings): TunnelEndpoint? {
        synchronized(lock) {
            if (!settings.wireguardEnabled || settings.wireguardConf.isBlank()) {
                if (runningKey != null) stopLocked()
                return null
            }
            if (settings.brokerHost.isBlank()) throw WgRouteException("Укажите адрес брокера")
            val conf = parseWgConf(settings.wireguardConf).getOrElse {
                throw WgRouteException(it.message ?: "Файл WireGuard не разобран")
            }
            val target = brokerTarget(conf, settings.brokerHost, settings.brokerPort)
            val endpoints = conf.peers.map { peer ->
                resolveEndpoint(peer.endpointHost, peer.endpointPort)
            }
            val ipc = conf.toIpc(endpoints)
            val key = ipc + "|" + target
            if (runningKey == key && runningPort != 0) {
                return endpoint(settings.brokerHost, runningPort)
            }
            stopLocked()
            val port = WgNative.start(ipc, conf.addressCsv(), conf.dnsCsv(), conf.mtu, target)
            runningKey = key
            runningPort = port
            return endpoint(settings.brokerHost, port)
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
        runningKey = null
        runningPort = 0
        runCatching { WgNative.stop() }
    }

    private fun endpoint(host: String, port: Int): TunnelEndpoint {
        // Hostname stays on the address so TLS checks the broker name, not 127.0.0.1.
        val loopback = InetAddress.getByAddress(host, byteArrayOf(127, 0, 0, 1))
        return TunnelEndpoint(port, InetSocketAddress(loopback, port))
    }
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

private object WgNative {
    fun start(ipc: String, addresses: String, dns: String, mtu: Int, broker: String): Int {
        WgTunnel.blockedByLibrary()?.let { throw WgRouteException(it) }
        if (!WgTunnel.libraryLoaded()) {
            throw WgRouteException("Не удалось загрузить библиотеку WireGuard")
        }
        return startNative(ipc, addresses, dns, mtu, broker)
    }

    fun stop() {
        if (!WgTunnel.libraryLoaded()) return
        runCatching { wgstack.Wgstack.stop() }
    }
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
