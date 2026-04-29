package vad.dashing.tbox.ui

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.formatTripDurationHuman
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TripRecord
import vad.dashing.tbox.TripRepository
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripsTab(
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
    onTripFinishAndStart: () -> Unit,
    onSaveToFile: (String, List<String>) -> Unit,
) {
    val trips by appDataViewModel.trips.collectAsStateWithLifecycle()
    val favorites by appDataViewModel.favoriteTripIds.collectAsStateWithLifecycle()
    val activeTrip by appDataViewModel.activeTrip.collectAsStateWithLifecycle()
    val sortedIds = remember(trips, favorites) {
        val favSet = favorites
        trips.sortedWith(
            compareByDescending<TripRecord> { it.startTimeEpochMs }
                .thenBy { if (favSet.contains(it.id)) 0 else 1 }
        ).map { it.id }
    }

    var selectedId by remember { mutableStateOf("") }

    LaunchedEffect(trips, sortedIds) {
        if (sortedIds.isEmpty()) {
            selectedId = ""
            return@LaunchedEffect
        }
        if (selectedId.isEmpty() || selectedId !in sortedIds) {
            selectedId = sortedIds.first()
        }
    }

    val selectedTrip = remember(trips, selectedId) {
        trips.firstOrNull { it.id == selectedId }
    }

    var expanded by remember { mutableStateOf(false) }
    var nameEdit by remember(selectedId) {
        mutableStateOf(selectedTrip?.name ?: "")
    }
    LaunchedEffect(selectedTrip?.name) {
        nameEdit = selectedTrip?.name ?: ""
    }

    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val focusManager = LocalFocusManager.current
    val dateTimeFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }

    val tabTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 24.sp,
        lineHeight = 24.sp * 1.3f,
        color = MaterialTheme.colorScheme.onSurface
    )

    var showExportDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ExposedDropdownMenuBox(
                expanded = expanded && sortedIds.isNotEmpty(),
                onExpandedChange = { open ->
                    if (sortedIds.isNotEmpty()) expanded = open
                },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    value = selectedTrip?.let { trip ->
                        val star = if (favorites.contains(trip.id)) " ★" else ""
                        val title = trip.name.trim().ifEmpty { "" }
                        val suffix = if (title.isNotEmpty()) " — $title" else ""
                        "${dateTimeFormat.format(Date(trip.startTimeEpochMs))}$suffix$star"
                    } ?: "",
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    modifier = Modifier
                        .menuAnchor(
                            type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                            enabled = sortedIds.isNotEmpty(),
                        )
                        .fillMaxWidth(),
                    singleLine = true,
                    textStyle = tabTextStyle,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.outline,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        cursorColor = MaterialTheme.colorScheme.primary,
                    )
                )
                ExposedDropdownMenu(
                    expanded = expanded && sortedIds.isNotEmpty(),
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.requiredWidthIn(
                        min = (configuration.screenWidthDp.dp - 36.dp).coerceAtLeast(280.dp)
                    )
                ) {
                    sortedIds.forEach { id ->
                        val trip = trips.firstOrNull { it.id == id } ?: return@forEach
                        val star = if (favorites.contains(id)) " ★" else ""
                        val title = trip.name.trim().ifEmpty { "" }
                        val suffix = if (title.isNotEmpty()) " — $title" else ""
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "${dateTimeFormat.format(Date(trip.startTimeEpochMs))}$suffix$star",
                                    style = tabTextStyle,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            onClick = {
                                selectedId = id
                                expanded = false
                            },
                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                        )
                    }
                }
            }
            Button(
                onClick = {
                    if (selectedId.isNotEmpty()) {
                        appDataViewModel.deleteTrip(selectedId)
                    }
                },
                enabled = selectedTrip != null && !selectedTrip.isActive
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.trips_delete))
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            color = MaterialTheme.colorScheme.outlineVariant
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = { if (trips.isNotEmpty()) showExportDialog = true },
                enabled = trips.isNotEmpty()
            ) {
                Text(stringResource(R.string.trips_export), fontSize = 24.sp)
            }
        }

        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text(stringResource(R.string.dialog_file_saving_title)) },
                text = { Text(stringResource(R.string.dialog_save_trips_downloads)) },
                confirmButton = {
                    Button(
                        onClick = {
                            val lines = buildTripExportLines(
                                context = context,
                                trips = trips,
                                favorites = favorites,
                                dateTimeFormat = dateTimeFormat,
                            )
                            onSaveToFile("trips", lines)
                            showExportDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { showExportDialog = false }) {
                        Text(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        selectedTrip?.let { trip ->
            OutlinedTextField(
                value = nameEdit,
                onValueChange = { nameEdit = it },
                label = {
                    Text(
                        stringResource(R.string.trips_name_label),
                        style = tabTextStyle.copy(fontSize = 20.sp)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
                textStyle = tabTextStyle,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.outline,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    appDataViewModel.updateTripName(trip.id, nameEdit.trim())
                    focusManager.clearFocus()
                })
            )

            val fav = favorites.contains(trip.id)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        appDataViewModel.setTripFavorite(trip.id, !fav)
                    },
                    enabled = fav || favorites.size < TripRepository.MAX_FAVORITES
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = if (fav) stringResource(R.string.trips_remove_favorite)
                        else stringResource(R.string.trips_add_favorite),
                        fontSize = 24.sp
                    )
                }
                if (trip.isActive && activeTrip?.id == trip.id) {
                    Button(onClick = onTripFinishAndStart) {
                        Text(
                            stringResource(R.string.trips_finish),
                            fontSize = 24.sp
                        )
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 12.dp)
            ) {
                if (trip.isActive) {
                    item {
                        StatusRow(
                            stringResource(R.string.trips_active_trip),
                            stringResource(R.string.value_yes)
                        )
                    }
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_start_time),
                        dateTimeFormat.format(Date(trip.startTimeEpochMs))
                    )
                }
                trip.endTimeEpochMs?.let { end ->
                    item {
                        StatusRow(
                            stringResource(R.string.trips_end_time),
                            dateTimeFormat.format(Date(end))
                        )
                    }
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_odometer_start),
                        trip.odometerStartKm?.let { odo ->
                            formatWithUnit(valueToString(odo, 0), stringResource(R.string.unit_km))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_distance),
                        formatWithUnit(valueToString(trip.distanceKm, 0), stringResource(R.string.unit_km))
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_moving_time),
                        formatTripDurationHuman(context, trip.movingTimeMs)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_idle_time),
                        formatTripDurationHuman(context, trip.idleTimeMs)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_parking_time),
                        formatTripDurationHuman(context, trip.parkingTimeMs)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_total_time),
                        formatTripDurationHuman(
                            context,
                            trip.movingTimeMs + trip.idleTimeMs + trip.parkingTimeMs
                        )
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_engine_start_count),
                        valueToString(trip.engineStartCount)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_max_speed),
                        formatWithUnit(valueToString(trip.maxSpeed, 1), stringResource(R.string.unit_kmh))
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_max_engine_temp),
                        trip.maxEngineTemp?.let {
                            formatWithUnit(valueToString(it, 1), stringResource(R.string.unit_celsius))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                trip.maxGearboxOilTemp?.let { gb ->
                    item {
                        StatusRow(
                            stringResource(R.string.trips_max_gearbox_temp),
                            formatWithUnit(valueToString(gb), stringResource(R.string.unit_celsius))
                        )
                    }
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_min_outside_temp),
                        trip.minOutsideTemp?.let {
                            formatWithUnit(valueToString(it, 1), stringResource(R.string.unit_celsius))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_max_outside_temp),
                        trip.maxOutsideTemp?.let {
                            formatWithUnit(valueToString(it, 1), stringResource(R.string.unit_celsius))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                item {
                    val avgM = TripRepository.averageSpeedMovingKmH(trip)
                    StatusRow(
                        stringResource(R.string.trips_avg_speed_moving),
                        avgM?.let {
                            formatWithUnit(valueToString(it, 1), stringResource(R.string.unit_kmh))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                item {
                    val avgT = TripRepository.averageSpeedTripKmH(trip)
                    StatusRow(
                        stringResource(R.string.trips_avg_speed_trip),
                        avgT?.let {
                            formatWithUnit(valueToString(it, 1), stringResource(R.string.unit_kmh))
                        } ?: stringResource(R.string.value_no_data)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_fuel_used),
                        formatWithUnit(valueToString(trip.fuelConsumedLiters, 1), stringResource(R.string.unit_liter))
                    )
                }
                item {
                    val avgFuel = TripRepository.averageFuelConsumptionLitersPer100Km(trip)
                    StatusRow(
                        stringResource(R.string.trips_fuel_consumption_l_100km),
                        formatWithUnit(avgFuel?.let { valueToString(it, 1) } ?: stringResource(R.string.value_no_data), if (avgFuel != null) stringResource(R.string.unit_l_100km) else "")
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_fuel_refueled),
                        formatWithUnit(valueToString(trip.fuelRefueledLiters, 1), stringResource(R.string.unit_liter))
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_fuel_refueled_cost),
                        formatWithUnit(valueToString(trip.fuelRefueledCostRub, 2), stringResource(R.string.unit_ruble))
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_refuel_count),
                        valueToString(trip.refuelCount)
                    )
                }
            }
        } ?: run {
            Text(
                text = stringResource(R.string.trips_empty),
                modifier = Modifier.padding(top = 24.dp),
                style = tabTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun formatWithUnit(value: String, unit: String): String =
    if (value.isBlank()) value else "$value\u2009$unit"

private fun MutableList<String>.appendStatusLine(label: String, value: String) {
    add("$label\t$value")
}

/**
 * Lines for export: same field order and formatted values as [TripsTab] list for one trip.
 * Trips are ordered like the dropdown (newest first, favorites before non-favorites at same start time).
 */
internal fun buildTripExportLines(
    context: Context,
    trips: List<TripRecord>,
    favorites: Set<String>,
    dateTimeFormat: SimpleDateFormat,
): List<String> {
    val favSet = favorites
    val sorted = trips.sortedWith(
        compareByDescending<TripRecord> { it.startTimeEpochMs }
            .thenBy { if (favSet.contains(it.id)) 0 else 1 }
    )
    val sep = context.getString(R.string.trips_export_separator)
    val yes = context.getString(R.string.value_yes)
    val noData = context.getString(R.string.value_no_data)
    val km = context.getString(R.string.unit_km)
    val kmh = context.getString(R.string.unit_kmh)
    val celsius = context.getString(R.string.unit_celsius)
    val liter = context.getString(R.string.unit_liter)

    return buildList {
        sorted.forEachIndexed { index, trip ->
            if (index > 0) add(sep)

            val star = if (favSet.contains(trip.id)) " ★" else ""
            val title = trip.name.trim()
            val titleSuffix = if (title.isNotEmpty()) " — $title" else ""
            add("${dateTimeFormat.format(Date(trip.startTimeEpochMs))}$titleSuffix$star")

            if (trip.isActive) {
                appendStatusLine(context.getString(R.string.trips_active_trip), yes)
            }
            appendStatusLine(
                context.getString(R.string.trips_start_time),
                dateTimeFormat.format(Date(trip.startTimeEpochMs))
            )
            trip.endTimeEpochMs?.let { end ->
                appendStatusLine(
                    context.getString(R.string.trips_end_time),
                    dateTimeFormat.format(Date(end))
                )
            }
            appendStatusLine(
                context.getString(R.string.trips_odometer_start),
                trip.odometerStartKm?.let { odo ->
                    formatWithUnit(valueToString(odo, 0), km)
                } ?: noData
            )
            appendStatusLine(
                context.getString(R.string.trips_distance),
                formatWithUnit(valueToString(trip.distanceKm, 0), km)
            )
            appendStatusLine(
                context.getString(R.string.trips_moving_time),
                formatTripDurationHuman(context, trip.movingTimeMs)
            )
            appendStatusLine(
                context.getString(R.string.trips_idle_time),
                formatTripDurationHuman(context, trip.idleTimeMs)
            )
            appendStatusLine(
                context.getString(R.string.trips_parking_time),
                formatTripDurationHuman(context, trip.parkingTimeMs)
            )
            appendStatusLine(
                context.getString(R.string.trips_total_time),
                formatTripDurationHuman(
                    context,
                    trip.movingTimeMs + trip.idleTimeMs + trip.parkingTimeMs
                )
            )
            appendStatusLine(
                context.getString(R.string.trips_engine_start_count),
                valueToString(trip.engineStartCount)
            )
            appendStatusLine(
                context.getString(R.string.trips_max_speed),
                formatWithUnit(valueToString(trip.maxSpeed, 1), kmh)
            )
            appendStatusLine(
                context.getString(R.string.trips_max_engine_temp),
                trip.maxEngineTemp?.let {
                    formatWithUnit(valueToString(it, 1), celsius)
                } ?: noData
            )
            trip.maxGearboxOilTemp?.let { gb ->
                appendStatusLine(
                    context.getString(R.string.trips_max_gearbox_temp),
                    formatWithUnit(valueToString(gb), celsius)
                )
            }
            appendStatusLine(
                context.getString(R.string.trips_min_outside_temp),
                trip.minOutsideTemp?.let {
                    formatWithUnit(valueToString(it, 1), celsius)
                } ?: noData
            )
            appendStatusLine(
                context.getString(R.string.trips_max_outside_temp),
                trip.maxOutsideTemp?.let {
                    formatWithUnit(valueToString(it, 1), celsius)
                } ?: noData
            )
            val avgM = TripRepository.averageSpeedMovingKmH(trip)
            appendStatusLine(
                context.getString(R.string.trips_avg_speed_moving),
                avgM?.let { formatWithUnit(valueToString(it, 1), kmh) } ?: noData
            )
            val avgT = TripRepository.averageSpeedTripKmH(trip)
            appendStatusLine(
                context.getString(R.string.trips_avg_speed_trip),
                avgT?.let { formatWithUnit(valueToString(it, 1), kmh) } ?: noData
            )
            appendStatusLine(
                context.getString(R.string.trips_fuel_used),
                formatWithUnit(valueToString(trip.fuelConsumedLiters, 1), liter)
            )
            appendStatusLine(
                context.getString(R.string.trips_fuel_refueled),
                formatWithUnit(valueToString(trip.fuelRefueledLiters, 1), liter)
            )
            appendStatusLine(
                context.getString(R.string.trips_fuel_refueled_cost),
                formatWithUnit(valueToString(trip.fuelRefueledCostRub, 2), context.getString(R.string.unit_ruble))
            )
            appendStatusLine(
                context.getString(R.string.trips_refuel_count),
                valueToString(trip.refuelCount)
            )
        }
    }
}
