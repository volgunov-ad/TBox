package vad.dashing.dataresize

enum class StepStatus { Pending, Running, Ok, Failed }

data class StepDef(val id: String, val title: String)

data class StepUi(
    val id: String,
    val title: String,
    val status: StepStatus = StepStatus.Pending,
    val detail: String = "",
)

data class Operation(val title: String, val steps: List<StepUi>, val error: String? = null)

object Journal {
    val MOVE = listOf(
        StepDef("check", "Проверка перед переносом"),
        StepDef("stop", "Остановка Android (экран замрёт)"),
        StepDef("freeze", "Заморозка /data"),
        StepDef("copy", "Копирование данных в новое место"),
        StepDef("verify", "Сверка копии (MD5)"),
        StepDef("wp_off", "Снятие защиты eMMC"),
        StepDef("sgpt", "Запись резервной GPT"),
        StepDef("pgpt", "Запись основной GPT"),
        StepDef("wp_on", "Возврат защиты eMMC"),
        StepDef("unfreeze", "Разморозка /data"),
        StepDef("reboot", "Перезагрузка"),
    )
    val STAGE2 = listOf(
        StepDef("check", "Проверка"),
        StepDef("kernel", "Ядро видит новый раздел"),
        StepDef("resize", "Расширение файловой системы"),
        StepDef("done", "Готово"),
    )
    val ROLLBACK = listOf(
        StepDef("check", "Проверка"),
        StepDef("wp_off", "Снятие защиты eMMC"),
        StepDef("gpt", "Запись старых GPT"),
        StepDef("wp_on", "Возврат защиты eMMC"),
        StepDef("reboot", "Перезагрузка"),
    )

    /** Strips the `YYYY-MM-DD HH:MM:SS ` prefix written by the script. */
    fun payload(line: String): String {
        val parts = line.split(' ', limit = 3)
        return if (parts.size == 3 && parts[0].length == 10 && parts[1].length == 8) parts[2] else line
    }

    fun time(line: String): String = line.split(' ', limit = 3).getOrNull(1)?.takeIf { it.length == 8 }.orEmpty()

    /**
     * Step statuses from journal lines: `@id text` starts a step (earlier ones are done),
     * `!FAIL text` fails the running one. [completed] marks the last started step done
     * once the outcome is confirmed (e.g. new GPT seen after the reboot).
     */
    fun steps(defs: List<StepDef>, lines: List<String>, completed: Boolean = false): Pair<List<StepUi>, String?> {
        var current = -1
        var failure: String? = null
        val details = HashMap<String, MutableList<String>>()
        for (line in lines) {
            val p = payload(line)
            when {
                p.startsWith("@") -> {
                    val id = p.drop(1).substringBefore(' ')
                    val idx = defs.indexOfFirst { it.id == id }
                    if (idx >= 0) {
                        current = idx
                        val text = p.substringAfter(' ', "")
                        val withTime = listOf(time(line), if (text == defs[idx].title) "" else text)
                            .filter { it.isNotBlank() }.joinToString("  ")
                        details.getOrPut(id) { mutableListOf() }.add(withTime)
                    }
                }
                p.startsWith("!FAIL") -> failure = p.removePrefix("!FAIL").trim()
                current >= 0 && p.isNotBlank() -> details.getOrPut(defs[current].id) { mutableListOf() }.add(p)
            }
        }
        val steps = defs.mapIndexed { i, def ->
            val status = when {
                i < current -> StepStatus.Ok
                i == current && failure != null -> StepStatus.Failed
                i == current && (completed || i == defs.lastIndex && def.id == "done") -> StepStatus.Ok
                i == current -> StepStatus.Running
                else -> StepStatus.Pending
            }
            StepUi(def.id, def.title, status, details[def.id].orEmpty().joinToString("\n"))
        }
        return steps to failure
    }
}
