package vad.dashing.mqtt.settings

object BrokerWarning {
    const val TEXT =
        "Брокер без пароля и шифрования. Любой, кто подключится к нему, увидит данные машины и сможет ею управлять."

    fun shouldWarn(username: String, tlsEnabled: Boolean, host: String): Boolean {
        if (username.isNotBlank() || tlsEnabled) return false
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return false
        return !isHomeNetwork(trimmed)
    }

    fun isHomeNetwork(host: String): Boolean {
        val trimmed = host.trim().removePrefix("[").removeSuffix("]")
        if (trimmed.endsWith(".local", ignoreCase = true)) return true
        if (isPrivateIpv4(trimmed)) return true
        if (trimmed.contains(':') && isPrivateIpv6(trimmed.lowercase())) return true
        return false
    }

    private fun isPrivateIpv4(host: String): Boolean {
        val parts = host.split('.')
        if (parts.size != 4) return false
        val numbers = parts.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        val first = numbers[0]
        val second = numbers[1]
        return first == 10 ||
            first == 127 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168) ||
            (first == 169 && second == 254)
    }

    private fun isPrivateIpv6(host: String): Boolean {
        if (host == "::1") return true
        if (host.startsWith("fc") || host.startsWith("fd")) return true
        return host.startsWith("fe8") ||
            host.startsWith("fe9") ||
            host.startsWith("fea") ||
            host.startsWith("feb")
    }
}
