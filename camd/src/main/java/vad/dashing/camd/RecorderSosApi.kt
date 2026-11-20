package vad.dashing.camd

/**
 * Pure decision for `REC SOS` / [RecorderController.requestEmergencySave].
 * Side effects (threads, copying) stay in the controller; replies are stable for tests.
 */
internal object RecorderSosApi {
    data class Plan(
        val reply: String,
        val action: Action,
        /** UI message when [action] is an error; null otherwise. */
        val message: String? = null,
    )

    enum class Action {
        /** Master switch off or pipeline not armed — nothing is being written. */
        ERROR_NOT_RECORDING,
        /** Emergency folder not chosen. */
        ERROR_NO_SOS_FOLDER,
        /** A save is already running — ignore the second tap. */
        BUSY,
        /** Start the async copy. */
        START,
    }

    fun plan(
        armed: Boolean,
        hasSosFolder: Boolean,
        saveRunning: Boolean,
    ): Plan = when {
        !armed -> Plan(
            reply = "state=error error=rec_off",
            action = Action.ERROR_NOT_RECORDING,
            message = "Запись не идёт — сохранять нечего",
        )
        !hasSosFolder -> Plan(
            reply = "state=error error=no_sos_folder",
            action = Action.ERROR_NO_SOS_FOLDER,
            message = "Выберите папку аварийного сохранения",
        )
        saveRunning -> Plan(
            reply = "state=rec_sos busy",
            action = Action.BUSY,
            message = "Предыдущее аварийное сохранение ещё выполняется",
        )
        else -> Plan(reply = "state=rec_sos started", action = Action.START)
    }
}
