package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.CycleDataViewModel
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CarDataTabContent(
    canViewModel: CanDataViewModel,
    cycleViewModel: CycleDataViewModel,
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val isGetCycleSignalEnabled by settingsViewModel.isGetCycleSignalEnabled.collectAsStateWithLifecycle()

    val odometer by canViewModel.odometer.collectAsStateWithLifecycle()
    val distanceToNextMaintenance by canViewModel.distanceToNextMaintenance.collectAsStateWithLifecycle()
    val distanceToFuelEmpty by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val breakingForce by canViewModel.breakingForce.collectAsStateWithLifecycle()
    val engineRPM by canViewModel.engineRPM.collectAsStateWithLifecycle()
    val param1 by canViewModel.param1.collectAsStateWithLifecycle()
    val param2 by canViewModel.param2.collectAsStateWithLifecycle()
    val param3 by canViewModel.param3.collectAsStateWithLifecycle()
    val param4 by canViewModel.param4.collectAsStateWithLifecycle()
    val voltage by canViewModel.voltage.collectAsStateWithLifecycle()
    val fuelLevelPercentage by canViewModel.fuelLevelPercentage.collectAsStateWithLifecycle()
    val fuelLevelPercentageFiltered by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val carSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val carSpeedAccurate by canViewModel.carSpeedAccurate.collectAsStateWithLifecycle()
    val wheelsSpeed by canViewModel.wheelsSpeed.collectAsStateWithLifecycle()
    val wheelsPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelsTemperature by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()
    val cruiseSetSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val steerAngle by canViewModel.steerAngle.collectAsStateWithLifecycle()
    val steerSpeed by canViewModel.steerSpeed.collectAsStateWithLifecycle()
    val engineTemperature by canViewModel.engineTemperature.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val gearBoxPreparedGear by canViewModel.gearBoxPreparedGear.collectAsStateWithLifecycle()
    val gearBoxChangeGear by canViewModel.gearBoxChangeGear.collectAsStateWithLifecycle()
    val gearBoxOilTemperature by canViewModel.gearBoxOilTemperature.collectAsStateWithLifecycle()
    val gearBoxDriveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val gearBoxWork by canViewModel.gearBoxWork.collectAsStateWithLifecycle()
    val frontRightSeatMode by canViewModel.frontRightSeatMode.collectAsStateWithLifecycle()
    val frontLeftSeatMode by canViewModel.frontLeftSeatMode.collectAsStateWithLifecycle()
    val outsideTemperature by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val insideTemperature by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val outsideAirQuality by canViewModel.outsideAirQuality.collectAsStateWithLifecycle()
    val insideAirQuality by canViewModel.insideAirQuality.collectAsStateWithLifecycle()
    val isWindowsBlocked by canViewModel.isWindowsBlocked.collectAsStateWithLifecycle()
    val motorHoursTrip by canViewModel.motorHoursTrip.collectAsStateWithLifecycle()

    val voltageC by cycleViewModel.voltage.collectAsStateWithLifecycle()
    val carSpeedC by cycleViewModel.carSpeed.collectAsStateWithLifecycle()
    val lateralAccelerationC by cycleViewModel.lateralAcceleration.collectAsStateWithLifecycle()
    val longitudinalAccelerationC by cycleViewModel.longitudinalAcceleration.collectAsStateWithLifecycle()
    val pressure1C by cycleViewModel.pressure1.collectAsStateWithLifecycle()
    val pressure2C by cycleViewModel.pressure2.collectAsStateWithLifecycle()
    val pressure3C by cycleViewModel.pressure3.collectAsStateWithLifecycle()
    val pressure4C by cycleViewModel.pressure4.collectAsStateWithLifecycle()
    val temperature1C by cycleViewModel.temperature1.collectAsStateWithLifecycle()
    val temperature2C by cycleViewModel.temperature2.collectAsStateWithLifecycle()
    val temperature3C by cycleViewModel.temperature3.collectAsStateWithLifecycle()
    val temperature4C by cycleViewModel.temperature4.collectAsStateWithLifecycle()
    val engineRPMC by cycleViewModel.engineRPM.collectAsStateWithLifecycle()

    val motorHours by appDataViewModel.motorHours.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("voltage"), valueToString(voltage, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("steerAngle"), valueToString(steerAngle, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("steerSpeed"), valueToString(steerSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("engineRPM"), valueToString(engineRPM, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param1"), valueToString(param1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param2"), valueToString(param2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param3"), valueToString(param3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("param4"), valueToString(param4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeed"), valueToString(carSpeed, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("carSpeedAccurate"), valueToString(carSpeedAccurate, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Speed"), valueToString(wheelsSpeed.wheel1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Speed"), valueToString(wheelsSpeed.wheel2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Speed"), valueToString(wheelsSpeed.wheel3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Speed"), valueToString(wheelsSpeed.wheel4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Pressure"), valueToString(wheelsPressure.wheel1, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Pressure"), valueToString(wheelsPressure.wheel2, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Pressure"), valueToString(wheelsPressure.wheel3, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Pressure"), valueToString(wheelsPressure.wheel4, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel1Temperature"), valueToString(wheelsTemperature.wheel1, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel2Temperature"), valueToString(wheelsTemperature.wheel2, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel3Temperature"), valueToString(wheelsTemperature.wheel3, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("wheel4Temperature"), valueToString(wheelsTemperature.wheel4, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("cruiseSetSpeed"), valueToString(cruiseSetSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("odometer"), valueToString(odometer)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("distanceToNextMaintenance"), valueToString(distanceToNextMaintenance)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("distanceToFuelEmpty"), valueToString(distanceToFuelEmpty)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("fuelLevelPercentage"), valueToString(fuelLevelPercentage)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("fuelLevelPercentageFiltered"), valueToString(fuelLevelPercentageFiltered)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("breakingForce"), valueToString(breakingForce)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("engineTemperature"), valueToString(engineTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxOilTemperature"), valueToString(gearBoxOilTemperature)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxMode"), gearBoxMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxDriveMode"), gearBoxDriveMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxWork"), gearBoxWork) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxCurrentGear"), valueToString(gearBoxCurrentGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxPreparedGear"), valueToString(gearBoxPreparedGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("gearBoxChangeGear"),
                valueToString(gearBoxChangeGear, booleanTrue = "переключение", booleanFalse = "нет")) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("frontLeftSeatMode"), seatModeToString(frontLeftSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("frontRightSeatMode"), seatModeToString(frontRightSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("outsideTemperature"), valueToString(outsideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("insideTemperature"), valueToString(insideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("outsideAirQuality"), valueToString(outsideAirQuality)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("insideAirQuality"), valueToString(insideAirQuality)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("isWindowsBlocked"), valueToString(isWindowsBlocked,
                booleanTrue = "заблокированы", booleanFalse = "разблокированы")) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("motorHours"), valueToString(motorHours, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey("motorHoursTrip"), valueToString(motorHoursTrip, 1)) }
            if (isGetCycleSignalEnabled) {
                item { StatusRow("Cycle напряжение, В", valueToString(voltageC, 1)) }
                item { StatusRow("Cycle скорость, км/ч", valueToString(carSpeedC, 1)) }
                item { StatusRow("Cycle обороты двигателя, об/мин", valueToString(engineRPMC, 1)) }
                //item { StatusRow("Cycle угловая скорость рысканья, °/с", valueToString(yawRateC, 2)) }
                item {
                    StatusRow(
                        "Cycle поперечное ускорение, м/с2",
                        valueToString(lateralAccelerationC, 2)
                    )
                }
                item {
                    StatusRow(
                        "Cycle продольное ускорение, м/с2",
                        valueToString(longitudinalAccelerationC, 2)
                    )
                }
                item { StatusRow("Cycle давление ПЛ, бар", valueToString(pressure1C, 1)) }
                item { StatusRow("Cycle давление ПП, бар", valueToString(pressure2C, 1)) }
                item { StatusRow("Cycle давление ЗЛ, бар", valueToString(pressure3C, 1)) }
                item { StatusRow("Cycle давление ЗП, бар", valueToString(pressure4C, 1)) }
                item { StatusRow("Cycle температура ПЛ, °C", valueToString(temperature1C, 1)) }
                item { StatusRow("Cycle температура ПП, °C", valueToString(temperature2C, 1)) }
                item { StatusRow("Cycle температура ЗП, °C", valueToString(temperature3C, 1)) }
                item { StatusRow("Cycle температура ЗЛ, °C", valueToString(temperature4C, 1)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogsTabContent(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val logLevel by settingsViewModel.logLevel.collectAsStateWithLifecycle()

    val logLevels = listOf("DEBUG", "INFO", "WARN", "ERROR")
    var searchText by remember { mutableStateOf("") }

    var showSaveDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                textStyle = TextStyle(fontSize = 20.sp),
                onValueChange = { newText ->
                    searchText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "Фильтр по тексту (минимум 3 символа)",
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите текст для поиска...",
                        fontSize = 18.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(
                            onClick = { searchText = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                    }
                )
            )

            GenericDropdownSelector(
                selectedValue = logLevel,
                options = logLevels,
                onValueChange = { settingsViewModel.saveLogLevel(it) },
                width = 200.dp,
                valueFontSize = 20.sp,
                itemFontSize = 20.sp
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить в файл",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Сохранение файла") },
                    text = {
                        Text("Сохранить журнал в папку Загрузки")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val csvLogEntries = mutableListOf<String>()
                                logs.forEach { logEntry ->
                                    csvLogEntries.add(logEntry)
                                }
                                onSaveToFile("log", csvLogEntries)
                                showSaveDialog = false
                            }
                        ) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }

        LogsCard(
            logs = logs,
            logLevel = logLevel,
            searchText = searchText
        )
    }
}

@Composable
fun CanTabContent(
    viewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    onSaveToFile: (String, List<String>) -> Unit
) {
    val canFramesStructured by canViewModel.canFramesStructured.collectAsStateWithLifecycle()
    val canFrameTime by viewModel.canFrameTime.collectAsStateWithLifecycle()

    var showSaveDialog by remember { mutableStateOf(false) }

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    val dateTimeFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    val formattedTime = remember(canFrameTime) {
        canFrameTime?.let { timeFormat.format(it) } ?: "нет данных"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        val sortedCanEntries = remember(canFramesStructured) {
            canFramesStructured.entries.sortedBy { it.key }
        }

        Text(
            text = "CAN ID (${sortedCanEntries.size}). " +
                "Последние данные: $formattedTime",
            modifier = Modifier.padding(6.dp),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { showSaveDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Сохранить текущие CAN данные в файл",
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text("Сохранение файла") },
                    text = {
                        Text("Сохранить ${sortedCanEntries.size} CAN ID в папку Загрузки")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val csvCanEntries = mutableListOf<String>()
                                sortedCanEntries.forEach { (canId, frames) ->
                                    frames.forEach { frame ->
                                        val timestamp = dateTimeFormat.format(frame.date)
                                        val rawValueHex = frame.rawValue.joinToString(" ") {
                                            "%02X".format(it)
                                        }
                                        val rawValueDec = frame.rawValue.joinToString(";") {
                                            (it.toInt() and 0xFF).toString()
                                        }
                                        csvCanEntries.add("$timestamp;$canId;$rawValueHex; ;$rawValueDec")
                                    }
                                }
                                onSaveToFile("can", csvCanEntries)
                                showSaveDialog = false
                            }
                        ) {
                            Text("Сохранить")
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text("Отмена")
                        }
                    }
                )
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            elevation = CardDefaults.cardElevation(4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(
                        items = sortedCanEntries,
                        key = { it.key }
                    ) { (canId, frames) ->
                        val lastFrame = frames.lastOrNull()
                        CanIdEntry(
                            canId = canId,
                            lastFrame = lastFrame
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ATcmdTabContent(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit
) {
    val atLogs by viewModel.atLogs.collectAsStateWithLifecycle()

    var atCmdText by remember { mutableStateOf("") }

    val focusManager = LocalFocusManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = atCmdText,
                textStyle = TextStyle(fontSize = 20.sp),
                onValueChange = { newText ->
                    atCmdText = newText
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                label = {
                    Text(
                        text = "AT команда",
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = "Введите AT команду",
                        fontSize = 18.sp
                    )
                },
                singleLine = true,
                trailingIcon = {
                    if (atCmdText.isNotEmpty()) {
                        IconButton(
                            onClick = { atCmdText = "" }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Очистить"
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions.Default.copy(
                    imeAction = ImeAction.Send
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (atCmdText.isNotEmpty()) {
                            onServiceCommand(
                                BackgroundService.ACTION_SEND_AT,
                                BackgroundService.EXTRA_AT_CMD,
                                atCmdText
                            )
                            //atCmdText = ""
                        }
                        focusManager.clearFocus()
                    }
                )
            )
            Box(modifier = Modifier.wrapContentSize()) {
                Button(
                    onClick = {
                        if (atCmdText != "") {
                            onServiceCommand(
                                BackgroundService.ACTION_SEND_AT,
                                BackgroundService.EXTRA_AT_CMD,
                                atCmdText
                            )
                            //atCmdText = ""
                        }
                    },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = "Отправить",
                        fontSize = 24.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(modifier = Modifier.wrapContentSize()) {
                Button(
                    onClick = {
                        onServiceCommand(
                            BackgroundService.ACTION_READ_ALL_SMS,
                            "",
                            ""
                        )
                    },
                    modifier = Modifier.width(200.dp)
                ) {
                    Text(
                        text = "Получить все SMS",
                        fontSize = 24.sp,
                        maxLines = 2,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        ATLogsCard(
            logs = atLogs
        )
    }
}
