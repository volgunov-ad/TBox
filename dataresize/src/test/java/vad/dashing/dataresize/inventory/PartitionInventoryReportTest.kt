package vad.dashing.dataresize.inventory

import org.junit.Assert.assertTrue
import org.junit.Test

class PartitionInventoryReportTest {
    private fun sampleInventory() = PartitionInventory(
        takenAt = "2026-11-06 12:04",
        model = "Jetour Dashing HU",
        displayId = "…1.0.5…",
        incremental = "JenkinsXXXXXXXX",
        hardware = "ac8x",
        diskSectors = 61_079_552L,
        lastUsable = 61_079_500L,
        freeTailSectors = 2048L * 328L,
        partitions = listOf(
            PartitionInfo(
                name = "userdata",
                firstLba = 30_445_568L,
                lastLba = 30_445_568L + 2048L * 2048L - 1,
                sectors = 2048L * 2048L,
                neighborRight = "media",
                fstype = "ext4",
                mountPoint = "/data",
                mountOptions = "rw,noatime",
                mounted = true,
                usedKiB = 920L * 1024L,
                freeKiB = 1100L * 1024L,
                sizeKiB = 2020L * 1024L,
                topDirs = listOf("app" to 410L * 1024L, "data" to 380L * 1024L),
                notes = listOf("обычный Android /data; APK в app/"),
                sampleFiles = listOf("app", "data"),
            ),
            PartitionInfo(
                name = "media",
                firstLba = 34_639_872L,
                lastLba = 34_639_872L + 2048L * 4096L - 1,
                sectors = 2048L * 4096L,
                neighborRight = "map",
                fstype = "ext4",
                mountPoint = "/mnt/media",
                mountOptions = "rw",
                mounted = true,
                usedKiB = 48L * 1024L,
                freeKiB = 4000L * 1024L,
                sizeKiB = 4096L * 1024L,
                topDirs = listOf("LOST.DIR" to 0L),
                notes = listOf("почти пуст", "нет Maps/navi на верхнем уровне"),
                sampleFiles = listOf("LOST.DIR"),
            ),
            PartitionInfo(
                name = "intsd",
                firstLba = 43_061_248L,
                lastLba = 43_061_248L + 2048L * 7678L - 1,
                sectors = 2048L * 7678L,
                neighborRight = "miscbk",
                fstype = "vfat",
                mountPoint = "/mnt/media_rw/internal_sdcard",
                mountOptions = "rw",
                mounted = true,
                usedKiB = 3100L * 1024L,
                freeKiB = 4500L * 1024L,
                sizeKiB = 7678L * 1024L,
                topDirs = listOf("Maps" to 1800L * 1024L, "Music" to 700L * 1024L),
                notes = listOf("есть Maps (навигация/карты)", "есть Music"),
                sampleFiles = listOf("Maps", "Music"),
            ),
            PartitionInfo(
                name = "cache",
                firstLba = 20_795_392L,
                lastLba = 20_795_392L + 2048L * 4608L - 1,
                sectors = 2048L * 4608L,
                neighborRight = "userdata",
                fstype = "ext4",
                mountPoint = "/cache",
                mountOptions = "rw",
                mounted = true,
                usedKiB = 1700L * 1024L,
                freeKiB = 2800L * 1024L,
                sizeKiB = 4608L * 1024L,
                topDirs = listOf("fota" to 1460L * 1024L),
                notes = listOf("есть /cache/fota (~1460 МиБ)"),
                sampleFiles = listOf("fota/update_os.zip"),
                fotaKiB = 1460L * 1024L,
            ),
            PartitionInfo(
                name = "map",
                firstLba = 43_028_480L,
                lastLba = 43_028_480L + 2048L * 16L - 1,
                sectors = 2048L * 16L,
                neighborRight = "intsd",
                fstype = null,
                mountPoint = null,
                mountOptions = null,
                mounted = false,
                usedKiB = null,
                freeKiB = null,
                sizeKiB = null,
                topDirs = emptyList(),
                notes = listOf("служебный раздел map (не путать с картами на intsd)"),
                sampleFiles = emptyList(),
            ),
            PartitionInfo(
                name = "miscbk",
                firstLba = 58_785_792L,
                lastLba = 58_785_792L + 2048L * 792L - 1,
                sectors = 2048L * 792L,
                neighborRight = "хвост",
                fstype = null,
                mountPoint = null,
                mountOptions = null,
                mounted = false,
                usedKiB = null,
                freeKiB = null,
                sizeKiB = null,
                topDirs = emptyList(),
                notes = listOf("OEM backup/калибровки — не трогать без отдельной задачи"),
                sampleFiles = emptyList(),
            ),
        ),
        userdataMediaAdjacent = true,
    )

    @Test
    fun formatContainsRussianHeadingsAndVerdicts() {
        val inv = sampleInventory()
        val verdicts = PartitionInventoryVerdicts.from(inv)
        val path = "/mnt/media_rw/ABCD-1234/tbox_part_report_1.0.5_20261106-1204.md"
        val md = PartitionInventoryReport.format(inv, verdicts, path)
        assertTrue(md.startsWith("# Отчёт по разделам ГУ (TBox)"))
        assertTrue(md.contains("## Вердикты (сводка)"))
        assertTrue(md.contains("## GPT (интересные записи)"))
        assertTrue(md.contains("## По разделам"))
        assertTrue(md.contains("### userdata → `/data`"))
        assertTrue(md.contains("### media"))
        assertTrue(md.contains("Сжать media"))
        assertTrue(md.contains("Трогать intsd"))
        assertTrue(md.contains(path))
        assertTrue(md.contains("только чтение"))
        assertTrue(md.contains("userdata ↔ media смежны: **да**"))
        assertTrue(md.contains("~328 МиБ"))
    }

    @Test
    fun summaryTextIsPlainAndCopyable() {
        val inv = sampleInventory()
        val verdicts = PartitionInventoryVerdicts.from(inv)
        val summary = PartitionInventoryReport.summaryText(verdicts, "/mnt/media_rw/ABCD-1234/report.md")
        assertTrue(summary.contains("Паспорт разделов"))
        assertTrue(summary.contains("Файл:"))
        assertTrue(!summary.contains("| Тема |"))
        assertTrue(summary.lines().any { it.startsWith("• ") })
    }

    @Test
    fun parseDuAndMountsHelpers() {
        val mounts = PartitionInventoryCollector.parseMounts(
            """
            /dev/block/userdata /data ext4 rw,seclabel 0 0
            /dev/block/cache /cache ext4 rw 0 0
            /dev/block/intsd /mnt/media_rw/internal_sdcard vfat rw 0 0
            """.trimIndent(),
        )
        assertTrue(mounts.any { it.point == "/data" && it.fstype == "ext4" })
        val du = PartitionInventoryCollector.parseDuLines(
            """
            419840	app
            389120	data
            97280	dalvik-cache
            """.trimIndent(),
        )
        assertTrue(du.first().first == "app")
        assertTrue(du.first().second == 419840L)
    }
}
