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
    }
}
