package vad.dashing.tbox.esp

/**
 * Unicore UM980 helpers: NMEA rate mapping and automotive presets.
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

    /** UART port wired ESP↔UM980 on this companion hardware. */
    const val UM980_COMPANION_COM = "COM3"

    /** Target SIGNALGROUP for presets (sent separately after SAVECONFIG if needed). */
    const val PRESET_SIGNALGROUP = 2

    /** Wait after CONFIG SIGNALGROUP before refresh (module reboot). */
    const val PRESET_SIGNALGROUP_REBOOT_MS = 12_000L

    /** Change UM980 serial baud on the companion UART port (does not SAVECONFIG). */
    fun comBaudCommand(baud: Int): String = "CONFIG $UM980_COMPANION_COM $baud"

    /** Safe defaults both presets restore (MASK/SBAS/GST/RTKHEIGHT). */
    private fun presetSafeDefaults(): List<String> = listOf(
        "MASK 5",
        "CONFIG SBAS DISABLE",
        "GPGST 0",
        "CONFIG SMOOTH RTKHEIGHT 0",
    )

    private fun presetNmeaBase(): List<String> = listOf(
        "GPGGA 0.5",
        "GPGSA 1",
        "GPGSV 1",
        "GPRMC 0.5",
        "GPZDA 2",
        "GPVTG 2",
    )

    /**
     * Max Precision preset (without SIGNALGROUP). Ends with SAVECONFIG.
     * SIGNALGROUP 2 is applied by the runner if the module is not already on group 2.
     */
    fun maxPrecisionPresetCommands(): List<String> =
        presetSafeDefaults() + presetNmeaBase() + listOf(
            "CONFIG DGPS TIMEOUT 300",
            "CONFIG RTK TIMEOUT 300",
            "CONFIG RTK USER_DEFAULTS",
            "CONFIG RTK MMPL 0",
            "CONFIG RTK RELIABILITY 3 3",
            "CONFIG STANDALONE ENABLE 100",
            "MODE ROVER",
            "CONFIG MMP ENABLE",
            "CONFIG AGNSS ENABLE",
            "CONFIG PPP ENABLE E6-HAS",
            "CONFIG ANTIJAM AUTO",
            "CONFIG PVTALG AUTO",
            "SAVECONFIG",
        )

    /**
     * Max Antispoof & Antijam preset (without SIGNALGROUP). Ends with SAVECONFIG.
     */
    fun maxAntispoofPresetCommands(): List<String> =
        presetSafeDefaults() + presetNmeaBase() + listOf(
            "CONFIG DGPS TIMEOUT 600",
            "CONFIG RTK TIMEOUT 0",
            "CONFIG RTK DISABLE",
            "CONFIG SMOOTH PSRVEL ENABLE",
            "CONFIG SMOOTH HEADING 5",
            "CONFIG STANDALONE ENABLE 3",
            "MODE ROVER AUTOMOTIVE",
            "CONFIG MMP ENABLE",
            "CONFIG AGNSS ENABLE",
            "CONFIG PPP DISABLE",
            "CONFIG ANTIJAM FORCE",
            "CONFIG PVTALG MULTI",
            "SAVECONFIG",
        )

    /**
     * Preview lines for UI: batch commands plus the conditional SIGNALGROUP step.
     * The last line is not sent as-is; the runner sends SIGNALGROUP only when needed.
     */
    fun presetPreviewLines(batchCommands: List<String>): List<String> =
        batchCommands + listOf(
            "CONFIG SIGNALGROUP $PRESET_SIGNALGROUP  # if not already $PRESET_SIGNALGROUP; +~12s reboot",
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
        var rtkAdrReliability: Int? = null
        var rtkMmpl: Int? = null
        var standalone: Boolean? = null
        var standaloneWaitSec: Int? = null
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
        var smoothHeading: Int? = null
        var psrVelDrPos: Boolean? = null
        var velStdThdEnabled: Boolean? = null
        var pppMode: String? = null
        var pppTimeout: Int? = null
        var pppDatum: String? = null
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
                line.contains("RTK MMPL") -> {
                    rtkMmpl = line.substringAfter("MMPL").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("RTK RELIABILITY") -> {
                    val parts = line.substringAfter("RELIABILITY").trim()
                        .split(Regex("\\s+|\\*"))
                        .mapNotNull { it.toIntOrNull() }
                    rtkReliability = parts.getOrNull(0)
                    rtkAdrReliability = parts.getOrNull(1)
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
                line.contains("STANDALONE ENABLE") -> {
                    standalone = true
                    standaloneWaitSec = line.substringAfter("ENABLE").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                }
                line.contains("STANDALONE DISABLE") -> {
                    standalone = false
                    standaloneWaitSec = null
                }
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
                    val after = line.substringAfter("RTKHEIGHT").trim()
                    smoothRtkHeight = when {
                        after.startsWith("DISABLE") -> 0
                        else -> after.split(Regex("\\s+|\\*")).firstOrNull()?.toIntOrNull()
                    }
                }
                line.contains("SMOOTH HEADING") -> {
                    val after = line.substringAfter("HEADING").trim()
                    smoothHeading = when {
                        after.startsWith("DISABLE") -> 0
                        else -> after.split(Regex("\\s+|\\*")).firstOrNull()?.toIntOrNull()
                    }
                }
                line.contains("PSRVELDRPOS ENABLE") -> psrVelDrPos = true
                line.contains("PSRVELDRPOS DISABLE") -> psrVelDrPos = false
                line.contains("VELSTDTHD ENABLE") -> velStdThdEnabled = true
                line.contains("VELSTDTHD DISABLE") -> velStdThdEnabled = false
                // CONFIG PPP (not PPPRTK / RTKASITPPP)
                !line.contains("PPPRTK") && !line.contains("RTKASITPPP") &&
                    Regex("""\bPPP\s+DISABLE\b""").containsMatchIn(line) -> {
                    pppMode = "DISABLE"
                }
                !line.contains("PPPRTK") && !line.contains("RTKASITPPP") &&
                    Regex("""\bPPP\s+ENABLE\b""").containsMatchIn(line) -> {
                    val after = line.substringAfter("ENABLE").trim()
                    pppMode = when {
                        after.startsWith("B2B-PPP") || after.startsWith("B2B") -> "B2b-PPP"
                        after.startsWith("E6-HAS") || after.startsWith("E6") -> "E6-HAS"
                        after.startsWith("L6MDCPPP") -> "L6MDCPPP"
                        after.startsWith("AUTO") -> "AUTO"
                        else -> after.split(Regex("\\s+|\\*")).firstOrNull()?.takeIf { it.isNotBlank() }
                    }
                }
                !line.contains("PPPRTK") && !line.contains("RTKASITPPP") &&
                    Regex("""\bPPP\s+TIMEOUT\b""").containsMatchIn(line) -> {
                    pppTimeout = line.substringAfter("TIMEOUT").trim()
                        .split(Regex("\\s+|\\*"))
                        .firstOrNull()?.toIntOrNull()
                    if (pppTimeout == 0) pppMode = pppMode ?: "DISABLE"
                }
                !line.contains("PPPRTK") && !line.contains("RTKASITPPP") &&
                    Regex("""\bPPP\s+DATUM\b""").containsMatchIn(line) -> {
                    pppDatum = when {
                        line.contains("WGS84") -> "WGS84"
                        line.contains("PPPORIGINAL") -> "PPPORIGINAL"
                        else -> line.substringAfter("DATUM").trim()
                            .split(Regex("\\s+|\\*"))
                            .firstOrNull()
                    }
                }
                isVersionaPayloadLine(raw) -> {
                    formatVersionLine(raw).takeIf { it.isNotBlank() }?.let { um980Version = it }
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
            rtkAdrReliability = rtkAdrReliability,
            rtkMmpl = rtkMmpl,
            standalone = standalone,
            standaloneWaitSec = standaloneWaitSec,
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
            smoothHeading = smoothHeading,
            psrVelDrPos = psrVelDrPos,
            velStdThdEnabled = velStdThdEnabled,
            pppMode = pppMode,
            pppTimeout = pppTimeout,
            pppDatum = pppDatum,
            um980Version = um980Version,
            rawLines = lines,
        )
    }

    private val LocaleUS = java.util.Locale.US

    /**
     * True for a Unicore ASCII VERSIONA **payload** (`#VERSIONA,…`), not the bare
     * command echo `VERSIONA` (which must not be shown as firmware version).
     */
    fun isVersionaPayloadLine(raw: String): Boolean =
        raw.trim().startsWith("#VERSIONA", ignoreCase = true)

    /**
     * Unicore ASCII: `#VERSIONA,...;"UM980","R4.10Build…","PN",…*crc`
     * Fields after `;`: product, firmware, auth, PN/SN, …
     * Prefer product + firmware. Empty if [raw] is not a payload line.
     */
    internal fun formatVersionLine(raw: String): String {
        if (!isVersionaPayloadLine(raw)) return ""
        val trimmed = raw.trim()
        val body = trimmed.substringAfter(';', missingDelimiterValue = "")
            .substringBefore('*')
            .trim()
        if (body.isBlank()) return ""
        val quoted = Regex("\"([^\"]+)\"").findAll(body).map { it.groupValues[1] }.toList()
        if (quoted.size >= 2) {
            return "${quoted[0]} ${quoted[1]}".take(160)
        }
        if (quoted.isNotEmpty()) return quoted[0].take(160)
        // Some dumps omit quotes: UM980,R4.10Build…,PN,…
        val parts = body.split(',').map { it.trim().removeSurrounding("\"") }.filter { it.isNotEmpty() }
        if (parts.size >= 2) return "${parts[0]} ${parts[1]}".take(160)
        if (parts.isNotEmpty()) return parts[0].take(160)
        return ""
    }
}

data class Um980ConfigSnapshot(
    val mode: String? = null,
    val dgpsTimeout: Int? = null,
    val rtkOff: Boolean? = null,
    val rtkTimeout: Int? = null,
    val rtkReliability: Int? = null,
    /** Second RELIABILITY arg (ADR threshold), when dump has `CONFIG RTK RELIABILITY a b`. */
    val rtkAdrReliability: Int? = null,
    /** 0 = normal, 1 = stringent. */
    val rtkMmpl: Int? = null,
    val standalone: Boolean? = null,
    /** Wait seconds from `STANDALONE ENABLE N` (3…100), not STANDALONE TIMEOUT. */
    val standaloneWaitSec: Int? = null,
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
    /** Dual-antenna heading smooth epochs; 0 = off. */
    val smoothHeading: Int? = null,
    val psrVelDrPos: Boolean? = null,
    val velStdThdEnabled: Boolean? = null,
    /** DISABLE / AUTO / B2b-PPP / E6-HAS / L6MDCPPP */
    val pppMode: String? = null,
    val pppTimeout: Int? = null,
    /** WGS84 / PPPORIGINAL */
    val pppDatum: String? = null,
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
