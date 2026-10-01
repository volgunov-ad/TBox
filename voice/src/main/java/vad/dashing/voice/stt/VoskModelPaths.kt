package vad.dashing.voice.stt

/** Asset layout from Gradle `:voice:fetchSttModel` (vosk-model-small-ru-0.22). */
object VoskModelPaths {
    const val MODEL_DIR = "vosk-model-small-ru-0.22"
    const val MARKER_ASSET = "$MODEL_DIR/conf/model.conf"
    const val SAMPLE_RATE = 16_000f
    /** Max listen window (ms) for one PTT press; user can stop earlier. */
    const val LISTEN_TIMEOUT_MS = 6_000
}
