package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.DashboardManager
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MainDashboardViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.loadWidgetsFromConfig
import vad.dashing.tbox.normalizeWidgetScale
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

    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }
    val totalWidgets = dashboardRows * dashboardCols
    val widgetConfigs = remember(widgetsConfig, totalWidgets) {
        normalizeWidgetConfigs(widgetsConfig, totalWidgets)
    }

    LaunchedEffect(widgetConfigs, totalWidgets, context) {
        val widgets = loadWidgetsFromConfig(widgetConfigs, totalWidgets, context)
        dashboardViewModel.dashboardManager.updateWidgets(widgets)
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

                            Box(modifier = Modifier.weight(1f)) {
                                CompositionLocalProvider(
                                    LocalWidgetTextScale provides widgetTextScale
                                ) {
                                    when (widget.dataKey) {
                                        "netWidget" -> {
                                            DashboardNetWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                viewModel = tboxViewModel
                                            )
                                        }
                                        "locWidget" -> {
                                            DashboardLocWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                viewModel = tboxViewModel
                                            )
                                        }
                                        "voltage+engineTemperatureWidget" -> {
                                            DashboardVoltEngTempWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                canViewModel = canViewModel,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "gearBoxWidget" -> {
                                            DashboardGearBoxWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                canViewModel = canViewModel,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "wheelsPressureWidget" -> {
                                            DashboardWheelsPressureWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                canViewModel = canViewModel,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "wheelsPressureTemperatureWidget" -> {
                                            DashboardWheelsPressureTemperatureWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                canViewModel = canViewModel,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "tempInOutWidget" -> {
                                            DashboardTempInOutWidgetItem(
                                                widget = widget,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                canViewModel = canViewModel,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "motorHoursWidget" -> {
                                            DashboardMotorHoursWidgetItem(
                                                widget = widget,
                                                dataProvider = dataProvider,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                onDoubleClick = {
                                                    appDataViewModel.setMotorHours(0f)
                                                },
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                        "restartTbox" -> {
                                            DashboardWidgetItem(
                                                widget = widget,
                                                dataProvider = dataProvider,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                onDoubleClick = {
                                                    if (restartEnabled) {
                                                        restartEnabled = false
                                                        onTboxRestartClick()
                                                    }
                                                },
                                                dashboardManager = dashboardViewModel.dashboardManager,
                                                dashboardChart = false,
                                                title = widgetConfig.showTitle,
                                                units = widgetConfig.showUnit,
                                                textColor = if (restartEnabled) {
                                                    if (tboxConnected) {
                                                        Color(0xD900A400)
                                                    } else {
                                                        Color(0xD9FF0000)
                                                    }
                                                } else {
                                                    Color(0xD97E4C4C)
                                                }
                                            )
                                        }
                                        else -> {
                                            DashboardWidgetItem(
                                                widget = widget,
                                                dataProvider = dataProvider,
                                                onClick = { showDialogForIndex = index },
                                                onLongClick = {},
                                                onDoubleClick = {
                                                    if (widget.dataKey == "motorHours") {
                                                        appDataViewModel.setMotorHours(0f)
                                                    }
                                                },
                                                dashboardManager = dashboardViewModel.dashboardManager,
                                                dashboardChart = dashboardChart,
                                                title = widgetConfig.showTitle,
                                                units = widgetConfig.showUnit
                                            )
                                        }
                                    }
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
    var selectedDataKey by remember {
        mutableStateOf(currentWidgets.getOrNull(widgetIndex)?.dataKey ?: "")
    }
    val initialConfig = currentWidgetConfigs.getOrNull(widgetIndex)
        ?: FloatingDashboardWidgetConfig(dataKey = "")
    var showTitle by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(initialConfig.showTitle)
    }
    var showUnit by remember(widgetIndex, currentWidgetConfigs) {
        mutableStateOf(initialConfig.showUnit)
    }
    var scale by remember(widgetIndex, currentWidgetConfigs) {
        mutableFloatStateOf(normalizeWidgetScale(initialConfig.scale))
    }
    val togglesEnabled = selectedDataKey.isNotEmpty()

    val availableOptions = listOf("" to stringResource(R.string.widget_option_not_selected)) +
            WidgetsRepository.getAvailableDataKeysWidgets()
                .filter { it.isNotEmpty() }
                .map { key ->
                    key to WidgetsRepository.getTitleUnitForDataKey(context, key)
                }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {  },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                SettingsTitle(stringResource(R.string.widget_select_data_for_tile, widgetIndex + 1))

                // Список опций с прокруткой
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(2f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(12.dp)
                    ) {
                        availableOptions.forEachIndexed { index, (key, displayName) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedDataKey = key }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = selectedDataKey == key,
                                    onClick = { selectedDataKey = key }
                                )
                                Text(
                                    text = displayName,
                                    fontSize = 24.sp,
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                SettingsTitle(stringResource(R.string.widget_additional_settings_for_tile, widgetIndex + 1))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState())
                            .padding(12.dp)
                    ) {
                        SettingSwitch(
                            showTitle,
                            { showTitle = it },
                            stringResource(R.string.widget_show_title),
                            "",
                            togglesEnabled
                        )
                        SettingSwitch(
                            showUnit,
                            { showUnit = it },
                            stringResource(R.string.widget_show_unit),
                            "",
                            togglesEnabled
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.widget_scale, scale),
                                fontSize = 24.sp
                            )
                            Text(
                                text = stringResource(R.string.widget_scale_hint),
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = scale,
                                onValueChange = { newValue ->
                                    scale = normalizeWidgetScale(newValue)
                                },
                                valueRange = 0.1f..2.0f,
                                steps = 18,
                                enabled = togglesEnabled,
                                modifier = Modifier
                                    .padding(top = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalizedScale = normalizeWidgetScale(scale)
                    scale = normalizedScale
                    val updatedWidgets = currentWidgets.toMutableList()
                    val newWidget = if (selectedDataKey.isNotEmpty()) {
                        DashboardWidget(
                            id = currentWidgets[widgetIndex].id,
                            title = WidgetsRepository.getTitleForDataKey(context, selectedDataKey),
                            unit = WidgetsRepository.getUnitForDataKey(context, selectedDataKey),
                            dataKey = selectedDataKey
                        )
                    } else {
                        DashboardWidget(
                            id = currentWidgets[widgetIndex].id,
                            title = "",
                            dataKey = ""
                        )
                    }
                    updatedWidgets[widgetIndex] = newWidget

                    // Обновляем виджеты
                    dashboardManager.updateWidgets(updatedWidgets)

                    // Сохраняем конфигурацию
                    val normalizedConfigs = normalizeWidgetConfigs(
                        currentWidgetConfigs,
                        updatedWidgets.size
                    ).toMutableList()
                    normalizedConfigs[widgetIndex] = if (selectedDataKey.isNotEmpty()) {
                        FloatingDashboardWidgetConfig(
                            dataKey = selectedDataKey,
                            showTitle = showTitle,
                            showUnit = showUnit,
                            scale = normalizedScale
                        )
                    } else {
                        FloatingDashboardWidgetConfig(dataKey = "")
                    }
                    settingsViewModel.saveDashboardWidgets(normalizedConfigs)

                    // Очищаем историю
                    dashboardManager.clearWidgetHistory(currentWidgets[widgetIndex].id)

                    onDismiss()
                }
            ) {
                Text(text = stringResource(R.string.action_save), fontSize = 24.sp)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel), fontSize = 24.sp)
            }
        }
    )
}

