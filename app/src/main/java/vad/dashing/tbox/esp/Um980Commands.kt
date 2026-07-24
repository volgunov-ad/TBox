package vad.dashing.tbox.esp

/**
 * Unicore UM980 helpers: NMEA rate mapping and GPS-guide command profile.
 * Unicore rate arg: 0.5 → 2 Hz, 1 → 1 Hz, 2 → 0.5 Hz, 0 → off.
 * Commands aligned with Unicore N4 Reference Manual V2 EN R1.14.
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

    /** Recommended automotive profile (without COM baud). */
    fun gpsGuideProfileCommands(): List<String> = listOf(
        "GPGGA 0.5",
        "GPGSA 1",
        "GPGSV 1",
        "GPRMC 0.5",
        "GPZDA 2",
        "GPVTG 2",
        "MASK 10",
        "CONFIG DGPS TIMEOUT 600",
        "CONFIG RTK TIMEOUT 0",
        "CONFIG STANDALONE ENABLE",
        "MODE ROVER AUTOMOTIVE",
        "CONFIG MMP ENABLE",
        "CONFIG AGNSS ENABLE",
        "CONFIG SBAS ENABLE AUTO",
        "CONFIG ANTIJAM FORCE",
        "CONFIG SIGNALGROUP 2",
        "CONFIG PVTALG MULTI",
        "CONFIG SMOOTH PSRVEL ENABLE",
        "CONFIG SMOOTH RTKHEIGHT 10",
        "CONFIG PSRVELDRPOS ENABLE",
        "SAVECONFIG",
    )

    /** Read-back used after save/profile and by «Получить конфигурацию». */
    fun refreshSnapshotCommands(): List<String> = listOf(
        "CONFIG",
        "MODE",
        "MASK",
        "VERSIONA",
    )

    fun parseConfigSnapshot(lines: List<String>): Um980ConfigSnapshot {
        var mode: String? = null
        var dgpsTimeout: Int? = null
        var rtkOff: Boolean? = null
        var rtkTimeout: Int? = null
        var rtkReliability: Int? = null
        var standalone: Boolean? = null
        var standaloneTimeout: Int? = null
        var mmp: Boolean? = null
        var agnss: Boolean? = null
        var antijamMode: String? = null
        var signalGroup: Int? = null
        var pvtAlg: String? = null
        var sbasMode: String? = null
        var maskElevation: Int? = null
        var smoothPsrVel: Boolean? = null
        var smoothRtkHeight: Int? = null
        var psrVelDrPos: Boolean? = null
        var velStdThdEnabled: Boolean? = null
        var um980Version: String? = null
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
                line.contains("RTK RELIABILITY") -> {
                    rtkReliability = line.substringAfter("RELIABILITY").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("RTK TIMEOUT") -> {
                    rtkTimeout = line.substringAfter("TIMEOUT").trim().split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                    when {
                        rtkTimeout == null -> Unit
                        rtkTimeout == 0 -> rtkOff = true
                        else -> rtkOff = false
                    }
                }
                line.contains("RTK DISABLE") ||
                    line.contains("CONFIG RTK OFF") ||
                    Regex("""\bRTK\s+OFF\b""").containsMatchIn(line) -> rtkOff = true
                line.contains("RTK USER_DEFAULTS") -> rtkOff = false
                line.contains("STANDALONE TIMEOUT") -> {
                    standaloneTimeout = line.substringAfter("TIMEOUT").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("STANDALONE ENABLE") -> standalone = true
                line.contains("STANDALONE DISABLE") -> standalone = false
                line.contains("MMP ENABLE") -> mmp = true
                line.contains("MMP DISABLE") -> mmp = false
                line.contains("AGNSS ENABLE") -> agnss = true
                line.contains("AGNSS DISABLE") -> agnss = false
                line.contains("ANTIJAM FORCE") -> antijamMode = "FORCE"
                line.contains("ANTIJAM AUTO") -> antijamMode = "AUTO"
                line.contains("ANTIJAM DISABLE") -> antijamMode = "DISABLE"
                line.contains("SIGNALGROUP") -> {
                    signalGroup = Regex("SIGNALGROUP\\s+(\\d+)").find(line)?.groupValues?.getOrNull(1)
                        ?.toIntOrNull()
                }
                line.contains("PVTALG MULTI") -> pvtAlg = "MULTI"
                line.contains("PVTALG SINGLE") -> pvtAlg = "SINGLE"
                line.contains("PVTALG AUTO") -> pvtAlg = "AUTO"
                line.contains("SBAS DISABLE") -> sbasMode = "DISABLE"
                line.contains("SBAS ENABLE") -> {
                    sbasMode = when {
                        line.contains("SDCM") -> "SDCM"
                        line.contains("EGNOS") -> "EGNOS"
                        line.contains("WAAS") -> "WAAS"
                        line.contains("AUTO") -> "AUTO"
                        else -> "AUTO"
                    }
                }
                Regex("""\bMASK\s+(\d+)""").containsMatchIn(line) -> {
                    maskElevation = Regex("""\bMASK\s+(\d+)""").find(line)
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()
                }
                line.contains("SMOOTH PSRVEL ENABLE") -> smoothPsrVel = true
                line.contains("SMOOTH PSRVEL DISABLE") -> smoothPsrVel = false
                line.contains("SMOOTH RTKHEIGHT") -> {
                    smoothRtkHeight = line.substringAfter("RTKHEIGHT").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("PSRVELDRPOS ENABLE") -> psrVelDrPos = true
                line.contains("PSRVELDRPOS DISABLE") -> psrVelDrPos = false
                line.contains("VELSTDTHD ENABLE") -> velStdThdEnabled = true
                line.contains("VELSTDTHD DISABLE") -> velStdThdEnabled = false
                line.contains("VERSIONA") ||
                    (line.startsWith("#VERSION") && !line.contains("RESPONSE")) -> {
                    um980Version = formatVersionLine(raw)
                }
            }
            // Bare elevation in "$CONFIG,MASK,MASK 5.000000"
            if (maskElevation == null) {
                Regex("""MASK[,\s]+(\d+(?:\.\d+)?)""").find(line)?.groupValues?.getOrNull(1)
                    ?.toDoubleOrNull()?.toInt()?.let { maskElevation = it }
            }
        }
        return Um980ConfigSnapshot(
            mode = mode,
            dgpsTimeout = dgpsTimeout,
            rtkOff = rtkOff,
            rtkTimeout = rtkTimeout,
            rtkReliability = rtkReliability,
            standalone = standalone,
            standaloneTimeout = standaloneTimeout,
            mmp = mmp,
            agnss = agnss,
            antijamMode = antijamMode,
            signalGroup = signalGroup,
            pvtAlg = pvtAlg,
            sbasMode = sbasMode,
            maskElevation = maskElevation,
            smoothPsrVel = smoothPsrVel,
            smoothRtkHeight = smoothRtkHeight,
            psrVelDrPos = psrVelDrPos,
            velStdThdEnabled = velStdThdEnabled,
            um980Version = um980Version,
            rawLines = lines,
        )
    }

    private val LocaleUS = java.util.Locale.US

    /**
     * Unicore ASCII: `#VERSIONA,...;"UM980","R4.10Build…",…*crc`
     * Prefer product + firmware fields after `;`.
     */
    internal fun formatVersionLine(raw: String): String {
        val trimmed = raw.trim()
        val body = trimmed.substringAfter(';', missingDelimiterValue = trimmed)
            .substringBefore('*')
            .trim()
        val quoted = Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        if (quoted.size >= 2) {
            return "${quoted[0]} ${quoted[1]}".take(160)
        }
        if (quoted.isNotEmpty()) return quoted[0].take(160)
        return body.ifBlank { trimmed }.take(160)
    }
}

data class Um980ConfigSnapshot(
    val mode: String? = null,
    val dgpsTimeout: Int? = null,
    val rtkOff: Boolean? = null,
    val rtkTimeout: Int? = null,
    val rtkReliability: Int? = null,
    val standalone: Boolean? = null,
    val standaloneTimeout: Int? = null,
    val mmp: Boolean? = null,
    val agnss: Boolean? = null,
    /** FORCE / AUTO / DISABLE */
    val antijamMode: String? = null,
    val signalGroup: Int? = null,
    /** MULTI / AUTO / SINGLE */
    val pvtAlg: String? = null,
    /** DISABLE / AUTO / SDCM / EGNOS / WAAS */
    val sbasMode: String? = null,
    val maskElevation: Int? = null,
    val smoothPsrVel: Boolean? = null,
    val smoothRtkHeight: Int? = null,
    val psrVelDrPos: Boolean? = null,
    val velStdThdEnabled: Boolean? = null,
    val um980Version: String? = null,
    val rawLines: List<String> = emptyList(),
) {
    val antijamForce: Boolean? get() = when (antijamMode) {
        "FORCE" -> true
        "AUTO", "DISABLE" -> false
        else -> null
    }

    val pvtAlgMulti: Boolean? get() = when (pvtAlg) {
        "MULTI" -> true
        "AUTO", "SINGLE" -> false
        else -> null
    }
}
