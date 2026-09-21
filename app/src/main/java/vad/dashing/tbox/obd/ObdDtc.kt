package vad.dashing.tbox.obd

/**
 * One stored diagnostic trouble code (Mode 03 / SAE J2012 letter+digit form).
 */
data class ObdDtc(
    /** e.g. `P0301`, `C0035`. */
    val code: String,
    val rawHigh: Int,
    val rawLow: Int,
) {
    companion object {
        private const val HEX = "0123456789ABCDEF"

        /**
         * Decode two payload bytes into an SAE DTC string.
         * Returns null for the all-zero padding code.
         */
        fun fromBytes(high: Int, low: Int): ObdDtc? {
            val h = high and 0xFF
            val l = low and 0xFF
            if (h == 0 && l == 0) return null
            val firstNibble = (h shr 6) and 0x03
            val letter = when (firstNibble) {
                0 -> 'P'
                1 -> 'C'
                2 -> 'B'
                else -> 'U'
            }
            val code = buildString(5) {
                append(letter)
                append(HEX[(h shr 4) and 0x03])
                append(HEX[h and 0x0F])
                append(HEX[(l shr 4) and 0x0F])
                append(HEX[l and 0x0F])
            }
            return ObdDtc(code = code, rawHigh = h, rawLow = l)
        }
    }
}
