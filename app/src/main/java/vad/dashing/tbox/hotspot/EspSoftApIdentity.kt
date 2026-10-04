package vad.dashing.tbox.hotspot

import java.security.SecureRandom

object EspSoftApIdentity {
    private const val SSID_PREFIX = "TBox-"
    private const val PSK_LENGTH = 10
    private val alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray()
    private val random = SecureRandom()

    fun randomSsid(): String {
        val bytes = ByteArray(2)
        random.nextBytes(bytes)
        return SSID_PREFIX + bytes.joinToString("") { "%02x".format(it) }
    }

    fun randomPsk(): String = buildString(PSK_LENGTH) {
        repeat(PSK_LENGTH) { append(alphabet[random.nextInt(alphabet.size)]) }
    }

    fun isValidSsid(value: String): Boolean {
        if (value.isEmpty() || value.length > 32) return false
        return value.all(::isIdentityChar)
    }

    fun isValidPsk(value: String): Boolean {
        if (value.length !in 8..63) return false
        return value.all(::isIdentityChar)
    }

    private fun isIdentityChar(c: Char): Boolean =
        c.code in 0x20..0x7E && c != '"' && c != '\\'
}
