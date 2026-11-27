package vad.dashing.voice.llm

import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.nlu.AliasNluMatcher

/**
 * Compact RU catalog snapshot for the cloud agent system prompt.
 * Keeps token use small: id + label + a few aliases; only Voice-invokable actions.
 */
object CatalogPromptBuilder {
    private const val MAX_ALIASES = 3
    private const val MAX_SIGNALS = 120
    private const val MAX_ACTIONS = 80
    private const val MAX_AUTOMATIONS = 40

    fun build(
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): String {
        val signals = catalog.signals.take(MAX_SIGNALS).joinToString("\n") { signal ->
            val aliases = signal.voiceAliasesRu.take(MAX_ALIASES).joinToString(", ")
            buildString {
                append(signal.id)
                append('|')
                append(signal.label)
                if (aliases.isNotBlank()) {
                    append('|')
                    append(aliases)
                }
            }
        }
        val actions = catalog.actionTypes
            .filter { AliasNluMatcher.isVoiceInvokable(it) }
            .take(MAX_ACTIONS)
            .joinToString("\n") { formatAction(it) }
        val rules = automations.take(MAX_AUTOMATIONS).joinToString("\n") { auto ->
            "${auto.id}|${auto.name}"
        }
        return buildString {
            appendLine("signals (id|label|aliases):")
            appendLine(signals.ifBlank { "(none)" })
            appendLine("actions (type|actionType|label|aliases):")
            appendLine(actions.ifBlank { "(none)" })
            appendLine("automations (id|name):")
            append(rules.ifBlank { "(none)" })
        }
    }

    private fun formatAction(action: CatalogAction): String {
        val aliases = action.voiceAliasesRu.take(MAX_ALIASES).joinToString(", ")
        return listOfNotNull(
            action.type,
            action.actionType.orEmpty(),
            action.label.orEmpty(),
            aliases.takeIf { it.isNotBlank() },
        ).joinToString("|")
    }
}
