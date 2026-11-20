package vad.dashing.tbox.automation

/**
 * Search over automation picker options: labels, group headers, synonyms, and
 * canonical storage keys. Pure Kotlin so JVM unit tests cover the matching rules.
 */
object AutomationPickerSearch {

    /** Lowercase, ё → е, collapse whitespace. Null-safe for blank queries. */
    fun normalize(raw: String): String =
        raw.lowercase().replace('ё', 'е').trim().replace(whitespace, " ")

    /** Words of the query, normalized; single-character words are kept as-is. */
    fun queryWords(raw: String): List<String> =
        normalize(raw).split(' ').filter { it.isNotEmpty() }

    /**
     * True when every query word matches some word of the option haystack:
     * label + group label + keywords + words of the canonical storage key
     * (`set_media_volume` → `set media volume`).
     *
     * Matching is inflection-tolerant for Russian: a word matches on exact
     * equality, on prefix (≥3 chars), or on a shared truncated stem
     * («руль» ↔ «руля», «печка» ↔ «печки»).
     */
    fun matches(
        query: String,
        label: String,
        groupLabel: String = "",
        keywords: List<String> = emptyList(),
        storageKey: String = "",
    ): Boolean {
        val words = queryWords(query)
        if (words.isEmpty()) return true
        val haystackWords = buildList {
            splitWords(normalize(label), this)
            if (groupLabel.isNotEmpty()) splitWords(normalize(groupLabel), this)
            keywords.forEach { splitWords(normalize(it), this) }
            if (storageKey.isNotEmpty()) splitWords(normalize(storageKey.replace('_', ' ')), this)
        }
        return words.all { queryWord -> haystackWords.any { wordMatches(it, queryWord) } }
    }

    private fun splitWords(text: String, into: MutableList<String>) {
        into.addAll(text.split(' ').filter { it.isNotEmpty() })
    }

    /** Inflection-tolerant comparison of a haystack word against a query word. */
    internal fun wordMatches(haystackWord: String, queryWord: String): Boolean {
        if (haystackWord == queryWord) return true
        val shared = minOf(haystackWord.length, queryWord.length)
        if (shared >= 3 && (haystackWord.startsWith(queryWord) || queryWord.startsWith(haystackWord))) {
            return true
        }
        // «руль» ↔ «руля»: shared stem of min(len)-1, but never shorter than 3 chars.
        val stem = shared - 1
        if (stem >= 3 &&
            haystackWord.take(stem) == queryWord.take(stem)
        ) {
            return true
        }
        return false
    }

    private val whitespace = Regex("\\s+")
}
