package vad.dashing.tbox

/**
 * Converts an ARGB [Int] color to a hex string like `#AARRGGBB`.
 */
fun colorIntToHex(color: Int): String {
    return String.format("#%08X", color)
}

/**
 * Parses a hex color string (`#AARRGGBB`, `#RRGGBB`, or `#RGB`) into an ARGB [Int].
 * Returns `null` if the string is not a valid hex color.
 */
fun colorHexToIntOrNull(hex: String): Int? {
    val h = hex.trim().removePrefix("#")
    return try {
        when (h.length) {
            8 -> h.toLong(16).toInt()
            6 -> (0xFF000000 or h.toLong(16)).toInt()
            3 -> {
                val r = h[0]
                val g = h[1]
                val b = h[2]
                "FF$r$r$g$g$b$b".toLong(16).toInt()
            }
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
