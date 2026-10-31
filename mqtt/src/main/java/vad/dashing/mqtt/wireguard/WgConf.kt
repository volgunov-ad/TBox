package vad.dashing.mqtt.wireguard

import java.net.InetAddress
import java.util.Base64

data class WgConf(
    val privateKeyHex: String,
    val addresses: List<String>,
    val dns: List<String>,
    val mtu: Int,
    val listenPort: Int,
    val peers: List<WgPeer>,
) {
    fun addressCsv(): String = addresses.joinToString(",") { it.substringBefore('/') }

    fun dnsCsv(): String = dns.joinToString(",")

    fun summary(): String {
        val peer = peers.first()
        return "${peer.endpointHost}:${peer.endpointPort}\n${addresses.first()}"
    }

    fun allows(ip: String): Boolean = peers.any { peer -> peer.allowedIps.any { cidr -> ipInCidr(ip, cidr) } }

    fun toIpc(endpoints: List<String>): String {
        val body = StringBuilder()
        body.append("private_key=").append(privateKeyHex).append('\n')
        body.append("listen_port=").append(listenPort).append('\n')
        body.append("replace_peers=true\n")
        peers.forEachIndexed { index, peer ->
            body.append("public_key=").append(peer.publicKeyHex).append('\n')
            peer.presharedKeyHex?.let { body.append("preshared_key=").append(it).append('\n') }
            body.append("endpoint=").append(endpoints[index]).append('\n')
            if (peer.keepalive != null && peer.keepalive > 0) {
                body.append("persistent_keepalive_interval=").append(peer.keepalive).append('\n')
            }
            body.append("replace_allowed_ips=true\n")
            peer.allowedIps.forEach { body.append("allowed_ip=").append(it).append('\n') }
        }
        return body.toString()
    }
}

data class WgPeer(
    val publicKeyHex: String,
    val presharedKeyHex: String?,
    val endpointHost: String,
    val endpointPort: Int,
    val allowedIps: List<String>,
    val keepalive: Int?,
)

fun parseWgConf(raw: String): Result<WgConf> {
    return try {
        Result.success(parseWgConfOrThrow(raw))
    } catch (error: IllegalArgumentException) {
        Result.failure(error)
    }
}

private fun parseWgConfOrThrow(raw: String): WgConf {
    var section = ""
    var privateKey: String? = null
    val addresses = mutableListOf<String>()
    val dns = mutableListOf<String>()
    var mtu = 1420
    var listenPort = 0
    val peers = mutableListOf<WgPeer>()
    var peerKey: String? = null
    var peerPsk: String? = null
    var peerEndpoint: Pair<String, Int>? = null
    val peerAllowed = mutableListOf<String>()
    var peerKeepalive: Int? = null
    var peerOpen = false

    fun finishPeer() {
        if (!peerOpen) return
        val key = peerKey ?: throw IllegalArgumentException("У peer нет PublicKey")
        val endpoint = peerEndpoint ?: throw IllegalArgumentException("У peer нет Endpoint")
        if (peerAllowed.isEmpty()) throw IllegalArgumentException("У peer нет AllowedIPs")
        peers += WgPeer(key, peerPsk, endpoint.first, endpoint.second, peerAllowed.toList(), peerKeepalive)
        peerKey = null
        peerPsk = null
        peerEndpoint = null
        peerAllowed.clear()
        peerKeepalive = null
        peerOpen = false
    }

    raw.trim().removePrefix("\uFEFF").replace("\r\n", "\n").lineSequence().forEach { line ->
        val text = line.trim()
        if (text.isEmpty() || text.startsWith("#") || text.startsWith(";")) return@forEach
        if (text.startsWith("[")) {
            finishPeer()
            section = text.trim('[', ']').trim().lowercase()
            if (section == "peer") peerOpen = true
            return@forEach
        }
        val eq = text.indexOf('=')
        if (eq <= 0) return@forEach
        val key = text.substring(0, eq).trim().lowercase()
        val value = text.substring(eq + 1).trim()
        when (section) {
            "interface" -> when (key) {
                "privatekey" -> privateKey = keyHex(value, "PrivateKey")
                "address" -> addresses += splitList(value).map { parseCidr(it, "Address") }
                "dns" -> dns += splitList(value).filter { isLiteralIp(it) }
                "mtu" -> mtu = value.toIntOrNull()?.takeIf { it in 1280..9000 }
                    ?: throw IllegalArgumentException("MTU должен быть от 1280 до 9000")
                "listenport" -> listenPort = value.toIntOrNull()?.takeIf { it in 0..65535 }
                    ?: throw IllegalArgumentException("ListenPort не число")
            }
            "peer" -> when (key) {
                "publickey" -> peerKey = keyHex(value, "PublicKey")
                "presharedkey" -> peerPsk = keyHex(value, "PresharedKey")
                "endpoint" -> peerEndpoint = parseEndpoint(value)
                "allowedips" -> peerAllowed += splitList(value).map { parseCidr(it, "AllowedIPs") }
                "persistentkeepalive" -> peerKeepalive = value.toIntOrNull()?.takeIf { it in 0..65535 }
                    ?: throw IllegalArgumentException("PersistentKeepalive не число")
            }
        }
    }
    finishPeer()
    val key = privateKey ?: throw IllegalArgumentException("В файле нет PrivateKey")
    if (addresses.isEmpty()) throw IllegalArgumentException("В файле нет Address")
    if (peers.isEmpty()) throw IllegalArgumentException("В файле нет [Peer]")
    return WgConf(key, addresses, dns, mtu, listenPort, peers)
}

internal fun ipInCidr(ip: String, cidr: String): Boolean {
    val slash = cidr.indexOf('/')
    if (slash <= 0) return false
    val prefix = cidr.substring(slash + 1).toIntOrNull() ?: return false
    val ipBytes = literalBytes(ip) ?: return false
    val netBytes = literalBytes(cidr.substring(0, slash)) ?: return false
    if (ipBytes.size != netBytes.size) return false
    var left = prefix
    for (index in ipBytes.indices) {
        if (left <= 0) return true
        val bits = if (left >= 8) 8 else left
        val mask = (0xFF shl (8 - bits)) and 0xFF
        if ((ipBytes[index].toInt() and mask) != (netBytes[index].toInt() and mask)) return false
        left -= 8
    }
    return true
}

internal fun isLiteralIp(text: String): Boolean = literalBytes(text) != null

internal fun formatEndpoint(host: String, port: Int): String {
    return if (host.contains(':')) "[$host]:$port" else "$host:$port"
}

private fun parseEndpoint(value: String): Pair<String, Int> {
    val text = value.trim()
    if (text.startsWith("[")) {
        val end = text.indexOf(']')
        if (end <= 1) throw IllegalArgumentException("Endpoint: IPv6 пишите как [адрес]:порт")
        val host = text.substring(1, end)
        if (!isLiteralIp(host)) throw IllegalArgumentException("Endpoint: не адрес")
        val port = portAfter(text, end + 1)
        return host to port
    }
    if (text.count { it == ':' } > 1) {
        throw IllegalArgumentException("Endpoint: IPv6 пишите как [адрес]:порт")
    }
    val colon = text.lastIndexOf(':')
    if (colon < 0) {
        if (text.isBlank()) throw IllegalArgumentException("У peer нет Endpoint")
        return text to 51820
    }
    val host = text.substring(0, colon).trim()
    if (host.isBlank()) throw IllegalArgumentException("У peer нет Endpoint")
    return host to portAfter(text, colon)
}

private fun portAfter(text: String, index: Int): Int {
    if (index >= text.length) return 51820
    if (text[index] != ':') throw IllegalArgumentException("Endpoint: не разобран")
    val port = text.substring(index + 1).trim().toIntOrNull()
        ?: throw IllegalArgumentException("Порт Endpoint не число")
    if (port !in 1..65535) throw IllegalArgumentException("Порт Endpoint не число")
    return port
}

private fun parseCidr(value: String, label: String): String {
    val slash = value.indexOf('/')
    if (slash <= 0) throw IllegalArgumentException("$label: нужен адрес/маска")
    val ip = value.substring(0, slash).trim()
    val bits = value.substring(slash + 1).trim().toIntOrNull()
        ?: throw IllegalArgumentException("$label: маска не число")
    val bytes = literalBytes(ip) ?: throw IllegalArgumentException("$label: не адрес")
    val max = if (bytes.size == 16) 128 else 32
    if (bits !in 0..max) throw IllegalArgumentException("$label: маска вне диапазона")
    return "$ip/$bits"
}

private fun keyHex(value: String, label: String): String {
    val text = value.trim()
    val padded = when (text.length % 4) {
        2 -> "$text=="
        3 -> "$text="
        else -> text
    }
    val bytes = try {
        Base64.getDecoder().decode(padded)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("$label не похож на ключ WireGuard")
    }
    if (bytes.size != 32) throw IllegalArgumentException("$label не похож на ключ WireGuard")
    return bytes.joinToString("") { "%02x".format(it) }
}

private fun splitList(value: String): List<String> =
    value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun literalBytes(text: String): ByteArray? {
    if (text.isEmpty()) return null
    val ipv4 = Regex("""\d{1,3}(\.\d{1,3}){3}""")
    if (ipv4.matches(text)) {
        val parts = text.split('.')
        if (parts.any { (it.toIntOrNull() ?: -1) !in 0..255 }) return null
        return ByteArray(4) { parts[it].toInt().toByte() }
    }
    val bare = text.substringBefore('%')
    if (!bare.contains(':') || bare.contains('.')) return null
    val address = try {
        InetAddress.getByName(bare)
    } catch (_: Exception) {
        return null
    }
    if (address.address.size != 16) return null
    return address.address
}
