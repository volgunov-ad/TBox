package vad.dashing.voice.tts

/**
 * Splits long TTS text into Piper-friendly chunks (one or few sentences each).
 * Piper is configured with maxNumSentences=1; very long sentences are hard-split.
 */
object TtsTextChunker {
    const val DEFAULT_MAX_CHUNK = 280

    fun chunk(text: String, maxChunkChars: Int = DEFAULT_MAX_CHUNK): List<String> {
        val normalized = text
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .trim()
        if (normalized.isEmpty()) return emptyList()
        if (normalized.length <= maxChunkChars) return listOf(normalized)

        val sentences = splitSentences(normalized)
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            val piece = sentence.trim()
            if (piece.isEmpty()) continue
            if (piece.length > maxChunkChars) {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                chunks.addAll(hardSplit(piece, maxChunkChars))
                continue
            }
            val candidate = if (current.isEmpty()) piece else "${current} $piece"
            if (candidate.length <= maxChunkChars) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(piece)
            } else {
                if (current.isNotEmpty()) {
                    chunks.add(current.toString().trim())
                    current.clear()
                }
                current.append(piece)
            }
        }
        if (current.isNotEmpty()) {
            chunks.add(current.toString().trim())
        }
        return chunks.filter { it.isNotEmpty() }
    }

    private fun splitSentences(text: String): List<String> {
        val parts = ArrayList<String>()
        val buffer = StringBuilder()
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\n') {
                if (buffer.isNotBlank()) {
                    parts.add(buffer.toString())
                    buffer.clear()
                }
                i++
                continue
            }
            buffer.append(ch)
            if (ch == '.' || ch == '!' || ch == '?' || ch == '…') {
                val next = text.getOrNull(i + 1)
                if (next == null || next.isWhitespace() || next == '\n') {
                    parts.add(buffer.toString())
                    buffer.clear()
                }
            }
            i++
        }
        if (buffer.isNotBlank()) {
            parts.add(buffer.toString())
        }
        return parts
    }

    private fun hardSplit(text: String, maxChunkChars: Int): List<String> {
        val out = ArrayList<String>()
        var start = 0
        while (start < text.length) {
            var end = (start + maxChunkChars).coerceAtMost(text.length)
            if (end < text.length) {
                val space = text.lastIndexOf(' ', end - 1)
                if (space > start + maxChunkChars / 3) {
                    end = space
                }
            }
            out.add(text.substring(start, end).trim())
            start = end
            while (start < text.length && text[start].isWhitespace()) start++
        }
        return out.filter { it.isNotEmpty() }
    }
}
