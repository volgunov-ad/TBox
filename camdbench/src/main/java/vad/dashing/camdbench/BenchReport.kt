package vad.dashing.camdbench

import java.util.Locale
import kotlin.math.roundToInt

/** Everything one run knows besides the CPU samples. */
class BenchRun(
    val startedAt: String,
    val camdVersion: String,
    val grabVersion: String,
    val statusStart: String,
    val statusEnd: String,
    val grabCopy: List<BenchLogs.GrabCopy>,
    val recorderSegments: List<BenchLogs.RecorderSegment>,
    val segmentFps: List<BenchLogs.SegmentFps>,
    val recorderFolder: String?,
    val notes: List<String> = emptyList(),
)

/**
 * Human-readable report and the machine-readable summary (TSV) of one run.
 * The summary is what [BenchCompare] reads back for the A/B comparison.
 */
object BenchReport {

    private const val TOP_PROCESSES = 20
    private const val TOP_THREADS_PER_APP = 15
    private const val MIN_SHOWN_PCT = 0.05
    private const val TOP_ZONES = 6

    private val focus = listOf(BenchAccumulator.CAMD_APP, BenchAccumulator.CAMD_GRAB)

    /**
     * camd took part in the run: the recorder was on at some point, camd itself was busy, or
     * the grabber copied frames. Only then the camd sections and recorder warnings apply.
     */
    fun camdInvolved(run: BenchRun, s: BenchAccumulator.Snapshot): Boolean {
        val recording = BenchLogs.statusFields(run.statusStart)["rec_active"] == "1" ||
            BenchLogs.statusFields(run.statusEnd)["rec_active"] == "1"
        return recording || (s.process(BenchAccumulator.CAMD_APP)?.avgPct ?: 0.0) >= 1.0 || run.grabCopy.isNotEmpty()
    }

    fun warnings(run: BenchRun, snapshot: BenchAccumulator.Snapshot): List<String> {
        val result = ArrayList<String>()
        val start = BenchLogs.statusFields(run.statusStart)
        val end = BenchLogs.statusFields(run.statusEnd)
        val recStart = start["rec_active"] == "1"
        val recEnd = end["rec_active"] == "1"
        if (recStart != recEnd) {
            result += "регистратор писал не всё время (rec_active в начале ${start["rec_active"] ?: "?"}, в конце ${end["rec_active"] ?: "?"}) — сравнение записи camd некорректно"
        }
        val windows = start["windows"]
        if ((recStart || recEnd) && windows != null && windows != "none") {
            result += "были открыты окна камер ($windows) — они тоже грузят процессор; для чистого сравнения записи закройте окна"
        }
        if (snapshot.durationSec < 120) {
            result += "замер короче 2 минут — цифры шумные"
        }
        if (!snapshot.ended) {
            result += "сэмплер не дописал конец файла — замер мог оборваться"
        }
        return result + run.notes
    }

    fun text(run: BenchRun, s: BenchAccumulator.Snapshot): String = buildString {
        val camdOn = camdInvolved(run, s)
        appendLine("Нагрузка ГУ — отчёт")
        appendLine("Начало: ${run.startedAt}")
        appendLine("Длительность: ${fmt(s.durationSec, 0)} с, выборок ${s.samples}, ядер ${s.cores}")
        if (camdOn) {
            appendLine("camd: ${run.camdVersion}, camd_grab: ${run.grabVersion}")
            appendLine("STATUS в начале: ${run.statusStart}")
            appendLine("STATUS в конце:  ${run.statusEnd}")
        }
        val warn = warnings(run, s)
        if (warn.isNotEmpty()) {
            appendLine()
            warn.forEach { appendLine("ВНИМАНИЕ: $it") }
        }

        appendLine()
        appendLine("== Процессор целиком ==")
        appendLine("Занято в среднем: ${fmt(s.cpuBusyPct)} % всех ядер (≈ ${fmt(s.cpuBusyPct * s.cores / 100, 2)} ядра), пик за выборку ${fmt(s.cpuBusyMaxPct)} %")
        appendLine("(% ядра — как в top: 100 % = одно ядро занято полностью)")
        appendLine()
        appendLine("== Ядра процессора ==")
        if (s.cpuCores.isEmpty()) appendLine("нет данных (замер версией до 1.2.0)")
        s.cpuCores.forEach {
            val mhz = s.freqMhz.getOrNull(it.cpu)?.let { f -> ", ${fmt(f, 0)} МГц" } ?: ""
            appendLine("cpu${it.cpu}: занято сред. ${fmt(it.avgPct)} %, пик ${fmt(it.maxPct)} %$mhz")
        }

        val ours = s.processes.filter { OurApps.isOurs(it.name) }
        appendLine()
        appendLine("== Наши приложения и DCL ==")
        appendLine(String.format(Locale.US, "%8s %8s  %s", "сред.%", "пик%", "процесс"))
        ours.forEach {
            appendLine(String.format(Locale.US, "%8.1f %8.1f  %s", it.avgPct, it.maxPct, OurApps.label(it.name)))
        }
        appendLine(String.format(Locale.US, "%8.1f %8s  %s", ours.sumOf { it.avgPct }, "", "всего"))

        appendLine()
        appendLine("== Где работают наши процессы и DCL ==")
        ours.filter { it.avgPct >= MIN_SHOWN_PCT }.forEach { process ->
            val placement = s.placement(process.name) ?: return@forEach
            appendLine("${OurApps.label(process.name)}: ${fmt(process.avgPct)} % ядра")
            appendLine("  разрешённые ядра: ${placement.allowedCpus}")
            if (placement.cpusets.isNotEmpty()) {
                appendLine("  группа (cpuset): " + placement.cpusets.joinToString(", ") { (name, share) -> "$name ${percent(share)}" })
            }
            if (placement.cores.isNotEmpty()) appendLine("  по ядрам: ${coreShares(placement.cores, limit = 8)}")
        }

        appendLine()
        appendLine("== Процессы, топ $TOP_PROCESSES по среднему ==")
        appendLine(String.format(Locale.US, "%8s %8s  %s", "сред.%", "пик%", "процесс"))
        s.processes.take(TOP_PROCESSES).forEach {
            appendLine(String.format(Locale.US, "%8.1f %8.1f  %s", it.avgPct, it.maxPct, it.name))
        }

        val threadsByOwner = s.threads.groupBy { OurApps.ownerOf(it.name) }
        ours.filter { it.avgPct >= MIN_SHOWN_PCT }.forEach { process ->
            val threads = threadsByOwner[process.name].orEmpty()
                .filter { it.avgPct >= MIN_SHOWN_PCT }
                .take(TOP_THREADS_PER_APP)
            if (threads.isEmpty()) return@forEach
            appendLine()
            appendLine("== Потоки ${OurApps.label(process.name)}: ${fmt(process.avgPct)} % ядра ==")
            appendLine(String.format(Locale.US, "%8s %8s  %s", "сред.%", "пик%", "поток"))
            threads.forEach {
                val cores = s.threadCores[it.name]?.let { shares -> "  [${coreShares(shares)}]" } ?: ""
                appendLine(String.format(Locale.US, "%8.1f %8.1f  %s%s", it.avgPct, it.maxPct, it.name, cores))
            }
        }
        appendLine("[ядро:доля] — на каких ядрах поток провёл своё время процессора (выборки 5 раз в секунду)")

        if (camdOn) {
            val camd = s.process(BenchAccumulator.CAMD_APP)
            val grab = s.process(BenchAccumulator.CAMD_GRAB)
            appendLine()
            appendLine("== Камеры ГУ (camd) ==")
            appendLine("camd (приложение): ${fmt(camd?.avgPct ?: 0.0)} % ядра, пик ${fmt(camd?.maxPct ?: 0.0)} %")
            appendLine("camd_grab (root):  ${fmt(grab?.avgPct ?: 0.0)} % ядра, пик ${fmt(grab?.maxPct ?: 0.0)} %")
            appendLine("camd всего:        ${fmt((camd?.avgPct ?: 0.0) + (grab?.avgPct ?: 0.0))} % ядра")
            appendLine()
            appendLine("== Запись: фактическая частота кадров по готовым роликам ==")
            if (run.recorderFolder == null) appendLine("папка регистратора не найдена")
            else appendLine("папка: ${run.recorderFolder}")
            val fps = BenchLogs.summarizeFps(run.segmentFps)
            if (fps.isEmpty()) appendLine("готовых роликов за время замера нет (сегмент длиннее замера?)")
            fps.forEach {
                appendLine("cam${it.inputId}: ${fmt(it.fps)} кадр/с — роликов ${it.segments}, кадров ${it.frames}, ${fmt(it.seconds, 0)} с")
            }
            run.segmentFps.forEach {
                appendLine("  ${it.file}: ${it.frames} кадров, ${fmt(it.seconds)} с, ${fmt(it.fps)} кадр/с")
            }

            appendLine()
            appendLine("== camd_grab: копирование кадра (camd_grab 5+) ==")
            val copy = BenchLogs.summarizeCopy(run.grabCopy)
            if (copy.isEmpty()) appendLine("строк copy нет (старый camd_grab или граббер не работал)")
            copy.forEach {
                appendLine("вход ${it.inputId}: memcpy сред. ${fmt(it.avgMs, 2)} мс, макс ${fmt(it.maxMs, 2)} мс, ${fmt(it.fps)} кадр/с (${it.periods} периодов по 30 с)")
            }

            appendLine()
            appendLine("== CamdRecorder: сегменты (camd 0.9.5+) ==")
            if (run.recorderSegments.isEmpty()) appendLine("строк нет (старый camd или сегменты не закрывались)")
            run.recorderSegments.forEach {
                appendLine("cam${it.inputId} ${it.file}: кадров ${it.frames}, пропущено ${it.skipped}, ${fmt(it.seconds)} с")
            }
        }

        if (s.cpuCores.isEmpty()) {
            appendLine()
            appendLine("== Частоты ядер (средние) ==")
            appendLine(s.freqMhz.mapIndexed { cpu, mhz -> "cpu$cpu ${fmt(mhz, 0)} МГц" }.joinToString(", "))
        }
        appendLine()
        appendLine("== Температуры, самые горячие ==")
        s.zones.sortedByDescending { it.maxC }.take(TOP_ZONES).forEach {
            appendLine("${it.name}: сред. ${fmt(it.avgC)} °C, макс ${fmt(it.maxC)} °C")
        }
        appendLine()
        appendLine("Минимум MemAvailable: ${s.memAvailableMinMb} МБ")
    }

    /** One record per line, tab-separated: `<kind>\t<key>\t<values…>`. */
    fun summary(run: BenchRun, s: BenchAccumulator.Snapshot): String = buildString {
        fun row(vararg cells: Any) = appendLine(cells.joinToString("\t"))
        row("format", "1")
        row("info", "started", run.startedAt)
        row("info", "camd", run.camdVersion)
        row("info", "grab", run.grabVersion)
        row("info", "status_start", run.statusStart)
        row("info", "status_end", run.statusEnd)
        warnings(run, s).forEach { row("warn", "-", it) }
        row("metric", "duration_s", fmt(s.durationSec, 0))
        row("metric", "cores", s.cores)
        row("metric", "cpu_busy_pct", fmt(s.cpuBusyPct, 2))
        row("metric", "cpu_busy_max_pct", fmt(s.cpuBusyMaxPct, 2))
        row("metric", "cpu_cores_used", fmt(s.cpuBusyPct * s.cores / 100, 3))
        val camd = s.process(BenchAccumulator.CAMD_APP)?.avgPct ?: 0.0
        val grab = s.process(BenchAccumulator.CAMD_GRAB)?.avgPct ?: 0.0
        row("metric", "camd_app_pct", fmt(camd, 2))
        row("metric", "camd_grab_pct", fmt(grab, 2))
        row("metric", "camd_total_pct", fmt(camd + grab, 2))
        row("metric", "mem_available_min_mb", s.memAvailableMinMb)
        s.cpuCores.forEach { row("core", "cpu${it.cpu}", fmt(it.avgPct, 2), fmt(it.maxPct, 2)) }
        s.placements.filter { OurApps.isOurs(it.name) }.forEach { placement ->
            placement.cores.forEach { (cpu, share) -> row("placement", "${placement.name}@cpu$cpu", fmt(share * 100, 2)) }
        }
        BenchLogs.summarizeFps(run.segmentFps).forEach {
            row("fps", "cam${it.inputId}", fmt(it.fps, 2))
        }
        BenchLogs.summarizeCopy(run.grabCopy).forEach {
            row("copy", "cam${it.inputId}", fmt(it.avgMs, 3), fmt(it.maxMs, 3))
        }
        val procNames = (
            s.processes.take(TOP_PROCESSES).map { it.name } + focus + s.processes.map { it.name }.filter(OurApps::isOurs)
        ).distinct()
        procNames.forEach { name ->
            val u = s.process(name)
            row("proc", name, fmt(u?.avgPct ?: 0.0, 2), fmt(u?.maxPct ?: 0.0, 2))
        }
        s.threads.groupBy { OurApps.ownerOf(it.name) }.values.forEach { threads ->
            threads.take(TOP_THREADS_PER_APP).forEach {
                row("thread", it.name, fmt(it.avgPct, 2), fmt(it.maxPct, 2))
            }
        }
    }

    private fun percent(share: Double): String = "${(share * 100).roundToInt()} %"

    /** `6:55% 7:40% 0:5%`, shares under 1 % dropped. */
    private fun coreShares(shares: List<Pair<Int, Double>>, limit: Int = 4): String =
        shares.filter { it.second >= 0.01 }.take(limit).joinToString(" ") { (cpu, share) ->
            "$cpu:${(share * 100).roundToInt()}%"
        }

    fun fmt(value: Double, digits: Int = 1): String = String.format(Locale.US, "%.${digits}f", value)
}
