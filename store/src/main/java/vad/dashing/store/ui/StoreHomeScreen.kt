package vad.dashing.store.ui

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.store.HuKind
import vad.dashing.store.R
import vad.dashing.store.StoreActionState
import vad.dashing.store.StoreAppRow
import vad.dashing.store.StoreListState
import vad.dashing.store.catalog.StoreAppOrigin
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.install.InstallPermissionHelper
import vad.dashing.store.install.formatApkSizeMegabytes
import vad.dashing.store.ui.theme.tboxBody
import vad.dashing.store.ui.theme.tboxButton
import vad.dashing.store.ui.theme.tboxHeadline
import vad.dashing.store.ui.theme.tboxTitle

@Composable
fun StoreHomeScreen(
    viewModel: StoreViewModel,
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val cloudSource by viewModel.cloudSource.collectAsStateWithLifecycle()
    val selectedAppId by viewModel.selectedAppId.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(viewModel.canInstallPackages()) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                canInstall = viewModel.canInstallPackages()
                viewModel.onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val selectedRow = (listState as? StoreListState.Ready)
        ?.rows
        ?.firstOrNull { it.entry.id == selectedAppId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.store_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelButton(
                label = stringResource(R.string.store_channel_release),
                selected = channel == UpdateChannel.RELEASE,
                onClick = { viewModel.setChannel(UpdateChannel.RELEASE) },
            )
            ChannelButton(
                label = stringResource(R.string.store_channel_development),
                selected = channel == UpdateChannel.DEVELOPMENT,
                onClick = { viewModel.setChannel(UpdateChannel.DEVELOPMENT) },
            )
            OutlinedButton(onClick = { viewModel.refresh() }) {
                Text(
                    stringResource(R.string.store_refresh),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ChannelButton(
                label = stringResource(R.string.store_cloud_auto),
                selected = cloudSource == CloudSource.AUTO,
                onClick = { viewModel.setCloudSource(CloudSource.AUTO) },
            )
            ChannelButton(
                label = stringResource(R.string.store_cloud_yandex),
                selected = cloudSource == CloudSource.YANDEX,
                onClick = { viewModel.setCloudSource(CloudSource.YANDEX) },
            )
            ChannelButton(
                label = stringResource(R.string.store_cloud_mailru),
                selected = cloudSource == CloudSource.MAILRU,
                onClick = { viewModel.setCloudSource(CloudSource.MAILRU) },
            )
        }

        Text(
            text = stringResource(
                R.string.store_channel_label,
                when (channel) {
                    UpdateChannel.RELEASE -> stringResource(R.string.store_channel_release)
                    UpdateChannel.DEVELOPMENT -> stringResource(R.string.store_channel_development)
                },
            ),
            style = MaterialTheme.typography.tboxBody,
        )
        Text(
            text = stringResource(
                R.string.store_cloud_label,
                when (cloudSource) {
                    CloudSource.AUTO -> stringResource(R.string.store_cloud_auto)
                    CloudSource.YANDEX -> stringResource(R.string.store_cloud_yandex)
                    CloudSource.MAILRU -> stringResource(R.string.store_cloud_mailru)
                },
            ),
            style = MaterialTheme.typography.tboxBody,
        )
        Text(
            text = stringResource(
                R.string.store_hu_label,
                when (viewModel.hu) {
                    HuKind.A9 -> stringResource(R.string.store_hu_a9)
                    HuKind.A10 -> stringResource(R.string.store_hu_a10)
                    HuKind.UNKNOWN -> stringResource(R.string.store_hu_unknown)
                },
            ),
            style = MaterialTheme.typography.tboxBody,
        )

        if (!canInstall) {
            Text(
                text = stringResource(R.string.store_install_hint),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = {
                    context.startActivity(
                        InstallPermissionHelper.createUnknownSourcesSettingsIntent(context),
                    )
                },
            ) {
                Text(
                    stringResource(R.string.store_grant_install),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }

        if (selectedRow != null) {
            AppDetail(
                row = selectedRow,
                icon = icons[selectedRow.entry.id],
                actionState = actionState,
                canInstall = canInstall,
                onBack = { viewModel.selectApp(null) },
                onDownload = { viewModel.download(selectedRow.entry.id) },
                onCancel = { viewModel.cancelDownload() },
                onInstall = { viewModel.install() },
                onOpen = { viewModel.openApp(selectedRow.entry.packageName) },
            )
        } else {
            CatalogList(
                listState = listState,
                icons = icons,
                onSelect = { viewModel.selectApp(it) },
            )
        }
    }
}

@Composable
private fun ChannelButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(onClick = onClick) {
            Text(label, style = MaterialTheme.typography.tboxButton)
        }
    } else {
        OutlinedButton(onClick = onClick) {
            Text(label, style = MaterialTheme.typography.tboxButton)
        }
    }
}

@Composable
private fun CatalogList(
    listState: StoreListState,
    icons: Map<String, Bitmap>,
    onSelect: (String) -> Unit,
) {
    when (listState) {
        StoreListState.Idle,
        StoreListState.Loading,
        -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    stringResource(R.string.store_loading),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
        }
        is StoreListState.Error -> {
            Text(
                text = if (listState.message == "network_unavailable") {
                    stringResource(R.string.store_error_network)
                } else {
                    stringResource(R.string.store_error, listState.message)
                },
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }
        is StoreListState.Ready -> {
            if (listState.rows.isEmpty()) {
                Text(
                    stringResource(R.string.store_empty),
                    style = MaterialTheme.typography.tboxBody,
                )
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(listState.rows, key = { it.entry.id }) { row ->
                        AppListItem(
                            row = row,
                            icon = icons[row.entry.id],
                            onClick = { onSelect(row.entry.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun AppListItem(
    row: StoreAppRow,
    icon: Bitmap?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StoreAppIcon(
            bitmap = icon,
            title = row.entry.title,
            contentDescription = row.entry.title,
            size = 56.dp,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = row.entry.title,
                    style = MaterialTheme.typography.tboxTitle,
                )
                if (row.entry.origin == StoreAppOrigin.THIRD_PARTY) {
                    Text(
                        text = stringResource(R.string.store_badge_third_party),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            if (row.entry.summary.isNotBlank()) {
                Text(row.entry.summary, style = MaterialTheme.typography.tboxBody)
            }
            Text(
                text = statusText(row),
                style = MaterialTheme.typography.tboxBody,
                color = if (row.unsupportedOnHu || row.manifestError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

@Composable
private fun AppDetail(
    row: StoreAppRow,
    icon: Bitmap?,
    actionState: StoreActionState,
    canInstall: Boolean,
    onBack: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.store_back), style = MaterialTheme.typography.tboxButton)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoreAppIcon(
                bitmap = icon,
                title = row.entry.title,
                contentDescription = row.entry.title,
                size = 72.dp,
            )
            Text(row.entry.title, style = MaterialTheme.typography.tboxHeadline)
        }
        if (row.entry.publisher.isNotBlank()) {
            Text(
                stringResource(R.string.store_publisher, row.entry.publisher),
                style = MaterialTheme.typography.tboxBody,
            )
        }
        if (row.entry.summary.isNotBlank()) {
            Text(row.entry.summary, style = MaterialTheme.typography.tboxBody)
        }
        Text(statusText(row), style = MaterialTheme.typography.tboxBody)
        row.remote?.apkSizeBytes?.let { bytes ->
            Text(
                stringResource(R.string.store_apk_size_mb, formatApkSizeMegabytes(bytes)),
                style = MaterialTheme.typography.tboxBody,
            )
        }
        row.remote?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            Text(stringResource(R.string.store_changelog), style = MaterialTheme.typography.tboxTitle)
            Text(changelog, style = MaterialTheme.typography.tboxBody)
        }
        row.manifestError?.let { err ->
            Text(
                stringResource(R.string.store_error, err),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }

        when {
            actionState is StoreActionState.Downloading &&
                actionState.appId == row.entry.id -> {
                val percent = actionState.percent
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.store_downloading, percent),
                        style = MaterialTheme.typography.tboxBody,
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.store_downloading_unknown),
                        style = MaterialTheme.typography.tboxBody,
                    )
                }
                OutlinedButton(onClick = onCancel) {
                    Text(
                        stringResource(R.string.store_cancel),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            actionState is StoreActionState.Verifying &&
                actionState.appId == row.entry.id -> {
                Text(
                    stringResource(R.string.store_verifying),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            actionState is StoreActionState.ReadyToInstall &&
                actionState.appId == row.entry.id -> {
                Button(onClick = onInstall, enabled = canInstall) {
                    Text(
                        stringResource(R.string.store_install),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            actionState is StoreActionState.Error &&
                (actionState.appId == row.entry.id || actionState.appId == null) -> {
                Text(
                    text = if (actionState.message == "network_unavailable") {
                        stringResource(R.string.store_error_network)
                    } else {
                        stringResource(R.string.store_error, actionState.message)
                    },
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        if (!row.unsupportedOnHu && row.remote != null &&
            actionState !is StoreActionState.Downloading &&
            actionState !is StoreActionState.Verifying &&
            (actionState as? StoreActionState.ReadyToInstall)?.appId != row.entry.id
        ) {
            val label = if (row.installed == null) {
                stringResource(R.string.store_download)
            } else if (row.updateAvailable) {
                stringResource(R.string.store_update)
            } else {
                null
            }
            if (label != null) {
                Button(onClick = onDownload) {
                    Text(label, style = MaterialTheme.typography.tboxButton)
                }
            }
        }
        if (row.installed != null) {
            OutlinedButton(onClick = onOpen) {
                Text(
                    stringResource(R.string.store_open),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
    }
}

@Composable
private fun statusText(row: StoreAppRow): String {
    return when {
        row.unsupportedOnHu -> stringResource(R.string.store_status_unsupported)
        row.remote != null && row.updateAvailable && row.installed != null ->
            stringResource(
                R.string.store_status_update,
                row.remote.versionName,
                row.remote.versionCode,
            )
        row.installed != null ->
            stringResource(
                R.string.store_status_installed,
                row.installed.versionName,
                row.installed.versionCode,
            )
        else -> stringResource(R.string.store_status_not_installed)
    }
}
