package vad.dashing.voice.stt

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VoskHypothesisParserTest {
    @Test
    fun extractText_fromFinalJson() {
        assertEquals("сколько градусов", VoskHypothesisParser.extractText("""{"text":"сколько градусов"}"""))
    }

    @Test
    fun extractText_fromPartialJson() {
        assertEquals("сколько", VoskHypothesisParser.extractText("""{"partial":"сколько"}"""))
    }

    @Test
    fun extractText_prefersTextOverPartial() {
        assertEquals(
            "готово",
            VoskHypothesisParser.extractText("""{"text":"готово","partial":"го"}"""),
        )
    }

    @Test
    fun extractText_blankOnGarbage() {
        assertEquals("", VoskHypothesisParser.extractText("not-json"))
    }
}

class VoskModelPathsTest {
    @Test
    fun pathsPointAtSmallRuBundle() {
        assertEquals("vosk-model-small-ru-0.22", VoskModelPaths.MODEL_DIR)
        assertEquals("vosk-model-small-ru-0.22/conf/model.conf", VoskModelPaths.MARKER_ASSET)
    }
}
