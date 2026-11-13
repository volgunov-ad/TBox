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
        val crop: CamdCrop? = null,
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
     * `OPEN id x y w h [rot [flipH [flipV]]] [crop=cx,cy,cw,ch]`
     *
     * Accepts 6…9 positional tokens plus at most one `crop=` token (case-insensitive prefix).
     * Size 6/7 stay as before (no flip).
     * Flip tokens require the rotation slot (even when rotation is 0).
     * flipH / flipV are 0 or 1; omitted flips default to false.
     * crop fractions are 0…1 in the lens-corrected frame; crop is stretched to fill the window.
     */
    private fun parseOpen(parts: List<String>): CamdCommand.Open? {
        val cropTokens = parts.filter { isCropToken(it) }
        if (cropTokens.size > 1) return null
        val crop = cropTokens.firstOrNull()?.let { parseCrop(it) ?: return null }
        val positional = parts.filterNot { isCropToken(it) }
        if (positional.size !in 6..9) return null
        val id = positional[1].toIntOrNull() ?: return null
        val x = positional[2].toFloatOrNull() ?: return null
        val y = positional[3].toFloatOrNull() ?: return null
        val w = positional[4].toFloatOrNull() ?: return null
        val h = positional[5].toFloatOrNull() ?: return null
        val rotation = if (positional.size >= 7) {
            when (positional[6].toIntOrNull()) {
                0, 90, 180, 270 -> positional[6].toInt()
                else -> return null
            }
        } else {
            0
        }
        val flipH = if (positional.size >= 8) {
            parseFlip(positional[7]) ?: return null
        } else {
            false
        }
        val flipV = if (positional.size >= 9) {
            parseFlip(positional[8]) ?: return null
        } else {
            false
        }
        if (KnownInputs.byId(id) == null) return null
        if (!fraction(x) || !fraction(y) || !fraction(w) || !fraction(h)) return null
        if (w < CamdCrop.MIN_SIZE || h < CamdCrop.MIN_SIZE) return null
        if (x + w > CamdCrop.EDGE_TOLERANCE || y + h > CamdCrop.EDGE_TOLERANCE) return null
        return CamdCommand.Open(id, x, y, w, h, rotation, flipH, flipV, crop)
    }

    private fun parseCrop(raw: String): CamdCrop? {
        val body = raw.substring(CROP_PREFIX.length)
        val fields = body.split(",")
        if (fields.size != 4) return null
        val x = fields[0].toFloatOrNull() ?: return null
        val y = fields[1].toFloatOrNull() ?: return null
        val w = fields[2].toFloatOrNull() ?: return null
        val h = fields[3].toFloatOrNull() ?: return null
        if (!fraction(x) || !fraction(y) || !fraction(w) || !fraction(h)) return null
        if (w < CamdCrop.MIN_SIZE || h < CamdCrop.MIN_SIZE) return null
        if (x + w > CamdCrop.EDGE_TOLERANCE || y + h > CamdCrop.EDGE_TOLERANCE) return null
        return CamdCrop(x, y, w, h)
    }

    private fun isCropToken(token: String): Boolean =
        token.startsWith(CROP_PREFIX, ignoreCase = true)

    private fun parseFlip(raw: String): Boolean? =
        when (raw.toIntOrNull()) {
            0 -> false
            1 -> true
            else -> null
        }

    private fun fraction(value: Float): Boolean = value in 0f..1f

    private const val CROP_PREFIX = "crop="
}
