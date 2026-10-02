package vad.dashing.tbox.externalapi

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun webPanel_whenDisabled_isHidden() {
        val router = router(webPanelEnabled = false)
        val response = router.handle("GET", ExternalApiConstants.PATH_WEB_PANEL, emptyMap(), emptyMap(), "")
        assertEquals(404, response.status)
        val health = JSONObject(router.handle("GET", ExternalApiConstants.PATH_HEALTH, emptyMap(), emptyMap(), "").body)
        assertEquals(false, health.getBoolean("webPanelEnabled"))
    }

    @Test
    fun webPanel_whenEnabled_servesClimatePageWithoutAuth() {
        val router = router(webPanelEnabled = true)
        val response = router.handle("GET", "/", emptyMap(), emptyMap(), "")
        assertEquals(200, response.status)
        assertTrue(response.contentType.startsWith("text/html"))
        assertEquals("no-store", response.headers["Cache-Control"])
        assertTrue(response.body.contains(ExternalApiClimatePanel.MARKER))
        assertTrue(response.body.contains("hvac_temperature_left"))
        assertTrue(response.body.contains("hvac_auto"))
        assertTrue(response.body.contains("hvac_sync"))
        assertTrue(response.body.contains("hvac_fan_direction"))
        assertTrue(response.body.contains("hvac_custom_mode"))
        assertTrue(response.body.contains("hu_media_volume"))
        assertTrue(response.body.contains("set_media_volume"))
        assertTrue(response.body.contains("Автомобиль"))
        assertTrue(response.body.contains("front_left_seat_mode"))
        assertTrue(response.body.contains("lang=\"ru\""))
        assertTrue(response.body.contains("var lang = \"ru\" === \"en\" ? STR.en : STR.ru;"))
        assertFalse(response.body.contains("__APP_LANG__"))
        assertFalse(response.body.contains("navigator.language"))
        assertTrue(response.body.contains("id=\"connect\""))
        assertTrue(response.body.contains("/v1/pair/request"))
        assertTrue(response.body.contains("/v1/pair/status"))
        val alias = router.handle("GET", ExternalApiConstants.PATH_WEB_PANEL_ALIAS, emptyMap(), emptyMap(), "")
        assertEquals(200, alias.status)
        val post = router.handle("POST", "/", emptyMap(), emptyMap(), "")
        assertEquals(405, post.status)
    }

    @Test
    fun webPanel_usesTheAppLanguage() {
        val english = router(webPanelEnabled = true, pageLanguage = { "en" })
        val enBody = english.handle("GET", "/", emptyMap(), emptyMap(), "").body
        assertTrue(enBody.contains("lang=\"en\""))
        assertTrue(enBody.contains("var lang = \"en\" === \"en\" ? STR.en : STR.ru;"))
        assertFalse(enBody.contains("__APP_LANG__"))

        val other = router(webPanelEnabled = true, pageLanguage = { "de" })
        val otherBody = other.handle("GET", "/", emptyMap(), emptyMap(), "").body
        assertTrue(otherBody.contains("lang=\"ru\""))
        assertTrue(otherBody.contains("var lang = \"ru\" === \"en\" ? STR.en : STR.ru;"))
    }

    @Test
    fun authenticatedRequest_reportsTheClient() {
        val token = "usage-token"
        var usedClientId: String? = null
        val router = router(
            clients = listOf(
                ExternalApiPairedClient(
                    clientId = "phone",
                    clientName = "Phone",
                    tokenHash = ExternalApiAuth.sha256Hex(token),
                    createdAtEpochMs = 1L,
                ),
            ),
            onAuthenticated = { usedClientId = it.clientId },
        )
        val response = router.handle(
            "GET",
            ExternalApiConstants.PATH_CATALOG,
            emptyMap(),
            mapOf("authorization" to "Bearer $token"),
            "",
        )
        assertEquals(200, response.status)
        assertEquals("phone", usedClientId)
    }

    private fun router(
        webPanelEnabled: Boolean = false,
        clients: List<ExternalApiPairedClient> = emptyList(),
        pageLanguage: () -> String = { "ru" },
        onAuthenticated: (ExternalApiPairedClient) -> Unit = {},
    ): ExternalApiRouter =
        ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { clients },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { emptyList() },
            runAutomationNow = { null },
            webPanelEnabled = { webPanelEnabled },
            pageLanguage = pageLanguage,
            onAuthenticated = onAuthenticated,
        )
}
