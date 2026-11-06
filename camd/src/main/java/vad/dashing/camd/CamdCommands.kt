package vad.dashing.camd

sealed class CamdCommand {
    data class Open(
        val inputId: Int,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float
    ) : CamdCommand()

    data object Close : CamdCommand()
    data object Status : CamdCommand()
}

object CamdCommands {
    fun parse(raw: String): CamdCommand? {
        val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return when (parts[0].uppercase()) {
            "OPEN" -> parseOpen(parts)
            "CLOSE" -> CamdCommand.Close
            "STATUS" -> CamdCommand.Status
            else -> null
        }
    }

    private fun parseOpen(parts: List<String>): CamdCommand.Open? {
        if (parts.size != 6) return null
        val id = parts[1].toIntOrNull() ?: return null
        val x = parts[2].toFloatOrNull() ?: return null
        val y = parts[3].toFloatOrNull() ?: return null
        val w = parts[4].toFloatOrNull() ?: return null
        val h = parts[5].toFloatOrNull() ?: return null
        if (KnownInputs.byId(id) == null) return null
        if (!fraction(x) || !fraction(y) || !fraction(w) || !fraction(h)) return null
        if (w < 0.05f || h < 0.05f) return null
        if (x + w > 1.02f || y + h > 1.02f) return null
        return CamdCommand.Open(id, x, y, w, h)
    }

    private fun fraction(value: Float): Boolean = value in 0f..1f
}
