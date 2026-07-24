package vad.dashing.tbox.esp

/**
 * Unicore UM980 helpers: NMEA rate mapping and GPS-guide command profile.
 * Unicore rate arg: 0.5 → 2 Hz, 1 → 1 Hz, 2 → 0.5 Hz, 0 → off.
 */
object Um980Commands {
    /** UI period seconds → Unicore NMEA rate number for GGA/RMC. */
    fun periodSecondsToNmeaRate(periodSec: Double): String = when {
        periodSec <= 0.0 -> "0"
        periodSec <= 0.5 -> "0.5"
        periodSec <= 1.0 -> "1"
        else -> "2"
    }

    fun nmeaRateToPeriodSeconds(rate: String): Double = when (rate.trim()) {
        "0", "0.0" -> 0.0
        "0.5" -> 0.5
        "1", "1.0" -> 1.0
        "2", "2.0" -> 2.0
        else -> 1.0
    }

    fun ggaRmcCommands(periodSec: Double): List<String> {
        val rate = periodSecondsToNmeaRate(periodSec)
        return listOf("GPGGA $rate", "GPRMC $rate")
    }

    /** Commands from «Улучшение работы GPS» (without COM baud). */
    fun gpsGuideProfileCommands(): List<String> = listOf(
        "GPGGA 0.5",
        "GPGSA 1",
        "GPGSV 1",
        "GPRMC 0.5",
        "GPZDA 2",
        "GPVTG 2",
        "CONFIG DGPS TIMEOUT 600",
        "CONFIG RTK TIMEOUT 0",
        "CONFIG RTK OFF",
        "CONFIG STANDALONE ENABLE",
        "CONFIG INS RESET",
        "MODE ROVER AUTOMOTIVE",
        "CONFIG MMP ENABLE",
        "CONFIG AGNSS ENABLE",
        "CONFIG ANTIJAM FORCE",
        "CONFIG SIGNALGROUP 2",
        "CONFIG PVTALG MULTI",
        "SAVECONFIG",
    )

    fun parseConfigSnapshot(lines: List<String>): Um980ConfigSnapshot {
        var mode: String? = null
        var dgpsTimeout: Int? = null
        var rtkOff: Boolean? = null
        var rtkTimeout: Int? = null
        var standalone: Boolean? = null
        var mmp: Boolean? = null
        var agnss: Boolean? = null
        var antijamForce: Boolean? = null
        var signalGroup: Int? = null
        var pvtAlgMulti: Boolean? = null
        for (raw in lines) {
            val line = raw.uppercase(LocaleUS)
            when {
                line.contains("MODE ROVER") -> {
                    mode = when {
                        line.contains("AUTOMOTIVE") -> "AUTOMOTIVE"
                        line.contains("UAV") -> "UAV"
                        else -> "ROVER"
                    }
                }
                line.contains("DGPS TIMEOUT") -> {
                    dgpsTimeout = line.substringAfter("TIMEOUT").trim().split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("RTK TIMEOUT") -> {
                    rtkTimeout = line.substringAfter("TIMEOUT").trim().split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("CONFIG RTK OFF") || line.contains("RTK OFF") -> rtkOff = true
                line.contains("STANDALONE ENABLE") -> standalone = true
                line.contains("STANDALONE DISABLE") -> standalone = false
                line.contains("MMP ENABLE") -> mmp = true
                line.contains("MMP DISABLE") -> mmp = false
                line.contains("AGNSS ENABLE") -> agnss = true
                line.contains("AGNSS DISABLE") -> agnss = false
                line.contains("ANTIJAM FORCE") -> antijamForce = true
                line.contains("SIGNALGROUP") -> {
                    signalGroup = Regex("SIGNALGROUP\\s+(\\d+)").find(line)?.groupValues?.getOrNull(1)
                        ?.toIntOrNull()
                }
                line.contains("PVTALG MULTI") -> pvtAlgMulti = true
            }
        }
        return Um980ConfigSnapshot(
            mode = mode,
            dgpsTimeout = dgpsTimeout,
            rtkOff = rtkOff,
            rtkTimeout = rtkTimeout,
            standalone = standalone,
            mmp = mmp,
            agnss = agnss,
            antijamForce = antijamForce,
            signalGroup = signalGroup,
            pvtAlgMulti = pvtAlgMulti,
            rawLines = lines,
        )
    }

    private val LocaleUS = java.util.Locale.US
}

data class Um980ConfigSnapshot(
    val mode: String? = null,
    val dgpsTimeout: Int? = null,
    val rtkOff: Boolean? = null,
    val rtkTimeout: Int? = null,
    val standalone: Boolean? = null,
    val mmp: Boolean? = null,
    val agnss: Boolean? = null,
    val antijamForce: Boolean? = null,
    val signalGroup: Int? = null,
    val pvtAlgMulti: Boolean? = null,
    val rawLines: List<String> = emptyList(),
)
