package vad.dashing.tbox.ui.launcher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

sealed class LauncherHomeItem {
    abstract val key: String

    data class App(val packageName: String) : LauncherHomeItem() {
        override val key: String = "app:$packageName"
    }

    data class Split(val presetId: String) : LauncherHomeItem() {
        override val key: String = "split:$presetId"
    }
}

internal object LauncherHomeStore {
    private const val PREFS = "tbox_launcher_app_config"
    private const val KEY_HOME = "home_items_json"

    fun loadItems(context: Context): List<LauncherHomeItem> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_HOME, null)
        if (!raw.isNullOrBlank()) {
            return parseItems(raw)
        }
        return migrateFromGrid(context)
    }

    fun saveItems(context: Context, items: List<LauncherHomeItem>) {
        val array = JSONArray()
        items.forEach { item ->
            when (item) {
                is LauncherHomeItem.App -> array.put(JSONObject().put("type", "app").put("package", item.packageName))
                is LauncherHomeItem.Split -> array.put(JSONObject().put("type", "split").put("id", item.presetId))
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HOME, array.toString())
            .apply()
    }

    fun addApp(context: Context, packageName: String) {
        val items = loadItems(context).toMutableList()
        if (items.none { it is LauncherHomeItem.App && it.packageName == packageName }) {
            items.add(LauncherHomeItem.App(packageName))
            saveItems(context, items)
        }
    }

    fun addSplit(context: Context, presetId: String) {
        val items = loadItems(context).toMutableList()
        if (items.none { it is LauncherHomeItem.Split && it.presetId == presetId }) {
            items.add(LauncherHomeItem.Split(presetId))
            saveItems(context, items)
        }
    }

    fun removeAt(context: Context, index: Int) {
        val items = loadItems(context).toMutableList()
        if (index in items.indices) {
            items.removeAt(index)
            saveItems(context, items)
        }
    }

    fun replaceAt(context: Context, index: Int, item: LauncherHomeItem) {
        val items = loadItems(context).toMutableList()
        if (index in items.indices) {
            items[index] = item
            saveItems(context, items)
        }
    }

    private fun parseItems(raw: String): List<LauncherHomeItem> =
        runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    when (obj.optString("type")) {
                        "app" -> obj.optString("package").takeIf { it.isNotBlank() }?.let { add(LauncherHomeItem.App(it)) }
                        "split" -> obj.optString("id").takeIf { it.isNotBlank() }?.let { add(LauncherHomeItem.Split(it)) }
                    }
                }
            }
        }.getOrDefault(emptyList())

    private fun migrateFromGrid(context: Context): List<LauncherHomeItem> =
        LauncherAppConfigStore.gridPackages(context)
            .filter { it.isNotBlank() }
            .map { LauncherHomeItem.App(it) }
            .also { if (it.isNotEmpty()) saveItems(context, it) }
}
