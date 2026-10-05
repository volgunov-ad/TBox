package vad.dashing.voice

/**
 * Public Intent contract for TBox Monitor automations / hard-key forward.
 * Monitor should send this action to package [VOICE_PACKAGE].
 */
object VoiceIntents {
    const val VOICE_PACKAGE = "vad.dashing.voice"
    const val ACTION_LISTEN = "vad.dashing.voice.action.LISTEN"
    const val EXTRA_SOURCE = "source"

    const val SOURCE_UI = "ui"
    const val SOURCE_WAKE = "wake"
    const val SOURCE_HARD_KEY = "hard_key"
    const val SOURCE_AUTOMATION = "automation"
}
