package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import vad.dashing.tbox.MainDashboardViewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetsRepository

@Composable
fun MainDashboardTab(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    settingsViewModel: SettingsViewModel,
    appDataViewModel: AppDataViewModel,
    onTboxRestartClick: () -> Unit,
) {
    val dashboardViewModel: MainDashboardViewModel = viewModel()
    val dashboardState by dashboardViewModel.dashboardManager.dashboardState.collectAsStateWithLifecycle()
    val widgetsConfig by settingsViewModel.dashboardWidgetsConfig.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()

    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(widgetsConfig, dashboardRows, dashboardCols) {
        val totalWidgets = dashboardRows * dashboardCols

        // Всегда загружаем/создаем виджеты при изменении зависимостей
        val widgets = if (widgetsConfig.isNotEmpty()) {
            loadWidgetsFromConfig(widgetsConfig, totalWidgets)
        } else {
            List(totalWidgets) { index ->
                DashboardWidget(
                    id = index,
                    title = "",
                    dataKey = ""
                )
            }
        }

        dashboardViewModel.dashboardManager.updateWidgets(widgets)
    }

    var restartEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartEnabled) {
        if (!restartEnabled) {
            delay(15000) // Блокировка на 15 секунд
            restartEnabled = true
        }
    }

    val dataProvider = remember { TboxDataProvider(tboxViewModel, canViewModel, appDataViewModel) }

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
                Text("Загрузка...")
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

                            Box(modifier = Modifier.weight(1f)) {
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
                                            canViewModel = canViewModel
                                        )
                                    }
                                    "gearBoxWidget" -> {
                                        DashboardGearBoxWidgetItem(
                                            widget = widget,
                                            onClick = { showDialogForIndex = index },
                                            onLongClick = {},
                                            canViewModel = canViewModel
                                        )
                                    }
                                    "wheelsPressureWidget" -> {
                                        DashboardWheelsPressureWidgetItem(
                                            widget = widget,
                                            onClick = { showDialogForIndex = index },
                                            onLongClick = {},
                                            canViewModel = canViewModel
                                        )
                                    }
                                    "tempInOutWidget" -> {
                                        DashboardTempInOutWidgetItem(
                                            widget = widget,
                                            onClick = { showDialogForIndex = index },
                                            onLongClick = {},
                                            canViewModel = canViewModel
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
                                            dashboardChart = dashboardChart
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        showDialogForIndex?.let { index ->
            MainWidgetSelectionDialog(
                dashboardViewModel = dashboardViewModel,
                settingsViewModel = settingsViewModel,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                onDismiss = { showDialogForIndex = null }
            )
        }
    }
}

@Composable
fun MainWidgetSelectionDialog(
    dashboardViewModel: MainDashboardViewModel,
    settingsViewModel: SettingsViewModel,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    onDismiss: () -> Unit
) {
    WidgetSelectionDialogImpl(
        dashboardManager = dashboardViewModel.dashboardManager,
        settingsViewModel = settingsViewModel,
        widgetIndex = widgetIndex,
        currentWidgets = currentWidgets,
        onDismiss = onDismiss,
        isMainDashboard = true
    )
}

@Composable
fun WidgetSelectionDialogImpl(
    dashboardManager: DashboardManager,
    settingsViewModel: SettingsViewModel,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    onDismiss: () -> Unit,
    isMainDashboard: Boolean
) {
    var selectedDataKey by remember {
        mutableStateOf(currentWidgets.getOrNull(widgetIndex)?.dataKey ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Выберите данные для отображения", fontSize = 24.sp) },
        text = {
            Column {
                Text(
                    "Плитка ${widgetIndex + 1}",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontWeight = FontWeight.Medium
                )

                val availableOptions = listOf("" to "Не выбрано") +
                        WidgetsRepository.getAvailableDataKeys()
                            .filter { it.isNotEmpty() }
                            .map { key ->
                                key to WidgetsRepository.getTitleUnitForDataKey(key)
                            }

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(availableOptions) { (key, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedDataKey = key }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedDataKey == key,
                                onClick = { selectedDataKey = key }
                            )
                            Text(
                                text = displayName,
                                fontSize = 22.sp,
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
        },
        confirmButton = {
            Button(
                onClick = {
                    val updatedWidgets = currentWidgets.toMutableList()
                    val newWidget = if (selectedDataKey.isNotEmpty()) {
                        DashboardWidget(
                            id = currentWidgets[widgetIndex].id,
                            title = WidgetsRepository.getTitleForDataKey(selectedDataKey),
                            unit = WidgetsRepository.getUnitForDataKey(selectedDataKey),
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
                    val config = updatedWidgets.joinToString("|") { it.dataKey }

                    if (isMainDashboard) {
                        settingsViewModel.saveDashboardWidgets(config)
                    } else {
                        settingsViewModel.saveFloatingDashboardWidgets(config)
                    }

                    // Очищаем историю
                    dashboardManager.clearWidgetHistory(currentWidgets[widgetIndex].id)

                    onDismiss()
                }
            ) {
                Text(text = "Сохранить", fontSize = 24.sp)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = "Отмена", fontSize = 24.sp)
            }
        }
    )
}


// Функция для загрузки виджетов из конфигурации
fun loadWidgetsFromConfig(config: String, widgetCount: Int): List<DashboardWidget> {
    val dataKeys = if (config.isNotEmpty()) {
        config.split("|")
    } else {
        List(widgetCount) { "" }
    }

    return (0 until widgetCount).map { index ->
        val dataKey = dataKeys.getOrNull(index) ?: ""
        if (dataKey.isNotEmpty() && dataKey != "null") {
            DashboardWidget(
                id = index,
                title = WidgetsRepository.getTitleForDataKey(dataKey),
                unit = WidgetsRepository.getUnitForDataKey(dataKey),
                dataKey = dataKey
            )
        } else {
            DashboardWidget(
                id = index,
                title = "",
                dataKey = ""
            )
        }
    }
}