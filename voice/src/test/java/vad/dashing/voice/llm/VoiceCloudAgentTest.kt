package vad.dashing.voice.llm

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.api.CatalogSignal
import vad.dashing.voice.nlu.VoiceIntent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoiceCloudAgentTest {
    private lateinit var server: MockWebServer
    private lateinit var agent: VoiceCloudAgent

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        agent = VoiceCloudAgent(OpenAiCompatibleClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val catalog = ApiCatalog(
        catalogVersion = 11,
        signals = listOf(
            CatalogSignal(
                id = "outside_temperature",
                label = "Температура снаружи",
                unit = "°C",
                valueType = "number",
                sources = listOf("head_unit"),
                voiceAliasesRu = listOf("на улице"),
            ),
        ),
        actionTypes = listOf(
            CatalogAction(
                type = "builtin",
                actionType = "media_next",
                label = "Следующий трек",
                voiceAliasesRu = listOf("следующий"),
            ),
            CatalogAction(
                type = "can_command",
                label = "Климат",
                voiceAliasesRu = listOf("включи климат"),
            ),
        ),
    )

    private val automations = listOf(
        AutomationSummary(
            id = "f9c025e4-b8c7-4f72-b593-a2ac0e3b4102",
            name = "климат",
            enabled = true,
        ),
    )

    @Test
    fun mapToolCall_querySignal() {
        val outcome = agent.mapToolCall(
            CloudToolCall("1", VoiceAgentTools.QUERY_SIGNAL, """{"signalId":"outside_temperature"}"""),
            catalog,
            automations,
        )
        assertTrue(outcome is CloudAgentOutcome.Intent)
        val intent = (outcome as CloudAgentOutcome.Intent).intent
        assertTrue(intent is VoiceIntent.QuerySignal)
        assertEquals("outside_temperature", (intent as VoiceIntent.QuerySignal).signal.id)
    }

    @Test
    fun mapToolCall_rejectsUnknownSignal() {
        val outcome = agent.mapToolCall(
            CloudToolCall("1", VoiceAgentTools.QUERY_SIGNAL, """{"signalId":"nope"}"""),
            catalog,
            automations,
        )
        assertEquals(null, outcome)
    }

    @Test
    fun mapToolCall_invokeBuiltin() {
        val outcome = agent.mapToolCall(
            CloudToolCall(
                "1",
                VoiceAgentTools.INVOKE_ACTION,
                """{"type":"builtin","actionType":"media_next"}""",
            ),
            catalog,
            automations,
        )
        assertTrue(outcome?.intent is VoiceIntent.InvokeAction)
        assertEquals(
            "media_next",
            (outcome!!.intent as VoiceIntent.InvokeAction).action.actionType,
        )
    }

    @Test
    fun mapToolCall_rejectsCanCommand() {
        val outcome = agent.mapToolCall(
            CloudToolCall(
                "1",
                VoiceAgentTools.INVOKE_ACTION,
                """{"type":"can_command","actionType":"hvac"}""",
            ),
            catalog,
            automations,
        )
        assertEquals(null, outcome)
    }

    @Test
    fun mapToolCall_runAutomationByName() {
        val outcome = agent.mapToolCall(
            CloudToolCall("1", VoiceAgentTools.RUN_AUTOMATION, """{"name":"Климат"}"""),
            catalog,
            automations,
        )
        assertTrue(outcome?.intent is VoiceIntent.RunAutomation)
        assertEquals("климат", (outcome!!.intent as VoiceIntent.RunAutomation).automation.name)
    }

    @Test
    fun resolve_fallsBackWhenNotConfigured() {
        val outcome = agent.resolve(
            phrase = "сколько градусов",
            settings = CloudLlmSettings(),
            catalog = catalog,
            automations = automations,
        )
        assertEquals(CloudAgentOutcome.Fallback, outcome)
    }

    @Test
    fun clarifyThenContinue_resolvesTool() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "role": "assistant",
                      "content": "Какая температура: на улице или в салоне?"
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "tool_calls",
                    "message": {
                      "role": "assistant",
                      "content": null,
                      "tool_calls": [{
                        "id": "call_1",
                        "type": "function",
                        "function": {
                          "name": "query_signal",
                          "arguments": "{\"signalId\":\"outside_temperature\"}"
                        }
                      }]
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        val settings = configuredSettings()
        val first = agent.resolve("температура", settings, catalog, automations)
        assertTrue(first is CloudAgentOutcome.Clarify)
        first as CloudAgentOutcome.Clarify
        assertTrue(first.question.contains("улице"))

        val second = agent.continueClarify("на улице", first.session, settings, catalog, automations)
        assertTrue(second is CloudAgentOutcome.Intent)
        val intent = (second as CloudAgentOutcome.Intent).intent as VoiceIntent.QuerySignal
        assertEquals("outside_temperature", intent.signal.id)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun continueClarify_doesNotOpenSecondClarify() {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {
                      "role": "assistant",
                      "content": "Какой именно сигнал?"
                    }
                  }]
                }
                """.trimIndent(),
            ),
        )
        val settings = configuredSettings()
        val session = ClarifySession(
            history = listOf(
                CloudChatMessage(role = "system", content = "sys"),
                CloudChatMessage(role = "user", content = "температура"),
                CloudChatMessage(role = "assistant", content = "Какая температура?"),
            ),
            originalPhrase = "температура",
        )
        val outcome = agent.continueClarify("ещё раз", session, settings, catalog, automations)
        // Second round may Speak the question text, but must not return Clarify.
        assertFalse(outcome is CloudAgentOutcome.Clarify)
        assertTrue(outcome is CloudAgentOutcome.Speak)
    }

    @Test
    fun clarifyDetector_recognizesQuestions() {
        assertTrue(ClarifyQuestionDetector.isClarifyQuestion("Какой сигнал?"))
        assertTrue(ClarifyQuestionDetector.isClarifyQuestion("Уточните правило"))
        assertFalse(ClarifyQuestionDetector.isClarifyQuestion("Не понял запрос"))
    }

    @Test
    fun freeformSpeak_allowsLongAnswer() {
        val story = "А. ".repeat(200).trim() // well over SHORT_MAX_CHARS
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {"role":"assistant","content":${org.json.JSONObject.quote(story)}}
                  }]
                }
                """.trimIndent(),
            ),
        )
        val withFreeform = configuredSettings().copy(allowFreeformSpeak = true)
        val outcome = agent.resolve(
            "расскажи историю Нижнего Новгорода",
            withFreeform,
            catalog,
            automations,
        )
        assertTrue(outcome is CloudAgentOutcome.Speak)
        assertTrue((outcome as CloudAgentOutcome.Speak).text.length > FreeformSpeakLimits.SHORT_MAX_CHARS)
    }

    @Test
    fun withoutFreeform_longAnswerFallsBack() {
        val story = "А. ".repeat(200).trim()
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "choices": [{
                    "finish_reason": "stop",
                    "message": {"role":"assistant","content":${org.json.JSONObject.quote(story)}}
                  }]
                }
                """.trimIndent(),
            ),
        )
        val outcome = agent.resolve(
            "расскажи историю",
            configuredSettings().copy(allowFreeformSpeak = false),
            catalog,
            automations,
        )
        assertEquals(CloudAgentOutcome.Fallback, outcome)
    }

    @Test
    fun catalogPrompt_includesSignalAndSkipsCanCommand() {
        val text = CatalogPromptBuilder.build(catalog, automations)
        assertTrue(text.contains("outside_temperature"))
        assertTrue(text.contains("media_next"))
        assertTrue(!text.contains("can_command"))
        assertTrue(text.contains("климат"))
    }

    @Test
    fun parseChatCompletion_readsToolCalls() {
        val raw = """
            {
              "choices": [{
                "finish_reason": "tool_calls",
                "message": {
                  "role": "assistant",
                  "content": null,
                  "tool_calls": [{
                    "id": "call_1",
                    "type": "function",
                    "function": {
                      "name": "query_signal",
                      "arguments": "{\"signalId\":\"outside_temperature\"}"
                    }
                  }]
                }
              }]
            }
        """.trimIndent()
        val parsed = OpenAiCompatibleClient.parseChatCompletion(raw)
        assertEquals(1, parsed.message.toolCalls.size)
        assertEquals(VoiceAgentTools.QUERY_SIGNAL, parsed.message.toolCalls[0].name)
        assertTrue(parsed.message.toolCalls[0].argumentsJson.contains("outside_temperature"))
    }

    private fun configuredSettings(): CloudLlmSettings {
        val base = server.url("v1").toString().trimEnd('/')
        return CloudLlmSettings(
            enabled = true,
            preferCloud = true,
            allowSendPhrases = true,
            baseUrl = base,
            apiKey = "test-key",
            model = "gpt-test",
        )
    }
}
