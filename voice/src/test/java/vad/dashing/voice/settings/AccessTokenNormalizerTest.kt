package vad.dashing.voice.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AccessTokenNormalizerTest {
    @Test
    fun stripsBearerPrefix() {
        assertEquals("abc123", AccessTokenNormalizer.normalize("Bearer abc123"))
        assertEquals("abc123", AccessTokenNormalizer.normalize("bearer abc123"))
        assertEquals("abc123", AccessTokenNormalizer.normalize("  BEARER   abc123  "))
    }

    @Test
    fun keepsRawToken() {
        assertEquals("abc123", AccessTokenNormalizer.normalize("abc123"))
    }
}
