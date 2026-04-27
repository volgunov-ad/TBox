package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.RefuelRecord
import vad.dashing.tbox.valueToString

@Composable
fun RefuelsTab(
    appDataViewModel: AppDataViewModel,
    onSaveToFile: (String, List<String>) -> Unit,
) {
    val refuels by appDataViewModel.refuels.collectAsStateWithLifecycle()
    val sortedRefuels = remember(refuels) { refuels.sortedByDescending { it.timeEpochMs } }
    val context = LocalContext.current
    val dateTimeFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }
    var showExportDialog by remember { mutableStateOf(false) }
    val actualEdits = remember { mutableStateMapOf<String, String>() }
    val priceEdits = remember { mutableStateMapOf<String, String>() }

    LaunchedEffect(refuels) {
        val ids = refuels.map { it.id }.toSet()
        actualEdits.keys.retainAll(ids)
        priceEdits.keys.retainAll(ids)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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

        if (refuels.isEmpty()) {
            Text(
                text = stringResource(R.string.refuels_empty),
                modifier = Modifier.padding(top = 24.dp),
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 16.dp)
                    .horizontalScroll(scrollState)
            ) {
                RefuelHeaderRow()
                LazyColumn {
                    items(sortedRefuels.size, key = { sortedRefuels[it].id }) { index ->
                        val refuel = sortedRefuels[index]
                        RefuelTableRow(
                            refuel = refuel,
                            dateTimeFormat = dateTimeFormat,
                            actualDraft = actualEdits[refuel.id] ?: valueToString(refuel.actualLiters, 1),
                            priceDraft = priceEdits[refuel.id] ?: (refuel.pricePerLiterRub?.let { valueToString(it, 2) } ?: ""),
                            onActualDraftChange = { actualEdits[refuel.id] = it },
                            onPriceDraftChange = { priceEdits[refuel.id] = it },
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
                            onDelete = { appDataViewModel.deleteRefuel(refuel.id) },
                        )
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text(stringResource(R.string.dialog_file_saving_title)) },
            text = { Text(stringResource(R.string.dialog_save_refuels_downloads)) },
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
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                Button(onClick = { showExportDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun RefuelHeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RefuelCell(stringResource(R.string.refuels_time), 190)
        RefuelCell(stringResource(R.string.refuels_odometer), 120)
        RefuelCell(stringResource(R.string.refuels_coordinates), 210)
        RefuelCell(stringResource(R.string.refuels_fuel_before), 110)
        RefuelCell(stringResource(R.string.refuels_fuel_after), 110)
        RefuelCell(stringResource(R.string.refuels_estimated_liters), 140)
        RefuelCell(stringResource(R.string.refuels_actual_liters), 200)
        RefuelCell(stringResource(R.string.refuels_fuel_type), 110)
        RefuelCell(stringResource(R.string.refuels_price), 190)
        RefuelCell(stringResource(R.string.refuels_price_source), 220)
        RefuelCell(stringResource(R.string.refuels_cost), 140)
        RefuelCell("", 72)
    }
}

@Composable
private fun RefuelTableRow(
    refuel: RefuelRecord,
    dateTimeFormat: SimpleDateFormat,
    actualDraft: String,
    priceDraft: String,
    onActualDraftChange: (String) -> Unit,
    onPriceDraftChange: (String) -> Unit,
    onActualCommit: (String) -> Unit,
    onPriceCommit: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val noData = stringResource(R.string.value_no_data)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        RefuelCell(dateTimeFormat.format(Date(refuel.timeEpochMs)), 190)
        RefuelCell(refuel.odometerKm?.let { valueToString(it, 0) } ?: noData, 120)
        RefuelCell(formatCoordinates(refuel, noData), 210)
        RefuelCell(refuel.fuelPercentBefore?.let { valueToString(it, 1) } ?: noData, 110)
        RefuelCell(refuel.fuelPercentAfter?.let { valueToString(it, 1) } ?: noData, 110)
        RefuelCell(valueToString(refuel.estimatedLiters, 1), 140)
        RefuelEditableCell(
            value = actualDraft,
            widthDp = 200,
            onValueChange = onActualDraftChange,
            onCommit = onActualCommit,
        )
        RefuelCell(refuel.fuelName, 110)
        RefuelEditableCell(
            value = priceDraft,
            widthDp = 190,
            onValueChange = onPriceDraftChange,
            onCommit = onPriceCommit,
        )
        RefuelCell(refuel.priceSourceName ?: noData, 220)
        RefuelCell(refuel.costRub?.let { valueToString(it, 2) } ?: noData, 140)
        IconButton(
            onClick = onDelete,
            modifier = Modifier.width(72.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = stringResource(R.string.refuels_delete),
            )
        }
    }
}

@Composable
private fun RefuelEditableCell(
    value: String,
    widthDp: Int,
    onValueChange: (String) -> Unit,
    onCommit: (String) -> Unit,
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        trailingIcon = {
            IconButton(
                onClick = { onCommit(value) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.action_save),
                )
            }
        },
    )
}

@Composable
private fun RefuelCell(text: String, widthDp: Int) {
    Text(
        text = text,
        modifier = Modifier
            .width(widthDp.dp)
            .padding(end = 8.dp),
        fontSize = 24.sp,
        lineHeight = 24.sp * 1.3f,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

private fun parseLocalizedFloat(raw: String): Float? =
    raw.trim()
        .replace(',', '.')
        .takeIf { it.isNotBlank() }
        ?.toFloatOrNull()

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
