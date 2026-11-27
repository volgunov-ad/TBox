package vad.dashing.voice.llm

import org.json.JSONObject
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.TextNormalizer
import vad.dashing.voice.nlu.VoiceIntent

data class ClarifySession(
    val history: List<CloudChatMessage>,
    val originalPhrase: String,
)

sealed class CloudAgentOutcome {
    data class Intent(val intent: VoiceIntent) : CloudAgentOutcome()
    data class Speak(val text: String) : CloudAgentOutcome()
    data class Clarify(val question: String, val session: ClarifySession) : CloudAgentOutcome()
    data object Fallback : CloudAgentOutcome()
}

/**
 * OpenAI-compatible tool-calling agent over the External API catalog.
 * Stage C: one-shot tools. Stage D: at most one clarifying follow-up.
 */
class VoiceCloudAgent(
    private val client: OpenAiCompatibleClient = OpenAiCompatibleClient(),
) {
    fun resolve(
        phrase: String,
        settings: CloudLlmSettings,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): CloudAgentOutcome {
        if (!settings.looksConfigured()) return CloudAgentOutcome.Fallback
        val trimmed = phrase.trim()
        if (trimmed.isEmpty()) return CloudAgentOutcome.Fallback

        val catalogBlock = CatalogPromptBuilder.build(catalog, automations)
        val messages = listOf(
            CloudChatMessage(
                role = "system",
                content = VoiceAgentTools.systemPrompt + "\n\nКаталог:\n" + catalogBlock,
            ),
            CloudChatMessage(role = "user", content = trimmed),
        )
        return completeRound(
            messages = messages,
            settings = settings,
            catalog = catalog,
            automations = automations,
            originalPhrase = trimmed,
            allowClarify = true,
        )
    }

    /**
     * Second (and last) cloud round after [CloudAgentOutcome.Clarify].
     * Never opens another clarify — Intent, Speak, or Fallback only.
     */
    fun continueClarify(
        reply: String,
        session: ClarifySession,
        settings: CloudLlmSettings,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): CloudAgentOutcome {
        if (!settings.looksConfigured()) return CloudAgentOutcome.Fallback
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) return CloudAgentOutcome.Fallback
        val messages = session.history + CloudChatMessage(role = "user", content = trimmed)
        return completeRound(
            messages = messages,
            settings = settings,
            catalog = catalog,
            automations = automations,
            originalPhrase = session.originalPhrase,
            allowClarify = false,
        )
    }

    private fun completeRound(
        messages: List<CloudChatMessage>,
        settings: CloudLlmSettings,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
        originalPhrase: String,
        allowClarify: Boolean,
    ): CloudAgentOutcome {
        val chat = client.chatCompletions(
            baseUrl = settings.baseUrl,
            apiKey = settings.apiKey,
            model = settings.model,
            messages = messages,
            tools = VoiceAgentTools.definitions(),
        ).getOrElse { return CloudAgentOutcome.Fallback }

        val toolCall = chat.message.toolCalls.firstOrNull()
        if (toolCall != null) {
            return mapToolCall(toolCall, catalog, automations)
                ?: CloudAgentOutcome.Fallback
        }
        val content = chat.message.content?.trim().orEmpty()
        if (content.isEmpty()) return CloudAgentOutcome.Fallback
        if (allowClarify && ClarifyQuestionDetector.isClarifyQuestion(content)) {
            val history = messages + CloudChatMessage(role = "assistant", content = content)
            return CloudAgentOutcome.Clarify(
                question = content,
                session = ClarifySession(history = history, originalPhrase = originalPhrase),
            )
        }
        if (content.length <= 240) {
            return CloudAgentOutcome.Speak(content)
        }
        return CloudAgentOutcome.Fallback
    }

    fun mapToolCall(
        call: CloudToolCall,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): CloudAgentOutcome.Intent? {
        val args = runCatching { JSONObject(call.argumentsJson.ifBlank { "{}" }) }
            .getOrElse { return null }
        val intent = when (call.name) {
            VoiceAgentTools.QUERY_SIGNAL -> {
                val id = args.optString("signalId").trim()
                val signal = catalog.signals.firstOrNull { it.id == id } ?: return null
                VoiceIntent.QuerySignal(signal, matchedAlias = "cloud", score = 900)
            }
            VoiceAgentTools.INVOKE_ACTION -> {
                val action = resolveAction(args, catalog.actionTypes) ?: return null
                if (!AliasNluMatcher.isVoiceInvokable(action)) return null
                VoiceIntent.InvokeAction(action, matchedAlias = "cloud", score = 900)
            }
            VoiceAgentTools.RUN_AUTOMATION -> {
                val automation = resolveAutomation(args, automations) ?: return null
                VoiceIntent.RunAutomation(automation, matchedAlias = "cloud", score = 900)
            }
            else -> return null
        }
        return CloudAgentOutcome.Intent(intent)
    }

    private fun resolveAction(args: JSONObject, actions: List<CatalogAction>): CatalogAction? {
        val type = args.optString("type").trim().ifBlank { "builtin" }
        val actionType = args.optString("actionType").trim().takeIf { it.isNotBlank() }
        return actions.firstOrNull { action ->
            when (type) {
                "open_main_screen" -> action.type == "open_main_screen"
                "builtin" -> action.type == "builtin" && action.actionType == actionType
                else -> false
            }
        }
    }

    private fun resolveAutomation(
        args: JSONObject,
        automations: List<AutomationSummary>,
    ): AutomationSummary? {
        val id = args.optString("automationId").trim()
        if (id.isNotBlank()) {
            automations.firstOrNull { it.id == id }?.let { return it }
        }
        val name = TextNormalizer.normalize(args.optString("name"))
        if (name.isBlank()) return null
        return automations.firstOrNull { TextNormalizer.normalize(it.name) == name }
    }
}
