package vad.dashing.tbox.utils

import android.app.ActivityManager
import android.os.StatFs
import java.io.BufferedReader
import java.io.FileReader

/**
 * Head-unit CPU load, RAM, and filesystem usage.
 * CPU usage is the busy share between two `/proc/stat` snapshots.
 * Idle includes iowait, so a core waiting on disk is not counted as busy.
 */
object SystemMetricsReader {

    data class CpuSnapshot(
        val totalTicks: Long,
        val idleTicks: Long,
    )

    data class CoreCpuSnapshot(
        val index: Int,
        val totalTicks: Long,
        val idleTicks: Long,
    )

    data class ProcStatSnapshot(
        val aggregate: CpuSnapshot?,
        val cores: List<CoreCpuSnapshot>,
    )

    /** Used bytes out of a total (RAM or a filesystem). [usedPercent] is 0…100. */
    data class ByteUsage(
        val usedBytes: Long,
        val totalBytes: Long,
        val usedPercent: Float,
    )

    fun readCpuSnapshot(statPath: String = "/proc/stat"): CpuSnapshot? {
        return try {
            BufferedReader(FileReader(statPath)).use { reader ->
                val line = reader.readLine() ?: return null
                parseCpuLine(line)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Aggregate `cpu` line plus each `cpuN` line, in core index order. */
    fun readProcStat(statPath: String = "/proc/stat"): ProcStatSnapshot? {
        return try {
            val text = BufferedReader(FileReader(statPath)).use { it.readText() }
            parseProcStat(text)
        } catch (_: Exception) {
            null
        }
    }

    /** Visible for tests — parse the aggregate `cpu …` line from `/proc/stat`. */
    fun parseCpuLine(line: String): CpuSnapshot? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0] != "cpu") return null
        return parseCpuCounters(parts)
    }

    /** Visible for tests — parse `/proc/stat` text (aggregate line and per-core lines). */
    fun parseProcStat(text: String): ProcStatSnapshot {
        var aggregate: CpuSnapshot? = null
        val cores = ArrayList<CoreCpuSnapshot>()
        for (raw in text.lineSequence()) {
            val parts = raw.trim().split(Regex("\\s+"))
            if (parts.isEmpty()) continue
            val name = parts[0]
            if (name == "cpu") {
                aggregate = parseCpuCounters(parts)
            } else if (name.length > 3 && name.startsWith("cpu") && name.drop(3).all { it.isDigit() }) {
                val index = name.drop(3).toIntOrNull() ?: continue
                val counters = parseCpuCounters(parts) ?: continue
                cores.add(
                    CoreCpuSnapshot(
                        index = index,
                        totalTicks = counters.totalTicks,
                        idleTicks = counters.idleTicks,
                    ),
                )
            }
        }
        cores.sortBy { it.index }
        return ProcStatSnapshot(aggregate = aggregate, cores = cores)
    }

    /**
     * user nice system idle iowait irq softirq steal guest guest_nice …
     * Idle includes iowait, matching [cpuUsagePercent] for the aggregate widget.
     */
    private fun parseCpuCounters(parts: List<String>): CpuSnapshot? {
        if (parts.size < 5) return null
        var total = 0L
        for (i in 1 until parts.size) {
            total += parts[i].toLongOrNull() ?: return null
        }
        val idle = parts[4].toLongOrNull() ?: return null
        val iowait = if (parts.size > 5) parts[5].toLongOrNull() ?: 0L else 0L
        return CpuSnapshot(totalTicks = total, idleTicks = idle + iowait)
    }

    fun cpuUsagePercent(previous: CpuSnapshot, current: CpuSnapshot): Float? {
        val totalDelta = current.totalTicks - previous.totalTicks
        val idleDelta = current.idleTicks - previous.idleTicks
        if (totalDelta <= 0L) return null
        val busy = 1.0 - (idleDelta.toDouble() / totalDelta.toDouble())
        return (busy * 100.0).coerceIn(0.0, 100.0).toFloat()
    }

    data class CoreUsage(
        val index: Int,
        val percent: Float?,
    )

    /** Per-core busy share. A core missing from [previous] yields a null percent. */
    fun coreUsagePercents(
        previous: List<CoreCpuSnapshot>,
        current: List<CoreCpuSnapshot>,
    ): List<CoreUsage> {
        val prevByIndex = previous.associateBy { it.index }
        return current.map { core ->
            val prev = prevByIndex[core.index]
            val percent = if (prev == null) {
                null
            } else {
                cpuUsagePercent(
                    CpuSnapshot(prev.totalTicks, prev.idleTicks),
                    CpuSnapshot(core.totalTicks, core.idleTicks),
                )
            }
            CoreUsage(index = core.index, percent = percent)
        }
    }

    fun freeRamPercent(availMemBytes: Long, totalMemBytes: Long): Float? {
        if (totalMemBytes <= 0L) return null
        if (availMemBytes < 0L) return null
        return ((availMemBytes.toDouble() / totalMemBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .toFloat()
    }

    fun freeRamPercent(activityManager: ActivityManager): Float? {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return freeRamPercent(info.availMem, info.totalMem)
    }

    /** Occupied RAM: total minus [ActivityManager.MemoryInfo.availMem]. */
    fun memoryUsage(availMemBytes: Long, totalMemBytes: Long): ByteUsage? {
        if (totalMemBytes <= 0L || availMemBytes < 0L) return null
        val used = (totalMemBytes - availMemBytes).coerceAtLeast(0L)
        return byteUsage(used, totalMemBytes)
    }

    fun readMemoryUsage(activityManager: ActivityManager): ByteUsage? {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return memoryUsage(info.availMem, info.totalMem)
    }

    /**
     * Occupied space on a filesystem. [StatFs.freeBytes] includes blocks
     * reserved for root, so the figure matches "how full is this volume".
     */
    fun readDiskUsage(path: String): ByteUsage? {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val free = stat.freeBytes
            if (total <= 0L || free < 0L) return null
            byteUsage((total - free).coerceAtLeast(0L), total)
        } catch (_: Exception) {
            null
        }
    }

    fun byteUsage(usedBytes: Long, totalBytes: Long): ByteUsage? {
        if (totalBytes <= 0L || usedBytes < 0L) return null
        val used = usedBytes.coerceAtMost(totalBytes)
        val percent = ((used.toDouble() / totalBytes.toDouble()) * 100.0)
            .coerceIn(0.0, 100.0)
            .toFloat()
        return ByteUsage(usedBytes = used, totalBytes = totalBytes, usedPercent = percent)
    }
}
