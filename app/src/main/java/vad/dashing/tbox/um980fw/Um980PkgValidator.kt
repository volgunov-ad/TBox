package vad.dashing.tbox.um980fw

/**
 * Unicore UM980 `.pkg` preflight checks (magic from UPrecise capture).
 */
object Um980PkgValidator {
    const val MAGIC0: Int = 0xA5
    const val MAGIC1: Int = 0xA4
    const val MAGIC2: Int = 0xA3
    const val MAGIC3: Int = 0xA2

    /** Practical upper bound (~8 MiB); real images ~3 MiB. */
    const val MAX_PKG_SIZE: Long = 8L * 1024L * 1024L
    const val MIN_PKG_SIZE: Long = 32L * 1024L

    /**
     * @return error code or null if OK.
     * Codes: empty, too_small, too_large, bad_magic
     */
    fun validate(size: Long, first4: ByteArray?): String? {
        if (size <= 0L) return "empty"
        if (size < MIN_PKG_SIZE) return "too_small"
        if (size > MAX_PKG_SIZE) return "too_large"
        if (first4 == null || first4.size < 4) return "bad_magic"
        val ok = (first4[0].toInt() and 0xFF) == MAGIC0 &&
            (first4[1].toInt() and 0xFF) == MAGIC1 &&
            (first4[2].toInt() and 0xFF) == MAGIC2 &&
            (first4[3].toInt() and 0xFF) == MAGIC3
        return if (ok) null else "bad_magic"
    }

    /** Extract BuildNNNNN from filename like `UM980_R4.10Build25102.pkg`. */
    fun buildFromFileName(name: String): Int? {
        val m = Regex("""Build(\d+)""", RegexOption.IGNORE_CASE).find(name) ?: return null
        return m.groupValues[1].toIntOrNull()
    }

    fun buildFromVersionA(line: String): Int? {
        // #VERSIONA,...,25102,0,18,...;"UM980","R4.10Build25102",...
        val m = Regex("""Build(\d+)""", RegexOption.IGNORE_CASE).find(line)
        if (m != null) return m.groupValues[1].toIntOrNull()
        val parts = line.substringAfter(';', line).split(',')
        // Also try numeric field after FINE week
        val fields = line.substringAfter('#', line).split(';').firstOrNull()?.split(',') ?: return null
        // indices vary; look for 4–6 digit token before quoted product
        for (f in fields) {
            val t = f.trim()
            if (t.length in 4..6 && t.all { it.isDigit() }) {
                val v = t.toIntOrNull() ?: continue
                if (v in 1000..999999) return v
            }
        }
        return null
    }
}
