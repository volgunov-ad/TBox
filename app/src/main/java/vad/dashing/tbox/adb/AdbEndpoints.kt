package vad.dashing.tbox.adb

/**
 * Host/port helpers for comparing ADB TCP endpoints.
 *
 * Treats loopback aliases (`127.0.0.1`, `localhost`, `::1`) as the same host so the
 * ADB tab and [LocalhostAdbSession] agree on «same adbd».
 */
internal object AdbEndpoints {
    fun format(host: String, port: Int): String = "${host.trim()}:$port"

    fun matches(endpoint: String, host: String, port: Int): Boolean {
        val parsed = parse(endpoint) ?: return false
        if (parsed.port != port) return false
        return sameHost(parsed.host, host)
    }

    fun parse(endpoint: String): Parsed? {
        val trimmed = endpoint.trim()
        val sep = trimmed.lastIndexOf(':')
        if (sep <= 0 || sep == trimmed.lastIndex) return null
        val host = trimmed.substring(0, sep).trim()
        val port = trimmed.substring(sep + 1).trim().toIntOrNull() ?: return null
        if (host.isEmpty() || port !in 1..65535) return null
        return Parsed(host, port)
    }

    fun sameHost(a: String, b: String): Boolean {
        val left = normalizeHost(a)
        val right = normalizeHost(b)
        if (left == right) return true
        return left in LOOPBACK && right in LOOPBACK
    }

    private fun normalizeHost(host: String): String = host.trim().lowercase()

    private val LOOPBACK = setOf("127.0.0.1", "localhost", "::1")

    data class Parsed(val host: String, val port: Int)
}
