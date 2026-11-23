package vad.dashing.camd

/**
 * Human-readable text for the UI from the one-line API replies of [CameraWindows]
 * (`state=… error=… input=…`) and its STATUS line (`windows=… avm=… keep_avm=…`).
 * The wire format stays as is for other programs; only the screen is translated.
 */
object CamdUiText {

    /** Camera ids with an open window, from `windows=4,8` (empty for `none` / missing). */
    fun openWindowIds(status: String): Set<Int> =
        field(status, "windows")
            ?.split(",")
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.toSet()
            .orEmpty()

    fun describeStatus(status: String): String {
        if (status.isBlank()) return ""
        val ids = openWindowIds(status)
        val windows = if (ids.isEmpty()) {
            "Окон нет"
        } else {
            "Открыты окна: " + ids.joinToString(", ") { cameraTitle(it) }
        }
        val avm = when (field(status, "avm")) {
            "running" -> "штатный AVM работает"
            "stopped" -> "штатный AVM остановлен"
            null -> null
            else -> "штатный AVM: состояние неизвестно"
        }
        val keep = if (field(status, "keep_avm") == "1") " (держится выключенным)" else ""
        return if (avm == null) windows else "$windows · $avm$keep"
    }

    /** Readable result of OPEN / CLOSE / AVM; anything that is not a `state=` reply is returned as is. */
    fun describeResult(result: String): String {
        val state = field(result, "state") ?: return result
        val input = field(result, "input")?.toIntOrNull()
        return when (state) {
            "open" -> "Окно открыто: ${input?.let(::cameraTitle).orEmpty()}".trimEnd(':', ' ')
            "moved" -> "Окно перемещено: ${input?.let(::cameraTitle).orEmpty()}".trimEnd(':', ' ')
            "closed" -> if (input != null) "Окно закрыто: ${cameraTitle(input)}" else "Все окна закрыты"
            "avm_held" -> "Штатный AVM остановлен и останется выключенным"
            "avm_restore" -> "Штатный AVM снова включается"
            "error" -> "Ошибка: " + describeError(errorText(result))
            else -> result
        }
    }

    fun describeError(error: String): String = when (error) {
        "unknown_preset" -> "нет такого пресета"
        "unknown_input" -> "неизвестная камера"
        "stock_cal_missing" -> "на ГУ нет штатной калибровки AVM для этого вида"
        "overlay_permission" -> "нет разрешения на показ поверх других окон"
        OverlayOpSequencer.ERROR_TIMEOUT -> "окно не успело обновиться — система занята, попробуйте ещё раз"
        OverlayOpSequencer.ERROR_SUPERSEDED -> "окно уже меняет более новая команда"
        "recorder_active" -> "идёт запись регистратора — штатный AVM включить нельзя"
        "bad_geometry" -> "неверные размеры или положение окна"
        "" -> "неизвестная ошибка"
        else -> error
    }

    private fun cameraTitle(id: Int): String = CamdSources.camera(id)?.title ?: "камера $id"

    /** `error=` runs to the next ` input=` (messages such as «mb_daemon не ответил» contain spaces). */
    private fun errorText(line: String): String {
        val start = line.indexOf("error=").takeIf { it >= 0 } ?: return ""
        val body = line.substring(start + "error=".length)
        val end = body.indexOf(" input=")
        return (if (end >= 0) body.substring(0, end) else body).trim()
    }

    private fun field(line: String, key: String): String? =
        line.trim().split(Regex("\\s+")).firstOrNull { it.startsWith("$key=") }?.substring(key.length + 1)
}
