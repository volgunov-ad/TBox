package vad.dashing.camdbench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchReportTest {

    private fun snapshot(camdTicks: Long, grabTicks: Long, seconds: Int = 300): BenchAccumulator.Snapshot {
        val acc = BenchAccumulator()
        val lines = listOf(
            "K 100", "N 8",
            "T 0 0", "C 0 0",
            "P 10 0 vad.dashing.camd", "H 10 11 0 camd-rec-4", "P 20 0 camd_grab",
            "T ${seconds * 1000} 0", "C ${seconds * 800L} ${seconds * 600L}",
            "P 10 $camdTicks vad.dashing.camd", "H 10 11 ${camdTicks / 2} camd-rec-4", "P 20 $grabTicks camd_grab",
            "E",
        )
        lines.forEach(acc::feed)
        return acc.snapshot()
    }

    private fun run(
        statusStart: String = "OK windows=none rec_active=1",
        statusEnd: String = "OK windows=none rec_active=1",
        camd: String = "0.9.4 (22)",
    ) = BenchRun(
        startedAt = "2026-11-25 10:00:00",
        camdVersion = camd,
        grabVersion = "4",
        statusStart = statusStart,
        statusEnd = statusEnd,
        grabCopy = listOf(BenchLogs.GrabCopy(4, 750, 25.0, 9.5, 15.0)),
        recorderSegments = emptyList(),
        segmentFps = listOf(BenchLogs.SegmentFps(4, "cam4_x.mp4", 751, 60.0)),
        recorderFolder = "/storage/usb/dvr",
    )

    @Test
    fun reportHasHeadlineNumbers() {
        val text = BenchReport.text(run(), snapshot(camdTicks = 15_000, grabTicks = 9_000))
        assertTrue(text, text.contains("camd (приложение): 50.0 % ядра"))
        assertTrue(text, text.contains("camd_grab (root):  30.0 % ядра"))
        assertTrue(text, text.contains("Занято в среднем: 25.0 %"))
        assertTrue(text, text.contains("cam4: 12.5 кадр/с"))
        assertTrue(text, text.contains("memcpy сред. 9.50 мс"))
        assertTrue(text, text.contains("vad.dashing.camd/camd-rec-4"))
    }

    @Test
    fun warningsCatchAnUnfairRun() {
        val warnings = BenchReport.warnings(
            run(statusStart = "OK windows=4 rec_active=1", statusEnd = "OK windows=4 rec_active=0"),
            snapshot(100, 100, seconds = 60),
        )
        assertEquals(3, warnings.size)
        assertTrue(warnings[0].contains("регистратор"))
        assertTrue(warnings[1].contains("окна"))
        assertTrue(warnings[2].contains("2 минут"))
    }

    @Test
    fun summaryRoundTripsIntoComparison() {
        val a = BenchCompare.parse(BenchReport.summary(run(), snapshot(15_000, 9_000)))
        val b = BenchCompare.parse(BenchReport.summary(run(camd = "0.9.5 (23)"), snapshot(12_000, 9_000)))
        assertEquals("0.9.4 (22)", a.info["camd"])
        assertEquals(50.0, a.metrics.getValue("camd_app_pct"), 1e-9)
        assertEquals(80.0, a.metrics.getValue("camd_total_pct"), 1e-9)
        assertEquals(2.0, a.metrics.getValue("cpu_cores_used"), 1e-9)
        assertEquals(12.5, a.fps.getValue("cam4"), 1e-9)
        assertEquals(9.5, a.copyAvgMs.getValue("cam4"), 1e-9)
        assertEquals(25.0, a.threads.getValue("vad.dashing.camd/camd-rec-4"), 1e-9)
        assertTrue(a.warnings.isEmpty())

        val text = BenchCompare.text(a, b, "run-a", "run-b")
        val camdRow = text.lines().first { it.startsWith("camd (приложение), % ядра") }
        assertTrue(camdRow, camdRow.contains("50.00") && camdRow.contains("40.00") && camdRow.contains("-10.00") && camdRow.contains("0.80"))
        assertTrue(text, text.contains("0.9.5 (23)"))
        val othersRow = text.lines().first { it.startsWith("Всё остальное (не camd)") }
        assertTrue(othersRow, othersRow.contains("120.00"))
    }

    @Test
    fun compareShowsDashForMissingValues() {
        val a = BenchCompare.parse("format\t1\nmetric\tcamd_app_pct\t10\n")
        val b = BenchCompare.parse("format\t1\nmetric\tcamd_app_pct\t5\ncopy\tcam4\t3.0\t4.0\n")
        val text = BenchCompare.text(a, b, "a", "b")
        val copyRow = text.lines().first { it.startsWith("cam4") }
        assertTrue(copyRow, copyRow.contains("—"))
    }

    @Test
    fun ourAppsAndDclGetProcessAndThreadSections() {
        val acc = BenchAccumulator()
        listOf(
            "K 100", "N 8", "T 0 0", "C 0 0",
            "P 30 0 vad.dashing.tbox", "H 30 31 0 main", "H 30 32 0 mbcan-state-apply",
            "P 40 0 ras.dashing.tbox.launcher", "H 40 41 0 RenderThread",
            "P 50 0 dclwm", "P 60 0 surfaceflinger",
            "T 100000 0", "C 80000 60000",
            "P 30 2000 vad.dashing.tbox", "H 30 31 1500 main", "H 30 32 500 mbcan-state-apply",
            "P 40 1000 ras.dashing.tbox.launcher", "H 40 41 1000 RenderThread",
            "P 50 100 dclwm", "P 60 3000 surfaceflinger",
            "E",
        ).forEach(acc::feed)
        val s = acc.snapshot()
        val text = BenchReport.text(run(), s)
        assertTrue(text, text.contains("== Наши приложения и DCL =="))
        assertTrue(text, text.contains("vad.dashing.tbox (TBox Monitor)"))
        assertTrue(text, text.contains("dclwm (DCL окна, root)"))
        assertTrue(text, text.contains("== Потоки ras.dashing.tbox.launcher (DCL лаунчер): 10.0 % ядра =="))
        assertTrue(text, text.contains("vad.dashing.tbox/mbcan-state-apply"))
        val ours = text.substringAfter("== Наши приложения и DCL ==").substringBefore("\n\n")
        assertTrue(ours, !ours.contains("surfaceflinger"))
        assertTrue(ours, ours.lines().last().contains("31.0") && ours.lines().last().contains("всего"))

        val a = BenchCompare.parse(BenchReport.summary(run(), s))
        assertEquals(5.0, a.threads.getValue("vad.dashing.tbox/mbcan-state-apply"), 1e-9)
        val compare = BenchCompare.text(a, a, "a", "b")
        assertTrue(compare, compare.contains("== Наши приложения и DCL, % ядра =="))
        assertTrue(compare, compare.contains("== Потоки vad.dashing.tbox (TBox Monitor), % ядра =="))
        val row = compare.lines().first { it.startsWith("mbcan-state-apply") }
        assertTrue(row, row.contains("5.00") && row.contains("+0.00"))
    }

    @Test
    fun labelsKeepServiceProcessSuffix() {
        assertEquals("vad.dashing.tbox:api (TBox Monitor, api)", OurApps.label("vad.dashing.tbox:api"))
        assertEquals("system_server", OurApps.label("system_server"))
        assertTrue(OurApps.isOurs("vad.dashing.voice"))
        assertTrue(!OurApps.isOurs("com.wt.launcher3"))
    }
}
