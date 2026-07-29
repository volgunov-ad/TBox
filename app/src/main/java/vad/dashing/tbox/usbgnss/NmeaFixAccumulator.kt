package vad.dashing.tbox.usbgnss

import vad.dashing.tbox.LocValues
import vad.dashing.tbox.utils.LocPayloadParser
import java.util.Date

/**
 * Merges streaming NMEA GGA/RMC lines into [LocValues] via [LocPayloadParser.parseNmea].
 */
class NmeaFixAccumulator {
    private var lastRmc: String? = null
    private var lastGga: String? = null

    fun reset() {
        lastRmc = null
        lastGga = null
    }

    /**
     * @return updated fix when the line is RMC/GGA; null for other/ignored lines.
     */
    fun onLine(line: String, updateTime: Date = Date()): LocValues? {
        val trimmed = line.trim()
        if (trimmed.length < 6 || trimmed[0] != '$') return null
        val upper = trimmed.uppercase()
        val type = upper.substringBefore(',').removePrefix("$")
        when {
            type.endsWith("RMC") -> lastRmc = trimmed
            type.endsWith("GGA") -> lastGga = trimmed
            else -> return null
        }
        val blob = listOfNotNull(lastRmc, lastGga).joinToString("\n")
        if (blob.isEmpty()) return null
        return LocPayloadParser.parseNmea(blob.toByteArray(Charsets.US_ASCII), updateTime)
    }
}
