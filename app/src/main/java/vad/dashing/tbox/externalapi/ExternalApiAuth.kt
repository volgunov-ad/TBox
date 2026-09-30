package vad.dashing.tbox.externalapi

import java.security.MessageDigest
import java.security.SecureRandom

object ExternalApiAuth {
    private val secureRandom = SecureRandom()

    fun generateToken(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return bytes.toHex()
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(value.toByteArray(Charsets.UTF_8))
        return hash.toHex()
    }

    fun findClientByToken(
        clients: List<ExternalApiPairedClient>,
        token: String,
    ): ExternalApiPairedClient? {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) return null
        val hash = sha256Hex(trimmed)
        return clients.firstOrNull { it.tokenHash == hash }
    }

    fun revokeClient(
        clients: List<ExternalApiPairedClient>,
        clientId: String,
    ): List<ExternalApiPairedClient> =
        clients.filterNot { it.clientId == clientId.trim() }

    fun registerApprovedClient(
        clients: List<ExternalApiPairedClient>,
        clientId: String,
        clientName: String,
        accessToken: String,
        createdAtEpochMs: Long,
    ): List<ExternalApiPairedClient> {
        val normalizedId = clientId.trim()
        val withoutExisting = clients.filterNot { it.clientId == normalizedId }
        return withoutExisting + ExternalApiPairedClient(
            clientId = normalizedId,
            clientName = clientName.trim(),
            tokenHash = sha256Hex(accessToken),
            createdAtEpochMs = createdAtEpochMs,
        )
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
}
