package vad.dashing.tbox.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TripRecord
import vad.dashing.tbox.TripRepository
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
@Composable
fun TripsTab(
    appDataViewModel: AppDataViewModel,
    settingsViewModel: SettingsViewModel,
) {
    val trips by appDataViewModel.trips.collectAsStateWithLifecycle()
    val favorites by appDataViewModel.favoriteTripIds.collectAsStateWithLifecycle()
    val sortedIds = remember(trips, favorites) {
        val favSet = favorites
        trips.sortedWith(
            compareByDescending<TripRecord> { it.startTimeEpochMs }
                .thenBy { if (favSet.contains(it.id)) 0 else 1 }
        ).map { it.id }
    }

    var selectedId by remember(trips) {
        mutableStateOf(
            trips.maxByOrNull { it.startTimeEpochMs }?.id ?: ""
        )
    }

    LaunchedEffect(sortedIds) {
        if (selectedId.isNotEmpty() && sortedIds.none { it == selectedId }) {
            selectedId = sortedIds.firstOrNull() ?: ""
        }
        if (selectedId.isEmpty() && sortedIds.isNotEmpty()) {
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

    val focusManager = LocalFocusManager.current
    val dateTimeFormat = remember {
        SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
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
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = sortedIds.isNotEmpty()) { expanded = true },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp)
                )
                DropdownMenu(
                    expanded = expanded && sortedIds.isNotEmpty(),
                    onDismissRequest = { expanded = false }
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
                                    fontSize = 18.sp
                                )
                            },
                            onClick = {
                                selectedId = id
                                expanded = false
                            }
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
                enabled = selectedId.isNotEmpty()
            ) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.trips_delete))
            }
        }

        selectedTrip?.let { trip ->
            OutlinedTextField(
                value = nameEdit,
                onValueChange = { nameEdit = it },
                label = { Text(stringResource(R.string.trips_name_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                singleLine = true,
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
                        if (fav) stringResource(R.string.trips_remove_favorite)
                        else stringResource(R.string.trips_add_favorite)
                    )
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
                        stringResource(R.string.trips_distance),
                        formatWithUnit(valueToString(trip.distanceKm, 2), stringResource(R.string.unit_km))
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_moving_time),
                        formatDurationMs(trip.movingTimeMs)
                    )
                }
                item {
                    StatusRow(
                        stringResource(R.string.trips_idle_time),
                        formatDurationMs(trip.idleTimeMs)
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
                item {
                    StatusRow(
                        stringResource(R.string.trips_max_gearbox_temp),
                        trip.maxGearboxOilTemp?.let {
                            formatWithUnit(valueToString(it), stringResource(R.string.unit_celsius))
                        } ?: stringResource(R.string.value_no_data)
                    )
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
                        formatWithUnit(valueToString(trip.fuelConsumedLiters, 2), stringResource(R.string.unit_liter))
                    )
                }
            }
        } ?: run {
            Text(
                text = stringResource(R.string.trips_empty),
                modifier = Modifier.padding(top = 24.dp),
                fontSize = 20.sp
            )
        }
    }
}

private fun formatWithUnit(value: String, unit: String): String =
    if (value.isBlank()) value else "$value\u2009$unit"

private fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0"
    val totalSec = (ms + 500) / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", m, s)
    }
}
