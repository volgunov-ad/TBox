package vad.dashing.tbox

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppPickerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PANEL_ID = "extra_panel_id"
        const val EXTRA_WIDGET_INDEX = "extra_widget_index"
        const val EXTRA_SHOW_TITLE = "extra_show_title"
        const val EXTRA_SHOW_UNIT = "extra_show_unit"
    }

    private var panelId: String = ""
    private var widgetIndex: Int = -1
    private var showTitle: Boolean = false
    private var showUnit: Boolean = true

    private val pickAppLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            finish()
            return@registerForActivityResult
        }
        val chosenComponent = resolveChosenComponent(result.data)
        if (chosenComponent == null) {
            finish()
            return@registerForActivityResult
        }
        saveSelectionAndFinish(chosenComponent)
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

        val baseIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pickIntent = Intent(Intent.ACTION_PICK_ACTIVITY).apply {
            putExtra(Intent.EXTRA_INTENT, baseIntent)
        }
        pickAppLauncher.launch(pickIntent)
    }

    private fun resolveChosenComponent(data: Intent?): ComponentName? {
        return data?.getParcelableExtra(Intent.EXTRA_CHOSEN_COMPONENT)
            ?: data?.component
            ?: data?.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.component
    }

    private fun saveSelectionAndFinish(component: ComponentName) {
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
                if (previousWidgetId != null) {
                    ExternalWidgetHostManager.deleteAppWidgetId(this@AppPickerActivity, previousWidgetId)
                }
                widgets[widgetIndex] = FloatingDashboardWidgetConfig(
                    dataKey = WidgetsRepository.LAUNCH_APP_DATA_KEY,
                    showTitle = showTitle,
                    showUnit = showUnit,
                    appPackageName = component.packageName,
                    appClassName = component.className
                )
                config.copy(widgetsConfig = widgets)
            }
            settingsManager.saveFloatingDashboards(updatedDashboards)
            finish()
        }
    }
}
