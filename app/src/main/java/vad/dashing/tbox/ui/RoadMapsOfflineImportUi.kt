package vad.dashing.tbox.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.location.roadmatch.OfflineImportProgress
import vad.dashing.tbox.location.roadmatch.OfflineRegionReadiness
import vad.dashing.tbox.location.roadmatch.OfflineRegionUiState
import vad.dashing.tbox.location.roadmatch.RoadMapDownloadManager
import vad.dashing.tbox.location.roadmatch.RoadMapOfflineCatalog
import vad.dashing.tbox.location.roadmatch.RoadMapOfflineImportManager
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import java.util.Locale

/**
 * Stage G: SAF pick of USB catalog JSON → region list → atomic offline install.
 */
@Composable
fun RoadMapsUsbInstallSection(
    downloadManager: RoadMapDownloadManager,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember(downloadManager) {
        RoadMapOfflineImportManager(context.applicationContext, downloadManager)
    }
    var catalogUri by remember { mutableStateOf<Uri?>(null) }
    var folderUri by remember { mutableStateOf<Uri?>(null) }
    var catalog by remember { mutableStateOf<RoadMapOfflineCatalog?>(null) }
    var regionStates by remember { mutableStateOf<List<OfflineRegionUiState>>(emptyList()) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDialog by remember { mutableStateOf(false) }
    var catalogError by remember { mutableStateOf<String?>(null) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf<OfflineImportProgress?>(null) }
    var doneMessage by remember { mutableStateOf<String?>(null) }

    fun refreshStates(cat: RoadMapOfflineCatalog, catUri: Uri, folder: Uri?) {
        regionStates = importer.buildRegionStates(cat, catUri, folder)
        selected = regionStates
            .filter {
                it.readiness == OfflineRegionReadiness.NOT_INSTALLED ||
                    it.readiness == OfflineRegionReadiness.UPDATE
            }
            .map { it.offline.region.id }
            .toSet()
    }

    val openCatalog = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importer.tryPersistReadPermission(uri)
        scope.launch {
            catalogError = null
            doneMessage = null
            val result = importer.readCatalog(uri)
            result.onSuccess { cat ->
                catalogUri = uri
                folderUri = null
                catalog = cat
                refreshStates(cat, uri, null)
                showDialog = true
            }.onFailure { e ->
                catalogError = e.message ?: "error"
                catalog = null
                regionStates = emptyList()
                showDialog = true
            }
        }
    }

    val openFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importer.tryPersistReadPermission(uri)
        val cat = catalog
        val catUri = catalogUri
        if (cat == null || catUri == null) return@rememberLauncherForActivityResult
        folderUri = uri
        refreshStates(cat, catUri, uri)
    }

    OutlinedButton(
        onClick = rememberWrappedOnClick {
            openCatalog.launch(arrayOf("application/json", "text/json", "text/plain", "*/*"))
        },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = stringResource(R.string.road_maps_action_install_usb),
            style = MaterialTheme.typography.tboxButton,
        )
    }

    if (showDialog) {
        RoadMapsOfflineImportDialog(
            catalog = catalog,
            catalogError = catalogError,
            regionStates = regionStates,
            selected = selected,
            importing = importing,
            progress = progress,
            doneMessage = doneMessage,
            needsFolderFallback = regionStates.any {
                it.readiness == OfflineRegionReadiness.MISSING_FILE
            },
            onToggle = { id, checked ->
                selected = if (checked) selected + id else selected - id
            },
            onSelectAll = {
                selected = regionStates.filter { it.selectable }.map { it.offline.region.id }.toSet()
            },
            onClearAll = { selected = emptySet() },
            onPickFolder = { openFolder.launch(null) },
            onInstall = {
                val cat = catalog
                val catUri = catalogUri
                if (cat == null || catUri == null) return@RoadMapsOfflineImportDialog
                if (selected.isEmpty()) {
                    doneMessage = context.getString(R.string.road_maps_usb_nothing_selected)
                    return@RoadMapsOfflineImportDialog
                }
                importing = true
                doneMessage = null
                progress = null
                scope.launch {
                    val summary = withContext(Dispatchers.IO) {
                        importer.importSelected(
                            catalogUri = catUri,
                            folderUri = folderUri,
                            catalog = cat,
                            regionIds = selected,
                            onProgress = { p -> progress = p },
                        )
                    }
                    importing = false
                    progress = null
                    downloadManager.ensureLoaded()
                    refreshStates(cat, catUri, folderUri)
                    val ok = context.getString(R.string.road_maps_usb_done_ok, summary.succeeded.size)
                    val fail = if (summary.failed.isEmpty()) {
                        null
                    } else {
                        context.getString(
                            R.string.road_maps_usb_done_fail,
                            summary.failed.joinToString { "${it.first}: ${it.second}" },
                        )
                    }
                    doneMessage = listOfNotNull(ok, fail).joinToString("\n")
                }
            },
            onCancelImport = { importer.cancel() },
            onDismiss = {
                if (!importing) {
                    showDialog = false
                    catalogError = null
                    doneMessage = null
                }
            },
        )
    }
}

@Composable
private fun RoadMapsOfflineImportDialog(
    catalog: RoadMapOfflineCatalog?,
    catalogError: String?,
    regionStates: List<OfflineRegionUiState>,
    selected: Set<String>,
    importing: Boolean,
    progress: OfflineImportProgress?,
    doneMessage: String?,
    needsFolderFallback: Boolean,
    onToggle: (String, Boolean) -> Unit,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    onPickFolder: () -> Unit,
    onInstall: () -> Unit,
    onCancelImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val isRu = remember {
        Locale.getDefault().language.equals("ru", ignoreCase = true)
    }
    val title = catalog?.title?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.road_maps_usb_catalog_untitled)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !importing,
            dismissOnClickOutside = !importing,
        ),
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .fillMaxHeight(0.92f),
        title = { AppAlertDialogTitle(stringResource(R.string.road_maps_usb_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (catalogError != null) {
                    Text(
                        text = stringResource(R.string.road_maps_usb_bad_catalog, catalogError),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                    )
                    return@Column
                }
                if (catalog != null) {
                    Text(
                        text = stringResource(
                            R.string.road_maps_usb_catalog_meta,
                            title,
                            catalog.version,
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                if (needsFolderFallback) {
                    Text(
                        text = stringResource(R.string.road_maps_usb_siblings_hint),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick(onPickFolder),
                        enabled = !importing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.road_maps_usb_pick_folder),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = rememberWrappedOnClick(onSelectAll),
                        enabled = !importing,
                    ) {
                        Text(
                            text = stringResource(R.string.road_maps_usb_select_all),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    TextButton(
                        onClick = rememberWrappedOnClick(onClearAll),
                        enabled = !importing,
                    ) {
                        Text(
                            text = stringResource(R.string.road_maps_usb_clear_all),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                for (state in regionStates) {
                    UsbRegionRow(
                        state = state,
                        isRussian = isRu,
                        checked = state.offline.region.id in selected,
                        enabled = state.selectable && !importing,
                        onCheckedChange = { onToggle(state.offline.region.id, it) },
                        formatBytes = { formatUsbBytes(context, it) },
                    )
                }
                if (importing && progress != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.road_maps_usb_importing,
                            progress.currentIndex + 1,
                            progress.totalCount,
                            progress.currentId ?: "—",
                        ),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val overall = if (progress.totalCount > 0) {
                        (
                            progress.currentIndex.toFloat() + progress.regionProgress
                            ) / progress.totalCount.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { overall.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                    )
                }
                if (doneMessage != null) {
                    Text(
                        text = doneMessage,
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            if (importing) {
                TextButton(onClick = rememberWrappedOnClick(onCancelImport)) {
                    AppAlertDialogButtonLabel(stringResource(R.string.road_maps_action_cancel))
                }
            } else {
                TextButton(
                    onClick = rememberWrappedOnClick(onInstall),
                    enabled = catalog != null && catalogError == null,
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.road_maps_usb_install_selected))
                }
            }
        },
        dismissButton = {
            if (!importing) {
                TextButton(onClick = rememberWrappedOnClick(onDismiss)) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                }
            }
        },
    )
}

@Composable
private fun UsbRegionRow(
    state: OfflineRegionUiState,
    isRussian: Boolean,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    formatBytes: (Long) -> String,
) {
    val region = state.offline.region
    val statusText = when (state.readiness) {
        OfflineRegionReadiness.MISSING_FILE -> stringResource(
            R.string.road_maps_usb_status_missing,
            state.detail ?: state.offline.relativeFile,
        )
        OfflineRegionReadiness.UNVERIFIED -> stringResource(R.string.road_maps_usb_status_unverified)
        OfflineRegionReadiness.NOT_INSTALLED -> stringResource(
            R.string.road_maps_usb_status_not_installed,
            formatBytes(region.bytes),
        )
        OfflineRegionReadiness.UPDATE -> {
            val parts = state.detail?.split('|')
            val from = parts?.getOrNull(0)?.toIntOrNull() ?: 0
            val to = parts?.getOrNull(1)?.toIntOrNull() ?: region.graphVersion
            stringResource(
                R.string.road_maps_usb_status_update,
                from,
                to,
                formatBytes(region.bytes),
            )
        }
        OfflineRegionReadiness.ALREADY_INSTALLED -> stringResource(
            R.string.road_maps_usb_status_already,
            state.detail?.toIntOrNull() ?: region.graphVersion,
        )
        OfflineRegionReadiness.ERROR -> stringResource(
            R.string.road_maps_usb_status_error,
            state.detail ?: "—",
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = region.title(isRussian),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatUsbBytes(context: Context, bytes: Long): String {
    val unitB = context.getString(R.string.unit_byte)
    val unitKb = context.getString(R.string.unit_kilobyte)
    val unitMb = context.getString(R.string.unit_megabyte)
    return when {
        bytes < 1000L -> "$bytes $unitB"
        bytes < 1000L * 1000L -> String.format(Locale.US, "%.1f %s", bytes / 1000.0, unitKb)
        else -> String.format(Locale.US, "%.1f %s", bytes / (1000.0 * 1000.0), unitMb)
    }
}
