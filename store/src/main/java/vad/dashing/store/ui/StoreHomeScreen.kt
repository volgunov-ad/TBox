package vad.dashing.store.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.store.BuildConfig
import vad.dashing.store.CardFileActionState
import vad.dashing.store.DownloadTarget
import vad.dashing.store.HuKind
import vad.dashing.store.R
import vad.dashing.store.StoreActionState
import vad.dashing.store.StoreAppRow
import vad.dashing.store.StoreErrors
import vad.dashing.store.StoreListState
import vad.dashing.store.catalog.StoreDownloadFile
import vad.dashing.store.catalog.StoreSection
import vad.dashing.store.disk.ApkLanguage
import vad.dashing.store.disk.AppChangelogState
import vad.dashing.store.disk.CloudSource
import vad.dashing.store.disk.UpdateChannel
import vad.dashing.store.dexopt.CompileErrors
import vad.dashing.store.dexopt.CompileState
import vad.dashing.store.install.CardFilesResult
import vad.dashing.store.install.DownloadsSaver
import vad.dashing.store.install.InstallPermissionHelper
import vad.dashing.store.install.formatApkSizeMegabytes
import vad.dashing.store.ui.theme.StoreDimens
import vad.dashing.store.ui.theme.tboxBody
import vad.dashing.store.ui.theme.tboxButton

private sealed interface PendingWrite {
    data class Apk(val appId: String, val target: DownloadTarget) : PendingWrite
    data class CardFile(val appId: String, val fileName: String) : PendingWrite
}

@Composable
fun StoreHomeScreen(
    viewModel: StoreViewModel,
) {
    val listState by viewModel.listState.collectAsStateWithLifecycle()
    val actionState by viewModel.actionState.collectAsStateWithLifecycle()
    val cardFileActionState by viewModel.cardFileActionState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val cloudSource by viewModel.cloudSource.collectAsStateWithLifecycle()
    val apkLanguage by viewModel.apkLanguage.collectAsStateWithLifecycle()
    val selectedAppId by viewModel.selectedAppId.collectAsStateWithLifecycle()
    val selectedSectionId by viewModel.selectedSectionId.collectAsStateWithLifecycle()
    val page by viewModel.page.collectAsStateWithLifecycle()
    val icons by viewModel.icons.collectAsStateWithLifecycle()
    val uiLang = if (BuildConfig.FLAVOR.equals("en", ignoreCase = true)) "en" else "ru"
    val appChangelogs by viewModel.appChangelogs.collectAsStateWithLifecycle()
    val compileStates by viewModel.compileStates.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isHeadUnit = viewModel.isHeadUnit
    var canInstall by remember { mutableStateOf(viewModel.canInstallPackages()) }
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    var pendingWrite by remember { mutableStateOf<PendingWrite?>(null) }
    var uninstallConfirmRow by remember { mutableStateOf<StoreAppRow?>(null) }
    var uninstallBlocked by remember { mutableStateOf(false) }

    val writePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingWrite ?: return@rememberLauncherForActivityResult
        pendingWrite = null
        if (granted) {
            when (pending) {
                is PendingWrite.Apk -> viewModel.download(pending.appId, pending.target)
                is PendingWrite.CardFile -> viewModel.downloadCardFile(pending.appId, pending.fileName)
            }
        } else {
            when (pending) {
                is PendingWrite.Apk ->
                    viewModel.reportError(pending.appId, StoreErrors.DOWNLOADS_PERMISSION)
                is PendingWrite.CardFile ->
                    viewModel.reportCardFileError(
                        pending.appId,
                        pending.fileName,
                        StoreErrors.DOWNLOADS_PERMISSION,
                    )
            }
        }
    }

    fun startDownload(row: StoreAppRow, target: DownloadTarget) {
        val toDownloads = target == DownloadTarget.DOWNLOADS || row.entry.isPhoneDistribution
        if (toDownloads && !DownloadsSaver.hasWritePermission(context)) {
            pendingWrite = PendingWrite.Apk(row.entry.id, target)
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.download(row.entry.id, target)
    }

    fun startCardFileDownload(row: StoreAppRow, fileName: String) {
        if (!DownloadsSaver.hasWritePermission(context)) {
            pendingWrite = PendingWrite.CardFile(row.entry.id, fileName)
            writePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return
        }
        viewModel.downloadCardFile(row.entry.id, fileName)
    }

    fun openInstallSettings() {
        try {
            context.startActivity(InstallPermissionHelper.createUnknownSourcesSettingsIntent(context))
        } catch (_: ActivityNotFoundException) {
            viewModel.reportError(null, StoreErrors.NO_ACTIVITY)
        } catch (_: SecurityException) {
            viewModel.reportError(null, StoreErrors.NO_ACTIVITY)
        }
    }

    fun openApp(row: StoreAppRow) {
        if (!viewModel.openApp(row.entry.packageName)) {
            viewModel.reportError(row.entry.id, OPEN_FAILED)
        }
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

    BackHandler(enabled = selectedAppId != null) { viewModel.selectApp(null) }

    val readyRows = (listState as? StoreListState.Ready)?.rows.orEmpty()
    val selectedRow = readyRows.firstOrNull { it.entry.id == selectedAppId }
    val selfRow = readyRows.firstOrNull { it.entry.packageName == context.packageName }
    val selectedChangelog = selectedRow?.let { appChangelogs[it.entry.id] } ?: AppChangelogState.Idle

    val compilePackage = selectedRow
        ?.takeIf { isHeadUnit && !it.entry.isPhoneDistribution && it.installed != null }
        ?.entry?.packageName
    LaunchedEffect(compilePackage, selectedRow?.installed?.versionCode) {
        compilePackage?.let { viewModel.checkCompileStatus(it) }
    }

    LaunchedEffect(selectedRow?.entry?.id, selectedChangelog is AppChangelogState.Idle) {
        val id = selectedRow?.entry?.id
        if (id != null && selectedChangelog is AppChangelogState.Idle) {
            viewModel.loadChangelog(id)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            StoreTopBar(
                detailTitle = selectedRow?.entry?.title,
                onBack = { viewModel.selectApp(null) },
                onRefresh = { viewModel.refresh() },
                refreshing = listState is StoreListState.Loading,
                showAdvanced = showAdvanced,
                onToggleAdvanced = { showAdvanced = !showAdvanced },
                appCount = readyRows.size.takeIf { listState is StoreListState.Ready },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (selectedRow != null) {
                AppDetail(
                    row = selectedRow,
                    icon = icons[selectedRow.entry.id],
                    actionState = actionState,
                    cardFileActionState = cardFileActionState,
                    canInstall = canInstall,
                    isHeadUnit = isHeadUnit,
                    isSelf = selectedRow.entry.packageName == context.packageName,
                    phoneQrUrl = viewModel.phoneApkBrowseUrl(selectedRow),
                    sectionTitles = (listState as? StoreListState.Ready)?.sections
                        ?.filter { it.id in selectedRow.entry.sections }
                        ?.map { it.displayTitle(uiLang) }
                        .orEmpty(),
                    changelogState = selectedChangelog,
                    compileState = compilePackage?.let { compileStates[it] ?: CompileState.Unknown },
                    compileBusy = compileStates.values.any { it is CompileState.Compiling },
                    onCompile = { compilePackage?.let { viewModel.compile(it) } },
                    onSelectFlavor = { viewModel.setAppApkFlavor(selectedRow.entry.id, it) },
                    onDownload = { target -> startDownload(selectedRow, target) },
                    onDownloadCardFile = { fileName -> startCardFileDownload(selectedRow, fileName) },
                    onCancel = { viewModel.cancelDownload() },
                    onInstall = { viewModel.install() },
                    onOpen = { openApp(selectedRow) },
                    onUninstall = { uninstallConfirmRow = selectedRow },
                    onGrantInstall = { openInstallSettings() },
                    onDismissCardFileResult = { viewModel.dismissActionResult() },
                )
            } else {
                val catalogView = remember(listState, selectedSectionId, page, uiLang) {
                    val ready = listState as? StoreListState.Ready
                    if (ready == null) {
                        CatalogView(emptyList(), null, emptyList(), 0, 1)
                    } else {
                        buildCatalogView(
                            rows = ready.rows,
                            sections = ready.sections,
                            selectedSectionId = selectedSectionId,
                            page = page,
                            lang = uiLang,
                        )
                    }
                }
                CatalogScreen(
                    listState = listState,
                    catalogView = catalogView,
                    icons = icons,
                    actionState = actionState,
                    storePackageName = context.packageName,
                    isHeadUnit = isHeadUnit,
                    onSelectSection = { viewModel.selectSection(it) },
                    onPage = { viewModel.setPage(it) },
                    header = {
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
                        if (!isHeadUnit) {
                            MessageBanner(
                                kind = MessageKind.INFO,
                                text = stringResource(R.string.store_not_hu_hint),
                            )
                        } else if (!canInstall) {
                            MessageBanner(
                                kind = MessageKind.ERROR,
                                text = stringResource(R.string.store_install_hint),
                                actionLabel = stringResource(R.string.store_grant_install),
                                onAction = { openInstallSettings() },
                            )
                        }
                        if (selfRow != null && selfRow.updateAvailable && isHeadUnit) {
                            SelfUpdateBanner(
                                row = selfRow,
                                actionState = actionState,
                                onUpdate = { startDownload(selfRow, DownloadTarget.INSTALL) },
                                onInstall = { viewModel.install() },
                            )
                        }
                        ListActionResult(
                            actionState = actionState,
                            rows = readyRows,
                            onDismiss = { viewModel.dismissActionResult() },
                        )
                    },
                    onRetry = { viewModel.refresh() },
                    onSelect = { viewModel.selectApp(it) },
                    onDownload = { row, target -> startDownload(row, target) },
                    onInstall = { viewModel.install() },
                    onOpen = { openApp(it) },
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
                        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(stringResource(R.string.store_uninstall), style = MaterialTheme.typography.tboxButton)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { uninstallConfirmRow = null },
                        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                    ) {
                        Text(stringResource(R.string.store_cancel), style = MaterialTheme.typography.tboxButton)
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
                    TextButton(
                        onClick = { uninstallBlocked = false },
                        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                    ) {
                        Text(stringResource(R.string.store_close), style = MaterialTheme.typography.tboxButton)
                    }
                },
            )
        }
    }
}

/** UI-only error key: launch intent missing or refused. */
private const val OPEN_FAILED = "open_failed"

// region Top bar and filters

@Composable
private fun StoreTopBar(
    detailTitle: String?,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    refreshing: Boolean,
    showAdvanced: Boolean,
    onToggleAdvanced: () -> Unit,
    appCount: Int?,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (detailTitle != null) {
                BarIconButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.store_back),
                    onClick = onBack,
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detailTitle ?: stringResource(R.string.store_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detailTitle == null) {
                    val version = stringResource(
                        R.string.store_version_line,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                    )
                    val subtitle = if (appCount != null) {
                        version + " · " + stringResource(R.string.store_apps_count, appCount)
                    } else {
                        version
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (detailTitle == null) {
                BarIconButton(
                    icon = Icons.Filled.Settings,
                    contentDescription = stringResource(
                        if (showAdvanced) R.string.store_settings_hide else R.string.store_settings,
                    ),
                    onClick = onToggleAdvanced,
                    selected = showAdvanced,
                )
            }
            if (refreshing) {
                Box(
                    modifier = Modifier.size(StoreDimens.IconButton),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                }
            } else {
                BarIconButton(
                    icon = Icons.Filled.Refresh,
                    contentDescription = stringResource(R.string.store_refresh),
                    onClick = onRefresh,
                )
            }
        }
    }
}

@Composable
private fun BarIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(StoreDimens.IconButton),
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        } else {
            IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(30.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LabeledSegments(
                label = stringResource(R.string.store_section_channel),
                options = listOf(
                    UpdateChannel.RELEASE to stringResource(R.string.store_channel_release),
                    UpdateChannel.DEVELOPMENT to stringResource(R.string.store_channel_development),
                ),
                selected = channel,
                onSelect = onChannel,
            )
            LabeledSegments(
                label = stringResource(R.string.store_apk_flavor_label),
                options = listOf(
                    ApkLanguage.RU to stringResource(R.string.store_apk_lang_ru),
                    ApkLanguage.EN to stringResource(R.string.store_apk_lang_en),
                ),
                selected = apkLanguage,
                onSelect = onLanguage,
            )
        }
        AnimatedVisibility(visible = showAdvanced) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                LabeledSegments(
                    label = stringResource(R.string.store_section_source),
                    options = listOf(
                        CloudSource.AUTO to stringResource(R.string.store_cloud_auto),
                        CloudSource.YANDEX to stringResource(R.string.store_cloud_yandex),
                        CloudSource.MAILRU to stringResource(R.string.store_cloud_mailru),
                    ),
                    selected = cloudSource,
                    onSelect = onCloud,
                )
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
            }
        }
    }
}

@Composable
private fun <T> LabeledSegments(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SingleChoiceSegmentedButtonRow {
            options.forEachIndexed { index, (value, text) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    modifier = Modifier.heightIn(min = StoreDimens.ChipHeight),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        activeContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        inactiveContainerColor = MaterialTheme.colorScheme.surface,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }
    }
}

// endregion

// region Banners and messages

private enum class MessageKind { INFO, SUCCESS, ERROR }

@Composable
private fun MessageBanner(
    kind: MessageKind,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    onDismiss: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (kind) {
        MessageKind.INFO -> colors.secondaryContainer to colors.onSecondaryContainer
        MessageKind.SUCCESS -> colors.tertiaryContainer to colors.onTertiaryContainer
        MessageKind.ERROR -> colors.errorContainer to colors.onErrorContainer
    }
    val icon = when (kind) {
        MessageKind.INFO -> Icons.Filled.Info
        MessageKind.SUCCESS -> Icons.Filled.CheckCircle
        MessageKind.ERROR -> Icons.Filled.Warning
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                    colors = ButtonDefaults.textButtonColors(contentColor = content),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.tboxButton)
                }
            }
            if (onDismiss != null) {
                IconButton(onClick = onDismiss, modifier = Modifier.size(StoreDimens.CompactButtonHeight)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.store_close))
                }
            }
        }
    }
}

@Composable
private fun SelfUpdateBanner(
    row: StoreAppRow,
    actionState: StoreActionState,
    onUpdate: () -> Unit,
    onInstall: () -> Unit,
) {
    val version = row.remote?.versionName.orEmpty()
    val ready = (actionState as? StoreActionState.ReadyToInstall)?.appId == row.entry.id
    val busy = actionState.activeAppId == row.entry.id
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(28.dp))
            Text(
                text = stringResource(R.string.store_self_update_banner, version),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            when {
                busy -> ProgressBadge(actionState)
                ready -> PrimaryButton(
                    text = stringResource(R.string.store_install),
                    onClick = onInstall,
                    compact = true,
                )
                else -> PrimaryButton(
                    text = stringResource(R.string.store_self_update_button),
                    onClick = onUpdate,
                    enabled = actionState.activeAppId == null,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun FilesResultBanners(files: CardFilesResult?) {
    if (files == null) return
    if (files.readyCount > 0) {
        MessageBanner(
            kind = MessageKind.SUCCESS,
            text = stringResource(R.string.store_files_saved, files.folderPath, files.readyCount),
        )
    }
    files.failed.forEach { failure ->
        MessageBanner(
            kind = MessageKind.ERROR,
            text = stringResource(
                R.string.store_files_failed,
                failure.name,
                knownErrorText(failure.error) ?: failure.error,
            ),
        )
    }
}

@Composable
private fun CardFileActionBanners(
    appId: String,
    state: CardFileActionState,
    onDismiss: () -> Unit,
) {
    when {
        state is CardFileActionState.Saved && state.appId == appId -> {
            FilesResultBanners(state.files)
        }
        state is CardFileActionState.Error && state.appId == appId -> {
            MessageBanner(
                kind = MessageKind.ERROR,
                text = actionErrorText(state.message),
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun CardFileRow(
    file: StoreDownloadFile,
    appId: String,
    actionState: StoreActionState,
    cardFileActionState: CardFileActionState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val downloading = cardFileActionState as? CardFileActionState.Downloading
    val busyHere = downloading?.appId == appId && downloading.fileName == file.name
    val busyElsewhere = actionState.activeAppId != null ||
        (cardFileActionState.activeAppId != null && !busyHere)
    val size = file.sizeBytes?.let { formatApkSizeMegabytes(it) }
    val label = if (size != null) "${file.name} · $size MB" else file.name

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.tboxBody,
                modifier = Modifier.weight(1f),
            )
            if (!busyHere) {
                PrimaryButton(
                    text = stringResource(R.string.store_file_download),
                    onClick = onDownload,
                    enabled = !busyElsewhere,
                    compact = true,
                )
            }
        }
        if (busyHere) {
            val percent = downloading.percent
            Text(
                text = if (percent != null) {
                    stringResource(R.string.store_downloading, percent)
                } else {
                    stringResource(R.string.store_downloading_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            SecondaryButton(text = stringResource(R.string.store_cancel), onClick = onCancel)
        }
    }
}

/** Result of a quick action started from the list (no detail screen open to show it). */
@Composable
private fun ListActionResult(
    actionState: StoreActionState,
    rows: List<StoreAppRow>,
    onDismiss: () -> Unit,
) {
    when (actionState) {
        is StoreActionState.Error -> {
            val title = rows.firstOrNull { it.entry.id == actionState.appId }?.entry?.title
            val message = actionErrorText(actionState.message)
            MessageBanner(
                kind = MessageKind.ERROR,
                text = if (title != null) "$title: $message" else message,
                onDismiss = onDismiss,
            )
        }
        is StoreActionState.SavedToDownloads -> {
            MessageBanner(
                kind = MessageKind.SUCCESS,
                text = stringResource(R.string.store_saved_to_downloads, actionState.downloadsPath),
                onDismiss = onDismiss,
            )
        }
        else -> Unit
    }
}

@Composable
private fun actionErrorText(message: String): String =
    knownErrorText(message) ?: stringResource(R.string.store_error, message)

/** Localized text for a [StoreErrors] key; null for a raw exception message. */
@Composable
private fun knownErrorText(message: String): String? = when (message) {
    StoreErrors.NETWORK_UNAVAILABLE -> stringResource(R.string.store_error_network)
    StoreErrors.PHONE_APK_NOT_FOR_HU -> stringResource(R.string.store_error_phone_not_for_hu)
    StoreErrors.DOWNLOADS_PERMISSION -> stringResource(R.string.store_error_downloads_permission)
    StoreErrors.DOWNLOADS_WRITE_FAILED -> stringResource(R.string.store_error_downloads_write_failed)
    StoreErrors.FILE_CHECKSUM_MISMATCH -> stringResource(R.string.store_error_file_checksum_mismatch)
    StoreErrors.FILE_WRITE_FAILED -> stringResource(R.string.store_error_file_write_failed)
    StoreErrors.NO_ACTIVITY -> stringResource(R.string.store_error_no_activity)
    StoreErrors.APK_MISSING -> stringResource(R.string.store_error_apk_missing)
    StoreErrors.CHECKSUM_MISMATCH -> stringResource(R.string.store_error_checksum_mismatch)
    StoreErrors.PACKAGE_MISMATCH -> stringResource(R.string.store_error_package_mismatch)
    StoreErrors.CERT_MISMATCH -> stringResource(R.string.store_error_cert_mismatch)
    StoreErrors.CERT_UNREADABLE -> stringResource(R.string.store_error_cert_unreadable)
    StoreErrors.CERT_CONFLICTS_INSTALLED -> stringResource(R.string.store_error_cert_conflicts_installed)
    OPEN_FAILED -> stringResource(R.string.store_error_open_failed)
    else -> null
}

// endregion

// region Catalog list

@Composable
private fun CatalogScreen(
    listState: StoreListState,
    catalogView: CatalogView,
    icons: Map<String, Bitmap>,
    actionState: StoreActionState,
    storePackageName: String,
    isHeadUnit: Boolean,
    onSelectSection: (String?) -> Unit,
    onPage: (Int) -> Unit,
    header: @Composable ColumnScope.() -> Unit,
    onRetry: () -> Unit,
    onSelect: (String) -> Unit,
    onDownload: (StoreAppRow, DownloadTarget) -> Unit,
    onInstall: () -> Unit,
    onOpen: (StoreAppRow) -> Unit,
) {
    val listStateLazy = rememberLazyListState()
    val scope = rememberCoroutineScope()
    fun scrollToSectionsOrCards() {
        scope.launch {
            // Scroll to sections row (or first cards) under the header.
            listStateLazy.scrollToItem(1)
        }
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listStateLazy,
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = StoreDimens.ContentMaxWidth),
            contentPadding = PaddingValues(StoreDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Column(
                    modifier = Modifier.padding(bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = header,
                )
            }
            when (listState) {
                StoreListState.Idle,
                StoreListState.Loading,
                -> item(key = "loading") {
                    CenteredState {
                        CircularProgressIndicator(modifier = Modifier.size(56.dp), strokeWidth = 4.dp)
                        Text(
                            stringResource(R.string.store_loading),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is StoreListState.Error -> item(key = "error") {
                    CenteredState {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(56.dp),
                        )
                        Text(
                            text = if (listState.message == StoreErrors.NETWORK_UNAVAILABLE) {
                                stringResource(R.string.store_error_network)
                            } else {
                                stringResource(R.string.store_error, listState.message)
                            },
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onBackground,
                            textAlign = TextAlign.Center,
                        )
                        PrimaryButton(
                            text = stringResource(R.string.store_retry),
                            onClick = onRetry,
                            compact = true,
                        )
                    }
                }
                is StoreListState.Ready -> {
                    if (catalogView.chips.isNotEmpty()) {
                        item(key = "sections") {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 2.dp),
                            ) {
                                items(catalogView.chips, key = { it.id ?: "all" }) { chip ->
                                    val selected = catalogView.selectedSectionId == chip.id
                                    val title = if (chip.id == null) {
                                        stringResource(R.string.store_section_all)
                                    } else {
                                        chip.title
                                    }
                                    FilterChip(
                                        selected = selected,
                                        onClick = { onSelectSection(chip.id) },
                                        label = {
                                            Text(
                                                stringResource(
                                                    R.string.store_section_chip,
                                                    title,
                                                    chip.count,
                                                ),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        },
                                        modifier = Modifier.heightIn(min = StoreDimens.ChipHeight),
                                    )
                                }
                            }
                        }
                    }
                    if (listState.rows.isEmpty()) {
                        item(key = "empty") {
                            CenteredState {
                                Text(
                                    stringResource(R.string.store_empty),
                                    style = MaterialTheme.typography.tboxBody,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    } else {
                        items(catalogView.pageRows, key = { it.entry.id }) { row ->
                            AppStoreCard(
                                row = row,
                                icon = icons[row.entry.id],
                                actionState = actionState,
                                isSelf = row.entry.packageName == storePackageName,
                                isHeadUnit = isHeadUnit,
                                sectionTitles = catalogView.chips
                                    .filter { it.id != null && it.id in row.entry.sections }
                                    .map { it.title },
                                onClick = { onSelect(row.entry.id) },
                                onDownload = { target -> onDownload(row, target) },
                                onInstall = onInstall,
                                onOpen = { onOpen(row) },
                            )
                        }
                        if (catalogView.pageCount > 1) {
                            item(key = "pager") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onPage(catalogView.page - 1)
                                            scrollToSectionsOrCards()
                                        },
                                        enabled = catalogView.page > 0,
                                        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                            contentDescription = null,
                                        )
                                        Text(stringResource(R.string.store_page_prev))
                                    }
                                    Text(
                                        text = stringResource(
                                            R.string.store_page_indicator,
                                            catalogView.page + 1,
                                            catalogView.pageCount,
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.Center,
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            onPage(catalogView.page + 1)
                                            scrollToSectionsOrCards()
                                        },
                                        enabled = catalogView.page < catalogView.pageCount - 1,
                                        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
                                    ) {
                                        Text(stringResource(R.string.store_page_next))
                                        Icon(
                                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredState(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
        content = content,
    )
}

private enum class CardAction { QR, DOWNLOADS, GET, UPDATE, OPEN }

private fun cardAction(row: StoreAppRow, isSelf: Boolean, isHeadUnit: Boolean): CardAction? {
    if (row.remote == null) {
        return if (row.installed != null && !isSelf && isHeadUnit) CardAction.OPEN else null
    }
    if (row.entry.isPhoneDistribution) return if (isHeadUnit) CardAction.QR else CardAction.DOWNLOADS
    if (!isHeadUnit) return CardAction.DOWNLOADS
    if (row.unsupportedOnHu) return null
    return when {
        row.installed == null -> CardAction.GET
        row.updateAvailable -> CardAction.UPDATE
        isSelf -> null
        else -> CardAction.OPEN
    }
}

@Composable
private fun AppStoreCard(
    row: StoreAppRow,
    icon: Bitmap?,
    actionState: StoreActionState,
    isSelf: Boolean,
    isHeadUnit: Boolean,
    sectionTitles: List<String>,
    onClick: () -> Unit,
    onDownload: (DownloadTarget) -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
) {
    val id = row.entry.id
    val busyHere = actionState.activeAppId == id
    val busyElsewhere = actionState.activeAppId != null && !busyHere
    val readyHere = (actionState as? StoreActionState.ReadyToInstall)?.appId == id
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StoreDimens.CardCorner),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StoreAppIcon(
                bitmap = icon,
                title = row.entry.title,
                contentDescription = null,
                size = StoreDimens.ListIcon,
                corner = 18.dp,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = row.entry.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = metaLine(row, sectionTitles)
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                StatusPill(row = row, isSelf = isSelf, isHeadUnit = isHeadUnit)
            }
            Box(
                modifier = Modifier.widthIn(min = 150.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                when {
                    busyHere -> ProgressBadge(actionState)
                    readyHere -> PrimaryButton(
                        text = stringResource(R.string.store_install),
                        onClick = onInstall,
                        compact = true,
                    )
                    else -> when (cardAction(row, isSelf, isHeadUnit)) {
                        CardAction.QR -> TonalButton(stringResource(R.string.store_phone_qr_button), onClick)
                        CardAction.DOWNLOADS -> TonalButton(
                            text = stringResource(R.string.store_save_short),
                            onClick = { onDownload(DownloadTarget.DOWNLOADS) },
                            enabled = !busyElsewhere,
                        )
                        CardAction.GET -> PrimaryButton(
                            text = stringResource(R.string.store_get),
                            onClick = { onDownload(DownloadTarget.INSTALL) },
                            enabled = !busyElsewhere,
                            compact = true,
                        )
                        CardAction.UPDATE -> PrimaryButton(
                            text = stringResource(R.string.store_update),
                            onClick = { onDownload(DownloadTarget.INSTALL) },
                            enabled = !busyElsewhere,
                            compact = true,
                        )
                        CardAction.OPEN -> TonalButton(stringResource(R.string.store_open), onOpen)
                        null -> Unit
                    }
                }
            }
        }
    }
}

/** Download progress shown in place of an action button. */
@Composable
private fun ProgressBadge(actionState: StoreActionState) {
    val percent = (actionState as? StoreActionState.Downloading)?.percent
    Row(
        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (percent != null) {
            CircularProgressIndicator(
                progress = { percent / 100f },
                modifier = Modifier.size(36.dp),
                strokeWidth = 4.dp,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Text(
                "$percent%",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 4.dp)
        }
    }
}

@Composable
private fun StatusPill(row: StoreAppRow, isSelf: Boolean, isHeadUnit: Boolean) {
    val colors = MaterialTheme.colorScheme
    val text: String
    val container: Color
    val content: Color
    when {
        row.manifestError != null -> {
            text = stringResource(R.string.store_error, row.manifestError)
            container = colors.errorContainer
            content = colors.onErrorContainer
        }
        row.unsupportedOnHu -> {
            text = stringResource(R.string.store_status_unsupported)
            container = colors.surfaceContainerHigh
            content = colors.onSurfaceVariant
        }
        row.entry.isPhoneDistribution -> {
            text = stringResource(R.string.store_phone_only)
            container = colors.secondaryContainer
            content = colors.onSecondaryContainer
        }
        !isHeadUnit -> {
            val version = row.remote?.versionName.orEmpty()
            text = if (version.isNotBlank()) "v$version" else stringResource(R.string.store_status_not_installed)
            container = colors.surfaceContainerHigh
            content = colors.onSurfaceVariant
        }
        row.updateAvailable && row.installed != null -> {
            text = statusText(row)
            container = colors.tertiaryContainer
            content = colors.onTertiaryContainer
        }
        isSelf -> {
            text = stringResource(R.string.store_this_app)
            container = colors.surfaceContainerHigh
            content = colors.onSurfaceVariant
        }
        else -> {
            text = statusText(row)
            container = if (row.installed != null) colors.secondaryContainer else colors.surfaceContainerHigh
            content = if (row.installed != null) colors.onSecondaryContainer else colors.onSurfaceVariant
        }
    }
    Pill(text = text, container = container, content = content)
}

@Composable
private fun Pill(
    text: String,
    container: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(10.dp)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun metaLine(row: StoreAppRow, sectionTitles: List<String>): String {
    val parts = mutableListOf<String>()
    if (row.entry.publisher.isNotBlank()) parts += row.entry.publisher
    if (sectionTitles.isNotEmpty()) {
        parts += sectionTitles.joinToString(", ")
    }
    return parts.joinToString(" · ")
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

// endregion

// region Detail

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppDetail(
    row: StoreAppRow,
    icon: Bitmap?,
    actionState: StoreActionState,
    cardFileActionState: CardFileActionState,
    canInstall: Boolean,
    isHeadUnit: Boolean,
    isSelf: Boolean,
    phoneQrUrl: String?,
    sectionTitles: List<String>,
    changelogState: AppChangelogState,
    compileState: CompileState?,
    compileBusy: Boolean,
    onCompile: () -> Unit,
    onSelectFlavor: (String) -> Unit,
    onDownload: (DownloadTarget) -> Unit,
    onDownloadCardFile: (String) -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onGrantInstall: () -> Unit,
    onDismissCardFileResult: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = StoreDimens.ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(StoreDimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            DetailCard {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoreAppIcon(
                        bitmap = icon,
                        title = row.entry.title,
                        contentDescription = null,
                        size = StoreDimens.DetailIcon,
                        corner = 26.dp,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(row.entry.title, style = MaterialTheme.typography.headlineMedium)
                        if (row.entry.publisher.isNotBlank()) {
                            Text(
                                row.entry.publisher,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            StatusPill(row = row, isSelf = isSelf, isHeadUnit = isHeadUnit)
                            sectionTitles.forEach { title -> Pill(title) }
                            row.remote?.versionName?.takeIf { it.isNotBlank() }?.let { Pill("v$it") }
                            row.remote?.apkSizeBytes?.let { bytes ->
                                Pill(stringResource(R.string.store_apk_size_mb, formatApkSizeMegabytes(bytes)))
                            }
                        }
                    }
                }
            }

            DetailCard {
                DetailActions(
                    row = row,
                    actionState = actionState,
                    cardFileBusy = cardFileActionState.activeAppId != null,
                    canInstall = canInstall,
                    isHeadUnit = isHeadUnit,
                    isSelf = isSelf,
                    onSelectFlavor = onSelectFlavor,
                    onDownload = onDownload,
                    onCancel = onCancel,
                    onInstall = onInstall,
                    onOpen = onOpen,
                    onUninstall = onUninstall,
                    onGrantInstall = onGrantInstall,
                )
            }

            if (row.entry.hasDownloadFiles) {
                val downloads = row.entry.downloads!!
                DetailCard(title = stringResource(R.string.store_files_title)) {
                    Text(
                        stringResource(R.string.store_files_hint, downloads.folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    CardFileActionBanners(
                        appId = row.entry.id,
                        state = cardFileActionState,
                        onDismiss = onDismissCardFileResult,
                    )
                    downloads.files.forEach { file ->
                        CardFileRow(
                            file = file,
                            appId = row.entry.id,
                            actionState = actionState,
                            cardFileActionState = cardFileActionState,
                            onDownload = { onDownloadCardFile(file.name) },
                            onCancel = onCancel,
                        )
                    }
                }
            }

            if (compileState != null) {
                DetailCard(title = stringResource(R.string.store_compile_title)) {
                    CompileSection(state = compileState, busy = compileBusy, onCompile = onCompile)
                }
            }

            if (row.entry.isPhoneDistribution && isHeadUnit && !phoneQrUrl.isNullOrBlank()) {
                DetailCard(title = stringResource(R.string.store_phone_qr_title)) {
                    PhoneQrSection(url = phoneQrUrl)
                }
            }

            if (row.entry.summary.isNotBlank()) {
                DetailCard(title = stringResource(R.string.store_section_about)) {
                    Text(row.entry.summary, style = MaterialTheme.typography.tboxBody)
                }
            }

            ChangelogSection(state = changelogState)

            row.manifestError?.let { err ->
                MessageBanner(kind = MessageKind.ERROR, text = stringResource(R.string.store_error, err))
            }
        }
    }
}

@Composable
private fun DetailCard(
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(StoreDimens.CardCorner),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun ColumnScope.CompileSection(
    state: CompileState,
    busy: Boolean,
    onCompile: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val (text, color) = when (state) {
        CompileState.Unknown -> stringResource(R.string.store_compile_status_unknown) to colors.onSurfaceVariant
        CompileState.Checking -> stringResource(R.string.store_compile_status_checking) to colors.onSurfaceVariant
        CompileState.Compiling -> stringResource(R.string.store_compile_status_compiling) to colors.primary
        is CompileState.Compiled ->
            stringResource(R.string.store_compile_status_compiled, state.filter) to colors.tertiary
        is CompileState.NotCompiled -> if (state.tooLarge) {
            stringResource(R.string.store_compile_too_large) to colors.onSurfaceVariant
        } else {
            stringResource(R.string.store_compile_status_not_compiled, state.filter) to colors.error
        }
        is CompileState.Failed -> when (state.message) {
            CompileErrors.ADB_CONNECT -> stringResource(R.string.store_compile_error_adb)
            CompileErrors.TIMEOUT -> stringResource(R.string.store_compile_error_timeout)
            CompileErrors.TOO_LARGE -> stringResource(R.string.store_compile_too_large)
            else -> stringResource(R.string.store_compile_error, state.message)
        } to colors.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state == CompileState.Checking || state == CompileState.Compiling) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
    if (state == CompileState.Compiling) {
        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            trackColor = colors.surfaceContainerHighest,
        )
    }
    val canCompile = when (state) {
        is CompileState.NotCompiled -> !state.tooLarge
        is CompileState.Failed -> state.message != CompileErrors.TOO_LARGE
        CompileState.Unknown -> true
        else -> false
    }
    if (canCompile) {
        SecondaryButton(
            text = stringResource(R.string.store_compile_button),
            onClick = onCompile,
            enabled = !busy,
        )
    }
    if (state !is CompileState.Compiled) {
        Text(
            stringResource(R.string.store_compile_hint),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChangelogSection(
    state: AppChangelogState,
) {
    when (state) {
        is AppChangelogState.Idle, is AppChangelogState.Loading -> {
            DetailCard(title = stringResource(R.string.store_changelog)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        stringResource(R.string.store_changelog_loading),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        is AppChangelogState.Ready -> DetailCard(title = stringResource(R.string.store_changelog)) {
            Text(state.text, style = MaterialTheme.typography.tboxBody)
        }
        is AppChangelogState.Absent -> Unit
        is AppChangelogState.Error -> DetailCard(title = stringResource(R.string.store_changelog)) {
            Text(
                actionErrorText(state.message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun PhoneQrSection(url: String) {
    val qr by rememberStoreQrImage(url, sizePx = 512)
    Text(
        stringResource(R.string.store_phone_qr_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Always black on white: phone cameras do not read inverted codes reliably.
        Surface(color = Color.White, shape = RoundedCornerShape(16.dp)) {
            val bitmap = qr
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = stringResource(R.string.store_phone_qr_button),
                    modifier = Modifier
                        .size(240.dp)
                        .padding(12.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(modifier = Modifier.size(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(40.dp),
                        strokeWidth = 3.dp,
                        color = Color(0xFF1A73E8),
                    )
                }
            }
        }
    }
    Text(
        url,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailActions(
    row: StoreAppRow,
    actionState: StoreActionState,
    cardFileBusy: Boolean,
    canInstall: Boolean,
    isHeadUnit: Boolean,
    isSelf: Boolean,
    onSelectFlavor: (String) -> Unit,
    onDownload: (DownloadTarget) -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onOpen: () -> Unit,
    onUninstall: () -> Unit,
    onGrantInstall: () -> Unit,
) {
    val id = row.entry.id
    val phoneOnly = row.entry.isPhoneDistribution
    val busyHere = actionState.activeAppId == id
    val busyElsewhere = (actionState.activeAppId != null && !busyHere) || cardFileBusy
    val ready = (actionState as? StoreActionState.ReadyToInstall)?.takeIf { it.appId == id }

    if (row.hasLanguageChoice) {
        LabeledSegments(
            label = stringResource(R.string.store_apk_flavor_label),
            options = row.languageFlavors.map { flavor ->
                flavor to if (flavor == "en") {
                    stringResource(R.string.store_apk_lang_en)
                } else {
                    stringResource(R.string.store_apk_lang_ru)
                }
            },
            selected = row.selectedFlavor?.lowercase() ?: row.languageFlavors.first(),
            onSelect = { if (!busyHere && !cardFileBusy) onSelectFlavor(it) },
        )
    }

    when {
        actionState is StoreActionState.Downloading && actionState.appId == id -> {
            val percent = actionState.percent
            Text(
                text = if (percent != null) {
                    stringResource(R.string.store_downloading, percent)
                } else {
                    stringResource(R.string.store_downloading_unknown)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }
            SecondaryButton(text = stringResource(R.string.store_cancel), onClick = onCancel)
            return
        }
        actionState is StoreActionState.Verifying && actionState.appId == id -> {
            Row(
                modifier = Modifier.heightIn(min = StoreDimens.ButtonHeight),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 3.dp)
                Text(
                    stringResource(
                        if (actionState.target == DownloadTarget.DOWNLOADS) {
                            R.string.store_verifying_saving
                        } else {
                            R.string.store_verifying
                        },
                    ),
                    style = MaterialTheme.typography.tboxBody,
                )
            }
            return
        }
        ready != null -> {
            MessageBanner(kind = MessageKind.SUCCESS, text = stringResource(R.string.store_ready_to_install))
        }
        actionState is StoreActionState.SavedToDownloads && actionState.appId == id -> {
            MessageBanner(
                kind = MessageKind.SUCCESS,
                text = stringResource(R.string.store_saved_to_downloads, actionState.downloadsPath),
            )
        }
        actionState is StoreActionState.Error &&
            (actionState.appId == id || actionState.appId == null) -> {
            MessageBanner(kind = MessageKind.ERROR, text = actionErrorText(actionState.message))
        }
    }

    if (ready != null && !phoneOnly) {
        if (!canInstall && isHeadUnit) {
            MessageBanner(
                kind = MessageKind.ERROR,
                text = stringResource(R.string.store_install_hint),
                actionLabel = stringResource(R.string.store_grant_install),
                onAction = onGrantInstall,
            )
        }
        PrimaryButton(
            text = stringResource(R.string.store_install),
            onClick = onInstall,
            enabled = canInstall || !isHeadUnit,
        )
    }

    val remote = row.remote
    val canDownload = remote != null && !busyElsewhere
    val installLabel: String? = when {
        phoneOnly || remote == null || row.unsupportedOnHu || ready != null -> null
        row.installed == null ->
            if (isHeadUnit) stringResource(R.string.store_get) else stringResource(R.string.store_install_here)
        row.updateAvailable -> when {
            isSelf -> stringResource(R.string.store_self_update_button)
            isHeadUnit -> stringResource(R.string.store_update)
            else -> stringResource(R.string.store_install_here)
        }
        else -> null
    }
    val downloadsLabel = if (phoneOnly) {
        stringResource(R.string.store_save_to_downloads)
    } else {
        stringResource(R.string.store_save_apk_to_downloads)
    }

    if (isHeadUnit && !phoneOnly) {
        installLabel?.let { label ->
            PrimaryButton(text = label, onClick = { onDownload(DownloadTarget.INSTALL) }, enabled = canDownload)
        }
        if (remote != null) {
            SecondaryButton(
                text = downloadsLabel,
                onClick = { onDownload(DownloadTarget.DOWNLOADS) },
                enabled = canDownload,
            )
            Text(
                stringResource(R.string.store_downloads_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        if (remote != null) {
            PrimaryButton(
                text = downloadsLabel,
                onClick = { onDownload(DownloadTarget.DOWNLOADS) },
                enabled = canDownload,
            )
        }
        installLabel?.let { label ->
            SecondaryButton(text = label, onClick = { onDownload(DownloadTarget.INSTALL) }, enabled = canDownload)
        }
    }

    if (!phoneOnly && row.installed != null && !isSelf) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SecondaryButton(
                text = stringResource(R.string.store_open),
                onClick = onOpen,
                modifier = Modifier.weight(1f),
            )
            SecondaryButton(
                text = stringResource(R.string.store_uninstall),
                onClick = onUninstall,
                modifier = Modifier.weight(1f),
                destructive = true,
            )
        }
    }
}

// endregion

// region Buttons

@Composable
private fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = if (compact) {
            modifier.heightIn(min = StoreDimens.CompactButtonHeight)
        } else {
            modifier
                .fillMaxWidth()
                .heightIn(min = StoreDimens.ButtonHeight)
        },
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        ButtonText(text)
    }
}

@Composable
private fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = StoreDimens.ButtonHeight),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, if (destructive) colors.error.copy(alpha = 0.7f) else colors.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (destructive) colors.error else colors.primary,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        ButtonText(text)
    }
}

@Composable
private fun TonalButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = StoreDimens.CompactButtonHeight),
        shape = RoundedCornerShape(28.dp),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
    ) {
        ButtonText(text)
    }
}

@Composable
private fun RowScope.ButtonText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.tboxButton,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// endregion
