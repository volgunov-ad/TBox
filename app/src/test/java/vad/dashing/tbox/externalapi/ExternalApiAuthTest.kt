package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun isLocalClientTrusted_needsSamePackageAndCertificate() {
        val clients = ExternalApiAuth.registerApprovedClient(
            clients = emptyList(),
            clientId = "local:vad.dashing.mqtt",
            clientName = "TBox MQTT",
            accessToken = "token",
            createdAtEpochMs = 0L,
            packageName = "vad.dashing.mqtt",
            certSha256 = "aa11",
        )
        val id = "local:vad.dashing.mqtt"
        assertTrue(ExternalApiAuth.isLocalClientTrusted(clients, id, "vad.dashing.mqtt", "AA11"))
        assertFalse(ExternalApiAuth.isLocalClientTrusted(clients, id, "vad.dashing.mqtt", "bb22"))
        assertFalse(ExternalApiAuth.isLocalClientTrusted(clients, id, "vad.dashing.voice", "aa11"))
        assertFalse(
            ExternalApiAuth.isLocalClientTrusted(
                ExternalApiAuth.revokeClient(clients, id),
                id,
                "vad.dashing.mqtt",
                "aa11",
            ),
        )
    }

    @Test
    fun isLocalClientTrusted_ignoresHttpPairedClientWithSameId() {
        val clients = ExternalApiAuth.registerApprovedClient(
            clients = emptyList(),
            clientId = "local:vad.dashing.mqtt",
            clientName = "Spoofed",
            accessToken = "token",
            createdAtEpochMs = 0L,
        )
        assertFalse(
            ExternalApiAuth.isLocalClientTrusted(clients, "local:vad.dashing.mqtt", "vad.dashing.mqtt", "aa11"),
        )
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
    fun mergeUnsavedClients_keepsDiskTokensAndANewerMemoryToken() {
        val disk = listOf(client("phone", 10L), client("launcher", 10L))
        val rotated = client("launcher", 50L).copy(
            tokenHash = "hash-launcher-new",
            createdAtEpochMs = 50L,
        )
        val fresh = client("mqtt", 40L).copy(createdAtEpochMs = 40L)
        val merged = ExternalApiAuth.mergeUnsavedClients(disk, listOf(rotated, fresh))
        assertEquals(listOf("phone", "launcher", "mqtt"), merged.map { it.clientId })
        assertEquals("hash-phone", merged[0].tokenHash)
        assertEquals("hash-launcher-new", merged[1].tokenHash)
        assertEquals("hash-mqtt", merged[2].tokenHash)
    }

    @Test
    fun reconcileStoredClients_doesNotHideSavedTokensBeforeHydrate() {
        val disk = listOf(client("phone", 10L))
        val reconciled = ExternalApiAuth.reconcileStoredClients(
            disk = disk,
            memory = emptyList(),
            hydrated = false,
            dirty = false,
        )
        assertEquals(listOf("phone"), reconciled.map { it.clientId })
    }

    @Test
    fun reconcileStoredClients_keepsDirtyMemoryOverStaleDisk() {
        val disk = listOf(client("launcher", 10L))
        val memory = listOf(
            client("launcher", 80L).copy(
                tokenHash = "hash-launcher-new",
                createdAtEpochMs = 80L,
            ),
        )
        val reconciled = ExternalApiAuth.reconcileStoredClients(
            disk = disk,
            memory = memory,
            hydrated = true,
            dirty = true,
        )
        assertEquals("hash-launcher-new", reconciled.single().tokenHash)
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
