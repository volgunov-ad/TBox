package vad.dashing.voice.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PiperModelPathsTest {
    @Test
    fun pathsPointAtIrinaInt8Bundle() {
        assertEquals("vits-piper-ru_RU-irina-medium-int8", PiperModelPaths.MODEL_DIR)
        assertTrue(PiperModelPaths.MODEL_ONNX.endsWith("ru_RU-irina-medium.onnx"))
        assertTrue(PiperModelPaths.TOKENS.endsWith("tokens.txt"))
        assertTrue(PiperModelPaths.ESPEAK_ASSET_DIR.endsWith("espeak-ng-data"))
    }
}

class NoOpVoiceTtsTest {
    @Test
    fun speakRecordsText() {
        val tts = NoOpVoiceTts()
        tts.speak(" на улице плюс пять ")
        assertEquals(" на улице плюс пять ", tts.lastSpoken)
        assertEquals(1, tts.speakCount)
    }
}
