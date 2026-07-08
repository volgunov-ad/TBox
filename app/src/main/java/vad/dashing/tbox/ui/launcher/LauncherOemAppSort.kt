package vad.dashing.tbox.ui.launcher

import android.content.Context
import org.json.JSONArray

/**
 * OEM app drawer sort order from WT_Launcher3 `app_sort.json`.
 */
internal object LauncherOemAppSort {

    private const val ASSET = "launcher_oem_app_sort.json"

    fun loadPriorityPackages(context: Context): List<String> {
        return runCatching {
            context.assets.open(ASSET).bufferedReader().use { reader ->
                val arr = JSONArray(reader.readText())
                buildList {
                    for (i in 0 until arr.length()) {
                        arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                    }
                }
            }
        }.getOrElse { emptyList() }
    }

    fun sortEntries(
        entries: List<vad.dashing.tbox.ui.LaunchableAppEntry>,
        priorityPackages: List<String>,
    ): List<vad.dashing.tbox.ui.LaunchableAppEntry> {
        if (priorityPackages.isEmpty()) return entries
        val indexByPkg = priorityPackages.withIndex().associate { (idx, pkg) -> pkg to idx }
        return entries.sortedWith(
            compareBy<vad.dashing.tbox.ui.LaunchableAppEntry> { entry ->
                indexByPkg[entry.packageName] ?: Int.MAX_VALUE
            }.thenBy { it.label.lowercase() }
        )
    }
}
