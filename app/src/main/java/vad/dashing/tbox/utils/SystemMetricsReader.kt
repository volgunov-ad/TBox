package vad.dashing.tbox.utils

import android.app.ActivityManager
import java.io.BufferedReader
import java.io.FileReader

/**
 * Pure helpers for head-unit CPU load and free RAM percentage.
 * CPU usage is derived from two consecutive `/proc/stat` snapshots (aggregate `cpu` line).
 */
object SystemMetricsReader {

    data class CpuSnapshot(
        val totalTicks: Long,
        val idleTicks: Long,
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

    /** Visible for tests — parse the aggregate `cpu …` line from `/proc/stat`. */
    fun parseCpuLine(line: String): CpuSnapshot? {
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.isEmpty() || parts[0] != "cpu") return null
        // user nice system idle iowait irq softirq steal guest guest_nice …
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
}
