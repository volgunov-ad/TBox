package vad.dashing.tbox.dcl

/**
 * Cached value of «Работа в среде лаунчера DCL».
 * Default [false] matches DataStore when the key is absent.
 * Updated from [vad.dashing.tbox.TboxApplication] / settings saves.
 */
object DclLauncherEnvironmentSetting {
    @Volatile
    var enabled: Boolean = false
        private set

    fun update(enabled: Boolean) {
        this.enabled = enabled
    }
}
