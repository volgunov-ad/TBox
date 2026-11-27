package vad.dashing.voice.llm

/**
 * Heuristic: short assistant text without a tool call that asks the user to disambiguate.
 * Used to enter the single follow-up clarify round (stage D).
 */
object ClarifyQuestionDetector {
    private val cues = listOf(
        "какой",
        "какая",
        "какие",
        "какое",
        "что именно",
        "уточн",
        "имеете в виду",
        "какого",
        "какую",
        "куда",
    )

    fun isClarifyQuestion(text: String): Boolean {
        val normalized = text.trim().lowercase().replace('ё', 'е')
        if (normalized.isEmpty() || normalized.length > 240) return false
        if (normalized.contains('?')) return true
        return cues.any { normalized.contains(it) }
    }
}
