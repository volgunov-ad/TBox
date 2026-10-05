package vad.dashing.voice.tts

/**
 * Asset layout produced by [tools/fetch_voice_tts_model.py]
 * (sherpa-onnx `vits-piper-ru_RU-irina-medium-int8`).
 */
object PiperModelPaths {
    const val MODEL_DIR = "vits-piper-ru_RU-irina-medium-int8"
    const val MODEL_ONNX = "$MODEL_DIR/ru_RU-irina-medium.onnx"
    const val TOKENS = "$MODEL_DIR/tokens.txt"
    const val ESPEAK_ASSET_DIR = "$MODEL_DIR/espeak-ng-data"
    const val MARKER_ASSET = TOKENS
}
