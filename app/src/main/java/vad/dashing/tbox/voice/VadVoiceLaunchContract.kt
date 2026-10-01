package vad.dashing.tbox.voice

/**
 * Intent contract mirrored for Monitor-side launchers (automation builtin later).
 * Keep in sync with [vad.dashing.voice.VoiceIntents].
 */
object VadVoiceLaunchContract {
    const val PACKAGE = "vad.dashing.voice"
    const val ACTION_LISTEN = "vad.dashing.voice.action.LISTEN"
    const val EXTRA_SOURCE = "source"
    const val SOURCE_AUTOMATION = "automation"
    const val SOURCE_HARD_KEY = "hard_key"
}
