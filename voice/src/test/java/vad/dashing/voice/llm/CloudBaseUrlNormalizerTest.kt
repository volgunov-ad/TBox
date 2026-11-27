package vad.dashing.voice.llm

import org.junit.Assert.assertEquals
import org.junit.Test

class CloudBaseUrlNormalizerTest {
    @Test
    fun keepsKnownPresetRoots() {
        assertEquals(
            "https://api.aitunnel.ru/v1",
            CloudBaseUrlNormalizer.normalize("https://api.aitunnel.ru/v1"),
        )
        assertEquals(
            "https://api.openai.com/v1",
            CloudBaseUrlNormalizer.normalize("https://api.openai.com/v1/"),
        )
        assertEquals(
            "https://openrouter.ai/api/v1",
            CloudBaseUrlNormalizer.normalize("https://openrouter.ai/api/v1"),
        )
    }

    @Test
    fun appendsV1WhenMissing() {
        assertEquals(
            "https://api.aitunnel.ru/v1",
            CloudBaseUrlNormalizer.normalize("api.aitunnel.ru"),
        )
        assertEquals(
            "https://example.com/openai/v1",
            CloudBaseUrlNormalizer.normalize("https://example.com/openai"),
        )
    }

    @Test
    fun stripsModelsAndChatSuffixes() {
        assertEquals(
            "https://api.aitunnel.ru/v1",
            CloudBaseUrlNormalizer.normalize("https://api.aitunnel.ru/v1/models"),
        )
        assertEquals(
            "https://api.openai.com/v1",
            CloudBaseUrlNormalizer.normalize("https://api.openai.com/v1/chat/completions"),
        )
    }

    @Test
    fun modelsUrlAppendsPath() {
        assertEquals(
            "https://api.aitunnel.ru/v1/models",
            CloudBaseUrlNormalizer.modelsUrl("https://api.aitunnel.ru/v1"),
        )
    }
}
