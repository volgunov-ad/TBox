package vad.dashing.tbox

import android.content.Context
import kotlinx.coroutines.flow.first

object ThemeSettingsValidator {

    suspend fun validateOnStartup(context: Context, settingsManager: SettingsManager) {
        validateActiveTheme(context, settingsManager)
        sanitizeDriveModeThemePaths(context, settingsManager)
        normalizePagingState(settingsManager)
    }

    private suspend fun validateActiveTheme(context: Context, settingsManager: SettingsManager) {
        val uri = settingsManager.activeThemeUriFlow.first().trim()
        if (uri.isEmpty()) return
        when {
            ThemeCacheKeys.isLikelyCacheKey(uri) -> {
                if (!ThemeMaterialization.isMaterialized(context, uri)) {
                    settingsManager.clearActiveTheme()
                }
            }
            ThemeFileResolver.isAccessible(context, uri) -> Unit
            else -> settingsManager.clearActiveTheme()
        }
    }

    private suspend fun sanitizeDriveModeThemePaths(context: Context, settingsManager: SettingsManager) {
        val paths = settingsManager.driveModeThemePathsFlow.first()
        if (paths.isEmpty()) return
        val filtered = paths.filterValues { path ->
            ThemeFileResolver.isAccessible(context, path)
        }
        if (filtered.size != paths.size) {
            settingsManager.saveDriveModeThemePaths(filtered)
        }
    }

    private suspend fun normalizePagingState(settingsManager: SettingsManager) {
        val pageCount = settingsManager.mainScreenPageCountFlow.first()
        val current = settingsManager.mainScreenCurrentPageFlow.first()
        val normalizedCurrent = PagingStateNormalizer.normalizeCurrentPage(current, pageCount)
        if (normalizedCurrent != current) {
            settingsManager.saveMainScreenCurrentPage(normalizedCurrent)
        }
        val panels = settingsManager.mainScreenDashboardsFlow.first()
        val clamped = PagingStateNormalizer.clampPanelsToPageCount(panels, pageCount)
        if (clamped != panels) {
            settingsManager.saveMainScreenDashboards(clamped)
        }
    }
}
