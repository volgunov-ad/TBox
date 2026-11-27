package vad.dashing.voice.nlu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.voice.api.CatalogAction

class LastRelativeAdjustTest {
    @Test
    fun freshWithinTtl() {
        val last = LastRelativeAdjust(
            action = CatalogAction(type = "builtin", actionType = "set_media_volume", voiceAliasesRu = emptyList()),
            steps = -1,
            savedAtElapsedMs = 1_000L,
        )
        assertTrue(last.isFresh(1_000L + 30_000L))
        assertFalse(last.isFresh(1_000L + LastRelativeAdjust.TTL_MS + 1))
    }
}
