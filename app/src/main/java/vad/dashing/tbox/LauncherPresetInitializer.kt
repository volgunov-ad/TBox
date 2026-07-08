package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * Applies the bundled Tesla-style launcher layout on first run when main-screen panels are empty.
 */
object LauncherPresetInitializer {

    private const val PRESET_ASSET = "launcher_tesla_preset.json"

    suspend fun ensureDefaultLayoutIfNeeded(context: Context, settingsManager: SettingsManager) {
        if (LauncherStateStore.isPresetApplied(context)) return
        val panels = settingsManager.mainScreenDashboardsFlow.first()
        if (panels.isNotEmpty()) {
            LauncherStateStore.setPresetApplied(context)
            return
        }
        val json = runCatching {
            context.assets.open(PRESET_ASSET).bufferedReader().use { it.readText() }
        }.getOrElse { return }
        LauncherLayoutImporter.importJson(settingsManager, json).getOrElse { return }
        settingsManager.saveSelectedTab(SettingsManager.MAIN_SCREEN_TAB_KEY)
        LauncherStateStore.setPresetApplied(context)
    }
}
