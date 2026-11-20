package vad.dashing.camd

/**
 * Pure decision for `REC ON|OFF` / [RecorderController.applyEnabledFromApi].
 * Side effects (prefs, threads) stay in the controller; replies are stable for tests.
 */
internal object RecorderEnableApi {
    data class Plan(
        val reply: String,
        val action: Action,
        /** UI message when [action] is an error; null otherwise. */
        val message: String? = null,
    )

    enum class Action {
        ERROR_NO_CAMERAS,
        ERROR_NO_FOLDER,
        /** Already armed and pref on — no start thread. */
        IDEMPOTENT_ON,
        /** Persist on and start recording if not armed. */
        TURN_ON,
        /** Persist off; stop if armed. */
        TURN_OFF,
    }

    fun plan(
        wantEnabled: Boolean,
        hasInputs: Boolean,
        hasFolder: Boolean,
        armed: Boolean,
        prefEnabled: Boolean,
    ): Plan {
        if (wantEnabled) {
            if (!hasInputs) {
                return Plan(
                    reply = "state=error error=no_cameras",
                    action = Action.ERROR_NO_CAMERAS,
                    message = "Выберите хотя бы одну камеру",
                )
            }
            if (!hasFolder) {
                return Plan(
                    reply = "state=error error=no_folder",
                    action = Action.ERROR_NO_FOLDER,
                    message = "Выберите папку для записи",
                )
            }
            if (armed && prefEnabled) {
                return Plan(reply = "state=rec_on enabled=1", action = Action.IDEMPOTENT_ON)
            }
            return Plan(reply = "state=rec_on enabled=1", action = Action.TURN_ON)
        }
        return Plan(reply = "state=rec_off enabled=0", action = Action.TURN_OFF)
    }
}
