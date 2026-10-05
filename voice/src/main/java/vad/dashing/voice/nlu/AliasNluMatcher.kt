package vad.dashing.voice.nlu

import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import vad.dashing.voice.api.CatalogAction
import vad.dashing.voice.api.CatalogSignal

object TextNormalizer {
    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(Regex("[^\\p{L}\\p{N}\\s]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
}

sealed class VoiceIntent {
    data class QuerySignal(
        val signal: CatalogSignal,
        val matchedAlias: String,
        val score: Int,
    ) : VoiceIntent()

    data class InvokeAction(
        val action: CatalogAction,
        val matchedAlias: String,
        val score: Int,
    ) : VoiceIntent()

    data class RunAutomation(
        val automation: AutomationSummary,
        val matchedAlias: String,
        val score: Int,
    ) : VoiceIntent()

    data object Unknown : VoiceIntent()
}

class AliasNluMatcher(
    private val minAliasLength: Int = 3,
    private val minScore: Int = 30,
) {
    fun match(
        phrase: String,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): VoiceIntent {
        val normalized = TextNormalizer.normalize(phrase)
        if (normalized.isEmpty()) return VoiceIntent.Unknown

        val automationHit = bestAutomation(normalized, automations)
        val signalHit = bestSignal(normalized, catalog.signals)
        val actionHit = bestAction(normalized, catalog.actionTypes)

        // Priority: run automation → invoke → query (plan §5).
        val ranked = listOfNotNull(automationHit, actionHit, signalHit)
            .sortedByDescending { it.second }
        val best = ranked.firstOrNull() ?: return VoiceIntent.Unknown
        if (best.second < minScore) return VoiceIntent.Unknown
        return best.first
    }

    private fun bestSignal(
        phrase: String,
        signals: List<CatalogSignal>,
    ): Pair<VoiceIntent, Int>? {
        var best: Pair<VoiceIntent.QuerySignal, Int>? = null
        for (signal in signals) {
            val aliases = (signal.voiceAliasesRu + signal.label).map { TextNormalizer.normalize(it) }
            for (alias in aliases) {
                val score = scoreAlias(phrase, alias) ?: continue
                val candidate = VoiceIntent.QuerySignal(signal, alias, score)
                if (best == null || score > best.second) {
                    best = candidate to score
                }
            }
        }
        return best
    }

    private fun bestAction(
        phrase: String,
        actions: List<CatalogAction>,
    ): Pair<VoiceIntent, Int>? {
        var best: Pair<VoiceIntent.InvokeAction, Int>? = null
        for (action in actions) {
            val labelAliases = listOfNotNull(action.label, action.actionType?.replace('_', ' '))
            val aliases = (action.voiceAliasesRu + labelAliases).map { TextNormalizer.normalize(it) }
            for (alias in aliases) {
                val score = scoreAlias(phrase, alias) ?: continue
                val candidate = VoiceIntent.InvokeAction(action, alias, score)
                if (best == null || score > best.second) {
                    best = candidate to score
                }
            }
        }
        return best
    }

    private fun bestAutomation(
        phrase: String,
        automations: List<AutomationSummary>,
    ): Pair<VoiceIntent, Int>? {
        var best: Pair<VoiceIntent.RunAutomation, Int>? = null
        for (automation in automations) {
            val alias = TextNormalizer.normalize(automation.name)
            val score = scoreAlias(phrase, alias) ?: continue
            // Prefer phrases that look like a run command.
            val boost = if (
                phrase.contains("запусти") ||
                phrase.contains("включи правило") ||
                phrase.contains("выполни")
            ) {
                40
            } else {
                0
            }
            val total = score + boost
            val candidate = VoiceIntent.RunAutomation(automation, alias, total)
            if (best == null || total > best.second) {
                best = candidate to total
            }
        }
        return best
    }

    private fun scoreAlias(phrase: String, alias: String): Int? {
        if (alias.length < minAliasLength) return null
        return when {
            phrase == alias -> 1000 + alias.length
            phrase.contains(alias) -> 100 + alias.length * 3
            alias.contains(phrase) && phrase.length >= minAliasLength -> 50 + phrase.length
            else -> tokenOverlapScore(phrase, alias)
        }
    }

    private fun tokenOverlapScore(phrase: String, alias: String): Int? {
        val phraseTokens = phrase.split(' ').filter { it.length >= 2 }.toSet()
        val aliasTokens = alias.split(' ').filter { it.length >= 2 }.toSet()
        if (phraseTokens.isEmpty() || aliasTokens.isEmpty()) return null
        val overlap = phraseTokens.intersect(aliasTokens)
        if (overlap.isEmpty()) return null
        if (overlap.size * 2 < aliasTokens.size) return null
        return overlap.sumOf { it.length } * 4
    }
}
