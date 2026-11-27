package vad.dashing.voice.llm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val agent = VoiceCloudAgent()

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
}
