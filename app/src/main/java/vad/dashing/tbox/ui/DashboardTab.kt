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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DashboardWidget
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetViewModel
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardTab(
    viewModel: TboxViewModel,
    widgetViewModel: WidgetViewModel,
    settingsViewModel: SettingsViewModel
) {
    val dashboardState by widgetViewModel.dashboardState.collectAsStateWithLifecycle()

    val widgetsConfig by settingsViewModel.dashboardWidgetsConfig.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()

    // Собираем все необходимые состояния как State
    val voltage by viewModel.voltage.collectAsStateWithLifecycle()
    val steerAngle by viewModel.steerAngle.collectAsStateWithLifecycle()
    val steerSpeed by viewModel.steerSpeed.collectAsStateWithLifecycle()
    val engineRPM by viewModel.engineRPM.collectAsStateWithLifecycle()
    val carSpeed by viewModel.carSpeed.collectAsStateWithLifecycle()
    val carSpeedAccurate by viewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val cruiseSetSpeed by viewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val odometer by viewModel.odometer.collectAsStateWithLifecycle()
    val distanceToNextMaintenance by viewModel.distanceToNextMaintenance.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by viewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val breakingForce by viewModel.breakingForce.collectAsStateWithLifecycle()
    val fuelLevelPercentage by viewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelLevelPercentageFiltered by viewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val engineTemperature by viewModel.engineTemperature.collectAsStateWithLifecycle()
    val wheelsSpeed by viewModel.wheelsSpeed.collectAsStateWithLifecycle()
    val wheelsPressure by viewModel.wheelsPressure.collectAsStateWithLifecycle()
    val gearBoxOilTemperature by viewModel.gearBoxOilTemperature.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by viewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val gearBoxPreparedGear by viewModel.gearBoxPreparedGear.collectAsStateWithLifecycle()
    val gearBoxChangeGear by viewModel.gearBoxChangeGear.collectAsStateWithLifecycle()
    val gearBoxMode by viewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxDriveMode by viewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val gearBoxWork by viewModel.gearBoxWork.collectAsStateWithLifecycle()
    val frontLeftSeatMode by viewModel.frontLeftSeatMode.collectAsStateWithLifecycle()
    val frontRightSeatMode by viewModel.frontRightSeatMode.collectAsStateWithLifecycle()
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val netState by viewModel.netState.collectAsStateWithLifecycle()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val locLastUpdate = remember(locValues.updateTime) {
        locValues.updateTime?.let { updateTime ->
            timeFormat.format(updateTime)
        } ?: ""
    }
    val locLastRefersh = remember(locationUpdateTime) {
        locationUpdateTime?.let { locationUpdateTime ->
            timeFormat.format(locationUpdateTime)
        } ?: ""
    }

    var showDialogForIndex by remember { mutableStateOf<Int?>(null) }

    // Принудительная инициализация при первом показе
    LaunchedEffect(Unit) {
        if (dashboardState.widgets.isEmpty()) {
            val widgets = if (widgetsConfig.isNotEmpty()) {
                loadWidgetsFromConfig(widgetsConfig, dashboardRows * dashboardCols)
            } else {
                // Создаем 12 пустых виджетов (4x3)
                List(dashboardRows * dashboardCols) { index ->
                    DashboardWidget(
                        id = index,
                        title = "",
                        dataKey = ""
                    )
                }
            }
            widgetViewModel.updateDashboardWidgets(widgets)
        }
    }

    // Инициализация из конфигурации при изменении widgetsConfig
    LaunchedEffect(widgetsConfig) {
        if (widgetsConfig.isNotEmpty()) {
            val widgets = loadWidgetsFromConfig(widgetsConfig, dashboardRows * dashboardCols)
            widgetViewModel.updateDashboardWidgets(widgets)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Проверяем, есть ли виджеты для отображения
        if (dashboardState.widgets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Загрузка...",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Сетка 4x3 - 4 колонки по горизонтали, 3 строки по вертикали
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 3 строки
                for (row in 0 until dashboardRows) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 4 колонки в каждой строке
                        for (col in 0 until dashboardCols) {
                            val index = row * dashboardCols + col
                            val widget = dashboardState.widgets.getOrNull(index) ?: continue

                            Box(
                                modifier = Modifier.weight(1f)
                            ) {
                                val value = when (widget.dataKey) {
                                    "voltage" -> valueToString(voltage, 1)
                                    "steerAngle" -> valueToString(steerAngle, 1)
                                    "steerSpeed" -> valueToString(steerSpeed)
                                    "engineRPM" -> valueToString(engineRPM, 1)
                                    "carSpeed" -> valueToString(carSpeed, 1)
                                    "carSpeedAccurate" -> valueToString(carSpeedAccurate, 1)
                                    "wheel1Speed" -> valueToString(wheelsSpeed.wheel1, 1)
                                    "wheel2Speed" -> valueToString(wheelsSpeed.wheel2, 1)
                                    "wheel3Speed" -> valueToString(wheelsSpeed.wheel3, 1)
                                    "wheel4Speed" -> valueToString(wheelsSpeed.wheel4, 1)
                                    "wheel1Pressure" -> valueToString(wheelsPressure.wheel1, 2)
                                    "wheel2Pressure" -> valueToString(wheelsPressure.wheel2, 2)
                                    "wheel3Pressure" -> valueToString(wheelsPressure.wheel3, 2)
                                    "wheel4Pressure" -> valueToString(wheelsPressure.wheel4, 2)
                                    "cruiseSetSpeed" -> valueToString(cruiseSetSpeed)
                                    "odometer" -> valueToString(odometer)
                                    "distanceToNextMaintenance" -> valueToString(distanceToNextMaintenance)
                                    "distanceToFuelEmpty" -> valueToString(distanceToFuelEmpty)
                                    "breakingForce" -> valueToString(breakingForce)
                                    "fuelLevelPercentage" -> valueToString(fuelLevelPercentage)
                                    "fuelLevelPercentageFiltered" -> valueToString(fuelLevelPercentageFiltered)
                                    "engineTemperature" -> valueToString(engineTemperature, 1)
                                    "gearBoxOilTemperature" -> valueToString(gearBoxOilTemperature)
                                    "gearBoxCurrentGear" -> valueToString(gearBoxCurrentGear)
                                    "gearBoxPreparedGear" -> valueToString(gearBoxPreparedGear)
                                    "gearBoxChangeGear" -> valueToString(
                                        gearBoxChangeGear,
                                        booleanTrue = "переключение",
                                        booleanFalse = "нет"
                                    )
                                    "gearBoxMode" -> gearBoxMode
                                    "gearBoxDriveMode" -> gearBoxDriveMode
                                    "gearBoxWork" -> gearBoxWork
                                    "frontRightSeatMode" -> seatModeToString(frontRightSeatMode)
                                    "frontLeftSeatMode" -> seatModeToString(frontLeftSeatMode)
                                    "signalLevel" -> valueToString(netState.signalLevel)
                                    "netStatus" -> netState.netStatus
                                    "regStatus" -> netState.regStatus
                                    "simStatus" -> netState.simStatus
                                    "locateStatus" -> valueToString(locValues.locateStatus)
                                    "isLocValuesTrue" -> valueToString(isLocValuesTrue)
                                    "gnssSpeed" -> valueToString(locValues.speed, 1)
                                    "longitude" -> valueToString(locValues.longitude, 6)
                                    "latitude" -> valueToString(locValues.latitude, 6)
                                    "altitude" -> valueToString(locValues.altitude, 2)
                                    "visibleSatellites" -> valueToString(locValues.visibleSatellites)
                                    "trueDirection" -> valueToString(locValues.trueDirection, 1)
                                    "locationUpdateTime" -> locLastUpdate
                                    "locationRefreshTime" -> locLastRefersh
                                    else -> {
                                        ""
                                    }
                                }
                                DashboardWidgetItem(
                                    widget = widget,
                                    value = value,
                                    onEditClick = {
                                        showDialogForIndex = index
                                    },
                                    widgetViewModel,
                                    settingsViewModel
                                )
                            }
                        }
                    }
                }
            }
        }

        // Диалог выбора данных
        showDialogForIndex?.let { index ->
            WidgetSelectionDialog(
                widgetViewModel = widgetViewModel,
                settingsViewModel = settingsViewModel,
                widgetIndex = index,
                currentWidgets = dashboardState.widgets,
                onDismiss = { showDialogForIndex = null },
            )
        }
    }
}

/*@Composable
fun DashboardWidgetItem(
    widget: DashboardWidget,
    value: String,
    onEditClick: () -> Unit,
    viewModel: WidgetViewModel
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onEditClick() }, // .combinedClickable(onClick = {}, onLongClick = { onEditClick() })
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), // Уменьшаем отступы для экономии места
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = widget.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            //Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = value,
                fontSize = 46.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            if (widget.unit.isNotEmpty()) {
                Text(
                    text = widget.unit,
                    fontSize = 26.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}*/

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WidgetSelectionDialog(
    widgetViewModel: WidgetViewModel,
    settingsViewModel: SettingsViewModel,
    widgetIndex: Int,
    currentWidgets: List<DashboardWidget>,
    onDismiss: () -> Unit,
) {
    var selectedDataKey by remember {
        mutableStateOf(currentWidgets.getOrNull(widgetIndex)?.dataKey ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Выберите данные для отображения",
                fontSize = 24.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    "Плитка ${widgetIndex + 1}",
                    fontSize = 24.sp,
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontWeight = FontWeight.Medium
                )

                // Список доступных данных с прокруткой
                val availableOptions = listOf("" to "Не выбрано") +
                        WidgetsRepository.getAvailableDataKeys()
                            .filter { it.isNotEmpty() }
                            .map { key ->
                                key to WidgetsRepository.getTitleUnitForDataKey(key)
                            }

                // Прокручиваемый список
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp) // Ограничиваем максимальную высоту
                ) {
                    items(availableOptions) { (key, displayName) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedDataKey = key
                                }
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

                        // Разделитель между элементами (кроме последнего)
                        if (key != availableOptions.last().first) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 48.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    // Обновляем виджет
                    val updatedWidgets = currentWidgets.toMutableList()
                    val newWidget = if (selectedDataKey.isNotEmpty()) {
                        DashboardWidget(
                            id = widgetIndex,
                            title = WidgetsRepository.getTitleForDataKey(selectedDataKey),
                            unit = WidgetsRepository.getUnitForDataKey(selectedDataKey),
                            dataKey = selectedDataKey
                        )
                    } else {
                        DashboardWidget(
                            id = widgetIndex,
                            title = "",
                            dataKey = ""
                        )
                    }
                    updatedWidgets[widgetIndex] = newWidget

                    // Сохраняем в ViewModel
                    widgetViewModel.updateDashboardWidgets(updatedWidgets)

                    // Сохраняем конфигурацию
                    val config = updatedWidgets.joinToString("|") { it.dataKey }
                    settingsViewModel.saveDashboardWidgets(config)

                    widgetViewModel.clearWidgetHistory(widgetIndex)

                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss
            ) {
                Text("Отмена")
            }
        }
    )
}

// Функция для загрузки виджетов из конфигурации
private fun loadWidgetsFromConfig(config: String, numberWidgets: Int): List<DashboardWidget> {
    val dataKeys = if (config.isNotEmpty()) {
        config.split("|")
    } else {
        List(numberWidgets) { "" }
    }

    return (0 until numberWidgets).map { index ->
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