package vad.dashing.voice.stt

import org.json.JSONObject

/** Parses Vosk JSON hypotheses (`text` / `partial`). */
object VoskHypothesisParser {
    fun extractText(rawJson: String): String {
        if (rawJson.isBlank()) return ""
        return runCatching {
            val json = JSONObject(rawJson)
            json.optString("text")
                .ifBlank { json.optString("partial") }
                .trim()
        }.getOrDefault("")
    }
}
