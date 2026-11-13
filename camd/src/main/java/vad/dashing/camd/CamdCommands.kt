package vad.dashing.camd

sealed class CamdCommand {
    data class Open(
        val inputId: Int,
        val x: Float,
        val y: Float,
        val w: Float,
        val h: Float,
        val rotation: Int = 0,
        val flipH: Boolean = false,
        val flipV: Boolean = false,
    ) : CamdCommand()

    data object CloseAll : CamdCommand()
    data class Close(val inputId: Int) : CamdCommand()
    data object Status : CamdCommand()
    data class SetKeepAvm(val keep: Boolean) : CamdCommand()
}

object CamdCommands {
    fun parse(raw: String): CamdCommand? {
        val parts = raw.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        return when (parts[0].uppercase()) {
            "OPEN" -> parseOpen(parts)
            "CLOSE" -> when (parts.size) {
                1 -> CamdCommand.CloseAll
                2 -> parts[1].toIntOrNull()?.let { CamdCommand.Close(it) }
                else -> null
            }
            "STATUS" -> CamdCommand.Status
            "AVM" -> parseAvm(parts)
            else -> null
        }
    }

    private fun parseAvm(parts: List<String>): CamdCommand.SetKeepAvm? {
        if (parts.size != 2) return null
        return when (parts[1].uppercase()) {
            "ON", "1" -> CamdCommand.SetKeepAvm(true)
            "OFF", "0" -> CamdCommand.SetKeepAvm(false)
            else -> null
        }
    }

    /**
     * `OPEN id x y w h [rot [flipH [flipV]]]`
     *
     * Accepts 6…9 tokens. Size 6/7 stay as before (no flip).
     * Flip tokens require the rotation slot (even when rotation is 0).
     * flipH / flipV are 0 or 1; omitted flips default to false.
     */
    private fun parseOpen(parts: List<String>): CamdCommand.Open? {
        if (parts.size !in 6..9) return null
        val id = parts[1].toIntOrNull() ?: return null
        val x = parts[2].toFloatOrNull() ?: return null
        val y = parts[3].toFloatOrNull() ?: return null
        val w = parts[4].toFloatOrNull() ?: return null
        val h = parts[5].toFloatOrNull() ?: return null
        val rotation = if (parts.size >= 7) {
            when (parts[6].toIntOrNull()) {
                0, 90, 180, 270 -> parts[6].toInt()
                else -> return null
            }
        } else {
            0
        }
        val flipH = if (parts.size >= 8) {
            parseFlip(parts[7]) ?: return null
        } else {
            false
        }
        val flipV = if (parts.size >= 9) {
            parseFlip(parts[8]) ?: return null
        } else {
            false
        }
        if (KnownInputs.byId(id) == null) return null
        if (!fraction(x) || !fraction(y) || !fraction(w) || !fraction(h)) return null
        if (w < 0.05f || h < 0.05f) return null
        if (x + w > 1.02f || y + h > 1.02f) return null
        return CamdCommand.Open(id, x, y, w, h, rotation, flipH, flipV)
    }

    private fun parseFlip(raw: String): Boolean? =
        when (raw.toIntOrNull()) {
            0 -> false
            1 -> true
            else -> null
        }

    private fun fraction(value: Float): Boolean = value in 0f..1f
}
