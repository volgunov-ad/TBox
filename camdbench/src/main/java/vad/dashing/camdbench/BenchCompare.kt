package vad.dashing.camdbench

import java.util.Locale

/** Side-by-side comparison of two runs from their `summary.tsv`. */
object BenchCompare {

    class Summary(
        val info: Map<String, String>,
        val warnings: List<String>,
        val metrics: Map<String, Double>,
        val fps: Map<String, Double>,
        val copyAvgMs: Map<String, Double>,
        val proc: Map<String, Double>,
        val threads: Map<String, Double>,
    )

    fun parse(text: String): Summary {
        val info = LinkedHashMap<String, String>()
        val warnings = ArrayList<String>()
        val metrics = LinkedHashMap<String, Double>()
        val fps = LinkedHashMap<String, Double>()
        val copy = LinkedHashMap<String, Double>()
        val proc = LinkedHashMap<String, Double>()
        val threads = LinkedHashMap<String, Double>()
        text.lineSequence().forEach { line ->
            val cells = line.split('\t')
            if (cells.size < 3) return@forEach
            val key = cells[1]
            val value = cells[2]
            when (cells[0]) {
                "info" -> info[key] = value
                "warn" -> warnings += value
                "metric" -> value.toDoubleOrNull()?.let { metrics[key] = it }
                "fps" -> value.toDoubleOrNull()?.let { fps[key] = it }
                "copy" -> value.toDoubleOrNull()?.let { copy[key] = it }
                "proc" -> value.toDoubleOrNull()?.let { proc[key] = it }
                "thread" -> value.toDoubleOrNull()?.let { threads[key] = it }
            }
        }
        return Summary(info, warnings, metrics, fps, copy, proc, threads)
    }

    private val metricLabels = listOf(
        "cpu_busy_pct" to "Процессор занят, % всех ядер",
        "cpu_cores_used" to "Процессор занят, ядер",
        "camd_app_pct" to "camd (приложение), % ядра",
        "camd_grab_pct" to "camd_grab, % ядра",
        "camd_total_pct" to "camd всего, % ядра",
        "cpu_busy_max_pct" to "Пик за выборку, % всех ядер",
        "mem_available_min_mb" to "Минимум MemAvailable, МБ",
        "duration_s" to "Длительность, с",
    )

    fun text(a: Summary, b: Summary, nameA: String, nameB: String): String = buildString {
        appendLine("Сравнение замеров")
        appendLine("A: $nameA — camd ${a.info["camd"] ?: "?"}, camd_grab ${a.info["grab"] ?: "?"}, ${a.info["started"] ?: ""}")
        appendLine("B: $nameB — camd ${b.info["camd"] ?: "?"}, camd_grab ${b.info["grab"] ?: "?"}, ${b.info["started"] ?: ""}")
        a.warnings.forEach { appendLine("ВНИМАНИЕ (A): $it") }
        b.warnings.forEach { appendLine("ВНИМАНИЕ (B): $it") }
        appendLine()
        header("Итог")
        metricLabels.forEach { (key, label) -> row(label, a.metrics[key], b.metrics[key]) }

        appendLine()
        header("Фактическая частота записи, кадр/с")
        (a.fps.keys + b.fps.keys).distinct().sorted().forEach { row(it, a.fps[it], b.fps[it]) }

        appendLine()
        header("camd_grab: memcpy кадра, мс")
        (a.copyAvgMs.keys + b.copyAvgMs.keys).distinct().sorted().forEach { row(it, a.copyAvgMs[it], b.copyAvgMs[it]) }

        appendLine()
        header("Процессы, % ядра")
        rankedKeys(a.proc, b.proc, 15).forEach { row(it, a.proc[it], b.proc[it]) }

        appendLine()
        header("Потоки camd и camd_grab, % ядра")
        rankedKeys(a.threads, b.threads, 20).forEach { row(it, a.threads[it], b.threads[it]) }
        appendLine()
        appendLine("«—» — значения нет в этом замере (старая версия его не пишет или процесс не работал).")
    }

    /** Keys from both runs, ordered by the larger of the two values. */
    private fun rankedKeys(a: Map<String, Double>, b: Map<String, Double>, limit: Int): List<String> =
        (a.keys + b.keys).distinct()
            .sortedByDescending { maxOf(a[it] ?: 0.0, b[it] ?: 0.0) }
            .take(limit)

    private fun StringBuilder.header(title: String) {
        appendLine("== $title ==")
        appendLine(String.format(Locale.US, "%-44s %10s %10s %10s %8s", "", "A", "B", "B−A", "B/A"))
    }

    private fun StringBuilder.row(label: String, a: Double?, b: Double?) {
        val diff = if (a != null && b != null) signed(b - a) else "—"
        val ratio = if (a != null && b != null && a > 0.0) String.format(Locale.US, "%.2f", b / a) else "—"
        appendLine(
            String.format(
                Locale.US, "%-44s %10s %10s %10s %8s",
                label.take(44), a?.let { num(it) } ?: "—", b?.let { num(it) } ?: "—", diff, ratio,
            ),
        )
    }

    private fun num(value: Double): String = String.format(Locale.US, "%.2f", value)

    private fun signed(value: Double): String = String.format(Locale.US, "%+.2f", value)
}
