package vad.dashing.tbox.externalapi

import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId

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

    /**
     * Drops clients whose last successful API call is strictly older than
     * [ExternalApiConstants.TOKEN_IDLE_MONTHS] calendar months before [nowEpochMs].
     * Tokens saved before usage tracking existed use their creation time.
     */
    fun retainRecentlyUsed(
        clients: List<ExternalApiPairedClient>,
        nowEpochMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ExternalApiPairedClient> {
        val cutoff = idleCutoffEpochMs(nowEpochMs, zone)
        return clients.filter { it.lastUsedAtEpochMs >= cutoff }
    }

    fun idleCutoffEpochMs(nowEpochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(nowEpochMs)
            .atZone(zone)
            .minusMonths(ExternalApiConstants.TOKEN_IDLE_MONTHS.toLong())
            .toInstant()
            .toEpochMilli()

    /**
     * Membership follows [incoming] (disk). A newer in-memory use of the same token is kept.
     */
    fun mergeNewerUsage(
        incoming: List<ExternalApiPairedClient>,
        current: List<ExternalApiPairedClient>,
    ): List<ExternalApiPairedClient> {
        if (current.isEmpty()) return incoming
        val live = current.associateBy { it.clientId }
        return incoming.map { client ->
            val memory = live[client.clientId]
            if (
                memory != null &&
                memory.tokenHash == client.tokenHash &&
                memory.lastUsedAtEpochMs > client.lastUsedAtEpochMs
            ) {
                client.copy(lastUsedAtEpochMs = memory.lastUsedAtEpochMs)
            } else {
                client
            }
        }
    }

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
