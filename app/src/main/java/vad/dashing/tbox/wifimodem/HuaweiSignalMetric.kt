package vad.dashing.tbox.wifimodem

/**
 * Parses HiLink radio metrics that often include units, e.g. `-71dBm`, `-9.0dB`, `19dB`.
 */
object HuaweiSignalMetric {
    fun parseInt(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim()
        val num = buildString {
            var i = 0
            if (s[i] == '-' || s[i] == '+') {
                append(s[i])
                i++
            }
            var seenDot = false
            while (i < s.length) {
                val c = s[i]
                when {
                    c.isDigit() -> append(c)
                    c == '.' && !seenDot -> {
                        append(c)
                        seenDot = true
                    }
                    else -> break
                }
                i++
            }
        }
        return num.toDoubleOrNull()?.toInt()
    }
}
