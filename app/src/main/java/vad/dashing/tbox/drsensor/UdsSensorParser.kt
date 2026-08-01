package vad.dashing.tbox.drsensor

/**
 * Parses Mengbo FactoryMode / AIService `uds-sensor` lines:
 * `$GYR,<ts>,<temp>,<pitch>,<yaw>,<roll>` and `$A3D,<ts>,<pitch>,<yaw>,<roll>`.
 */
object UdsSensorParser {
    data class GyroSample(
        val timestamp: Double,
        val temperature: Float,
        val pitch: Float,
        val yaw: Float,
        val roll: Float,
    )

    data class AccelSample(
        val timestamp: Double,
        val pitch: Float,
        val yaw: Float,
        val roll: Float,
    )

    data class ParseResult(
        val gyros: List<GyroSample> = emptyList(),
        val accels: List<AccelSample> = emptyList(),
    )

    fun parse(raw: String): ParseResult {
        if (raw.isBlank()) return ParseResult()
        val gyros = ArrayList<GyroSample>()
        val accels = ArrayList<AccelSample>()
        for (line in raw.split("\r\n", "\n")) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val parts = trimmed.split(',')
            when {
                trimmed.contains("GYR", ignoreCase = true) && parts.size > 5 -> {
                    gyros += GyroSample(
                        timestamp = parts[1].toDoubleOrNull() ?: 0.0,
                        temperature = parts[2].toFloatOrNull() ?: 0f,
                        pitch = parts[3].toFloatOrNull() ?: 0f,
                        yaw = parts[4].toFloatOrNull() ?: 0f,
                        roll = parts[5].toFloatOrNull() ?: 0f,
                    )
                }
                trimmed.contains("A3D", ignoreCase = true) && parts.size > 4 -> {
                    accels += AccelSample(
                        timestamp = parts[1].toDoubleOrNull() ?: 0.0,
                        pitch = parts[2].toFloatOrNull() ?: 0f,
                        yaw = parts[3].toFloatOrNull() ?: 0f,
                        roll = parts[4].toFloatOrNull() ?: 0f,
                    )
                }
            }
        }
        return ParseResult(gyros = gyros, accels = accels)
    }
}
