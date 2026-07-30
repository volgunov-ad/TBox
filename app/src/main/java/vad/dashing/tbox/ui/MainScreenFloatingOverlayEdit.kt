package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING
import vad.dashing.tbox.ExternalWidgetHostManager
import vad.dashing.tbox.FloatingDashboardViewModel
import vad.dashing.tbox.FloatingDashboardViewModelFactory
import vad.dashing.tbox.FloatingPanelEditModeTracker
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.loadWidgetsFromConfig

@Composable
internal fun MainScreenFloatingOverlayEdit(
    panelId: String,
    widgetIndex: Int,
    settingsViewModel: SettingsViewModel,
    currentTheme: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val overlayEditHost = remember(panelId, widgetIndex) {
        ExternalWidgetHostManager.acquireHost(context)
    }
    DisposableEffect(overlayEditHost) {
        onDispose { ExternalWidgetHostManager.releaseHost() }
    }
    val dashboardViewModel: FloatingDashboardViewModel = viewModel(
        key = "floating-overlay-edit-$panelId",
        factory = FloatingDashboardViewModelFactory(panelId)
    )
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState
        .collectAsStateWithLifecycle()
    val panelConfig by settingsViewModel.floatingDashboardConfig(panelId)
        .collectAsStateWithLifecycle()
    val widgetConfigs = panelConfig.widgetsConfig
    val totalTiles = panelConfig.rows * panelConfig.cols
    if (widgetIndex !in 0 until totalTiles) {
        LaunchedEffect(panelId, widgetIndex) {
            onDismiss()
        }
    } else {
        DisposableEffect(panelId) {
            FloatingPanelEditModeTracker.setTileEditDialogOpen(panelId, true)
            onDispose {
                FloatingPanelEditModeTracker.setTileEditDialogOpen(panelId, false)
            }
        }
        LaunchedEffect(
            widgetConfigs,
            panelConfig.rows,
            panelConfig.cols,
            context
        ) {
            val totalWidgets = panelConfig.rows * panelConfig.cols
            val widgets = loadWidgetsFromConfig(
                configs = widgetConfigs,
                widgetCount = totalWidgets,
                context = context,
                defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING,
                defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
            )
            dashboardViewModel.dashboardManager.updateWidgets(widgets)
        }
        FloatingOverlayFloatingPanelWidgetSelectionDialog(
            dashboardManager = dashboardViewModel.dashboardManager,
            settingsViewModel = settingsViewModel,
            panelId = panelId,
            widgetIndex = widgetIndex,
            currentWidgets = dashboardState.widgets,
            currentWidgetConfigs = widgetConfigs,
            currentTheme = currentTheme,
            onDismiss = onDismiss,
            onDeletePanel = { settingsViewModel.deleteFloatingDashboard(panelId) },
        )
    }
}
