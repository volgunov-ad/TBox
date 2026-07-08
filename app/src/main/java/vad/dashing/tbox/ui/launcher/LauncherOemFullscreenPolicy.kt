package vad.dashing.tbox.ui.launcher

import android.content.Context
import org.json.JSONArray

/**
 * Jetour / WT head units allow direct fullscreen [startActivity] only for packages listed in
 * system `fullScreenPkgsListName` (see firmware defaultConfigList.json).
 */
object LauncherOemFullscreenPolicy {
    private var cached: Set<String>? = null

    fun allowsDirectFullscreen(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return allowedPackages(context).contains(packageName)
    }

    private fun allowedPackages(context: Context): Set<String> {
        cached?.let { return it }
        val loaded = runCatching {
            context.assets.open("oem/fullscreen_packages.json").use { stream ->
                val text = stream.bufferedReader().readText()
                buildSet {
                    val array = JSONArray(text)
                    for (i in 0 until array.length()) {
                        val pkg = array.optString(i).orEmpty()
                        if (pkg.isNotBlank()) add(pkg)
                    }
                }
            }
        }.getOrElse { emptySet() }
        cached = loaded
        return loaded
    }
}
