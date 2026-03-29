package vad.dashing.tbox

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.ui.FloatingPanelWidgetSelectionDialog
import vad.dashing.tbox.ui.theme.TboxAppTheme

/**
 * Shows the same tile configuration [androidx.compose.material3.AlertDialog] as MainScreen panels,
 * without resizing the floating overlay window.
 */
class FloatingPanelWidgetSelectionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PANEL_ID = "extra_panel_id"
        const val EXTRA_WIDGET_INDEX = "extra_widget_index"
        const val EXTRA_THEME = "extra_theme"

        fun start(context: Context, panelId: String, widgetIndex: Int, theme: Int) {
            val intent = Intent(context, FloatingPanelWidgetSelectionActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                putExtra(EXTRA_PANEL_ID, panelId)
                putExtra(EXTRA_WIDGET_INDEX, widgetIndex)
                putExtra(EXTRA_THEME, theme)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val panelId = intent.getStringExtra(EXTRA_PANEL_ID).orEmpty()
        val widgetIndex = intent.getIntExtra(EXTRA_WIDGET_INDEX, -1)
        val theme = intent.getIntExtra(EXTRA_THEME, 1)

        if (panelId.isBlank() || widgetIndex < 0) {
            finish()
            return
        }

        val settingsManager = SettingsManager(applicationContext)

        setContent {
            val context = LocalContext.current
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(settingsManager)
            )
            val dashboardViewModel: FloatingDashboardViewModel = viewModel(
                key = "floating-$panelId",
                factory = FloatingDashboardViewModelFactory(panelId)
            )
            val panelConfig by settingsViewModel.floatingDashboardConfig(panelId)
                .collectAsStateWithLifecycle()
            val widgetConfigs = panelConfig.widgetsConfig
            val totalWidgets = panelConfig.rows * panelConfig.cols
            val widgetsForDialog = remember(
                widgetConfigs,
                panelConfig.rows,
                panelConfig.cols,
                context
            ) {
                if (totalWidgets <= 0) {
                    emptyList()
                } else {
                    loadWidgetsFromConfig(
                        configs = widgetConfigs,
                        widgetCount = totalWidgets,
                        context = context,
                        defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_FLOATING,
                        defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_FLOATING
                    )
                }
            }

            LaunchedEffect(widgetIndex, totalWidgets) {
                if (totalWidgets <= 0 || widgetIndex >= totalWidgets) {
                    finish()
                }
            }

            LaunchedEffect(widgetsForDialog) {
                if (widgetsForDialog.isNotEmpty()) {
                    dashboardViewModel.dashboardManager.updateWidgets(widgetsForDialog)
                }
            }

            TboxAppTheme(theme = theme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FloatingPanelWidgetSelectionDialog(
                        dashboardManager = dashboardViewModel.dashboardManager,
                        settingsViewModel = settingsViewModel,
                        panelId = panelId,
                        widgetIndex = widgetIndex,
                        currentWidgets = widgetsForDialog,
                        currentWidgetConfigs = widgetConfigs,
                        onDismiss = { finish() }
                    )
                }
            }
        }
    }
}
