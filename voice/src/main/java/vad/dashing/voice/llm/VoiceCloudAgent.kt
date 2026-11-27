package vad.dashing.voice.llm

import org.json.JSONObject
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.TextNormalizer
import vad.dashing.voice.nlu.VoiceIntent

sealed class CloudAgentOutcome {
    data class Intent(val intent: VoiceIntent) : CloudAgentOutcome()
    data class Speak(val text: String) : CloudAgentOutcome()
    data object Fallback : CloudAgentOutcome()
}

/**
 * One-shot OpenAI-compatible tool-calling agent over the External API catalog (stage C).
 * Does not call Monitor itself — returns a [VoiceIntent] (or short speak / fallback).
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
        if (content.isNotEmpty() && content.length <= 240) {
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
