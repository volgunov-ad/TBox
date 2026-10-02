package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ExternalApiAuthTest {
    @Test
    fun sha256Hex_isStableHex() {
        val hash = ExternalApiAuth.sha256Hex("test-token")
        assertEquals(64, hash.length)
        assertEquals(hash, ExternalApiAuth.sha256Hex("test-token"))
    }

    @Test
    fun findClientByToken_matchesHashNotPlaintext() {
        val token = "my-secret-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "client-1",
                clientName = "Client",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 0L,
            ),
        )
        assertNotNull(ExternalApiAuth.findClientByToken(clients, token))
        assertNull(ExternalApiAuth.findClientByToken(clients, "wrong"))
    }

    @Test
    fun registerApprovedClient_replacesSameClientId() {
        val first = ExternalApiAuth.registerApprovedClient(
            clients = emptyList(),
            clientId = "manual-1",
            clientName = "Phone",
            accessToken = "token-a",
            createdAtEpochMs = 1L,
        )
        val second = ExternalApiAuth.registerApprovedClient(
            clients = first,
            clientId = "manual-1",
            clientName = "Phone 2",
            accessToken = "token-b",
            createdAtEpochMs = 2L,
        )
        assertEquals(1, second.size)
        assertEquals("Phone 2", second[0].clientName)
        assertNotNull(ExternalApiAuth.findClientByToken(second, "token-b"))
        assertNull(ExternalApiAuth.findClientByToken(second, "token-a"))
        assertEquals(2L, second[0].lastUsedAtEpochMs)
    }

    @Test
    fun retainRecentlyUsed_dropsTokensIdleMoreThanSixMonths() {
        val zone = ZoneId.of("UTC")
        val now = ZonedDateTime.of(2026, 10, 2, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
        val fresh = Instant.ofEpochMilli(now).atZone(zone).minusDays(10).toInstant().toEpochMilli()
        val boundary = ExternalApiAuth.idleCutoffEpochMs(now, zone)
        val stale = Instant.ofEpochMilli(boundary).atZone(zone).minusDays(1).toInstant().toEpochMilli()
        val clients = listOf(
            client("fresh", fresh),
            client("boundary", boundary),
            client("stale", stale),
        )
        val kept = ExternalApiAuth.retainRecentlyUsed(clients, now, zone).map { it.clientId }
        assertEquals(listOf("fresh", "boundary"), kept)
    }

    @Test
    fun mergeNewerUsage_keepsInMemoryUseAndIncomingMembership() {
        val disk = listOf(client("kept", 10L), client("new", 20L))
        val memory = listOf(client("kept", 50L), client("revoked-only-in-memory", 40L))
        val merged = ExternalApiAuth.mergeNewerUsage(disk, memory)
        assertEquals(listOf("kept", "new"), merged.map { it.clientId })
        assertEquals(50L, merged[0].lastUsedAtEpochMs)
    }

    private fun client(id: String, lastUsedAtEpochMs: Long) = ExternalApiPairedClient(
        clientId = id,
        clientName = id,
        tokenHash = "hash-$id",
        createdAtEpochMs = 1L,
        lastUsedAtEpochMs = lastUsedAtEpochMs,
    )
}
