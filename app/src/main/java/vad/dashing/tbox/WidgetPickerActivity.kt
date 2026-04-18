package vad.dashing.tbox

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Allocates an app widget id and opens the system [AppWidgetManager.ACTION_APPWIDGET_PICK] flow.
 */
class WidgetPickerActivity : ComponentActivity() {

    companion object {
        private const val TAG = "WidgetPicker"
        const val EXTRA_PANEL_ID = "extra_panel_id"
        const val EXTRA_WIDGET_INDEX = "extra_widget_index"
        const val EXTRA_SHOW_TITLE = "extra_show_title"
        const val EXTRA_SHOW_UNIT = "extra_show_unit"
        const val EXTRA_SAVE_TARGET = "extra_save_target"

        /** Update a floating overlay panel ([SettingsManager.saveFloatingDashboards]). */
        const val SAVE_TARGET_FLOATING = 0

        /** Update a MainScreen embedded panel ([SettingsManager.saveMainScreenDashboards]). */
        const val SAVE_TARGET_MAIN_SCREEN = 1

        /** Update the in-app main tab dashboard ([SettingsManager.saveDashboardWidgets]). */
        const val SAVE_TARGET_MAIN_DASHBOARD = 2

        fun start(
            context: Context,
            saveTarget: Int,
            panelId: String,
            widgetIndex: Int,
            showTitle: Boolean,
            showUnit: Boolean
        ) {
            try {
                val intent = Intent(context, WidgetPickerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    putExtra(EXTRA_SAVE_TARGET, saveTarget)
                    putExtra(EXTRA_PANEL_ID, panelId)
                    putExtra(EXTRA_WIDGET_INDEX, widgetIndex)
                    putExtra(EXTRA_SHOW_TITLE, showTitle)
                    putExtra(EXTRA_SHOW_UNIT, showUnit)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    private val appWidgetManager by lazy { AppWidgetManager.getInstance(this) }
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var saveTarget: Int = SAVE_TARGET_FLOATING
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

        saveTarget = intent.getIntExtra(EXTRA_SAVE_TARGET, SAVE_TARGET_FLOATING)
        panelId = intent.getStringExtra(EXTRA_PANEL_ID).orEmpty()
        widgetIndex = intent.getIntExtra(EXTRA_WIDGET_INDEX, -1)
        showTitle = intent.getBooleanExtra(EXTRA_SHOW_TITLE, false)
        showUnit = intent.getBooleanExtra(EXTRA_SHOW_UNIT, true)

        if (widgetIndex < 0) {
            finish()
            return
        }
        if (saveTarget != SAVE_TARGET_MAIN_DASHBOARD && panelId.isBlank()) {
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
        val configure = info.configure
        if (configure != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !isConfigureActivityExported(configure)) {
                Log.w(
                    TAG,
                    "Configure activity ${configure.flattenToShortString()} is not exported; saving widget without config UI."
                )
                saveSelectionAndFinish()
                return
            }
            val configIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = configure
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            try {
                configureWidgetLauncher.launch(configIntent)
            } catch (e: SecurityException) {
                Log.w(
                    TAG,
                    "Cannot start APPWIDGET_CONFIGURE for ${configure.flattenToShortString()}; saving widget as-is.",
                    e
                )
                saveSelectionAndFinish()
            } catch (e: RuntimeException) {
                if (e.cause is SecurityException) {
                    Log.w(
                        TAG,
                        "Cannot start APPWIDGET_CONFIGURE for ${configure.flattenToShortString()}; saving widget as-is.",
                        e
                    )
                    saveSelectionAndFinish()
                } else {
                    throw e
                }
            }
        } else {
            saveSelectionAndFinish()
        }
    }

    private fun isConfigureActivityExported(component: ComponentName): Boolean {
        return try {
            @Suppress("DEPRECATION")
            val ai = packageManager.getActivityInfo(component, PackageManager.GET_META_DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ai.exported
            } else {
                true
            }
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun saveSelectionAndFinish() {
        lifecycleScope.launch {
            val settingsManager = SettingsManager(applicationContext)
            settingsManager.ensureDefaultFloatingDashboards()
            when (saveTarget) {
                SAVE_TARGET_FLOATING -> saveFloating(settingsManager)
                SAVE_TARGET_MAIN_SCREEN -> saveMainScreen(settingsManager)
                SAVE_TARGET_MAIN_DASHBOARD -> saveMainDashboard(settingsManager)
            }
            finish()
        }
    }

    private suspend fun saveFloating(settingsManager: SettingsManager) {
        val dashboards = settingsManager.floatingDashboardsFlow.first()
        val updatedDashboards = dashboards.map { config ->
            if (config.id != panelId) return@map config
            config.copy(widgetsConfig = mergeWidgetAt(config.widgetsConfig))
        }
        settingsManager.saveFloatingDashboards(updatedDashboards)
    }

    private suspend fun saveMainScreen(settingsManager: SettingsManager) {
        val panels = settingsManager.mainScreenDashboardsFlow.first()
        val updated = panels.map { panel ->
            if (panel.id != panelId) return@map panel
            panel.copy(widgetsConfig = mergeWidgetAt(panel.widgetsConfig))
        }
        settingsManager.saveMainScreenDashboards(updated)
    }

    private suspend fun saveMainDashboard(settingsManager: SettingsManager) {
        val widgets = settingsManager.dashboardWidgetsFlow.first().toMutableList()
        while (widgets.size <= widgetIndex) {
            widgets.add(FloatingDashboardWidgetConfig(dataKey = ""))
        }
        val previousConfig = widgets[widgetIndex]
        val previousWidgetId = previousConfig.appWidgetId
        if (previousWidgetId != null && previousWidgetId != appWidgetId) {
            ExternalWidgetHostManager.deleteAppWidgetId(this@WidgetPickerActivity, previousWidgetId)
        }
        widgets[widgetIndex] = previousConfig.copy(
            dataKey = WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY,
            showTitle = showTitle,
            showUnit = showUnit,
            appWidgetId = appWidgetId,
            launcherAppPackage = ""
        )
        settingsManager.saveDashboardWidgets(widgets)
    }

    private fun mergeWidgetAt(widgets: List<FloatingDashboardWidgetConfig>): List<FloatingDashboardWidgetConfig> {
        val list = widgets.toMutableList()
        while (list.size <= widgetIndex) {
            list.add(FloatingDashboardWidgetConfig(dataKey = ""))
        }
        val previousConfig = list[widgetIndex]
        val previousWidgetId = previousConfig.appWidgetId
        if (previousWidgetId != null && previousWidgetId != appWidgetId) {
            ExternalWidgetHostManager.deleteAppWidgetId(this, previousWidgetId)
        }
        list[widgetIndex] = previousConfig.copy(
            dataKey = WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY,
            showTitle = showTitle,
            showUnit = showUnit,
            appWidgetId = appWidgetId,
            launcherAppPackage = ""
        )
        return list
    }

    private fun cleanupAndFinish() {
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            ExternalWidgetHostManager.deleteAppWidgetId(this, appWidgetId)
        }
        finish()
    }
}
