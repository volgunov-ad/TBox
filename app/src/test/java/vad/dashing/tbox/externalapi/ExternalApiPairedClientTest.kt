package vad.dashing.tbox.externalapi

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiPairedClientTest {
    @Test
    fun jsonRoundtrip_preservesClients() {
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "voice-phone",
                clientName = "TBox Voice",
                tokenHash = "abc123",
                createdAtEpochMs = 1_700_000_000_000L,
            ),
            ExternalApiPairedClient(
                clientId = "gu-assistant",
                clientName = "Голос на ГУ",
                tokenHash = "def456",
                createdAtEpochMs = 1_700_000_100_000L,
            ),
        )
        val encoded = ExternalApiPairedClient.encodeList(clients)
        val decoded = ExternalApiPairedClient.decodeList(encoded)
        assertEquals(clients, decoded)
    }

    @Test
    fun decodeList_missingLastUsed_fallsBackToCreatedAt() {
        val json = JSONArray()
            .put(
                JSONObject()
                    .put("clientId", "old")
                    .put("clientName", "Phone")
                    .put("tokenHash", "abc")
                    .put("createdAtEpochMs", 1_700_000_000_000L),
            )
        val decoded = ExternalApiPairedClient.decodeList(json.toString())
        assertEquals(1_700_000_000_000L, decoded.single().lastUsedAtEpochMs)
    }

    @Test
    fun decodeList_emptyAndBlank() {
        assertTrue(ExternalApiPairedClient.decodeList("").isEmpty())
        assertTrue(ExternalApiPairedClient.decodeList("[]").isEmpty())
    }
}
