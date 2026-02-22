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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
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
import vad.dashing.tbox.R
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
    val context = LocalContext.current
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
        val switchingLabel = stringResource(R.string.value_switching)
        val noLabel = stringResource(R.string.value_no)
        val blockedLabel = stringResource(R.string.value_blocked)
        val unblockedLabel = stringResource(R.string.value_unblocked)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "voltage"), valueToString(voltage, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "steerAngle"), valueToString(steerAngle, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "steerSpeed"), valueToString(steerSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "engineRPM"), valueToString(engineRPM, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "param1"), valueToString(param1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "param2"), valueToString(param2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "param3"), valueToString(param3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "param4"), valueToString(param4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "carSpeed"), valueToString(carSpeed, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "carSpeedAccurate"), valueToString(carSpeedAccurate, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel1Speed"), valueToString(wheelsSpeed.wheel1, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel2Speed"), valueToString(wheelsSpeed.wheel2, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel3Speed"), valueToString(wheelsSpeed.wheel3, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel4Speed"), valueToString(wheelsSpeed.wheel4, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel1Pressure"), valueToString(wheelsPressure.wheel1, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel2Pressure"), valueToString(wheelsPressure.wheel2, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel3Pressure"), valueToString(wheelsPressure.wheel3, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel4Pressure"), valueToString(wheelsPressure.wheel4, 2)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel1Temperature"), valueToString(wheelsTemperature.wheel1, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel2Temperature"), valueToString(wheelsTemperature.wheel2, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel3Temperature"), valueToString(wheelsTemperature.wheel3, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "wheel4Temperature"), valueToString(wheelsTemperature.wheel4, 0)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "cruiseSetSpeed"), valueToString(cruiseSetSpeed)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "odometer"), valueToString(odometer)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "distanceToNextMaintenance"), valueToString(distanceToNextMaintenance)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "distanceToFuelEmpty"), valueToString(distanceToFuelEmpty)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "fuelLevelPercentage"), valueToString(fuelLevelPercentage)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "fuelLevelPercentageFiltered"), valueToString(fuelLevelPercentageFiltered)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "breakingForce"), valueToString(breakingForce)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "engineTemperature"), valueToString(engineTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxOilTemperature"), valueToString(gearBoxOilTemperature)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxMode"), gearBoxMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxDriveMode"), gearBoxDriveMode) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxWork"), gearBoxWork) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxCurrentGear"), valueToString(gearBoxCurrentGear)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxPreparedGear"), valueToString(gearBoxPreparedGear)) }
            item {
                StatusRow(
                    WidgetsRepository.getTitleUnitForDataKey(context, "gearBoxChangeGear"),
                    valueToString(gearBoxChangeGear, booleanTrue = switchingLabel, booleanFalse = noLabel)
                )
            }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "frontLeftSeatMode"), seatModeToString(context, frontLeftSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "frontRightSeatMode"), seatModeToString(context, frontRightSeatMode)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "outsideTemperature"), valueToString(outsideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "insideTemperature"), valueToString(insideTemperature, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "outsideAirQuality"), valueToString(outsideAirQuality)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "insideAirQuality"), valueToString(insideAirQuality)) }
            item {
                StatusRow(
                    WidgetsRepository.getTitleUnitForDataKey(context, "isWindowsBlocked"),
                    valueToString(isWindowsBlocked, booleanTrue = blockedLabel, booleanFalse = unblockedLabel)
                )
            }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "motorHours"), valueToString(motorHours, 1)) }
            item { StatusRow(WidgetsRepository.getTitleUnitForDataKey(context, "motorHoursTrip"), valueToString(motorHoursTrip, 1)) }
            if (isGetCycleSignalEnabled) {
                item { StatusRow(stringResource(R.string.cycle_voltage), valueToString(voltageC, 1)) }
                item { StatusRow(stringResource(R.string.cycle_speed), valueToString(carSpeedC, 1)) }
                item { StatusRow(stringResource(R.string.cycle_engine_rpm), valueToString(engineRPMC, 1)) }
                //item { StatusRow("Cycle угловая скорость рысканья, °/с", valueToString(yawRateC, 2)) }
                item {
                    StatusRow(
                        stringResource(R.string.cycle_lateral_acceleration),
                        valueToString(lateralAccelerationC, 2)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.cycle_longitudinal_acceleration),
                        valueToString(longitudinalAccelerationC, 2)
                    )
                }
                item { StatusRow(stringResource(R.string.cycle_pressure_fl), valueToString(pressure1C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_pressure_fr), valueToString(pressure2C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_pressure_rl), valueToString(pressure3C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_pressure_rr), valueToString(pressure4C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_temperature_fl), valueToString(temperature1C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_temperature_fr), valueToString(temperature2C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_temperature_rr), valueToString(temperature3C, 1)) }
                item { StatusRow(stringResource(R.string.cycle_temperature_rl), valueToString(temperature4C, 1)) }
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
                        text = stringResource(R.string.logs_filter_label),
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.logs_filter_placeholder),
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
                                contentDescription = stringResource(R.string.action_clear)
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
                    text = stringResource(R.string.button_save_to_file),
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text(stringResource(R.string.dialog_file_saving_title)) },
                    text = {
                        Text(stringResource(R.string.dialog_save_logs_downloads))
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
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text(stringResource(R.string.action_cancel))
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

    val noDataLabel = stringResource(R.string.value_no_data)
    val formattedTime = remember(canFrameTime, noDataLabel) {
        canFrameTime?.let { timeFormat.format(it) } ?: noDataLabel
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
            text = stringResource(R.string.can_id_last_data, sortedCanEntries.size, formattedTime),
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
                    text = stringResource(R.string.button_save_can_to_file),
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }

            if (showSaveDialog) {
                AlertDialog(
                    onDismissRequest = { showSaveDialog = false },
                    title = { Text(stringResource(R.string.dialog_file_saving_title)) },
                    text = {
                        Text(stringResource(R.string.dialog_save_can_downloads, sortedCanEntries.size))
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
                            Text(stringResource(R.string.action_save))
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { showSaveDialog = false }
                        ) {
                            Text(stringResource(R.string.action_cancel))
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
                        text = stringResource(R.string.at_command_label),
                        fontSize = 20.sp
                    )
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.at_command_placeholder),
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
                                contentDescription = stringResource(R.string.action_clear)
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
                        text = stringResource(R.string.action_send),
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
                        text = stringResource(R.string.action_get_all_sms),
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
