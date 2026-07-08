package vad.dashing.tbox.ui.launcher

import android.content.Context
import android.graphics.Rect
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class LauncherSplitPreset(
    val id: String,
    val name: String,
    val leftPackage: String,
    val rightPackage: String,
    /** Left pane share of split area, 0.2 … 0.8 */
    val leftRatio: Float = 0.5f,
)

internal object LauncherSplitPresetStore {
    private const val PREFS = "tbox_launcher_split_presets"
    private const val KEY_PRESETS = "presets_json"

    fun loadPresets(context: Context): List<LauncherSplitPreset> =
        runCatching {
            val raw = prefs(context).getString(KEY_PRESETS, null) ?: return emptyList()
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val left = obj.optString("leftPackage")
                    val right = obj.optString("rightPackage")
                    if (left.isBlank() || right.isBlank()) continue
                    add(
                        LauncherSplitPreset(
                            id = obj.optString("id", UUID.randomUUID().toString()),
                            name = obj.optString("name", "Сплит ${i + 1}"),
                            leftPackage = left,
                            rightPackage = right,
                            leftRatio = obj.optDouble("leftRatio", 0.5).toFloat().coerceIn(0.2f, 0.8f),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())

    fun savePresets(context: Context, presets: List<LauncherSplitPreset>) {
        val array = JSONArray()
        presets.forEach { preset ->
            array.put(
                JSONObject()
                    .put("id", preset.id)
                    .put("name", preset.name)
                    .put("leftPackage", preset.leftPackage)
                    .put("rightPackage", preset.rightPackage)
                    .put("leftRatio", preset.leftRatio.toDouble()),
            )
        }
        prefs(context).edit().putString(KEY_PRESETS, array.toString()).apply()
    }

    fun upsertPreset(context: Context, preset: LauncherSplitPreset) {
        val list = loadPresets(context).toMutableList()
        val idx = list.indexOfFirst { it.id == preset.id }
        if (idx >= 0) list[idx] = preset else list.add(preset)
        savePresets(context, list)
    }

    fun deletePreset(context: Context, id: String) {
        savePresets(context, loadPresets(context).filterNot { it.id == id })
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
