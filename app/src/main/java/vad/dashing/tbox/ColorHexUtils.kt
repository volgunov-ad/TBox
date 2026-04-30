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
            8 -> h.toLong(16).toInt()                          // #AARRGGBB
            6 -> (0xFF000000 or h.toLong(16)).toInt()          // #RRGGBB → full alpha
            3 -> {                                              // #RGB → #FFRRGGBB
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

/**
 * Reads a color value from a [org.json.JSONObject] that may be stored as either
 * a hex string (`"#FF000000"`) or a legacy integer.
 * Returns the ARGB int, or [default] if the key is missing.
 */
fun parseColorFromJson(obj: org.json.JSONObject, key: String, default: Int): Int {
    if (!obj.has(key)) return default
    val raw = obj.opt(key)
    if (raw is String) {
        return colorHexToIntOrNull(raw) ?: default
    }
    if (raw is Number) {
        return raw.toInt()
    }
    return default
}

/**
 * Reads an optional nullable color from JSON (hex string or legacy int).
 * Returns `null` if the key is absent.
 */
fun parseOptionalColorFromJson(obj: org.json.JSONObject, key: String): Int? {
    if (!obj.has(key)) return null
    val raw = obj.opt(key)
    if (raw is String) return colorHexToIntOrNull(raw)
    if (raw is Number) return raw.toInt()
    return null
}
