package vad.dashing.tbox.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SystemMetricsReaderTest {

    @Test
    fun parseCpuLine_readsAggregateIdleAndTotal() {
        val snap = SystemMetricsReader.parseCpuLine(
            "cpu  100 20 30 400 50 5 5 0 0 0",
        )
        requireNotNull(snap)
        // 100+20+30+400+50+5+5+0+0+0 = 610; idle+iowait = 400+50 = 450
        assertEquals(610L, snap.totalTicks)
        assertEquals(450L, snap.idleTicks)
    }

    @Test
    fun parseCpuLine_rejectsNonCpuOrShortLines() {
        assertNull(SystemMetricsReader.parseCpuLine("cpu0 1 2 3 4"))
        assertNull(SystemMetricsReader.parseCpuLine("cpu 1 2"))
        assertNull(SystemMetricsReader.parseCpuLine(""))
    }

    @Test
    fun cpuUsagePercent_computesBusyShare() {
        val prev = SystemMetricsReader.CpuSnapshot(totalTicks = 1000, idleTicks = 800)
        val curr = SystemMetricsReader.CpuSnapshot(totalTicks = 1100, idleTicks = 850)
        // totalDelta=100, idleDelta=50 → busy 50%
        assertEquals(50f, SystemMetricsReader.cpuUsagePercent(prev, curr)!!, 0.01f)
    }

    @Test
    fun cpuUsagePercent_fullIdleAndFullBusy() {
        val prev = SystemMetricsReader.CpuSnapshot(1000, 900)
        assertEquals(
            0f,
            SystemMetricsReader.cpuUsagePercent(
                prev,
                SystemMetricsReader.CpuSnapshot(1100, 1000),
            )!!,
            0.01f,
        )
        assertEquals(
            100f,
            SystemMetricsReader.cpuUsagePercent(
                prev,
                SystemMetricsReader.CpuSnapshot(1100, 900),
            )!!,
            0.01f,
        )
    }

    @Test
    fun cpuUsagePercent_nullWhenNoProgress() {
        val snap = SystemMetricsReader.CpuSnapshot(1000, 800)
        assertNull(SystemMetricsReader.cpuUsagePercent(snap, snap))
    }

    @Test
    fun parseProcStat_readsAggregateAndCoresInOrder() {
        val snap = SystemMetricsReader.parseProcStat(
            """
            cpu  10 0 10 70 10 0 0 0 0 0
            cpu1 4 0 4 30 2 0 0 0 0 0
            intr 1 2 3
            cpu0 6 0 6 40 8 0 0 0 0 0
            """.trimIndent() + "\n",
        )
        requireNotNull(snap.aggregate)
        assertEquals(100L, snap.aggregate.totalTicks)
        assertEquals(80L, snap.aggregate.idleTicks)
        assertEquals(listOf(0, 1), snap.cores.map { it.index })
        assertEquals(60L, snap.cores[0].totalTicks)
        assertEquals(48L, snap.cores[0].idleTicks)
        assertEquals(40L, snap.cores[1].totalTicks)
        assertEquals(32L, snap.cores[1].idleTicks)
    }

    @Test
    fun coreUsagePercents_matchesPerCoreBusyShare() {
        val previous = listOf(
            SystemMetricsReader.CoreCpuSnapshot(index = 0, totalTicks = 1000, idleTicks = 800),
            SystemMetricsReader.CoreCpuSnapshot(index = 1, totalTicks = 1000, idleTicks = 500),
        )
        val current = listOf(
            SystemMetricsReader.CoreCpuSnapshot(index = 0, totalTicks = 1100, idleTicks = 850),
            SystemMetricsReader.CoreCpuSnapshot(index = 1, totalTicks = 1100, idleTicks = 500),
            SystemMetricsReader.CoreCpuSnapshot(index = 2, totalTicks = 50, idleTicks = 10),
        )
        val usage = SystemMetricsReader.coreUsagePercents(previous, current)
        assertEquals(3, usage.size)
        assertEquals(50f, usage[0].percent!!, 0.01f)
        assertEquals(100f, usage[1].percent!!, 0.01f)
        assertNull(usage[2].percent)
    }

    @Test
    fun memoryUsage_isOccupiedShare() {
        val usage = SystemMetricsReader.memoryUsage(availMemBytes = 250L, totalMemBytes = 1000L)
        requireNotNull(usage)
        assertEquals(750L, usage.usedBytes)
        assertEquals(1000L, usage.totalBytes)
        assertEquals(75f, usage.usedPercent, 0.01f)
        assertNull(SystemMetricsReader.memoryUsage(10L, 0L))
        assertNull(SystemMetricsReader.memoryUsage(-1L, 1000L))
    }

    @Test
    fun byteUsage_clampsAboveTotal() {
        val usage = SystemMetricsReader.byteUsage(usedBytes = 1500L, totalBytes = 1000L)
        requireNotNull(usage)
        assertEquals(1000L, usage.usedBytes)
        assertEquals(100f, usage.usedPercent, 0.01f)
        assertNull(SystemMetricsReader.byteUsage(-1L, 1000L))
    }

    @Test
    fun readProcStat_fromTempFile() {
        val file = File.createTempFile("proc_stat_cores_", ".txt")
        try {
            file.writeText(
                """
                cpu  10 0 10 80 0 0 0 0 0 0
                cpu0 5 0 5 40 0 0 0 0 0 0
                """.trimIndent() + "\n",
            )
            val snap = SystemMetricsReader.readProcStat(file.absolutePath)
            requireNotNull(snap)
            assertEquals(1, snap.cores.size)
            assertEquals(0, snap.cores[0].index)
            assertEquals(50L, snap.cores[0].totalTicks)
        } finally {
            assertTrue(file.delete() || !file.exists())
        }
    }

    @Test
    fun freeRamPercent_basicAndGuards() {
        assertEquals(25f, SystemMetricsReader.freeRamPercent(250L, 1000L)!!, 0.01f)
        assertEquals(100f, SystemMetricsReader.freeRamPercent(1000L, 1000L)!!, 0.01f)
        assertNull(SystemMetricsReader.freeRamPercent(10L, 0L))
        assertNull(SystemMetricsReader.freeRamPercent(-1L, 1000L))
    }

    @Test
    fun readCpuSnapshot_fromTempProcStatFile() {
        val file = File.createTempFile("proc_stat_", ".txt")
        try {
            file.writeText(
                """
                cpu  10 0 10 80 0 0 0 0 0 0
                cpu0 5 0 5 40 0 0 0 0 0 0
                """.trimIndent() + "\n",
            )
            val snap = SystemMetricsReader.readCpuSnapshot(file.absolutePath)
            requireNotNull(snap)
            assertEquals(100L, snap.totalTicks)
            assertEquals(80L, snap.idleTicks)
        } finally {
            assertTrue(file.delete() || !file.exists())
        }
    }

    @Test
    fun parseThermalZoneTemperature_millidegreesToCelsius() {
        assertEquals(47.9f, SystemMetricsReader.parseThermalZoneTemperature("47900")!!, 0.001f)
        assertEquals(25f, SystemMetricsReader.parseThermalZoneTemperature("25000\n")!!, 0.001f)
        assertEquals(-40f, SystemMetricsReader.parseThermalZoneTemperature(" -40000 ")!!, 0.001f)
    }

    @Test
    fun parseThermalZoneTemperature_rejectsInvalidValues() {
        assertNull(SystemMetricsReader.parseThermalZoneTemperature(""))
        assertNull(SystemMetricsReader.parseThermalZoneTemperature("n/a"))
        // -40000 stays valid (disconnected PA sensor), but -67 °C and below is dropped.
        assertNull(SystemMetricsReader.parseThermalZoneTemperature("-67000"))
        assertNull(SystemMetricsReader.parseThermalZoneTemperature("150000"))
    }

    @Test
    fun readThermalZoneTemperature_fromTempFileAndMissingPath() {
        val dir = File.createTempFile("thermal_zone_probe_", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            File(dir, "temp").writeText("48200\n")
            assertEquals(48.2f, SystemMetricsReader.readThermalZoneTemperature(dir.absolutePath)!!, 0.001f)
            assertNull(SystemMetricsReader.readThermalZoneTemperature(dir.absolutePath + "/nope"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun thermalReaders_pickA9Zones() {
        val dir = File.createTempFile("thermal_cls_", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            // zone0 = soc (unreadable temp), zone1 = aoss-usr, zone6 = cpu-1-0-usr,
            // zone10 = gpu-usr; matching the A9 layout.
            writeZone(dir, "thermal_zone0", "soc", null)
            writeZone(dir, "thermal_zone1", "aoss-usr", 48200)
            writeZone(dir, "thermal_zone6", "cpu-1-0-usr", 48500)
            writeZone(dir, "thermal_zone7", "cpuss-1-usr", 47900)
            writeZone(dir, "thermal_zone10", "gpu-usr", 47600)
            writeZone(dir, "thermal_zone11", "wlan-usr", 47300)

            assertEquals(48.2f, SystemMetricsReader.readSocTemperature(dir.absolutePath)!!, 0.001f)
            assertEquals(48.5f, SystemMetricsReader.readCpuTemperature(dir.absolutePath)!!, 0.001f)
            assertEquals(47.6f, SystemMetricsReader.readGpuTemperature(dir.absolutePath)!!, 0.001f)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun thermalReaders_pickA10Zones() {
        val dir = File.createTempFile("thermal_cls_a10_", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            // A10 has a single cls0 zone; no CPU/GPU zones.
            writeZone(dir, "thermal_zone0", "cls0", 36000)

            assertEquals(36f, SystemMetricsReader.readSocTemperature(dir.absolutePath)!!, 0.001f)
            assertNull(SystemMetricsReader.readCpuTemperature(dir.absolutePath))
            assertNull(SystemMetricsReader.readGpuTemperature(dir.absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun thermalReaders_missingDirectory() {
        assertNull(SystemMetricsReader.readSocTemperature("no/such/dir"))
        assertNull(SystemMetricsReader.readCpuTemperature("no/such/dir"))
        assertNull(SystemMetricsReader.readGpuTemperature("no/such/dir"))
        assertEquals(emptyList<SystemMetricsReader.ThermalZone>(), SystemMetricsReader.scanThermalZones("no/such/dir"))
    }

    @Test
    fun scanThermalZones_skipsUnreadableTemp() {
        val dir = File.createTempFile("thermal_scan_", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        try {
            writeZone(dir, "thermal_zone0", "soc", null)
            writeZone(dir, "thermal_zone1", "aoss-usr", 48200)
            val zones = SystemMetricsReader.scanThermalZones(dir.absolutePath)
            assertEquals(2, zones.size)
            assertEquals("aoss-usr", zones[1].type)
            assertEquals(48.2f, zones[1].temperatureCelsius!!, 0.001f)
            assertNull(zones[0].temperatureCelsius)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun writeZone(dir: File, name: String, type: String, milliTemp: Long?) {
        val zone = File(dir, name)
        zone.mkdirs()
        File(zone, "type").writeText(type + "\n")
        if (milliTemp == null) {
            // Mimics EINVAL on read: create a file that yields no valid content.
            File(zone, "temp").writeText("")
        } else {
            File(zone, "temp").writeText(milliTemp.toString() + "\n")
        }
    }
}
