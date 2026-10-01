package vad.dashing.tbox.externalapi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.automation.AutomationActionResult

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ExternalApiRouterTest {
    @Test
    fun health_withoutAuth_returnsMinimalPayload() {
        val router = ExternalApiRouter(
            appVersion = "0.18.1-test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { emptyList() },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle("GET", ExternalApiConstants.PATH_HEALTH, emptyMap(), emptyMap(), "")
        assertEquals(200, response.status)
        val json = JSONObject(response.body)
        assertTrue(json.getBoolean("ok"))
        assertEquals(1, json.getInt("apiVersion"))
        assertEquals(3, json.getInt("catalogVersion"))
        assertTrue(json.getBoolean("serverEnabled"))
        assertEquals("0.18.1-test", json.getString("appVersion"))
    }

    @Test
    fun pairRequest_whenInactive_returns403() {
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { emptyList() },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle(
            "POST",
            ExternalApiConstants.PATH_PAIR_REQUEST,
            emptyMap(),
            emptyMap(),
            """{"clientId":"id","clientName":"Name"}""",
        )
        assertEquals(403, response.status)
        val error = JSONObject(response.body).getJSONObject("error")
        assertEquals("pairing_inactive", error.getString("code"))
    }

    @Test
    fun catalog_requiresBearer() {
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { emptyList() },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { _ ->
                listOf(AutomationActionResult.ok())
            },
            runAutomationNow = { null },
        )
        val response = router.handle("GET", ExternalApiConstants.PATH_CATALOG, emptyMap(), emptyMap(), "")
        assertEquals(401, response.status)
    }

    @Test
    fun catalog_withAuth_includesNonEmptyVoiceAliasesRu() {
        val token = "catalog-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "voice",
                clientName = "Voice",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 1L,
            ),
        )
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { clients },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle(
            "GET",
            ExternalApiConstants.PATH_CATALOG,
            emptyMap(),
            mapOf("authorization" to "Bearer $token"),
            "",
        )
        assertEquals(200, response.status)
        val json = JSONObject(response.body)
        assertEquals(3, json.getInt("catalogVersion"))
        val signals = json.getJSONArray("signals")
        assertTrue(signals.length() > 0)
        var foundOutside = false
        for (i in 0 until signals.length()) {
            val signal = signals.getJSONObject(i)
            val aliases = signal.getJSONArray("voiceAliasesRu")
            assertTrue(
                "signal ${signal.getString("id")} needs aliases",
                aliases.length() > 0,
            )
            if (signal.getString("id") == "outside_temperature") {
                foundOutside = true
                val joined = buildString {
                    for (j in 0 until aliases.length()) {
                        append(aliases.getString(j)).append('\n')
                    }
                }
                assertTrue(joined.contains("сколько градусов на улице"))
            }
        }
        assertTrue(foundOutside)

        val actions = json.getJSONArray("actionTypes")
        assertTrue(actions.length() > 0)
        val first = actions.getJSONObject(0)
        assertTrue(first.getJSONArray("voiceAliasesRu").length() > 0)
    }

    @Test
    fun pairStatus_afterApproveAndStop_stillReturnsAccessToken() {
        val session = ExternalApiPairingSession()
        session.startPairing()
        val pending = session.submitPairRequest("pc-tools", "TBox API tools (PC)", "tools")
        session.approveRequest(pending.requestId, "plain-token-value")
        session.stopPairing()

        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = session,
            pairedClients = { emptyList() },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle(
            "GET",
            ExternalApiConstants.PATH_PAIR_STATUS,
            mapOf("requestId" to pending.requestId),
            emptyMap(),
            "",
        )
        assertEquals(200, response.status)
        val json = JSONObject(response.body)
        assertEquals("approved", json.getString("status"))
        assertEquals("plain-token-value", json.getString("accessToken"))
        assertEquals("pc-tools", json.getString("clientId"))
    }
}
