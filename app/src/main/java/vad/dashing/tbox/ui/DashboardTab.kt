package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainDashboardViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SharedMediaControlService
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.collectMediaPlayersFromWidgetConfigs
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.MAIN_DASHBOARD_DEFAULT_WIDGET_ELEVATION
import vad.dashing.tbox.MAIN_DASHBOARD_DEFAULT_WIDGET_SHAPE
import vad.dashing.tbox.normalizeWidgetConfigs

@Composable
fun MainDashboardTab(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestartClick: () -> Unit,
) {
    val context = LocalContext.current
    val dashboardViewModel: MainDashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState.collectAsStateWithLifecycle()
    val widgetsConfig by settingsViewModel.dashboardWidgetsConfig.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val currentTheme by tboxViewModel.currentTheme.collectAsStateWithLifecycle()

    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    val totalWidgets = dashboardRows * dashboardCols
    val widgetConfigs = remember(widgetsConfig, totalWidgets) {
        normalizeWidgetConfigs(widgetsConfig, totalWidgets)
    }
    val mediaSourceId = remember { "main-dashboard" }
    val requestedMediaPlayers = remember(widgetConfigs) {
        collectMediaPlayersFromWidgetConfigs(widgetConfigs)
    }

    LaunchedEffect(widgetConfigs, totalWidgets, context) {
        val widgets = loadWidgetsFromConfig(widgetConfigs, totalWidgets, context)
        dashboardViewModel.dashboardManager.updateWidgets(widgets)
    }
    LaunchedEffect(mediaSourceId, requestedMediaPlayers, context) {
        SharedMediaControlService.updateSourceSelection(
            context = context,
            sourceId = mediaSourceId,
            mediaPackages = requestedMediaPlayers
        )
    }
    DisposableEffect(mediaSourceId) {
        onDispose {
            SharedMediaControlService.clearSourceSelection(mediaSourceId)
        }
    }

    var restartEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartEnabled = true
        }
    }

    val dataProvider = remember(context) {
        TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel, context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        if (dashboardState.widgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.loading))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (row in 0 until dashboardRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (col in 0 until dashboardCols) {
                            val index = row * dashboardCols + col
                            val widget = dashboardState.widgets.getOrNull(index) ?: continue
                            val widgetConfig = widgetConfigs.getOrNull(index)
                                ?: FloatingDashboardWidgetConfig(dataKey = "")
                            val widgetTextScale = normalizeWidgetScale(widgetConfig.scale)
                            val widgetTextColor = widget.resolveTextColorForTheme(currentTheme)
                            val widgetBackgroundColor = widget.resolveBackgroundColorForTheme(currentTheme)

                            Box(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(
                                    LocalWidgetTextScale provides widgetTextScale
                                ) {
                                    DashboardWidgetRenderer(
                                        widget = widget,
                                        widgetConfig = widgetConfig,
                                        tboxViewModel = tboxViewModel,
                                        canViewModel = canViewModel,
                                        appDataViewModel = appDataViewModel,
                                        dataProvider = dataProvider,
                                        dashboardManager = dashboardViewModel.dashboardManager,
                                        dashboardChart = dashboardChart,
                                        tboxConnected = tboxConnected,
                                        restartEnabled = restartEnabled,
                                        widgetTextColor = widgetTextColor,
                                        widgetBackgroundColor = widgetBackgroundColor,
                                        onClick = {},
                                        onLongClick = { showDialogForIndex = index },
                                        onMusicSelectedPlayerChange = { selectedPackage ->
                                            settingsViewModel.saveDashboardMediaSelectedPlayer(
                                                widgetIndex = index,
                                                widgetCount = totalWidgets,
                                                selectedPackage = selectedPackage
                                            )
                                        },
                                        onRestartRequested = {
                                            if (restartEnabled) {
                                                restartEnabled = false
                                                onTboxRestartClick()
                                            }
                                        },
                                        elevation = MAIN_DASHBOARD_DEFAULT_WIDGET_ELEVATION.dp,
                                        shape = MAIN_DASHBOARD_DEFAULT_WIDGET_SHAPE.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        showDialogForIndex?.let { index ->
            WidgetSelectionDialog(
                dashboardManager = dashboardViewModel.dashboardManager,
                settingsViewModel = settingsViewModel,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                currentWidgetConfigs = widgetConfigs,
                onDismiss = { showDialogForIndex = null }
            )
        }
    }
}

@Composable
fun WidgetSelectionDialog(
    dashboardManager: DashboardManager,
    settingsViewModel: SettingsViewModel,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    currentWidgetConfigs: List<FloatingDashboardWidgetConfig>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val state = rememberWidgetSelectionDialogState(
        widgetIndex = widgetIndex,
        currentWidgets = currentWidgets,
        currentWidgetConfigs = currentWidgetConfigs,
        defaultBackgroundLight = DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN,
        defaultBackgroundDark = DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {  },
        modifier = Modifier.fillMaxWidth(0.8f), // Модификатор для всего диалога
        properties = DialogProperties(
            usePlatformDefaultWidth = false // Отключает стандартную ширину платформы
        ),
        text = {
            WidgetSelectionDialogForm(
                titleText = if (state.showAdvancedSettings) {
                    stringResource(R.string.widget_additional_settings_for_tile, widgetIndex + 1)
                } else {
                    stringResource(R.string.widget_select_data_for_tile, widgetIndex + 1)
                },
                state = state,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            )
        },
        confirmButton = {
            WidgetSelectionDialogActions(
                state = state,
                onDismiss = onDismiss,
                onSave = {
                    applyWidgetSelectionChanges(
                        context = context,
                        dashboardManager = dashboardManager,
                        currentWidgets = currentWidgets,
                        currentWidgetConfigs = currentWidgetConfigs,
                        widgetIndex = widgetIndex,
                        state = state,
                        saveConfigs = settingsViewModel::saveDashboardWidgets
                    )
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {}
    )
}

