package vad.dashing.camd

import android.content.SharedPreferences

/**
 * Named rot / flip / crop bundle for one camera. Built-ins are editable but never
 * deleted or renamed; user presets are freely managed. The name is the API address
 * (OPEN preset=<name>), so names are globally unique across all cameras.
 *
 * defaultCorrection = true resolves lens corrections from the source camera prefs
 * at OPEN time; false uses the correction values frozen inside the preset.
 */
data class CamdPreset(
    val name: String,
    val cameraId: Int,
    val rotation: Int = 0,
    val flipH: Boolean = false,
    val flipV: Boolean = false,
    val crop: CamdCrop = CamdCrop.IDENTITY,
    val defaultCorrection: Boolean = true,
    val correction: CamdLensCorrection = CamdLensCorrection.IDENTITY,
    val builtin: Boolean = false,
) {
    companion object {
        const val NAME_MAX = 32
        private val NAME_REGEX = Regex("^[a-z0-9_]{1,32}$")

        fun isValidName(name: String): Boolean = NAME_REGEX.matches(name)

        fun normalizeRotation(degrees: Int): Int = ((degrees % 360) + 360) % 360
    }
}

object CamdBuiltinPresets {
    val all: List<CamdPreset> = listOf(
        full("full_cabin", 0),
        full("full_oms", 1),
        full("full_dms", 3),
        full("full_left", 4),
        full("full_right", 5),
        full("full_front", 8),
        full("full_rear", 9),
        CamdPreset("front_left", 8, crop = CamdCrop(0f, 0f, 0.5f, 1f), builtin = true),
        CamdPreset("front_right", 8, crop = CamdCrop(0.5f, 0f, 0.5f, 1f), builtin = true),
        CamdPreset("rear_left", 9, crop = CamdCrop(0f, 0f, 0.5f, 1f), builtin = true),
        CamdPreset("rear_right", 9, crop = CamdCrop(0.5f, 0f, 0.5f, 1f), builtin = true),
        CamdPreset("left_top", 4, rotation = 90, crop = CamdCrop(0f, 0f, 1f, 0.5f), builtin = true),
        CamdPreset("left_bottom", 4, rotation = 90, crop = CamdCrop(0f, 0.5f, 1f, 0.5f), builtin = true),
        CamdPreset("right_top", 5, rotation = 270, crop = CamdCrop(0f, 0f, 1f, 0.5f), builtin = true),
        CamdPreset("right_bottom", 5, rotation = 270, crop = CamdCrop(0f, 0.5f, 1f, 0.5f), builtin = true),
    )

    fun byName(name: String): CamdPreset? = all.firstOrNull { it.name == name }

    private fun full(name: String, cameraId: Int) = CamdPreset(name, cameraId, builtin = true)
}

object CamdPresetStore {
    private const val KEY = "presets_json"

    fun all(prefs: SharedPreferences): List<CamdPreset> {
        val stored = readStored(prefs)
        val builtins = CamdBuiltinPresets.all.map { definition ->
            stored.overrides.firstOrNull { it.name == definition.name }?.let { saved ->
                definition.copy(
                    rotation = saved.rotation,
                    flipH = saved.flipH,
                    flipV = saved.flipV,
                    crop = saved.crop,
                    defaultCorrection = saved.defaultCorrection,
                    correction = saved.correction,
                )
            } ?: definition
        }
        val users = stored.user
            .filter { CamdBuiltinPresets.byName(it.name) == null }
            .map { it.copy(builtin = false) }
            .sortedBy { it.name }
        return builtins + users
    }

    fun byName(prefs: SharedPreferences, name: String): CamdPreset? =
        all(prefs).firstOrNull { it.name == name }

    /** Saves a user preset. Returns an error token or null on success. */
    fun saveUser(prefs: SharedPreferences, preset: CamdPreset): String? {
        if (!CamdPreset.isValidName(preset.name)) return "bad_name"
        if (KnownInputs.byId(preset.cameraId) == null) return "unknown_camera"
        val entry = preset.copy(
            rotation = CamdPreset.normalizeRotation(preset.rotation),
            crop = CamdCrop.clamp(preset.crop),
            correction = CamdLensCorrection.clamp(preset.correction),
            builtin = false,
        )
        val stored = readStored(prefs)
        val replacing = stored.user.any { it.name == entry.name }
        if (!replacing && CamdBuiltinPresets.byName(entry.name) != null) return "name_taken"
        val users = stored.user.filterNot { it.name == entry.name } + entry
        writeStored(prefs, stored.copy(user = users))
        return null
    }

    fun deleteUser(prefs: SharedPreferences, name: String): Boolean {
        val stored = readStored(prefs)
        if (stored.user.none { it.name == name }) return false
        writeStored(prefs, stored.copy(user = stored.user.filterNot { it.name == name }))
        return true
    }

    /** Persists user edits of a built-in preset; name and camera stay fixed. */
    fun saveBuiltinOverride(
        prefs: SharedPreferences,
        name: String,
        rotation: Int,
        flipH: Boolean,
        flipV: Boolean,
        crop: CamdCrop,
        defaultCorrection: Boolean,
        correction: CamdLensCorrection,
    ): Boolean {
        val definition = CamdBuiltinPresets.byName(name) ?: return false
        val entry = definition.copy(
            rotation = CamdPreset.normalizeRotation(rotation),
            flipH = flipH,
            flipV = flipV,
            crop = CamdCrop.clamp(crop),
            defaultCorrection = defaultCorrection,
            correction = CamdLensCorrection.clamp(correction),
        )
        val stored = readStored(prefs)
        val overrides = stored.overrides.filterNot { it.name == name } + entry
        writeStored(prefs, stored.copy(overrides = overrides))
        return true
    }

    internal fun encodePreset(preset: CamdPreset): CamdJson = CamdJson.obj(
        listOf(
            "name" to CamdJson.str(preset.name),
            "camera" to CamdJson.num(preset.cameraId),
            "rotation" to CamdJson.num(preset.rotation),
            "flipH" to CamdJson.bool(preset.flipH),
            "flipV" to CamdJson.bool(preset.flipV),
            "cropX" to CamdJson.num(preset.crop.x),
            "cropY" to CamdJson.num(preset.crop.y),
            "cropW" to CamdJson.num(preset.crop.w),
            "cropH" to CamdJson.num(preset.crop.h),
            "defCorr" to CamdJson.bool(preset.defaultCorrection),
            "corrK1" to CamdJson.num(preset.correction.k1),
            "corrK2" to CamdJson.num(preset.correction.k2),
            "corrCx" to CamdJson.num(preset.correction.cx),
            "corrCy" to CamdJson.num(preset.correction.cy),
            "corrTiltV" to CamdJson.num(preset.correction.tiltV),
            "corrTiltH" to CamdJson.num(preset.correction.tiltH),
        )
    )

    internal fun decodePreset(value: CamdJson, builtinDefault: Boolean): CamdPreset? {
        val fields = value.asObj() ?: return null
        val name = fields["name"]?.asStr() ?: return null
        if (!CamdPreset.isValidName(name)) return null
        val camera = fields["camera"]?.asInt() ?: return null
        if (KnownInputs.byId(camera) == null) return null
        val crop = CamdCrop.clamp(
            CamdCrop(
                x = fields["cropX"]?.asNum()?.toFloat() ?: 0f,
                y = fields["cropY"]?.asNum()?.toFloat() ?: 0f,
                w = fields["cropW"]?.asNum()?.toFloat() ?: 1f,
                h = fields["cropH"]?.asNum()?.toFloat() ?: 1f,
            )
        )
        // Entries saved before per-preset corrections default to camera corrections.
        val correction = CamdLensCorrection.clamp(
            CamdLensCorrection(
                k1 = fields["corrK1"]?.asNum()?.toFloat() ?: 0f,
                k2 = fields["corrK2"]?.asNum()?.toFloat() ?: 0f,
                cx = fields["corrCx"]?.asNum()?.toFloat() ?: 0f,
                cy = fields["corrCy"]?.asNum()?.toFloat() ?: 0f,
                tiltV = fields["corrTiltV"]?.asNum()?.toFloat() ?: 0f,
                tiltH = fields["corrTiltH"]?.asNum()?.toFloat() ?: 0f,
            )
        )
        return CamdPreset(
            name = name,
            cameraId = camera,
            rotation = CamdPreset.normalizeRotation(fields["rotation"]?.asInt() ?: 0),
            flipH = fields["flipH"]?.asBool() ?: false,
            flipV = fields["flipV"]?.asBool() ?: false,
            crop = crop,
            defaultCorrection = fields["defCorr"]?.asBool() ?: true,
            correction = correction,
            builtin = builtinDefault,
        )
    }

    private data class Stored(val user: List<CamdPreset>, val overrides: List<CamdPreset>)

    private fun readStored(prefs: SharedPreferences): Stored {
        val text = prefs.getString(KEY, null) ?: return Stored(emptyList(), emptyList())
        val root = CamdJson.parse(text) ?: return Stored(emptyList(), emptyList())
        val fields = root.asObj() ?: return Stored(emptyList(), emptyList())
        val user = fields["user"]?.asArr()?.mapNotNull { decodePreset(it, builtinDefault = false) } ?: emptyList()
        val overrides = fields["builtin"]?.asArr()?.mapNotNull { decodePreset(it, builtinDefault = true) } ?: emptyList()
        return Stored(user, overrides)
    }

    private fun writeStored(prefs: SharedPreferences, stored: Stored) {
        val json = CamdJson.obj(
            listOf(
                "user" to CamdJson.arr(stored.user.map { encodePreset(it) }),
                "builtin" to CamdJson.arr(stored.overrides.map { encodePreset(it) }),
            )
        )
        prefs.edit().putString(KEY, CamdJson.write(json)).apply()
    }
}
