package vad.dashing.tbox.usbgnss

import vad.dashing.tbox.LocValues
import vad.dashing.tbox.utils.LocPayloadParser
import java.util.Date

/**
 * Merges streaming NMEA GGA/RMC/GSA/GST/VTG/ZDA lines into [LocValues] via [LocPayloadParser.parseNmea].
 */
class NmeaFixAccumulator {
    private var lastRmc: String? = null
    private var lastGga: String? = null
    private var lastGsa: String? = null
    private var lastGst: String? = null
    private var lastVtg: String? = null
    private var lastZda: String? = null

    fun reset() {
        lastRmc = null
        lastGga = null
        lastGsa = null
        lastGst = null
        lastVtg = null
        lastZda = null
    }

    /**
     * @return updated fix when the line is RMC/GGA/GSA/GST/VTG/ZDA; null for other/ignored lines.
     */
    fun onLine(line: String, updateTime: Date = Date()): LocValues? {
        val trimmed = line.trim()
        if (trimmed.length < 6 || trimmed[0] != '$') return null
        val upper = trimmed.uppercase()
        val type = upper.substringBefore(',').removePrefix("$")
        when {
            type.endsWith("RMC") -> lastRmc = trimmed
            type.endsWith("GGA") -> lastGga = trimmed
            type.endsWith("GSA") -> lastGsa = trimmed
            type.endsWith("GST") -> lastGst = trimmed
            type.endsWith("VTG") -> lastVtg = trimmed
            type.endsWith("ZDA") -> lastZda = trimmed
            else -> return null
        }
        // Need at least one fix sentence (RMC/GGA) before publishing auxiliary updates.
        if (lastRmc == null && lastGga == null) return null
        val blob = listOfNotNull(lastRmc, lastGga, lastGsa, lastGst, lastVtg, lastZda)
            .joinToString("\n")
        if (blob.isEmpty()) return null
        return LocPayloadParser.parseNmea(blob.toByteArray(Charsets.US_ASCII), updateTime)
    }
}
