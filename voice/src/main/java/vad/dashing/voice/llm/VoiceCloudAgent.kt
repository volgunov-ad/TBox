package vad.dashing.voice.llm

import org.json.JSONObject
import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.nlu.AliasNluMatcher
import vad.dashing.voice.nlu.TextNormalizer
import vad.dashing.voice.nlu.VoiceIntent
import vad.dashing.voice.nlu.VoiceSlotParser

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
                content = VoiceAgentTools.systemPrompt(settings.allowFreeformSpeak) +
                    "\n\nКаталог:\n" + catalogBlock,
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
        if (allowClarify &&
            content.length <= FreeformSpeakLimits.SHORT_MAX_CHARS &&
            ClarifyQuestionDetector.isClarifyQuestion(content)
        ) {
            val history = messages + CloudChatMessage(role = "assistant", content = content)
            return CloudAgentOutcome.Clarify(
                question = content,
                session = ClarifySession(history = history, originalPhrase = originalPhrase),
            )
        }
        if (settings.allowFreeformSpeak) {
            return CloudAgentOutcome.Speak(truncateFreeform(content))
        }
        if (content.length <= FreeformSpeakLimits.SHORT_MAX_CHARS) {
            return CloudAgentOutcome.Speak(content)
        }
        return CloudAgentOutcome.Fallback
    }

    private fun truncateFreeform(text: String): String {
        if (text.length <= FreeformSpeakLimits.FREEFORM_MAX_CHARS) return text
        val cut = text.take(FreeformSpeakLimits.FREEFORM_MAX_CHARS)
        val lastStop = maxOf(
            cut.lastIndexOf('.'),
            cut.lastIndexOf('!'),
            cut.lastIndexOf('?'),
            cut.lastIndexOf('…'),
        )
        val body = if (lastStop > FreeformSpeakLimits.FREEFORM_MAX_CHARS / 2) {
            cut.take(lastStop + 1)
        } else {
            cut.trimEnd()
        }
        return "$body…"
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
                val slot = slotFromToolArgs(args)
                VoiceIntent.InvokeAction(
                    action = action,
                    matchedAlias = "cloud",
                    score = 900,
                    slotOverride = slot,
                )
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
        val signalId = args.optString("signalId").trim().takeIf { it.isNotBlank() }
        val bus = args.optString("bus").trim().takeIf { it.isNotBlank() }
        val propertyId = if (args.has("propertyId") && !args.isNull("propertyId")) {
            args.optInt("propertyId")
        } else {
            null
        }
        return actions.firstOrNull { action ->
            when (type) {
                "open_main_screen" -> action.type == "open_main_screen"
                "builtin" -> {
                    action.type == "builtin" && (
                        (actionType != null && action.actionType == actionType) ||
                            (signalId != null && action.signalId == signalId)
                        )
                }
                "can_command" -> {
                    action.type == "can_command" && action.write != null && (
                        (propertyId != null && action.propertyId == propertyId &&
                            (bus == null || action.bus == bus)) ||
                            (signalId != null && action.signalId == signalId)
                        )
                }
                else -> false
            }
        }
    }

    private fun slotFromToolArgs(args: JSONObject): VoiceSlotParser.Slot? {
        if (args.has("delta") && !args.isNull("delta")) {
            val delta = args.optInt("delta")
            if (delta != 0) return VoiceSlotParser.Slot.Relative(if (delta > 0) 1 else -1)
        }
        if (args.has("boolValue") && !args.isNull("boolValue")) {
            return VoiceSlotParser.Slot.Binary(args.optBoolean("boolValue"))
        }
        if (args.has("intValue") && !args.isNull("intValue")) {
            return VoiceSlotParser.Slot.AbsoluteNumber(args.optInt("intValue").toDouble())
        }
        if (args.has("value") && !args.isNull("value")) {
            val raw = args.get("value")
            when (raw) {
                is Boolean -> return VoiceSlotParser.Slot.Binary(raw)
                is Number -> return VoiceSlotParser.Slot.AbsoluteNumber(raw.toDouble())
                is String -> {
                    val trimmed = raw.trim()
                    when {
                        trimmed.equals("on", ignoreCase = true) ->
                            return VoiceSlotParser.Slot.Binary(true)
                        trimmed.equals("off", ignoreCase = true) ->
                            return VoiceSlotParser.Slot.Binary(false)
                        trimmed.equals("open", ignoreCase = true) ->
                            return VoiceSlotParser.Slot.Pulse(true)
                        trimmed.equals("close", ignoreCase = true) ->
                            return VoiceSlotParser.Slot.Pulse(false)
                        else -> {
                            val number = trimmed.replace(',', '.').toDoubleOrNull()
                            if (number != null) {
                                return VoiceSlotParser.Slot.AbsoluteNumber(number)
                            }
                        }
                    }
                }
            }
        }
        return null
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
