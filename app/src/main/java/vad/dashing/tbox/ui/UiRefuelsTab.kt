package vad.dashing.tbox.ui

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.FuelTypeOption
import vad.dashing.tbox.FuelTypes
import vad.dashing.tbox.R
import vad.dashing.tbox.RefuelRecord
import vad.dashing.tbox.REFUEL_AMBIENT_TEMP_DEFAULT_C
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.fuellevelcalibration.FuelCalibrationReportFormatter
import vad.dashing.tbox.valueToString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefuelsTab(
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onSaveToFile: (String, List<String>) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
) {
    val refuels by appDataViewModel.refuels.collectAsStateWithLifecycle()
    val fuelTankLiters by settingsViewModel.fuelTankLiters.collectAsStateWithLifecycle()
    val fuelCalibrationJson by settingsViewModel.fuelCalibrationJson.collectAsStateWithLifecycle()
    val fuelCalibrationZoneCount by settingsViewModel.fuelCalibrationZoneCount.collectAsStateWithLifecycle()
    val fuelCalibrationMaturityThreshold by settingsViewModel.fuelCalibrationMaturityThreshold.collectAsStateWithLifecycle()
    val reportLines = remember(
        fuelCalibrationJson,
        fuelTankLiters,
        fuelCalibrationZoneCount,
        fuelCalibrationMaturityThreshold,
    ) {
        FuelCalibrationReportFormatter.linesOrNull(
            fuelCalibrationJson,
            fuelTankLiters,
            fuelCalibrationZoneCount,
            fuelCalibrationMaturityThreshold,
        )
    }
    val sortedRefuels = remember(refuels) { refuels.sortedByDescending { it.timeEpochMs } }
    val context = LocalContext.current
    val dateTimeFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }
    var showExportDialog by remember { mutableStateOf(false) }
    var pendingDeleteRefuelId by remember { mutableStateOf<String?>(null) }
    var pendingCalibrationReset by remember { mutableStateOf(false) }
    val actualEdits = remember { mutableStateMapOf<String, String>() }
    val priceEdits = remember { mutableStateMapOf<String, String>() }
    val sourceEdits = remember { mutableStateMapOf<String, String>() }
    val tempEdits = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(refuels) {
        val ids = refuels.map { it.id }.toSet()
        actualEdits.keys.retainAll(ids)
        priceEdits.keys.retainAll(ids)
        sourceEdits.keys.retainAll(ids)
        tempEdits.keys.retainAll(ids)
    }

    var tankLitersDraft by remember { mutableStateOf(fuelTankLiters.toString()) }
    LaunchedEffect(fuelTankLiters) {
        tankLitersDraft = fuelTankLiters.toString()
    }
    var zoneCountDraft by remember { mutableStateOf(fuelCalibrationZoneCount.toString()) }
    LaunchedEffect(fuelCalibrationZoneCount) {
        zoneCountDraft = fuelCalibrationZoneCount.toString()
    }
    var maturityDraft by remember { mutableStateOf(fuelCalibrationMaturityThreshold.toString()) }
    LaunchedEffect(fuelCalibrationMaturityThreshold) {
        maturityDraft = fuelCalibrationMaturityThreshold.toString()
    }

    val calibrationScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val listState = rememberLazyListState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
    ) {
        val refuelAreaHeight = maxHeight / 2

        Column(Modifier.fillMaxSize()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    enabled = refuels.isNotEmpty(),
                    onClick = { showExportDialog = true },
                ) {
                    Text(stringResource(R.string.refuels_export), fontSize = 22.sp)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(refuelAreaHeight),
            ) {
                if (sortedRefuels.isEmpty()) {
                    Text(
                        text = stringResource(R.string.refuels_empty),
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 24.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                } else {
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(end = 14.dp, bottom = 10.dp)
                                .horizontalScroll(horizontalScrollState),
                        ) {
                            RefuelHeaderRow()
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                items(sortedRefuels.size, key = { sortedRefuels[it].id }) { index ->
                                    val refuel = sortedRefuels[index]
                                    RefuelTableRow(
                                        refuel = refuel,
                                        dateTimeFormat = dateTimeFormat,
                                        tempDraft = tempEdits[refuel.id] ?: valueToString(
                                            refuel.ambientTempAtRefuel ?: REFUEL_AMBIENT_TEMP_DEFAULT_C,
                                            1,
                                        ),
                                        actualDraft = actualEdits[refuel.id] ?: valueToString(refuel.actualLiters, 2),
                                        priceDraft = priceEdits[refuel.id] ?: (refuel.pricePerLiterRub?.let { valueToString(it, 2) } ?: ""),
                                        sourceDraft = sourceEdits[refuel.id] ?: refuel.priceSourceName.orEmpty(),
                                        onTempDraftChange = { tempEdits[refuel.id] = it },
                                        onActualDraftChange = { actualEdits[refuel.id] = it },
                                        onPriceDraftChange = { priceEdits[refuel.id] = it },
                                        onSourceDraftChange = { sourceEdits[refuel.id] = it },
                                        onTempCommit = { draft ->
                                            parseLocalizedFloat(draft)?.let {
                                                appDataViewModel.updateRefuelAmbientTemp(refuel.id, it)
                                                tempEdits.remove(refuel.id)
                                            }
                                        },
                                        onActualCommit = { draft ->
                                            parseLocalizedFloat(draft)?.let {
                                                appDataViewModel.updateRefuelActualLiters(refuel.id, it)
                                                actualEdits.remove(refuel.id)
                                            }
                                        },
                                        onPriceCommit = { draft ->
                                            val price = parseLocalizedFloat(draft)
                                            appDataViewModel.updateRefuelPricePerLiter(refuel.id, price)
                                            priceEdits.remove(refuel.id)
                                        },
                                        onSourceCommit = { draft ->
                                            appDataViewModel.updateRefuelPriceSourceName(refuel.id, draft)
                                            sourceEdits.remove(refuel.id)
                                        },
                                        onFuelTypeSelected = { option ->
                                            appDataViewModel.updateRefuelFuelType(refuel.id, option)
                                        },
                                        onRequestDelete = { pendingDeleteRefuelId = refuel.id },
                                        onRequestTrainCalibration = {
                                            onServiceCommand(
                                                BackgroundService.ACTION_FUEL_CALIBRATION_TRAIN,
                                                BackgroundService.EXTRA_REFUEL_ID,
                                                refuel.id,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                        RefuelVerticalScrollbar(
                            listState = listState,
                            totalItems = sortedRefuels.size,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(12.dp),
                        )
                        RefuelHorizontalScrollbar(
                            scrollState = horizontalScrollState,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(10.dp),
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(end = 14.dp)
                        .verticalScroll(calibrationScrollState),
                ) {
                SettingsTitle(stringResource(R.string.refuels_calibration_section_title))
                CalibrationIntCommitField(
                    title = stringResource(R.string.settings_fuel_tank_liters_title),
                    description = stringResource(R.string.refuels_calibration_tank_hint),
                    draft = tankLitersDraft,
                    onDraftChange = { tankLitersDraft = it },
                    savedValue = fuelTankLiters,
                    minValue = 1,
                    maxValue = 500,
                    onCommit = { value ->
                        appDataViewModel.applyFuelTankChangeWithCalibrationReset(value)
                    },
                )
                CalibrationIntCommitField(
                    title = stringResource(R.string.refuels_calibration_zones_title),
                    description = stringResource(R.string.refuels_calibration_zones_hint),
                    draft = zoneCountDraft,
                    onDraftChange = { zoneCountDraft = it },
                    savedValue = fuelCalibrationZoneCount,
                    minValue = 3,
                    maxValue = 20,
                    onCommit = { value ->
                        appDataViewModel.applyFuelCalibrationZoneCountWithReset(value)
                    },
                )
                CalibrationIntCommitField(
                    title = stringResource(R.string.refuels_calibration_maturity_title),
                    description = stringResource(R.string.refuels_calibration_maturity_hint),
                    draft = maturityDraft,
                    onDraftChange = { maturityDraft = it },
                    savedValue = fuelCalibrationMaturityThreshold,
                    minValue = SettingsManager.FUEL_CALIBRATION_MATURITY_THRESHOLD_MIN,
                    maxValue = SettingsManager.FUEL_CALIBRATION_MATURITY_THRESHOLD_MAX,
                    onCommit = { value ->
                        appDataViewModel.applyFuelCalibrationMaturityThreshold(value)
                    },
                )
                OutlinedButton(
                    onClick = { pendingCalibrationReset = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(stringResource(R.string.refuels_calibration_reset), fontSize = 20.sp)
                }
                Text(
                    text = stringResource(R.string.refuels_calibration_report_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (reportLines == null) {
                    Text(
                        text = stringResource(R.string.value_no_data),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    reportLines.forEach { line ->
                        Text(
                            text = line,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
                }
                ScrollStateVerticalScrollbar(
                    scrollState = calibrationScrollState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(12.dp),
                )
            }
        }
    }

    pendingDeleteRefuelId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteRefuelId = null },
            title = { AppAlertDialogTitle(stringResource(R.string.refuels_delete_confirm_title)) },
            text = { AppAlertDialogText(stringResource(R.string.refuels_delete_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        appDataViewModel.deleteRefuel(id)
                        pendingDeleteRefuelId = null
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingDeleteRefuelId = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (pendingCalibrationReset) {
        AlertDialog(
            onDismissRequest = { pendingCalibrationReset = false },
            title = { AppAlertDialogTitle(stringResource(R.string.refuels_calibration_reset_confirm_title)) },
            text = { AppAlertDialogText(stringResource(R.string.refuels_calibration_reset_confirm_message)) },
            confirmButton = {
                Button(
                    onClick = {
                        appDataViewModel.clearFuelCalibrationOnly()
                        pendingCalibrationReset = false
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.refuels_calibration_reset))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingCalibrationReset = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.dialog_file_saving_title)) },
            text = { AppAlertDialogText(stringResource(R.string.dialog_save_refuels_downloads)) },
            confirmButton = {
                Button(
                    onClick = {
                        onSaveToFile(
                            "refuels",
                            buildRefuelExportLines(context, refuels, dateTimeFormat),
                        )
                        showExportDialog = false
                    }
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showExportDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RefuelHeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RefuelHeaderCell("", 40)
        RefuelHeaderCell(stringResource(R.string.refuels_time), 190)
        RefuelHeaderCell(stringResource(R.string.refuels_odometer), 120)
        RefuelHeaderCell(stringResource(R.string.refuels_coordinates), 210)
        RefuelHeaderCell(stringResource(R.string.refuels_fuel_before), 110)
        RefuelHeaderCell(stringResource(R.string.refuels_fuel_after), 110)
        RefuelHeaderCell(stringResource(R.string.refuels_ambient_temp), 180)
        RefuelHeaderCell(stringResource(R.string.refuels_estimated_liters), 140)
        RefuelHeaderCell(stringResource(R.string.refuels_actual_liters), 180)
        RefuelHeaderCell(stringResource(R.string.refuels_fuel_type), 150)
        RefuelHeaderCell(stringResource(R.string.refuels_price), 180)
        RefuelHeaderCell(stringResource(R.string.refuels_price_source), 330)
        RefuelHeaderCell(stringResource(R.string.refuels_cost), 150)
        RefuelHeaderCell(stringResource(R.string.refuels_calibration_train), 180)
        RefuelHeaderCell("", 80)
    }
}

@Composable
private fun RefuelTableRow(
    refuel: RefuelRecord,
    dateTimeFormat: SimpleDateFormat,
    tempDraft: String,
    actualDraft: String,
    priceDraft: String,
    sourceDraft: String,
    onTempDraftChange: (String) -> Unit,
    onActualDraftChange: (String) -> Unit,
    onPriceDraftChange: (String) -> Unit,
    onSourceDraftChange: (String) -> Unit,
    onTempCommit: (String) -> Unit,
    onActualCommit: (String) -> Unit,
    onPriceCommit: (String) -> Unit,
    onSourceCommit: (String) -> Unit,
    onFuelTypeSelected: (FuelTypeOption) -> Unit,
    onRequestDelete: () -> Unit,
    onRequestTrainCalibration: () -> Unit,
) {
    val noData = stringResource(R.string.value_no_data)
    val coordinatesText = formatCoordinates(refuel, noData)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (refuel.pricePerLiterRub == null) {
                Icon(
                    painter = painterResource(R.drawable.ic_refuel_price_warning),
                    contentDescription = stringResource(R.string.refuels_price_missing_cd),
                    modifier = Modifier.size(24.dp),
                    tint = Color(0xFFFF9800),
                )
            }
        }
        RefuelCell(dateTimeFormat.format(Date(refuel.timeEpochMs)), 190)
        RefuelCell(refuel.odometerKm?.let { valueToString(it, 0) } ?: noData, 120)
        RefuelCoordinatesCell(
            text = coordinatesText,
            widthDp = 210,
            copyText = coordinatesText.takeIf { refuel.latitude != null && refuel.longitude != null }
        )
        RefuelCell(refuel.fuelPercentBefore?.let { valueToString(it, 1) } ?: noData, 110)
        RefuelCell(refuel.fuelPercentAfter?.let { valueToString(it, 1) } ?: noData, 110)
        RefuelEditableCell(
            value = tempDraft,
            widthDp = 180,
            showCommitButton = !refuelAmbientDraftMatchesSaved(tempDraft, refuel.ambientTempAtRefuel),
            onValueChange = onTempDraftChange,
            onCommit = onTempCommit,
        )
        RefuelCell(valueToString(refuel.estimatedLiters, 2), 140)
        RefuelEditableCell(
            value = actualDraft,
            widthDp = 180,
            showCommitButton = !refuelActualDraftMatchesSaved(actualDraft, refuel.actualLiters),
            onValueChange = onActualDraftChange,
            onCommit = onActualCommit,
        )
        RefuelFuelTypeCell(
            selected = FuelTypeOption(refuel.fuelId, refuel.fuelName),
            widthDp = 150,
            onSelect = onFuelTypeSelected,
        )
        RefuelEditableCell(
            value = priceDraft,
            widthDp = 180,
            showCommitButton = !refuelPriceDraftMatchesSaved(priceDraft, refuel.pricePerLiterRub),
            onValueChange = onPriceDraftChange,
            onCommit = onPriceCommit,
        )
        RefuelEditableCell(
            value = sourceDraft,
            widthDp = 330,
            showCommitButton = !refuelSourceDraftMatchesSaved(sourceDraft, refuel.priceSourceName),
            onValueChange = onSourceDraftChange,
            onCommit = onSourceCommit,
            keyboardType = KeyboardType.Text,
        )
        RefuelCell(refuel.costRub?.let { valueToString(it, 2) } ?: noData, 150)
        Box(
            modifier = Modifier
                .width(180.dp)
                .padding(end = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            when {
                refuel.usedForFuelCalibration -> {
                    Text(
                        text = stringResource(R.string.refuels_calibration_trained),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                refuel.fuelPercentBefore != null &&
                    refuel.fuelPercentAfter != null &&
                    refuel.actualLiters > 0f -> {
                    OutlinedButton(onClick = onRequestTrainCalibration) {
                        Text(
                            stringResource(R.string.refuels_calibration_train),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                else -> {
                    Text(text = noData, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(
            modifier = Modifier.width(80.dp),
            contentAlignment = Alignment.Center,
        ) {
            RefuelCircleIconButton(
                onClick = onRequestDelete,
                contentDescription = stringResource(R.string.refuels_delete),
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefuelFuelTypeCell(
    selected: FuelTypeOption,
    widthDp: Int,
    onSelect: (FuelTypeOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp),
    ) {
        Box {
            OutlinedTextField(
                value = selected.label,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                        enabled = true,
                    )
                    .fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 24.sp,
                    lineHeight = 24.sp * 1.3f,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Left,
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            ) {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            FuelTypes.options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = option.label,
                            fontSize = 24.sp,
                            color = if (option.id == selected.id) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}

/** Целое для настроек бака / зон: до первой точки или запятой. */
private fun parseSettingsInt(raw: String): Int? {
    val normalized = raw.trim().replace(',', '.')
    val intPart = normalized.substringBefore('.').trim()
    if (intPart.isEmpty()) return null
    return intPart.toIntOrNull()
}

@Composable
private fun CalibrationIntCommitField(
    title: String,
    description: String,
    draft: String,
    onDraftChange: (String) -> Unit,
    savedValue: Int,
    minValue: Int,
    maxValue: Int,
    onCommit: (Int) -> Unit,
) {
    val parsed = parseSettingsInt(draft)
    val canCommit = parsed != null && parsed in minValue..maxValue && parsed != savedValue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .width(150.dp),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                fontSize = 24.sp,
                lineHeight = 24.sp * 1.3f,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                focusedBorderColor = MaterialTheme.colorScheme.outline,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                cursorColor = MaterialTheme.colorScheme.primary,
            ),
            trailingIcon = if (canCommit) {
                {
                    RefuelCircleIconButton(
                        onClick = {
                            parseSettingsInt(draft)?.let { v ->
                                if (v in minValue..maxValue) onCommit(v)
                            }
                        },
                        contentDescription = stringResource(R.string.action_save),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refuel_save),
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                null
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp)
                .align(Alignment.CenterVertically),
        ) {
            Text(
                text = title,
                fontSize = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 0.dp),
            )
            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

@Composable
private fun RefuelEditableCell(
    value: String,
    widthDp: Int,
    showCommitButton: Boolean,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = 24.sp,
            lineHeight = 24.sp * 1.3f,
            color = MaterialTheme.colorScheme.onSurface,
        ),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = if (showCommitButton) {
            {
                RefuelCircleIconButton(
                    onClick = { onCommit(value) },
                    contentDescription = stringResource(R.string.action_save),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_refuel_save),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        } else {
            null
        },
    )
}

@Composable
private fun RefuelCircleIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(40.dp)
            .background(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                shape = CircleShape,
            ),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun RefuelHeaderCell(text: String, widthDp: Int) {
    Text(
        text = text,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp),
        fontSize = 24.sp,
        lineHeight = 24.sp * 1.3f,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun RefuelCoordinatesCell(text: String, widthDp: Int, copyText: String?) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val copiedMessage = stringResource(R.string.refuels_coordinates_copied)
    RefuelCell(
        text = text,
        widthDp = widthDp,
        modifier = if (copyText != null) {
            Modifier.clickable {
                scope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("coordinates", copyText)))
                }
                Toast.makeText(context, copiedMessage, Toast.LENGTH_SHORT).show()
            }
        } else {
            Modifier
        }
    )
}

@Composable
private fun RefuelCell(text: String, widthDp: Int, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp)
            .then(modifier),
        fontSize = 24.sp,
        lineHeight = 24.sp * 1.3f,
        maxLines = 2,
        textAlign = TextAlign.Center,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun ScrollStateVerticalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant.copy(alpha = 0.88f)
    val thumb = scheme.primary
    Canvas(modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRoundRect(
            color = track,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0) {
            drawRoundRect(
                color = thumb.copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
            return@Canvas
        }
        val total = h + maxScroll
        val thumbH = (h * h / total).coerceAtLeast(32.dp.toPx())
        val maxOff = (h - thumbH).coerceAtLeast(0f)
        val thumbOff = maxOff * scrollState.value / maxScroll
        drawRoundRect(
            color = thumb,
            topLeft = Offset(0f, thumbOff),
            size = Size(w, thumbH),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
    }
}

@Composable
private fun RefuelHorizontalScrollbar(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val track = scheme.surfaceVariant.copy(alpha = 0.88f)
    val thumb = scheme.primary
    Canvas(modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
        val vp = size.width
        val barH = size.height
        if (vp <= 0f || barH <= 0f) return@Canvas
        drawRoundRect(
            color = track,
            topLeft = Offset.Zero,
            size = Size(vp, barH),
            cornerRadius = CornerRadius(barH / 2f, barH / 2f),
        )
        val maxScroll = scrollState.maxValue
        if (maxScroll <= 0) {
            drawRoundRect(
                color = thumb.copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = Size(vp, barH),
                cornerRadius = CornerRadius(barH / 2f, barH / 2f),
            )
            return@Canvas
        }
        val total = vp + maxScroll
        val thumbW = (vp * vp / total).coerceAtLeast(32.dp.toPx())
        val maxOff = (vp - thumbW).coerceAtLeast(0f)
        val thumbOff = maxOff * scrollState.value / maxScroll
        drawRoundRect(
            color = thumb,
            topLeft = Offset(thumbOff, 0f),
            size = Size(thumbW, barH),
            cornerRadius = CornerRadius(barH / 2f, barH / 2f),
        )
    }
}

@Composable
private fun RefuelVerticalScrollbar(
    listState: LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier,
) {
    if (totalItems <= 0) return
    val scheme = MaterialTheme.colorScheme
    val trackColor = scheme.surfaceVariant.copy(alpha = 0.88f)
    val thumbColor = scheme.primary
    Canvas(modifier = modifier.padding(horizontal = 2.dp, vertical = 2.dp)) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        drawRoundRect(
            color = trackColor,
            topLeft = Offset.Zero,
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
        val layoutInfo = listState.layoutInfo
        val items = layoutInfo.visibleItemsInfo
        val viewport = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat().coerceAtLeast(1f)
        val avgItem = if (items.isEmpty()) {
            (viewport / totalItems.coerceAtLeast(1)).coerceAtLeast(24f)
        } else {
            (items.sumOf { it.size }.toFloat() / items.size.coerceAtLeast(1)).coerceAtLeast(1f)
        }
        val content = totalItems * avgItem
        val scrollPx = listState.firstVisibleItemIndex * avgItem + listState.firstVisibleItemScrollOffset
        val maxScroll = (content - viewport).coerceAtLeast(0f)
        if (maxScroll <= 0f) {
            drawRoundRect(
                color = thumbColor.copy(alpha = 0.35f),
                topLeft = Offset.Zero,
                size = Size(w, h),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
            return@Canvas
        }
        val progress = (scrollPx / maxScroll).coerceIn(0f, 1f)
        val thumbH = (h * h / content.coerceAtLeast(h)).coerceIn(32.dp.toPx().coerceAtMost(h), h)
        val thumbOff = (h - thumbH) * progress
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbOff),
            size = Size(w, thumbH),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
    }
}

private fun parseLocalizedFloat(raw: String): Float? =
    raw.trim()
        .replace(',', '.')
        .takeIf { it.isNotBlank() }
        ?.toFloatOrNull()

private fun floatDraftMatchesSaved(draft: String, saved: Float, decimals: Int): Boolean {
    val parsed = parseLocalizedFloat(draft)
    if (parsed != null && abs(parsed - saved) < 1e-4f) return true
    val normalizedDraft = draft.trim().replace(',', '.')
    val normalizedSaved = valueToString(saved, decimals).trim().replace(',', '.')
    return normalizedDraft == normalizedSaved
}

private fun refuelActualDraftMatchesSaved(draft: String, savedLiters: Float): Boolean =
    floatDraftMatchesSaved(draft, savedLiters, decimals = 1)

private fun refuelAmbientDraftMatchesSaved(draft: String, saved: Float?): Boolean {
    val effective = saved ?: REFUEL_AMBIENT_TEMP_DEFAULT_C
    return floatDraftMatchesSaved(draft, effective, decimals = 1)
}

private fun refuelPriceDraftMatchesSaved(draft: String, savedPrice: Float?): Boolean {
    val trimmed = draft.trim()
    if (savedPrice == null) return trimmed.isEmpty()
    return floatDraftMatchesSaved(draft, savedPrice, decimals = 2)
}

private fun refuelSourceDraftMatchesSaved(draft: String, savedSource: String?): Boolean {
    val saved = savedSource?.trim().orEmpty()
    return draft.trim() == saved
}

private fun formatCoordinates(refuel: RefuelRecord, noData: String): String =
    if (refuel.latitude != null && refuel.longitude != null) {
        "${valueToString(refuel.latitude, 6)}; ${valueToString(refuel.longitude, 6)}"
    } else {
        noData
    }

internal fun buildRefuelExportLines(
    context: Context,
    refuels: List<RefuelRecord>,
    dateTimeFormat: SimpleDateFormat,
): List<String> {
    val noData = context.getString(R.string.value_no_data)
    val lines = mutableListOf<String>()
    lines.add(
        listOf(
            context.getString(R.string.refuels_time),
            context.getString(R.string.refuels_odometer),
            context.getString(R.string.refuels_coordinates),
            context.getString(R.string.refuels_fuel_before),
            context.getString(R.string.refuels_fuel_after),
            context.getString(R.string.refuels_ambient_temp),
            context.getString(R.string.refuels_estimated_liters),
            context.getString(R.string.refuels_actual_liters),
            context.getString(R.string.refuels_fuel_type),
            context.getString(R.string.refuels_price),
            context.getString(R.string.refuels_price_source),
            context.getString(R.string.refuels_cost),
        ).joinToString("\t")
    )
    refuels.sortedByDescending { it.timeEpochMs }.forEach { refuel ->
        lines.add(
            listOf(
                dateTimeFormat.format(Date(refuel.timeEpochMs)),
                refuel.odometerKm?.let { valueToString(it, 0) } ?: noData,
                formatCoordinates(refuel, noData),
                refuel.fuelPercentBefore?.let { valueToString(it, 1) } ?: noData,
                refuel.fuelPercentAfter?.let { valueToString(it, 1) } ?: noData,
                valueToString(refuel.ambientTempAtRefuel ?: REFUEL_AMBIENT_TEMP_DEFAULT_C, 1),
                valueToString(refuel.estimatedLiters, 1),
                valueToString(refuel.actualLiters, 1),
                refuel.fuelName,
                refuel.pricePerLiterRub?.let { valueToString(it, 2) } ?: noData,
                refuel.priceSourceName ?: noData,
                refuel.costRub?.let { valueToString(it, 2) } ?: noData,
            ).joinToString("\t")
        )
    }
    return lines
}
