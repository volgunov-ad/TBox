package vad.dashing.camdbench

/**
 * Turns the sampler's text records (see camdbench_sampler.cpp) into per-process and
 * per-thread CPU usage. Fed line by line, so the service can tail the raw file live and
 * the final report reuses the same state.
 *
 * Process ticks are cumulative; a process seen in the first sample is its own baseline,
 * one that appears later (or a reused pid whose ticks went down) counts from zero.
 * The sampler prints a process only when its ticks changed, so a missing line means "idle".
 */
class BenchAccumulator {

    class Usage(val name: String, val avgPct: Double, val maxPct: Double)

    class Zone(val name: String, val avgC: Double, val maxC: Double)

    class Snapshot(
        val samples: Int,
        val durationSec: Double,
        val cores: Int,
        val cpuBusyPct: Double,
        val cpuBusyMaxPct: Double,
        val processes: List<Usage>,
        val threads: List<Usage>,
        val freqMhz: List<Double>,
        val zones: List<Zone>,
        val memAvailableMinMb: Long,
        val ended: Boolean,
    ) {
        fun process(name: String): Usage? = processes.firstOrNull { it.name == name }
    }

    class Live(val elapsedSec: Double, val cpuBusyPct: Double, val camdPct: Double, val grabPct: Double)

    private var clkTck = 100L
    private var cores = 1
    private val zoneNames = HashMap<Int, String>()

    private var samples = 0
    private var firstUptime = -1L
    private var prevUptime = -1L
    private var curUptime = -1L
    private var ended = false

    private var firstTotal = -1L
    private var firstIdle = -1L
    private var prevTotal = -1L
    private var prevIdle = -1L
    private var lastTotal = -1L
    private var lastIdle = -1L
    private var cpuBusyMax = 0.0
    private var lastIntervalBusy = 0.0

    private val procTicks = HashMap<Int, Long>()
    private val procNames = HashMap<Int, String>()
    private val procTotal = HashMap<String, Long>()
    private val procInterval = HashMap<String, Long>()
    private val procMax = HashMap<String, Double>()
    private val lastIntervalProc = HashMap<String, Double>()

    private val threadTicks = HashMap<Int, Long>()
    private val threadTotal = HashMap<String, Long>()
    private val threadInterval = HashMap<String, Long>()
    private val threadMax = HashMap<String, Double>()

    private val freqSum = HashMap<Int, Long>()
    private val freqCount = HashMap<Int, Int>()
    private val zoneSum = HashMap<Int, Long>()
    private val zoneCount = HashMap<Int, Int>()
    private val zoneMax = HashMap<Int, Long>()
    private var memMinKb = Long.MAX_VALUE

    fun feed(line: String) {
        val parts = line.trim().split(' ')
        if (parts.isEmpty()) return
        when (parts[0]) {
            "K" -> parts.getOrNull(1)?.toLongOrNull()?.takeIf { it > 0 }?.let { clkTck = it }
            "N" -> parts.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { cores = it }
            "ZT" -> {
                val zone = parts.getOrNull(1)?.toIntOrNull() ?: return
                zoneNames[zone] = parts.drop(2).joinToString(" ").ifBlank { "zone$zone" }
            }
            "T" -> startSample(parts.getOrNull(1)?.toLongOrNull() ?: return)
            "C" -> cpu(parts)
            "F" -> {
                val cpu = parts.getOrNull(1)?.toIntOrNull() ?: return
                val khz = parts.getOrNull(2)?.toLongOrNull() ?: return
                freqSum[cpu] = (freqSum[cpu] ?: 0L) + khz
                freqCount[cpu] = (freqCount[cpu] ?: 0) + 1
            }
            "Z" -> {
                val zone = parts.getOrNull(1)?.toIntOrNull() ?: return
                val milli = parts.getOrNull(2)?.toLongOrNull() ?: return
                zoneSum[zone] = (zoneSum[zone] ?: 0L) + milli
                zoneCount[zone] = (zoneCount[zone] ?: 0) + 1
                if (milli > (zoneMax[zone] ?: Long.MIN_VALUE)) zoneMax[zone] = milli
            }
            "M" -> parts.getOrNull(1)?.toLongOrNull()?.let { if (it < memMinKb) memMinKb = it }
            "P" -> process(parts)
            "H" -> thread(parts)
            "E" -> {
                closeInterval()
                ended = true
            }
        }
    }

    private fun startSample(uptime: Long) {
        closeInterval()
        if (firstUptime < 0) firstUptime = uptime
        curUptime = uptime
        samples += 1
    }

    private fun cpu(parts: List<String>) {
        val total = parts.getOrNull(1)?.toLongOrNull() ?: return
        val idle = parts.getOrNull(2)?.toLongOrNull() ?: return
        if (firstTotal < 0) {
            firstTotal = total
            firstIdle = idle
        }
        lastTotal = total
        lastIdle = idle
    }

    private fun process(parts: List<String>) {
        val pid = parts.getOrNull(1)?.toIntOrNull() ?: return
        val ticks = parts.getOrNull(2)?.toLongOrNull() ?: return
        val name = parts.drop(3).joinToString(" ").ifBlank { "pid$pid" }
        val prev = procTicks[pid]
        val delta = when {
            prev == null && samples <= 1 -> 0L
            prev == null -> ticks
            ticks < prev || procNames[pid] != name -> ticks
            else -> ticks - prev
        }
        procTicks[pid] = ticks
        procNames[pid] = name
        if (delta > 0) {
            procTotal[name] = (procTotal[name] ?: 0L) + delta
            procInterval[name] = (procInterval[name] ?: 0L) + delta
        } else {
            procTotal.putIfAbsent(name, 0L)
        }
    }

    private fun thread(parts: List<String>) {
        val pid = parts.getOrNull(1)?.toIntOrNull() ?: return
        val tid = parts.getOrNull(2)?.toIntOrNull() ?: return
        val ticks = parts.getOrNull(3)?.toLongOrNull() ?: return
        val threadName = parts.drop(4).joinToString(" ").ifBlank { "tid$tid" }
        val owner = procNames[pid] ?: "pid$pid"
        val key = "$owner/$threadName"
        val prev = threadTicks[tid]
        val delta = when {
            prev == null && samples <= 1 -> 0L
            prev == null -> ticks
            ticks < prev -> ticks
            else -> ticks - prev
        }
        threadTicks[tid] = ticks
        if (delta > 0) {
            threadTotal[key] = (threadTotal[key] ?: 0L) + delta
            threadInterval[key] = (threadInterval[key] ?: 0L) + delta
        } else {
            threadTotal.putIfAbsent(key, 0L)
        }
    }

    /** Closes the interval that ends at the current sample: peaks and the live view. */
    private fun closeInterval() {
        if (curUptime < 0) return
        if (prevUptime >= 0 && curUptime > prevUptime) {
            val seconds = (curUptime - prevUptime) / 1000.0
            if (prevTotal >= 0 && lastTotal > prevTotal) {
                val dt = (lastTotal - prevTotal).toDouble()
                val busy = 100.0 * (dt - (lastIdle - prevIdle)) / dt
                lastIntervalBusy = busy.coerceIn(0.0, 100.0)
                if (lastIntervalBusy > cpuBusyMax) cpuBusyMax = lastIntervalBusy
            }
            lastIntervalProc.clear()
            procInterval.forEach { (name, ticks) ->
                val pct = pct(ticks, seconds)
                lastIntervalProc[name] = pct
                if (pct > (procMax[name] ?: 0.0)) procMax[name] = pct
            }
            threadInterval.forEach { (key, ticks) ->
                val pct = pct(ticks, seconds)
                if (pct > (threadMax[key] ?: 0.0)) threadMax[key] = pct
            }
        }
        procInterval.clear()
        threadInterval.clear()
        prevUptime = curUptime
        prevTotal = lastTotal
        prevIdle = lastIdle
        curUptime = -1L
    }

    /** CPU time as percent of one core, like `top`. */
    private fun pct(ticks: Long, seconds: Double): Double =
        if (seconds <= 0.0) 0.0 else 100.0 * ticks / clkTck / seconds

    private fun durationSec(): Double {
        val last = if (curUptime >= 0) curUptime else prevUptime
        return if (firstUptime < 0 || last <= firstUptime) 0.0 else (last - firstUptime) / 1000.0
    }

    fun live(): Live = Live(
        elapsedSec = durationSec(),
        cpuBusyPct = lastIntervalBusy,
        camdPct = lastIntervalProc[CAMD_APP] ?: 0.0,
        grabPct = lastIntervalProc[CAMD_GRAB] ?: 0.0,
    )

    fun snapshot(): Snapshot {
        val seconds = durationSec()
        val busy = if (lastTotal > firstTotal && firstTotal >= 0) {
            val dt = (lastTotal - firstTotal).toDouble()
            (100.0 * (dt - (lastIdle - firstIdle)) / dt).coerceIn(0.0, 100.0)
        } else {
            0.0
        }
        val processes = procTotal.map { (name, ticks) ->
            Usage(name, pct(ticks, seconds), procMax[name] ?: 0.0)
        }.sortedByDescending { it.avgPct }
        val threads = threadTotal.map { (key, ticks) ->
            Usage(key, pct(ticks, seconds), threadMax[key] ?: 0.0)
        }.sortedByDescending { it.avgPct }
        val freq = (0 until cores).map { cpu ->
            val count = freqCount[cpu] ?: 0
            if (count == 0) 0.0 else (freqSum[cpu] ?: 0L) / count / 1000.0
        }
        val zones = zoneSum.keys.sorted().map { zone ->
            val count = zoneCount[zone] ?: 1
            Zone(
                zoneNames[zone] ?: "zone$zone",
                (zoneSum[zone] ?: 0L) / count / 1000.0,
                (zoneMax[zone] ?: 0L) / 1000.0,
            )
        }
        return Snapshot(
            samples = samples,
            durationSec = seconds,
            cores = cores,
            cpuBusyPct = busy,
            cpuBusyMaxPct = cpuBusyMax,
            processes = processes,
            threads = threads,
            freqMhz = freq,
            zones = zones,
            memAvailableMinMb = if (memMinKb == Long.MAX_VALUE) 0 else memMinKb / 1024,
            ended = ended,
        )
    }

    companion object {
        const val CAMD_APP = "vad.dashing.camd"
        const val CAMD_GRAB = "camd_grab"
    }
}
