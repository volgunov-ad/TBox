package vad.dashing.tbox.ui

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
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
                        stringResource(R.string.trips_total_time),
                        formatTripDurationHuman(
                            context,
                            trip.movingTimeMs + trip.idleTimeMs
                        )
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
                    StatusRow(
                        stringResource(R.string.trips_fuel_refueled),
                        formatWithUnit(valueToString(trip.fuelRefueledLiters, 1), stringResource(R.string.unit_liter))
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
