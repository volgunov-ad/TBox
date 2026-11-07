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
     * Disk is the membership source once a save has caught up. A newer in-memory use of the
     * same token is kept. A different hash on disk wins: that save already replaced the token.
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

    /**
     * First load from disk, possibly racing a client change that is not saved yet.
     * Disk clients stay. A newer in-memory token for the same id replaces the disk one.
     * Clients that exist only in memory are appended.
     */
    fun mergeUnsavedClients(
        disk: List<ExternalApiPairedClient>,
        memory: List<ExternalApiPairedClient>,
    ): List<ExternalApiPairedClient> {
        if (memory.isEmpty()) return disk
        val memoryById = memory.associateBy { it.clientId }
        val merged = disk.map { stored ->
            val live = memoryById[stored.clientId] ?: return@map stored
            when {
                live.createdAtEpochMs > stored.createdAtEpochMs -> live
                live.tokenHash == stored.tokenHash &&
                    live.lastUsedAtEpochMs > stored.lastUsedAtEpochMs ->
                    stored.copy(lastUsedAtEpochMs = live.lastUsedAtEpochMs)
                else -> stored
            }
        }
        val diskIds = disk.map { it.clientId }.toSet()
        return merged + memory.filter { it.clientId !in diskIds }
    }

    /**
     * Until the first disk snapshot is applied, unsaved memory must not hide saved tokens.
     * After that, a dirty list is the one just handed to a client and must not be replaced
     * by the previous JSON still sitting in DataStore.
     */
    fun reconcileStoredClients(
        disk: List<ExternalApiPairedClient>,
        memory: List<ExternalApiPairedClient>,
        hydrated: Boolean,
        dirty: Boolean,
    ): List<ExternalApiPairedClient> = when {
        !hydrated -> mergeUnsavedClients(disk, memory)
        dirty -> memory
        else -> mergeNewerUsage(disk, memory)
    }

    fun registerApprovedClient(
        clients: List<ExternalApiPairedClient>,
        clientId: String,
        clientName: String,
        accessToken: String,
        createdAtEpochMs: Long,
        packageName: String? = null,
        certSha256: String? = null,
    ): List<ExternalApiPairedClient> {
        val normalizedId = clientId.trim()
        val withoutExisting = clients.filterNot { it.clientId == normalizedId }
        return withoutExisting + ExternalApiPairedClient(
            clientId = normalizedId,
            clientName = clientName.trim(),
            tokenHash = sha256Hex(accessToken),
            createdAtEpochMs = createdAtEpochMs,
            packageName = packageName,
            certSha256 = certSha256,
        )
    }

    /** A local companion is trusted only with the certificate it had when the user confirmed it. */
    fun isLocalClientTrusted(
        clients: List<ExternalApiPairedClient>,
        clientId: String,
        packageName: String,
        certSha256: String,
    ): Boolean = clients.any {
        it.clientId == clientId &&
            it.packageName == packageName &&
            it.certSha256.equals(certSha256, ignoreCase = true)
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte ->
            "%02x".format(byte)
        }
}
