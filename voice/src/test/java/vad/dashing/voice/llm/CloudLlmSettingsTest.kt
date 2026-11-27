package vad.dashing.voice.llm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudLlmSettingsTest {
    @Test
    fun looksConfigured_requiresAllGates() {
        val ready = CloudLlmSettings(
            enabled = true,
            allowSendPhrases = true,
            baseUrl = "https://api.aitunnel.ru/v1",
            apiKey = "k",
            model = "gpt-4o-mini",
        )
        assertTrue(ready.looksConfigured())
        assertFalse(ready.copy(enabled = false).looksConfigured())
        assertFalse(ready.copy(allowSendPhrases = false).looksConfigured())
        assertFalse(ready.copy(apiKey = "").looksConfigured())
        assertFalse(ready.copy(model = " ").looksConfigured())
        assertFalse(ready.copy(baseUrl = "").looksConfigured())
    }
}
