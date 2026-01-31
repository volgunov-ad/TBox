package vad.dashing.tbox

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class WidgetPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PANEL_ID = "extra_panel_id"
        const val EXTRA_WIDGET_INDEX = "extra_widget_index"
        const val EXTRA_SHOW_TITLE = "extra_show_title"
        const val EXTRA_SHOW_UNIT = "extra_show_unit"
    }

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var panelId: String = ""
    private var widgetIndex: Int = -1
    private var showTitle: Boolean = false
    private var showUnit: Boolean = true

    private val pickWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            cleanupAndFinish()
            return@registerForActivityResult
        }
        handlePickResult(result.data)
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            cleanupAndFinish()
            return@registerForActivityResult
        }
        saveSelectionAndFinish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        panelId = intent.getStringExtra(EXTRA_PANEL_ID).orEmpty()
        widgetIndex = intent.getIntExtra(EXTRA_WIDGET_INDEX, -1)
        showTitle = intent.getBooleanExtra(EXTRA_SHOW_TITLE, false)
        showUnit = intent.getBooleanExtra(EXTRA_SHOW_UNIT, true)

        if (panelId.isBlank() || widgetIndex < 0) {
            finish()
            return
        }

        appWidgetId = ExternalWidgetHostManager.allocateAppWidgetId(this)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        pickWidgetLauncher.launch(pickIntent)
    }

    private fun handlePickResult(data: Intent?) {
        val pickedId = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            ?: appWidgetId
        if (pickedId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            cleanupAndFinish()
            return
        }
        if (pickedId != appWidgetId && appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ExternalWidgetHostManager.deleteAppWidgetId(this, appWidgetId)
        }
        appWidgetId = pickedId

        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info == null) {
            cleanupAndFinish()
            return
        }
        if (info.configure != null) {
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = info.configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            configureWidgetLauncher.launch(configIntent)
        } else {
            saveSelectionAndFinish()
        }
    }

    private fun saveSelectionAndFinish() {
        lifecycleScope.launch {
            val settingsManager = SettingsManager(applicationContext)
            settingsManager.ensureDefaultFloatingDashboards()
            val dashboards = settingsManager.floatingDashboardsFlow.first()
            val updatedDashboards = dashboards.map { config ->
                if (config.id != panelId) return@map config
                val widgets = config.widgetsConfig.toMutableList()
                while (widgets.size <= widgetIndex) {
                    widgets.add(FloatingDashboardWidgetConfig(dataKey = ""))
                }
                val previousConfig = widgets[widgetIndex]
                val previousWidgetId = previousConfig.appWidgetId
                if (previousWidgetId != null && previousWidgetId != appWidgetId) {
                    ExternalWidgetHostManager.deleteAppWidgetId(this@WidgetPickerActivity, previousWidgetId)
                }
                widgets[widgetIndex] = FloatingDashboardWidgetConfig(
                    dataKey = WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY,
                    showTitle = showTitle,
                    showUnit = showUnit,
                    appWidgetId = appWidgetId
                )
                config.copy(widgetsConfig = widgets)
            }
            settingsManager.saveFloatingDashboards(updatedDashboards)
            finish()
        }
    }

    private fun cleanupAndFinish() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ExternalWidgetHostManager.deleteAppWidgetId(this, appWidgetId)
        }
        finish()
    }
}
