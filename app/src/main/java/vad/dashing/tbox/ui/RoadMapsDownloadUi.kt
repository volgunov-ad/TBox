package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxRepository
import vad.dashing.tbox.location.roadmatch.RoadMapCatalog
import vad.dashing.tbox.location.roadmatch.RoadMapRegionStatus
import vad.dashing.tbox.location.roadmatch.RoadMapRegionUiState
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxTitle
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RoadMapsEntryButton(
    settingsViewModel: SettingsViewModel,
    enabled: Boolean = true,
) {
    var showHub by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = rememberWrappedOnClick { showHub = true },
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.road_maps_open_button),
            style = MaterialTheme.typography.tboxButton,
        )
    }
    if (showHub) {
        RoadMapsDownloadHubDialog(
            settingsViewModel = settingsViewModel,
            onDismiss = { showHub = false },
        )
    }
}

@Composable
fun RoadMapsDownloadHubDialog(
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val manager = remember(settingsViewModel) {
        settingsViewModel.roadMapDownloadManager(context)
    }
    LaunchedEffect(manager) {
        manager.ensureLoaded()
    }
    val snap by manager.snapshot.collectAsStateWithLifecycle()
    val loc by TboxRepository.locValues.collectAsStateWithLifecycle()
    val isRu = remember {
        Locale.getDefault().language.equals("ru", ignoreCase = true)
    }
    val covering = remember(snap, loc.latitude, loc.longitude) {
        manager.coveringInstalled(loc.latitude, loc.longitude)
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val pendingDeleteTitle = remember(pendingDeleteId, snap, isRu) {
        pendingDeleteId?.let { id ->
            snap.regions.firstOrNull { it.region.id == id }?.region?.title(isRu)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f),
        title = { AppAlertDialogTitle(stringResource(R.string.road_maps_hub_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.road_maps_hub_intro),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(
                        R.string.road_maps_disk_usage,
                        formatBytes(snap.totalBytesOnDisk),
                    ),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                Text(
                    text = if (covering.isEmpty()) {
                        stringResource(R.string.road_maps_coverage_none)
                    } else {
                        stringResource(
                            R.string.road_maps_coverage_ok,
                            covering.joinToString { it.title(isRu) },
                        )
                    },
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                val byCountry = snap.regions.groupBy { it.region.country }
                for (country in RoadMapCatalog.COUNTRY_ORDER) {
                    val list = byCountry[country].orEmpty()
                    if (list.isEmpty()) continue
                    Text(
                        text = countryTitle(country),
                        style = MaterialTheme.typography.tboxTitle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                    )
                    for (row in list) {
                        RoadMapRegionRow(
                            state = row,
                            isRussian = isRu,
                            onDownload = { manager.enqueueDownload(row.region.id) },
                            onDelete = { pendingDeleteId = row.region.id },
                            onCancel = { manager.cancelQueued(row.region.id) },
                        )
                    }
                }

                Text(
                    text = stringResource(R.string.road_maps_odbl_note),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = rememberWrappedOnClick(onDismiss)) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_close))
            }
        },
    )

    val deleteId = pendingDeleteId
    if (deleteId != null && pendingDeleteTitle != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { AppAlertDialogTitle(stringResource(R.string.road_maps_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.road_maps_delete_confirm_body, pendingDeleteTitle),
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = rememberWrappedOnClick {
                        manager.deleteInstalled(deleteId)
                        pendingDeleteId = null
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.road_maps_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = rememberWrappedOnClick { pendingDeleteId = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun countryTitle(code: String): String {
    val res = when (code) {
        "RU" -> R.string.road_maps_country_ru
        "BY" -> R.string.road_maps_country_by
        "KZ" -> R.string.road_maps_country_kz
        "AM" -> R.string.road_maps_country_am
        "AZ" -> R.string.road_maps_country_az
        "UZ" -> R.string.road_maps_country_uz
        else -> null
    }
    return if (res != null) stringResource(res) else code
}

@Composable
private fun RoadMapRegionRow(
    state: RoadMapRegionUiState,
    isRussian: Boolean,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    val region = state.region
    val installed = state.installed
    val statusText = when (state.status) {
        RoadMapRegionStatus.NOT_INSTALLED -> {
            if (region.bytes > 0L) {
                stringResource(
                    R.string.road_maps_status_not_installed_size,
                    formatBytes(region.bytes),
                )
            } else {
                stringResource(R.string.road_maps_status_not_installed)
            }
        }
        RoadMapRegionStatus.QUEUED -> stringResource(R.string.road_maps_status_queued)
        RoadMapRegionStatus.DOWNLOADING -> stringResource(
            R.string.road_maps_status_downloading,
            (state.progress * 100).toInt(),
        )
        RoadMapRegionStatus.INSTALLED -> {
            if (state.updateAvailable) {
                stringResource(R.string.road_maps_status_update_available)
            } else {
                stringResource(R.string.road_maps_status_installed_ok)
            }
        }
        RoadMapRegionStatus.ERROR -> stringResource(
            R.string.road_maps_status_error,
            state.errorMessage ?: "—",
        )
        RoadMapRegionStatus.UNAVAILABLE -> stringResource(R.string.road_maps_status_unavailable)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = region.title(isRussian),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (installed != null) {
            Text(
                text = stringResource(
                    R.string.road_maps_installed_details,
                    formatBytes(installed.bytesOnDisk.takeIf { it > 0 } ?: region.bytes),
                    installed.graphVersion,
                    formatInstalledDate(installed.installedAtEpochMs),
                ),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (state.status == RoadMapRegionStatus.INSTALLED && state.errorMessage != null) {
            Text(
                text = stringResource(R.string.road_maps_status_error, state.errorMessage),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (state.status == RoadMapRegionStatus.DOWNLOADING) {
            LinearProgressIndicator(
                progress = { state.progress.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state.status) {
                RoadMapRegionStatus.NOT_INSTALLED,
                RoadMapRegionStatus.ERROR,
                -> {
                    if (region.hasDownloadUrl) {
                        OutlinedButton(onClick = rememberWrappedOnClick(onDownload)) {
                            Text(
                                text = stringResource(R.string.road_maps_action_download),
                                style = MaterialTheme.typography.tboxButton,
                            )
                        }
                    }
                }
                RoadMapRegionStatus.QUEUED,
                RoadMapRegionStatus.DOWNLOADING,
                -> {
                    TextButton(onClick = rememberWrappedOnClick(onCancel)) {
                        Text(
                            text = stringResource(R.string.road_maps_action_cancel),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                RoadMapRegionStatus.INSTALLED -> {
                    if (region.hasDownloadUrl) {
                        OutlinedButton(onClick = rememberWrappedOnClick(onDownload)) {
                            Text(
                                text = stringResource(R.string.road_maps_action_update),
                                style = MaterialTheme.typography.tboxButton,
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    TextButton(onClick = rememberWrappedOnClick(onDelete)) {
                        Text(
                            text = stringResource(R.string.road_maps_action_delete),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                RoadMapRegionStatus.UNAVAILABLE -> Unit
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun formatInstalledDate(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(epochMs))
}
