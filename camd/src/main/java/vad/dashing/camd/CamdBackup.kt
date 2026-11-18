package vad.dashing.camd

import android.content.SharedPreferences

data class CamdImportSummary(
    val corrections: Int,
    val userPresets: Int,
    val builtinPresets: Int,
    val keepAvm: Boolean,
)

object CamdBackup {
    const val VERSION = 1
    const val FILE_PREFIX = "camd_settings"
    private const val APP_TAG = "camd"

    /**
     * Full settings document: per-camera corrections (including tilt), presets
     * (user + edited built-ins) and the keep-AVM flag. Version 1.
     */
    fun exportJson(prefs: SharedPreferences): String {
        val corrections = KnownInputs.all.map { input ->
            val correction = CamdLensCorrectionStore.load(prefs, input.id)
            input.id.toString() to CamdJson.obj(
                listOf(
                    "k1" to CamdJson.num(correction.k1),
                    "k2" to CamdJson.num(correction.k2),
                    "cx" to CamdJson.num(correction.cx),
                    "cy" to CamdJson.num(correction.cy),
                    "tiltV" to CamdJson.num(correction.tiltV),
                    "tiltH" to CamdJson.num(correction.tiltH),
                )
            )
        }
        val presets = CamdPresetStore.all(prefs)
        return CamdJson.write(
            CamdJson.obj(
                listOf(
                    "version" to CamdJson.num(VERSION),
                    "app" to CamdJson.str(APP_TAG),
                    "corrections" to CamdJson.obj(corrections),
                    "presets" to CamdJson.arr(presets.map { CamdPresetStore.encodePreset(it) }),
                    "keepAvmStopped" to CamdJson.bool(
                        prefs.getBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, false)
                    ),
                    "showCloseButton" to CamdJson.bool(
                        prefs.getBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, true)
                    ),
                    "tapToClose" to CamdJson.bool(
                        prefs.getBoolean(CameraWindows.TAP_TO_CLOSE_KEY, false)
                    ),
                )
            )
        )
    }

    /** Applies a settings document. Returns a summary or null when the file is not valid. */
    fun importJson(prefs: SharedPreferences, text: String): CamdImportSummary? {
        val root = CamdJson.parse(text)?.asObj() ?: return null
        if (root["app"]?.asStr() != APP_TAG) return null
        val version = root["version"]?.asInt() ?: return null
        if (version != VERSION) return null

        val correctionsJson = root["corrections"]?.asObj() ?: return null
        val corrections = ArrayList<Pair<Int, CamdLensCorrection>>()
        for ((key, value) in correctionsJson) {
            val cameraId = key.toIntOrNull() ?: return null
            if (KnownInputs.byId(cameraId) == null) return null
            val fields = value.asObj() ?: return null
            val correction = CamdLensCorrection.clamp(
                CamdLensCorrection(
                    k1 = fields["k1"]?.asNum()?.toFloat() ?: 0f,
                    k2 = fields["k2"]?.asNum()?.toFloat() ?: 0f,
                    cx = fields["cx"]?.asNum()?.toFloat() ?: 0f,
                    cy = fields["cy"]?.asNum()?.toFloat() ?: 0f,
                    tiltV = fields["tiltV"]?.asNum()?.toFloat() ?: 0f,
                    tiltH = fields["tiltH"]?.asNum()?.toFloat() ?: 0f,
                )
            )
            corrections.add(cameraId to correction)
        }

        val presetsJson = root["presets"]?.asArr() ?: return null
        val imported = presetsJson.map { CamdPresetStore.decodePreset(it, builtinDefault = false) }
        if (imported.any { it == null }) return null
        val presets = imported.filterNotNull()
        val names = presets.map { it.name }
        if (names.size != names.toSet().size) return null
        val users = presets.filter { CamdBuiltinPresets.byName(it.name) == null }
        val builtins = presets.filter { CamdBuiltinPresets.byName(it.name) != null }
        if (users.any { !CamdPreset.isValidName(it.name) || !CamdSources.isKnown(it.cameraId) }) {
            return null
        }

        val keepAvm = root["keepAvmStopped"]?.asBool() ?: false

        corrections.forEach { (cameraId, correction) ->
            CamdLensCorrectionStore.save(prefs, cameraId, correction)
        }
        users.forEach { CamdPresetStore.saveUser(prefs, it) }
        builtins.forEach {
            CamdPresetStore.saveBuiltinOverride(
                prefs,
                it.name,
                it.rotation,
                it.flipH,
                it.flipV,
                it.crop,
                it.defaultCorrection,
                it.correction,
            )
        }
        prefs.edit().putBoolean(CameraWindows.KEEP_AVM_STOPPED_KEY, keepAvm).apply()
        // Optional fields: files from older exports keep the current values.
        root["showCloseButton"]?.asBool()?.let {
            prefs.edit().putBoolean(CameraWindows.SHOW_CLOSE_BUTTON_KEY, it).apply()
        }
        root["tapToClose"]?.asBool()?.let {
            prefs.edit().putBoolean(CameraWindows.TAP_TO_CLOSE_KEY, it).apply()
        }
        return CamdImportSummary(
            corrections = corrections.size,
            userPresets = users.size,
            builtinPresets = builtins.size,
            keepAvm = keepAvm,
        )
    }
}
