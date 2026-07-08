package vad.dashing.tbox

import android.content.Context

/**
 * Lightweight prefs for Tesla launcher bootstrap and «back to last app» state.
 */
object LauncherStateStore {

    private const val PREFS = "tbox_launcher_state"
    private const val KEY_PRESET_APPLIED = "preset_applied"
    private const val KEY_LAST_APP_PACKAGE = "last_app_package"
    private const val KEY_LAST_APP_LABEL = "last_app_label"

    fun isPresetApplied(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRESET_APPLIED, false)

    fun setPresetApplied(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_PRESET_APPLIED, true)
            .apply()
    }

    fun saveLastLaunchedApp(context: Context, packageName: String, label: String) {
        if (packageName.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_APP_PACKAGE, packageName)
            .putString(KEY_LAST_APP_LABEL, label)
            .apply()
    }

    fun lastLaunchedAppPackage(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_APP_PACKAGE, "").orEmpty()

    fun lastLaunchedAppLabel(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LAST_APP_LABEL, "").orEmpty()

    fun clearLastLaunchedApp(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_LAST_APP_PACKAGE)
            .remove(KEY_LAST_APP_LABEL)
            .apply()
    }
}
