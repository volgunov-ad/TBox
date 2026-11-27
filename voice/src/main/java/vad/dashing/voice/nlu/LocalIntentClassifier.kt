package vad.dashing.voice.nlu

import vad.dashing.voice.api.ApiCatalog
import vad.dashing.voice.api.AutomationSummary

/**
 * Offline intent classifier for Voice (stage E).
 * Runs on-device with no WAN; returns [VoiceIntent.Unknown] when unsure.
 */
fun interface LocalIntentClassifier {
    fun classify(
        phrase: String,
        catalog: ApiCatalog,
        automations: List<AutomationSummary>,
    ): VoiceIntent
}
