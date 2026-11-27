package vad.dashing.voice.nlu

import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary
import kotlin.math.sqrt

/**
 * Offline «tiny classifier»: character n-gram cosine similarity against catalog
 * aliases + intent priors from RU cues. No native model — safe for A9/A10 HU RAM.
 *
 * A future ONNX/GGUF backend can implement [LocalIntentClassifier] the same way.
 */
class NgramLocalIntentClassifier(
    private val minSimilarity: Double = 0.28,
    private val ngramSize: Int = 3,
) : LocalIntentClassifier {
    override fun classify(
        phrase: String,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): VoiceIntent {
        val normalized = softNormalize(phrase)
        if (normalized.length < 3) return VoiceIntent.Unknown

        val phraseVec = ngramVector(normalized)
        val preferQuery = AliasNluMatcher.hasQuestionCue(normalized) || hasLooseQuestionCue(normalized)
        val preferRun = AliasNluMatcher.hasRunCue(normalized)
        val preferInvoke = hasActionCue(normalized) && !preferRun

        var best: Scored? = null

        for (signal in catalog.signals) {
            val aliases = (signal.voiceAliasesRu + signal.label).map { softNormalize(it) }
            for (alias in aliases) {
                if (alias.length < 3) continue
                val sim = cosine(phraseVec, ngramVector(alias))
                if (sim < minSimilarity) continue
                var score = sim
                if (preferQuery) score += 0.12
                if (preferRun || preferInvoke) score -= 0.05
                val candidate = Scored(
                    VoiceIntent.QuerySignal(signal, alias, (score * 1000).toInt()),
                    score,
                )
                if (best == null || candidate.score > best.score) best = candidate
            }
        }

        for (action in catalog.actionTypes) {
            if (!AliasNluMatcher.isVoiceInvokable(action)) continue
            val labelAliases = listOfNotNull(action.label, action.actionType?.replace('_', ' '))
            val aliases = (action.voiceAliasesRu + labelAliases).map { softNormalize(it) }
            for (alias in aliases) {
                if (alias.length < 3) continue
                val sim = cosine(phraseVec, ngramVector(alias))
                if (sim < minSimilarity) continue
                var score = sim
                if (preferInvoke) score += 0.14
                if (preferQuery) score -= 0.06
                val candidate = Scored(
                    VoiceIntent.InvokeAction(action, alias, (score * 1000).toInt()),
                    score,
                )
                if (best == null || candidate.score > best.score) best = candidate
            }
        }

        for (automation in automations) {
            val alias = softNormalize(automation.name)
            if (alias.length < 3) continue
            val sim = cosine(phraseVec, ngramVector(alias))
            // Automations often have short names — also try phrase-contains-name.
            val containsBoost = if (normalized.contains(alias)) 0.2 else 0.0
            val combined = maxOf(sim, if (containsBoost > 0) 0.35 + containsBoost else 0.0)
            if (combined < minSimilarity) continue
            var score = combined
            if (preferRun) score += 0.18
            if (preferQuery) score -= 0.08
            val candidate = Scored(
                VoiceIntent.RunAutomation(automation, alias, (score * 1000).toInt()),
                score,
            )
            if (best == null || candidate.score > best.score) best = candidate
        }

        val winner = best ?: return VoiceIntent.Unknown
        if (winner.score < minSimilarity) return VoiceIntent.Unknown
        return winner.intent
    }

    private data class Scored(val intent: VoiceIntent, val score: Double)

    companion object {
        private val STOP_WORDS = setOf(
            "скажи", "подскажи", "пожалуйста", "мне", "сейчас", "вообще",
            "просто", "давай", "ну", "а", "и", "то", "бы", "ли", "это",
            "там", "тут", "очень", "можно", "надо",
        )

        fun softNormalize(raw: String): String {
            val base = TextNormalizer.normalize(raw)
            if (base.isEmpty()) return ""
            return base.split(' ')
                .filter { it.length >= 2 && it !in STOP_WORDS }
                .joinToString(" ")
                .trim()
        }

        fun hasLooseQuestionCue(phrase: String): Boolean =
            phrase.contains("остал") ||
                phrase.contains("сколько") ||
                phrase.contains("каков") ||
                phrase.startsWith("что ") ||
                phrase.contains(" покажи")

        fun hasActionCue(phrase: String): Boolean {
            val cues = listOf(
                "следующ", "предыдущ", "пауза", "стоп", "играй", "плей",
                "лайк", "дальше", "назад", "некст", "продолжи",
            )
            return cues.any { phrase.contains(it) }
        }

        fun ngramVector(text: String, n: Int = 3): Map<String, Double> {
            val compact = text.replace(" ", "")
            if (compact.length < n) {
                return if (compact.isEmpty()) emptyMap() else mapOf(compact to 1.0)
            }
            val counts = LinkedHashMap<String, Double>()
            for (i in 0..compact.length - n) {
                val gram = compact.substring(i, i + n)
                counts[gram] = (counts[gram] ?: 0.0) + 1.0
            }
            return counts
        }

        fun cosine(a: Map<String, Double>, b: Map<String, Double>): Double {
            if (a.isEmpty() || b.isEmpty()) return 0.0
            var dot = 0.0
            var normA = 0.0
            var normB = 0.0
            for ((k, va) in a) {
                normA += va * va
                val vb = b[k]
                if (vb != null) dot += va * vb
            }
            for (vb in b.values) {
                normB += vb * vb
            }
            if (normA == 0.0 || normB == 0.0) return 0.0
            return dot / (sqrt(normA) * sqrt(normB))
        }
    }
}
