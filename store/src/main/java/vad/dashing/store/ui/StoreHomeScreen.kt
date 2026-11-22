package vad.dashing.store.ui

import android.Manifest
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
import vad.dashing.store.catalog.StoreAppCategory
import vad.dashing.store.catalog.StoreAppOrigin
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.install.DownloadsApkSaver
import vad.dashing.store.install.InstallPermissionHelper
import vad.dashing.store.install.formatApkSizeMegabytes
import vad.dashing.store.ui.theme.tboxBody
import vad.dashing.store.ui.theme.tboxButton
import vad.dashing.store.ui.theme.tboxHeadline
import vad.dashing.store.ui.theme.tboxTitle

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StoreHomeScreen(
    viewModel: StoreViewModel,
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val cloudSource by viewModel.cloudSource.collectAsStateWithLifecycle()
    val apkLanguage by viewModel.apkLanguage.collectAsStateWithLifecycle()
    val selectedAppId by viewModel.selectedAppId.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var canInstall by remember { mutableStateOf(viewModel.canInstallPackages()) }
    var showAdvanced by remember { mutableStateOf(false) }
    var pendingDownloadAppId by remember { mutableStateOf<String?>(null) }
    var uninstallConfirmRow by remember { mutableStateOf<StoreAppRow?>(null) }
    var uninstallBlocked by remember { mutableStateOf(false) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        val appId = pendingDownloadAppId
        pendingDownloadAppId = null
        if (appId != null) viewModel.download(appId)
    }

    fun startDownload(row: StoreAppRow) {
        val appId = row.entry.id
        val needsSidecar = row.entry.copyApkToDownloadsAppId.isNotBlank()
        if (needsSidecar &&
            DownloadsApkSaver.needsWritePermission() &&
            !DownloadsApkSaver.hasWritePermission(context)
        ) {
            pendingDownloadAppId = appId
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.download(appId)
    }

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

    val readyRows = (listState as? StoreListState.Ready)?.rows.orEmpty()
    val selfRow = readyRows.firstOrNull {
        it.entry.packageName == context.packageName
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            StoreTopBar(
                showBack = selectedRow != null,
                onBack = { viewModel.selectApp(null) },
                onRefresh = { viewModel.refresh() },
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
                appCount = readyRows.size.takeIf { listState is StoreListState.Ready },
            )

            if (selectedRow == null) {
                StoreFilterBar(
                    channel = channel,
                    apkLanguage = apkLanguage,
                    cloudSource = cloudSource,
                    showAdvanced = showAdvanced,
                    hu = viewModel.hu,
                    onChannel = { viewModel.setChannel(it) },
                    onLanguage = { viewModel.setApkLanguage(it) },
                    onCloud = { viewModel.setCloudSource(it) },
                )
            }

            if (!canInstall) {
                InstallPermissionBanner(
                    onGrant = {
                        context.startActivity(
                            InstallPermissionHelper.createUnknownSourcesSettingsIntent(context),
                        )
                    },
                )
            }

            if (selectedRow == null && selfRow != null && selfRow.updateAvailable) {
                val downloading = actionState as? StoreActionState.Downloading
                SelfUpdateBanner(
                    row = selfRow,
                    busy = downloading?.appId == selfRow.entry.id,
                    onUpdate = { startDownload(selfRow) },
                )
            }

            if (selectedRow != null) {
                AppDetail(
                    row = selectedRow,
                    icon = icons[selectedRow.entry.id],
                    actionState = actionState,
                    canInstall = canInstall,
                    isSelf = selectedRow.entry.packageName == context.packageName,
                    onSelectFlavor = { viewModel.setAppApkFlavor(selectedRow.entry.id, it) },
                    onDownload = { startDownload(selectedRow) },
                    onCancel = { viewModel.cancelDownload() },
                    onInstall = { viewModel.install() },
                    onOpen = { viewModel.openApp(selectedRow.entry.packageName) },
                    onUninstall = { uninstallConfirmRow = selectedRow },
                )
            } else {
                CatalogList(
                    listState = listState,
                    icons = icons,
                    actionState = actionState,
                    storePackageName = context.packageName,
                    onSelect = { viewModel.selectApp(it) },
                    onQuickAction = { row ->
                        val isSelf = row.entry.packageName == context.packageName
                        when {
                            isSelf && row.updateAvailable -> startDownload(row)
                            isSelf -> viewModel.selectApp(row.entry.id)
                            row.installed != null && !row.updateAvailable ->
                                viewModel.openApp(row.entry.packageName)
                            else -> startDownload(row)
                        }
                    },
                )
            }
        }

        uninstallConfirmRow?.let { row ->
            AlertDialog(
                onDismissRequest = { uninstallConfirmRow = null },
                title = { Text(stringResource(R.string.store_uninstall_confirm_title)) },
                text = {
                    Text(
                        stringResource(R.string.store_uninstall_confirm_message, row.entry.title),
                        style = MaterialTheme.typography.tboxBody,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            uninstallConfirmRow = null
                            if (!viewModel.uninstallApp(row.entry.packageName)) {
                                uninstallBlocked = true
                            }
                        },
                    ) {
                        Text(
                            stringResource(R.string.store_uninstall),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { uninstallConfirmRow = null }) {
                        Text(stringResource(R.string.store_cancel))
                    }
                },
            )
        }
        if (uninstallBlocked) {
            AlertDialog(
                onDismissRequest = { uninstallBlocked = false },
                title = { Text(stringResource(R.string.store_uninstall)) },
                text = {
                    Text(
                        stringResource(R.string.store_uninstall_self_blocked),
                        style = MaterialTheme.typography.tboxBody,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { uninstallBlocked = false }) {
                        Text(stringResource(R.string.store_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun StoreTopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    appCount: Int?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                TextButton(
                    onClick = onBack,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text("←", style = MaterialTheme.typography.tboxButton)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.store_title),
                    style = MaterialTheme.typography.tboxHeadline,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (appCount != null && !showBack) {
                    Text(
                        text = stringResource(R.string.store_apps_count, appCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!showBack) {
                TextButton(onClick = onToggleAdvanced) {
                    Text(
                        text = if (showAdvanced) {
                            stringResource(R.string.store_settings_hide)
                        } else {
                            stringResource(R.string.store_settings)
                        },
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
            FilledTonalButton(onClick = onRefresh) {
                Text(
                    stringResource(R.string.store_refresh),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StoreFilterBar(
    channel: UpdateChannel,
    apkLanguage: ApkLanguage,
    cloudSource: CloudSource,
    showAdvanced: Boolean,
    hu: HuKind,
    onChannel: (UpdateChannel) -> Unit,
    onLanguage: (ApkLanguage) -> Unit,
    onCloud: (CloudSource) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreChip(
                label = stringResource(R.string.store_channel_release),
                selected = channel == UpdateChannel.RELEASE,
                onClick = { onChannel(UpdateChannel.RELEASE) },
            )
            StoreChip(
                label = stringResource(R.string.store_channel_development),
                selected = channel == UpdateChannel.DEVELOPMENT,
                onClick = { onChannel(UpdateChannel.DEVELOPMENT) },
            )
            StoreChip(
                label = stringResource(R.string.store_apk_lang_ru),
                selected = apkLanguage == ApkLanguage.RU,
                onClick = { onLanguage(ApkLanguage.RU) },
            )
            StoreChip(
                label = stringResource(R.string.store_apk_lang_en),
                selected = apkLanguage == ApkLanguage.EN,
                onClick = { onLanguage(ApkLanguage.EN) },
            )
        }
        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        R.string.store_hu_label,
                        when (hu) {
                            HuKind.A9 -> stringResource(R.string.store_hu_a9)
                            HuKind.A10 -> stringResource(R.string.store_hu_a10)
                            HuKind.UNKNOWN -> stringResource(R.string.store_hu_unknown)
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StoreChip(
                        label = stringResource(R.string.store_cloud_auto),
                        selected = cloudSource == CloudSource.AUTO,
                        onClick = { onCloud(CloudSource.AUTO) },
                    )
                    StoreChip(
                        label = stringResource(R.string.store_cloud_yandex),
                        selected = cloudSource == CloudSource.YANDEX,
                        onClick = { onCloud(CloudSource.YANDEX) },
                    )
                    StoreChip(
                        label = stringResource(R.string.store_cloud_mailru),
                        selected = cloudSource == CloudSource.MAILRU,
                        onClick = { onCloud(CloudSource.MAILRU) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StoreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, style = MaterialTheme.typography.labelLarge)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        shape = RoundedCornerShape(20.dp),
    )
}

@Composable
private fun SelfUpdateBanner(
    row: StoreAppRow,
    busy: Boolean,
    onUpdate: () -> Unit,
) {
    val version = row.remote?.versionName.orEmpty()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.store_self_update_banner, version),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onUpdate,
                enabled = !busy,
                shape = RoundedCornerShape(24.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(
                        stringResource(R.string.store_self_update_button),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
            }
        }
    }
}

@Composable
private fun InstallPermissionBanner(onGrant: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.store_install_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(
                    stringResource(R.string.store_grant_install),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        }
    }
}

@Composable
private fun CatalogList(
    listState: StoreListState,
    icons: Map<String, Bitmap>,
    actionState: StoreActionState,
    storePackageName: String,
    onSelect: (String) -> Unit,
    onQuickAction: (StoreAppRow) -> Unit,
) {
    when (listState) {
        StoreListState.Idle,
        StoreListState.Loading,
        -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Text(
                        stringResource(R.string.store_loading),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is StoreListState.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
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
        }
        is StoreListState.Ready -> {
            if (listState.rows.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.store_empty),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(listState.rows, key = { it.entry.id }) { row ->
                        AppStoreCard(
                            row = row,
                            icon = icons[row.entry.id],
                            isSelf = row.entry.packageName == storePackageName,
                            busy = actionState is StoreActionState.Downloading &&
                                actionState.appId == row.entry.id,
                            onClick = { onSelect(row.entry.id) },
                            onAction = { onQuickAction(row) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AppStoreCard(
    row: StoreAppRow,
    icon: Bitmap?,
    isSelf: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        tonalElevation = 0.dp,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoreAppIcon(
                bitmap = icon,
                title = row.entry.title,
                contentDescription = row.entry.title,
                size = 72.dp,
                corner = 18.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.entry.title,
                    style = MaterialTheme.typography.tboxTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metaLine(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        row.unsupportedOnHu || row.manifestError != null ->
                            MaterialTheme.colorScheme.error
                        row.updateAvailable -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val actionLabel = cardActionLabel(row, isSelf)
            if (actionLabel != null && !row.unsupportedOnHu) {
                FilledTonalButton(
                    onClick = onAction,
                    enabled = !busy,
                    modifier = Modifier.widthIn(min = 120.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(actionLabel, style = MaterialTheme.typography.tboxButton)
                    }
                }
            }
        }
    }
}

@Composable
private fun metaLine(row: StoreAppRow): String {
    val parts = mutableListOf<String>()
    if (row.entry.publisher.isNotBlank()) parts += row.entry.publisher
    parts += when (row.entry.category) {
        StoreAppCategory.TOOL -> stringResource(R.string.store_category_tool)
        StoreAppCategory.ONESHOT -> stringResource(R.string.store_category_oneshot)
        StoreAppCategory.APP -> stringResource(R.string.store_category_app)
    }
    if (row.entry.origin == StoreAppOrigin.THIRD_PARTY) {
        parts += stringResource(R.string.store_badge_third_party)
    }
    return parts.joinToString(" · ")
}

@Composable
private fun cardActionLabel(row: StoreAppRow, isSelf: Boolean): String? {
    if (row.remote == null) return null
    return when {
        row.installed == null -> stringResource(R.string.store_get)
        row.updateAvailable -> stringResource(R.string.store_update)
        isSelf -> null
        else -> stringResource(R.string.store_open)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetail(
    row: StoreAppRow,
    icon: Bitmap?,
    actionState: StoreActionState,
    canInstall: Boolean,
    isSelf: Boolean,
    onSelectFlavor: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalAlignment = Alignment.Top,
        ) {
            StoreAppIcon(
                bitmap = icon,
                title = row.entry.title,
                contentDescription = row.entry.title,
                size = 112.dp,
                corner = 24.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(row.entry.title, style = MaterialTheme.typography.headlineMedium)
                if (row.entry.publisher.isNotBlank()) {
                    Text(
                        row.entry.publisher,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    if (isSelf) stringResource(R.string.store_this_app) else statusText(row),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MetaPill(metaLine(row).substringBefore(" · ").ifBlank { metaLine(row) })
                    row.remote?.apkSizeBytes?.let { bytes ->
                        MetaPill(
                            stringResource(
                                R.string.store_apk_size_mb,
                                formatApkSizeMegabytes(bytes),
                            ),
                        )
                    }
                    row.remote?.versionName?.takeIf { it.isNotBlank() }?.let { ver ->
                        MetaPill("v$ver")
                    }
                }
            }
        }

        if (row.hasLanguageChoice) {
            Text(
                stringResource(R.string.store_apk_flavor_label),
                style = MaterialTheme.typography.titleMedium,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (flavor in row.languageFlavors) {
                    val label = when (flavor) {
                        "en" -> stringResource(R.string.store_apk_lang_en)
                        else -> stringResource(R.string.store_apk_lang_ru)
                    }
                    StoreChip(
                        label = label,
                        selected = row.selectedFlavor.equals(flavor, ignoreCase = true),
                        onClick = { onSelectFlavor(flavor) },
                    )
                }
            }
        }

        DetailActions(
            row = row,
            actionState = actionState,
            canInstall = canInstall,
            isSelf = isSelf,
            onDownload = onDownload,
            onCancel = onCancel,
            onInstall = onInstall,
            onOpen = onOpen,
            onUninstall = onUninstall,
        )

        if (row.entry.summary.isNotBlank()) {
            DetailSection(title = stringResource(R.string.store_section_about)) {
                Text(row.entry.summary, style = MaterialTheme.typography.tboxBody)
            }
        }

        row.remote?.changelog?.takeIf { it.isNotBlank() }?.let { changelog ->
            DetailSection(title = stringResource(R.string.store_changelog)) {
                Text(changelog, style = MaterialTheme.typography.tboxBody)
            }
        }

        row.manifestError?.let { err ->
            Text(
                stringResource(R.string.store_error, err),
                style = MaterialTheme.typography.tboxBody,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun DetailActions(
    row: StoreAppRow,
    actionState: StoreActionState,
    canInstall: Boolean,
    isSelf: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            actionState is StoreActionState.Downloading &&
                actionState.appId == row.entry.id -> {
                val percent = actionState.percent
                if (percent != null) {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        stringResource(R.string.store_downloading, percent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        stringResource(R.string.store_downloading_unknown),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(stringResource(R.string.store_cancel), style = MaterialTheme.typography.tboxButton)
                }
            }
            actionState is StoreActionState.Verifying &&
                actionState.appId == row.entry.id -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        stringResource(R.string.store_verifying),
                        style = MaterialTheme.typography.tboxBody,
                    )
                }
            }
            actionState is StoreActionState.ReadyToInstall &&
                actionState.appId == row.entry.id -> {
                actionState.downloadsSidecarPath?.let { path ->
                    Text(
                        stringResource(R.string.store_sidecar_saved, path),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                actionState.downloadsSidecarError?.let { err ->
                    Text(
                        stringResource(R.string.store_sidecar_error, err),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                Button(
                    onClick = onInstall,
                    enabled = canInstall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(stringResource(R.string.store_install), style = MaterialTheme.typography.tboxButton)
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
            val label = when {
                row.installed == null -> stringResource(R.string.store_get)
                row.updateAvailable ->
                    if (isSelf) stringResource(R.string.store_self_update_button)
                    else stringResource(R.string.store_update)
                else -> null
            }
            if (label != null) {
                Button(
                    onClick = onDownload,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.tboxButton)
                }
            }
        }
        if (row.installed != null &&
            !isSelf &&
            actionState !is StoreActionState.Downloading &&
            (actionState as? StoreActionState.ReadyToInstall)?.appId != row.entry.id
        ) {
            OutlinedButton(
                onClick = onOpen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(28.dp),
            ) {
                Text(stringResource(R.string.store_open), style = MaterialTheme.typography.tboxButton)
            }
            TextButton(
                onClick = onUninstall,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text(
                    stringResource(R.string.store_uninstall),
                    style = MaterialTheme.typography.tboxButton,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun statusText(row: StoreAppRow): String {
    val remote = row.remote
    val installed = row.installed
    return when {
        row.unsupportedOnHu -> stringResource(R.string.store_status_unsupported)
        remote != null && row.updateAvailable && installed != null ->
            stringResource(
                R.string.store_status_update,
                remote.versionName,
                remote.versionCode,
            )
        installed != null ->
            stringResource(
                R.string.store_status_installed,
                installed.versionName,
                installed.versionCode,
            )
        else -> stringResource(R.string.store_status_not_installed)
    }
}
