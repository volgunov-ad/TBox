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
        assertEquals(1, json.getInt("catalogVersion"))
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
