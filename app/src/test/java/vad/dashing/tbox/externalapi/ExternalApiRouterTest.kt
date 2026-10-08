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
import vad.dashing.tbox.automation.AutomationSignalSource
import vad.dashing.tbox.automation.AutomationSignalValue
import vad.dashing.tbox.automation.AutomationSignalValueType

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
            headUnitPlatform = { "android9" },
        )
        val response = router.handle("GET", ExternalApiConstants.PATH_HEALTH, emptyMap(), emptyMap(), "")
        assertEquals(200, response.status)
        val json = JSONObject(response.body)
        assertTrue(json.getBoolean("ok"))
        assertEquals(1, json.getInt("apiVersion"))
        assertEquals(4, json.getInt("catalogVersion"))
        assertTrue(json.getBoolean("serverEnabled"))
        assertEquals("0.18.1-test", json.getString("appVersion"))
        assertEquals("android9", json.getString("headUnit"))
    }

    @Test
    fun windowSignalJson_carriesOpenFlag() {
        fun json(id: String, state: String) = ExternalApiSignalReader.signalToJson(
            id = id,
            source = AutomationSignalSource.HEAD_UNIT,
            valueType = AutomationSignalValueType.STATE,
            value = AutomationSignalValue.State(state),
        )
        assertTrue(json("window_front_left", "open").getBoolean("open"))
        assertTrue(json("window_front_left", "80%").getBoolean("open"))
        assertEquals(false, json("window_rear_right", "0%").getBoolean("open"))
        assertEquals(false, json("hvac_auto", "on").has("open"))
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
        assertEquals(4, json.getInt("catalogVersion"))
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
    fun actionsInvoke_malformedJson_returns400() {
        val token = "actions-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "voice",
                clientName = "Voice",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 1L,
            ),
        )
        val router = router(clients = clients)
        val response = router.handle(
            "POST",
            ExternalApiConstants.PATH_ACTIONS_INVOKE,
            emptyMap(),
            mapOf("authorization" to "Bearer $token"),
            "not-json{",
        )
        assertEquals(400, response.status)
        assertEquals(
            "invalid_request",
            JSONObject(response.body).getJSONObject("error").getString("code"),
        )
    }

    @Test
    fun actionsInvoke_outOfRangeDelay_isRejectedBeforeExecution() {
        val token = "delay-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "voice",
                clientName = "Voice",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 1L,
            ),
        )
        var executed = false
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { clients },
            dangerousEnabled = { true },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { executed = true; emptyList() },
            runAutomationNow = { null },
        )
        val response = router.handle(
            "POST",
            ExternalApiConstants.PATH_ACTIONS_INVOKE,
            emptyMap(),
            mapOf("authorization" to "Bearer $token"),
            """{"actions":[{"type":"delay","durationMillis":${Long.MAX_VALUE}}]}""",
        )
        assertEquals(400, response.status)
        assertFalse(executed)
    }

    @Test
    fun actionsInvoke_mediaCommandWithoutPlayer_targetsActiveSession() {
        val token = "media-token"
        val clients = listOf(
            ExternalApiPairedClient(
                clientId = "panel",
                clientName = "Panel",
                tokenHash = ExternalApiAuth.sha256Hex(token),
                createdAtEpochMs = 1L,
            ),
        )
        var executed = 0
        val router = ExternalApiRouter(
            appVersion = "test",
            serverEnabled = { true },
            pairingSession = ExternalApiPairingSession(),
            pairedClients = { clients },
            dangerousEnabled = { false },
            signalReader = ExternalApiSignalReader(),
            automationsProvider = { emptyList() },
            executeActions = { actions -> executed += actions.size; emptyList() },
            runAutomationNow = { null },
        )
        listOf("media_play_pause", "media_previous", "media_next").forEach { type ->
            val response = router.handle(
                "POST",
                ExternalApiConstants.PATH_ACTIONS_INVOKE,
                emptyMap(),
                mapOf("authorization" to "Bearer $token"),
                """{"actions":[{"type":"builtin","actionType":"$type","intValue":0,"stringValue":"","boolValue":false}]}""",
            )
            assertEquals(type, 200, response.status)
        }
        assertEquals(3, executed)
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
        assertTrue(response.body.contains("hvac_recirculation"))
        assertTrue(response.body.contains("hvac_front_off"))
        assertTrue(response.body.contains("hvac_power"))
        assertTrue(response.body.contains("toggleFront"))
        assertTrue(response.body.contains("Рециркуляция"))
        assertTrue(response.body.contains("Кондиционер"))
        assertTrue(response.body.contains("hvac_fan_direction"))
        assertTrue(response.body.contains("hvac_custom_mode"))
        assertTrue(response.body.contains("hu_media_volume"))
        assertTrue(response.body.contains("set_media_volume"))
        assertTrue(response.body.contains("media_title"))
        assertTrue(response.body.contains("media_artist"))
        assertTrue(response.body.contains("media_position_ms"))
        assertTrue(response.body.contains("media_play_pause"))
        assertTrue(response.body.contains("toggleMediaMute"))
        assertTrue(response.body.contains("volume-controls"))
        assertTrue(response.body.contains("Автомобиль"))
        assertTrue(response.body.contains("modeEco: \"Эко\""))
        assertTrue(response.body.contains("modeEco: \"ECO\""))
        assertTrue(response.body.contains("front_left_seat_mode"))
        assertTrue(response.body.contains("window_front_left"))
        assertTrue(response.body.contains("comfort_open"))
        assertTrue(response.body.contains("\"sunroof\""))
        assertTrue(response.body.contains("\"sunshade\""))
        assertTrue(response.body.contains("data-tab"))
        assertTrue(response.body.contains("tbox.panel.page"))
        assertTrue(response.body.contains("/v1/health"))
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
