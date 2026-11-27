package vad.dashing.voice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsTextChunkerTest {
    @Test
    fun shortText_singleChunk() {
        assertEquals(listOf("Привет"), TtsTextChunker.chunk("Привет"))
    }

    @Test
    fun splitsOnSentences() {
        val text = "Первое предложение. Второе предложение! Третье?"
        val chunks = TtsTextChunker.chunk(text, maxChunkChars = 40)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 40 })
        assertEquals(text.replace(" ", ""), chunks.joinToString("").replace(" ", ""))
    }

    @Test
    fun hardSplitsVeryLongSentence() {
        val long = "слово ".repeat(80).trim()
        val chunks = TtsTextChunker.chunk(long, maxChunkChars = 50)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 50 })
    }
}
