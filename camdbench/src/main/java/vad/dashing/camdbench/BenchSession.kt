package vad.dashing.camdbench

import android.content.Context
import android.os.Environment
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * One measurement: starts the root sampler, tails its output while the HU runs, stops
 * it, then writes the report folder into Downloads/camdbench.
 */
class BenchSession(private val context: Context, private val plannedSec: Int) {

    fun run() {
        BenchState.stopRequested = false
        BenchState.update {
            BenchUi(phase = BenchPhase.STARTING, plannedSec = plannedSec, message = "Запускаю сэмплер через mb_daemon…")
        }
        try {
            measure()
        } catch (error: Throwable) {
            BenchState.update {
                it.copy(phase = BenchPhase.ERROR, message = error.message ?: error.javaClass.simpleName)
            }
        }
    }

    private fun measure() {
        val nonce = UUID.randomUUID().toString().take(8)
        val startedWall = System.currentTimeMillis()
        val startedAt = stamp(startedWall, "yyyy-MM-dd HH:mm:ss")
        val camdVersion = CamdProbe.camdVersion(context)
        val camdVersionName = CamdProbe.camdVersionName(context)
        val grabVersion = CamdProbe.grabVersion()
        val statusStart = CamdProbe.status()
        val grabLogOffset = CamdProbe.grabLogLength()

        stage(nonce)
        RootChannel.arm(BenchScripts.start(nonce, plannedSec + BenchScripts.SAMPLER_GRACE_SEC), nonce)

        val raw = File(BenchScripts.RAW)
        waitFor("сэмплер не запустился, журнал ${BenchScripts.RUN_LOG}", 10_000) { raw.canRead() }

        val accumulator = BenchAccumulator()
        val tail = RawTail(raw, accumulator::feed)
        val runStart = System.currentTimeMillis()
        var lastGrowth = runStart
        BenchState.update { it.copy(phase = BenchPhase.RUNNING, message = "Идёт сбор данных") }
        while (true) {
            if (tail.poll()) lastGrowth = System.currentTimeMillis()
            val now = System.currentTimeMillis()
            val elapsed = ((now - runStart) / 1000).toInt()
            val live = accumulator.live()
            BenchState.update {
                it.copy(
                    elapsedSec = elapsed,
                    cpuBusyPct = live.cpuBusyPct,
                    camdPct = live.camdPct,
                    grabPct = live.grabPct,
                )
            }
            if (elapsed >= plannedSec || BenchState.stopRequested) break
            if (isDone(nonce)) break
            if (now - lastGrowth > 15_000) {
                throw IllegalStateException("сэмплер перестал писать данные, журнал ${BenchScripts.RUN_LOG}")
            }
            Thread.sleep(1_000)
        }

        BenchState.update { it.copy(phase = BenchPhase.FINISHING, message = "Останавливаю сэмплер и собираю отчёт…") }
        val statusEnd = CamdProbe.status()
        File(BenchScripts.CTL).writeText("stop\n")
        waitFor("сэмплер не завершился", 15_000) { isDone(nonce) }
        tail.poll()
        val endedWall = System.currentTimeMillis()
        val snapshot = accumulator.snapshot()

        val prefs = runCatching { File(BenchScripts.CAMD_PREFS).readText() }.getOrDefault("")
        val folder = BenchLogs.recorderFolder(prefs)
        val logcat = runCatching { File(BenchScripts.LOGCAT).readText() }.getOrDefault("")
        val grabLog = CamdProbe.grabLogSince(grabLogOffset)
        val run = BenchRun(
            startedAt = startedAt,
            camdVersion = camdVersion,
            grabVersion = grabVersion,
            statusStart = statusStart,
            statusEnd = statusEnd,
            grabCopy = BenchLogs.parseGrabCopy(grabLog),
            recorderSegments = BenchLogs.parseRecorderSegments(logcat),
            segmentFps = CamdProbe.segmentFps(folder, startedWall, endedWall),
            recorderFolder = folder,
        )

        val out = File(resultsRoot(), "${stamp(startedWall, "yyyy-MM-dd_HH-mm-ss")}_camd-$camdVersionName")
        if (!out.isDirectory && !out.mkdirs()) {
            throw IllegalStateException("не удалось создать ${out.absolutePath} — нет разрешения на память?")
        }
        val report = BenchReport.text(run, snapshot)
        File(out, REPORT).writeText(report)
        File(out, SUMMARY).writeText(BenchReport.summary(run, snapshot))
        runCatching { raw.copyTo(File(out, "raw.txt"), overwrite = true) }
        File(out, "logcat.txt").writeText(logcat)
        File(out, "camd-grab-log.txt").writeText(grabLog)

        val camd = snapshot.process(BenchAccumulator.CAMD_APP)?.avgPct ?: 0.0
        val grab = snapshot.process(BenchAccumulator.CAMD_GRAB)?.avgPct ?: 0.0
        val ours = snapshot.processes.filter { OurApps.isOurs(it.name) }.sumOf { it.avgPct }
        val warn = BenchReport.warnings(run, snapshot)
        BenchState.update {
            it.copy(
                phase = BenchPhase.DONE,
                message = if (warn.isEmpty()) "Готово" else "Готово, но: " + warn.joinToString("; "),
                resultFolder = out.absolutePath,
                resultHeadline = "Процессор ${BenchReport.fmt(snapshot.cpuBusyPct)} % всех ядер · " +
                    "наши и DCL ${BenchReport.fmt(ours)} % ядра · " +
                    (if (BenchReport.camdInvolved(run, snapshot)) "camd ${BenchReport.fmt(camd + grab)} % ядра · " else "") +
                    "${BenchReport.fmt(snapshot.durationSec, 0)} с",
            )
        }
    }

    private fun stage(nonce: String) {
        val files = context.filesDir
        files.mkdirs()
        val sampler = File(files, BenchScripts.SAMPLER_ASSET)
        context.assets.open(BenchScripts.SAMPLER_ASSET).use { input ->
            sampler.outputStream().use { input.copyTo(it) }
        }
        sampler.setExecutable(true, false)
        sampler.setReadable(true, false)
        File(files, "run.sh").writeText(BenchScripts.run())
        File(BenchScripts.CTL).writeText(BenchScripts.ctlRun(nonce))
    }

    private fun isDone(nonce: String): Boolean =
        runCatching { File(BenchScripts.DONE).readText().trim() == nonce }.getOrDefault(false)

    private fun waitFor(error: String, timeoutMs: Long, check: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (check()) return
            Thread.sleep(250)
        }
        throw IllegalStateException(error)
    }

    companion object {
        const val REPORT = "report.txt"
        const val SUMMARY = "summary.tsv"

        @Suppress("DEPRECATION")
        fun resultsRoot(): File =
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "camdbench")

        private fun stamp(wallMs: Long, pattern: String): String =
            SimpleDateFormat(pattern, Locale.US).format(Date(wallMs))
    }
}
