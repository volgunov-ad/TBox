package vad.dashing.tbox.speedcam

/**
 * Parses SpeedCamOnline iGO / iGO-ext CSV:
 * `IDX,X,Y,TYPE,SPEED,DIRTYPE,DIRECTION` (X=lon, Y=lat).
 */
object SpeedCamIgoParser {
    private val HEADER = Regex(
        """^\s*IDX\s*,\s*X\s*,\s*Y\s*,\s*TYPE\s*,\s*SPEED\s*,\s*DIRTYPE\s*,\s*DIRECTION\s*$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(text: String): List<SpeedCamPoint> {
        val lines = text.lineSequence()
        val out = ArrayList<SpeedCamPoint>(4096)
        var seenHeader = false
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (!seenHeader) {
                if (HEADER.matches(line) || line.startsWith("IDX,", ignoreCase = true)) {
                    seenHeader = true
                    continue
                }
                // Some dumps omit header — try parse as data.
            }
            parseDataLine(line)?.let { out.add(it) }
        }
        return out
    }

    fun parseDataLine(line: String): SpeedCamPoint? {
        val parts = line.split(',')
        if (parts.size < 7) return null
        val id = parts[0].trim().toIntOrNull() ?: return null
        val lon = parts[1].trim().toDoubleOrNull() ?: return null
        val lat = parts[2].trim().toDoubleOrNull() ?: return null
        val type = parts[3].trim().toIntOrNull() ?: return null
        val speed = parts[4].trim().toIntOrNull() ?: 0
        val dirType = parts[5].trim().toIntOrNull() ?: 0
        val direction = parts[6].trim().toIntOrNull() ?: 0
        if (!lon.isFinite() || !lat.isFinite()) return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return SpeedCamPoint(
            id = id,
            lon = lon,
            lat = lat,
            typeCode = type,
            speedKmh = speed.coerceAtLeast(0),
            dirType = dirType.coerceIn(0, 2),
            directionDeg = ((direction % 360) + 360) % 360,
        )
    }

    fun looksLikeIgoCsv(text: String): Boolean {
        val sample = text.lineSequence().take(5).joinToString("\n")
        if (HEADER.containsMatchIn(sample) || sample.startsWith("IDX,", ignoreCase = true)) {
            return true
        }
        // Headerless: first non-empty line parses as a point.
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            return parseDataLine(line) != null
        }
        return false
    }
}
