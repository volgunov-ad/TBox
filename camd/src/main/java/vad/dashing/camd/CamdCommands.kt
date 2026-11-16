package vad.dashing.camd

import java.util.Locale

enum class CamdShape { RECT, CIRCLE }

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
        val presetName: String? = null,
        val shape: CamdShape = CamdShape.RECT,
        /** Set internally when a preset carries its own corrections (never parsed from the wire). */
        val lensOverride: CamdLensCorrection? = null,
    ) : CamdCommand()

    data object CloseAll : CamdCommand()
    data class Close(val inputId: Int) : CamdCommand()
    data object Status : CamdCommand()
    data class SetKeepAvm(val keep: Boolean) : CamdCommand()
    data object ListPresets : CamdCommand()
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
            "STATUS" -> if (parts.size == 1) CamdCommand.Status else null
            "AVM" -> parseAvm(parts)
            "PRESETS" -> if (parts.size == 1) CamdCommand.ListPresets else null
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
     * `OPEN id x y w h [rot 0..360] [flipH [flipV]] [crop=cx,cy,cw,ch] [shape=circle]`
     * `OPEN preset=<name> x y w h [shape=circle]`
     *
     * Plain form: positional size 6…9, camera id required. No rot / flip / crop
     * tokens means the frame is shown as-is (per-camera lens correction still applies).
     * Rotation is integer degrees 0…360 (360 ≡ 0); older clients sending quarter
     * turns keep working. Named tokens: at most one of each (crop=, shape=),
     * case-insensitive prefix, anywhere in the line.
     *
     * Preset form: the camera comes from the preset, so there is no id; exactly 4
     * positional tokens (window rect) and no rot / flip / crop tokens — preset
     * settings cannot be overridden by the caller. An unknown but well-formed
     * name fails later with `ERR unknown_preset`.
     */
    private fun parseOpen(parts: List<String>): CamdCommand.Open? {
        val cropTokens = parts.filter { isNamedToken(it, CROP_PREFIX) }
        val shapeTokens = parts.filter { isNamedToken(it, SHAPE_PREFIX) }
        val presetTokens = parts.filter { isNamedToken(it, PRESET_PREFIX) }
        if (cropTokens.size > 1 || shapeTokens.size > 1 || presetTokens.size > 1) return null
        val shape = shapeTokens.firstOrNull()?.let { parseShape(it) ?: return null } ?: CamdShape.RECT
        val presetName = presetTokens.firstOrNull()?.substring(PRESET_PREFIX.length)
        val positional = parts.filterNot { isNamedToken(it) }
        if (presetName != null) {
            if (!CamdPreset.isValidName(presetName)) return null
            if (cropTokens.isNotEmpty()) return null
            if (positional.size != 5) return null
            val x = positional[1].toFloatOrNull() ?: return null
            val y = positional[2].toFloatOrNull() ?: return null
            val w = positional[3].toFloatOrNull() ?: return null
            val h = positional[4].toFloatOrNull() ?: return null
            if (!fraction(x) || !fraction(y) || !fraction(w) || !fraction(h)) return null
            if (w < CamdCrop.MIN_SIZE || h < CamdCrop.MIN_SIZE) return null
            if (x + w > CamdCrop.EDGE_TOLERANCE || y + h > CamdCrop.EDGE_TOLERANCE) return null
            return CamdCommand.Open(
                inputId = -1,
                x = x,
                y = y,
                w = w,
                h = h,
                presetName = presetName,
                shape = shape,
            )
        }
        val crop = cropTokens.firstOrNull()?.let { parseCrop(it) ?: return null }
        if (positional.size !in 6..9) return null
        val id = positional[1].toIntOrNull() ?: return null
        val x = positional[2].toFloatOrNull() ?: return null
        val y = positional[3].toFloatOrNull() ?: return null
        val w = positional[4].toFloatOrNull() ?: return null
        val h = positional[5].toFloatOrNull() ?: return null
    val rotation = if (positional.size >= 7) {
        val value = positional[6].toIntOrNull() ?: return null
        if (value !in 0..360) return null
        value % 360
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
        return CamdCommand.Open(id, x, y, w, h, rotation, flipH, flipV, crop, null, shape)
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

    private fun parseShape(raw: String): CamdShape? =
        when (raw.substring(SHAPE_PREFIX.length).lowercase(Locale.US)) {
            "circle" -> CamdShape.CIRCLE
            else -> null
        }

    private fun isNamedToken(token: String): Boolean =
        isNamedToken(token, CROP_PREFIX) || isNamedToken(token, SHAPE_PREFIX) || isNamedToken(token, PRESET_PREFIX)

    private fun isNamedToken(token: String, prefix: String): Boolean =
        token.startsWith(prefix, ignoreCase = true)

    private fun parseFlip(raw: String): Boolean? =
        when (raw.toIntOrNull()) {
            0 -> false
            1 -> true
            else -> null
        }

    private fun fraction(value: Float): Boolean = value in 0f..1f

    private const val CROP_PREFIX = "crop="
    private const val SHAPE_PREFIX = "shape="
    private const val PRESET_PREFIX = "preset="
}
