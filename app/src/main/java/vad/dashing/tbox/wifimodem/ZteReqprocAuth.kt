package vad.dashing.tbox.wifimodem

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

/**
 * Password encoding helpers for ZTE-lineage `/reqproc` LOGIN variants.
 *
 * F95 exact dialect is unverified until a live capture; see
 * [docs/WIFI_MODEM_OLAX_F95_RU.md].
 */
object ZteReqprocAuth {

    enum class Dialect {
        /** Olax M100 / many USB Olax: `password = base64(plaintext)`. */
        BASE64_PASSWORD,

        /**
         * Newer CPE / ZLT: `password = base64(sha256_hex(nonce + plaintext))`,
         * often with `username = base64(user)`.
         */
        SHA256_NONCE,
    }

    fun encodePassword(dialect: Dialect, plaintextPassword: String, nonce: String = ""): String {
        return when (dialect) {
            Dialect.BASE64_PASSWORD -> base64Utf8(plaintextPassword)
            Dialect.SHA256_NONCE -> {
                require(nonce.isNotEmpty()) { "SHA256_NONCE requires get_random_login nonce" }
                val digestHex = sha256Hex(nonce + plaintextPassword)
                base64Utf8(digestHex)
            }
        }
    }

    fun encodeUsername(username: String): String = base64Utf8(username)

    fun base64Utf8(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    /** LOGIN success codes observed across ZTE builds. */
    fun isLoginSuccess(result: String?): Boolean =
        result == "0" || result == "4" || result.equals("success", ignoreCase = true)
}
