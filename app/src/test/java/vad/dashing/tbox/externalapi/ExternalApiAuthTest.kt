package vad.dashing.tbox.externalapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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
    fun revokeClient_removesMatchingId() {
        val clients = listOf(
            ExternalApiPairedClient("a", "A", "hash-a", 0L),
            ExternalApiPairedClient("b", "B", "hash-b", 0L),
        )
        val updated = ExternalApiAuth.revokeClient(clients, "a")
        assertEquals(listOf(clients[1]), updated)
    }
}
