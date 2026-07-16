package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
// import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ThemeApply
import vad.dashing.tbox.ThemeApplyTarget
import vad.dashing.tbox.ThemeApplyTargetAvailability
import vad.dashing.tbox.ThemeBundleExport
import vad.dashing.tbox.ThemeCacheKeys
import vad.dashing.tbox.ThemeFileResolver
import vad.dashing.tbox.ThemeMaterialization
import vad.dashing.tbox.resolveDriveModeWidgetOption

@Composable
fun ThemesTabContent(
    settingsViewModel: SettingsViewModel,
    onRequestStorageAccess: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val activeThemeUri by settingsViewModel.activeThemeUri.collectAsStateWithLifecycle()
    val driveModeThemePaths by settingsViewModel.driveModeThemePaths.collectAsStateWithLifecycle()

    /* Runtime debug panel — uncomment for wallpaper/theme diagnostics
    val themeActivating by settingsViewModel.themeActivationInProgress.collectAsStateWithLifecycle()
    val wallpaperRevision by settingsViewModel.mainScreenWallpaperEpoch.collectAsStateWithLifecycle()

    var runtimeJsonDebugText by remember { mutableStateOf("") }
    var runtimeJsonRefreshToken by remember { mutableIntStateOf(0) }

    suspend fun refreshRuntimeJsonDebugText() {
        runtimeJsonDebugText = settingsViewModel.formatActiveThemeRuntimeJsonDebugText(context)
    }

    LaunchedEffect(activeThemeUri, themeActivating, wallpaperRevision, runtimeJsonRefreshToken) {
        if (themeActivating) return@LaunchedEffect
        refreshRuntimeJsonDebugText()
    }
    */

    var showCreateDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearSharedIconsDialog by remember { mutableStateOf(false) }
    var showClearSharedHttpRequestIconsDialog by remember { mutableStateOf(false) }
    var showClearSharedTileBackgroundsDialog by remember { mutableStateOf(false) }
    var includeMainScreenPanels by remember { mutableStateOf(true) }
    var includeMainScreenWallpapers by remember { mutableStateOf(true) }
    var includeTileBackgrounds by remember { mutableStateOf(true) }
    var includeFloatingPanels by remember { mutableStateOf(true) }
    var includeAppIcons by remember { mutableStateOf(true) }
    var themeExportBaseName by remember { mutableStateOf("") }

    var pendingThemeApply by remember { mutableStateOf<PendingThemeApply?>(null) }
    var pendingDriveModeApply by remember { mutableStateOf<PendingDriveModeThemeApply?>(null) }

    var pendingDriveModeRawValue by rememberSaveable { mutableIntStateOf(-1) }
    var showReplaceDownloadsDialog by remember { mutableStateOf(false) }
    var pendingReplaceExport by remember {
        mutableStateOf<PendingThemeExport?>(null)
    }

    LaunchedEffect(showCreateDialog) {
        if (showCreateDialog) {
            themeExportBaseName = ThemeBundleExport.defaultThemeExportBaseName()
        }
    }

    fun showThemeExportResult(result: Result<SettingsViewModel.ThemeExportResult>) {
        if (result.isSuccess) {
            Toast.makeText(
                context,
                context.getString(R.string.toast_saved_to, result.getOrNull()?.savedPath.orEmpty()),
                Toast.LENGTH_LONG,
            ).show()
        } else {
            val msg = result.exceptionOrNull()?.message.orEmpty()
            Toast.makeText(
                context,
                context.getString(R.string.toast_theme_create_error, msg),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    fun exportThemeToDownloads(pending: PendingThemeExport, replaceExisting: Boolean = false) {
        if (!replaceExisting) {
            val dest = ThemeBundleExport.downloadsThemeExportFile(pending.baseName)
            if (dest.exists()) {
                pendingReplaceExport = pending
                showReplaceDownloadsDialog = true
                return
            }
        }
        val export: () -> Unit = {
            scope.launch {
                val result = settingsViewModel.exportThemeBundleToDownloads(
                    context,
                    pending.applyTargets,
                    pending.baseName,
                )
                withContext(Dispatchers.Main) { showThemeExportResult(result) }
            }
            Unit
        }
        if (onRequestStorageAccess != null) {
            onRequestStorageAccess(export)
        } else {
            export()
        }
    }

    fun launchThemeExport() {
        val applyTargets = buildApplyTargets(
            mainScreenPanels = includeMainScreenPanels,
            mainScreenWallpapers = includeMainScreenWallpapers,
            tileBackgrounds = includeTileBackgrounds,
            floatingPanels = includeFloatingPanels,
            appIcons = includeAppIcons,
        )
        if (applyTargets.isEmpty()) {
            Toast.makeText(context, R.string.themes_apply_targets_select_one, Toast.LENGTH_SHORT).show()
            return
        }
        val baseName = ThemeBundleExport.sanitizeThemeExportBaseName(themeExportBaseName)
        if (baseName == null) {
            Toast.makeText(context, R.string.themes_create_invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        val pending = PendingThemeExport(applyTargets = applyTargets, baseName = baseName)
        showCreateDialog = false
        exportThemeToDownloads(pending)
    }

    suspend fun beginThemeApply(uri: Uri) {
        val uriString = uri.toString()
        val bytes = ThemeFileResolver.openBytes(context, uriString)
            ?: run {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_theme_apply_error, "theme_file_not_readable"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return
            }
        val availableTargets = ThemeApply.peekAvailableApplyTargets(bytes).getOrElse { emptySet() }
        if (availableTargets.isEmpty()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_theme_apply_error, "theme_apply_targets_empty"),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        val cacheKey = ThemeCacheKeys.resolveUniqueManualCacheKey(context, uriString)
        val isFirstMaterialize = !ThemeMaterialization.isMaterialized(context, cacheKey)
        if (isFirstMaterialize) {
            withContext(Dispatchers.Main) {
                pendingThemeApply = PendingThemeApply(
                    uriString = uriString,
                    availableTargets = availableTargets,
                    selectedTargets = ThemeApplyTargetAvailability.defaultEnabled(availableTargets),
                )
            }
            return
        }
        val result = settingsViewModel.applyThemeFromUri(context, uriString)
        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                Toast.makeText(context, R.string.toast_theme_apply_ok, Toast.LENGTH_LONG).show()
            } else {
                val msg = result.exceptionOrNull()?.message.orEmpty()
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_theme_apply_error, msg),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    suspend fun beginDriveModeThemeAssign(uri: Uri, rawValue: Int) {
        val uriString = uri.toString()
        val bytes = ThemeFileResolver.openBytes(context, uriString)
            ?: run {
                pendingDriveModeRawValue = -1
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.toast_theme_drive_mode_error, "theme_file_not_readable"),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return
            }
        val availableTargets = ThemeApply.peekAvailableApplyTargets(bytes).getOrElse { emptySet() }
        if (availableTargets.isEmpty()) {
            pendingDriveModeRawValue = -1
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_theme_drive_mode_error, "theme_apply_targets_empty"),
                    Toast.LENGTH_LONG,
                ).show()
            }
            return
        }
        val cacheKey = ThemeCacheKeys.driveModeCacheKey(rawValue)
        val isFirstMaterialize = !ThemeMaterialization.isMaterialized(context, cacheKey)
        if (isFirstMaterialize) {
            withContext(Dispatchers.Main) {
                pendingDriveModeApply = PendingDriveModeThemeApply(
                    uriString = uriString,
                    rawValue = rawValue,
                    availableTargets = availableTargets,
                    selectedTargets = ThemeApplyTargetAvailability.defaultEnabled(availableTargets),
                )
            }
            return
        }
        val result = settingsViewModel.assignDriveModeTheme(context, rawValue, uriString)
        pendingDriveModeRawValue = -1
        withContext(Dispatchers.Main) {
            if (result.isFailure) {
                val msg = result.exceptionOrNull()?.message.orEmpty()
                Toast.makeText(
                    context,
                    context.getString(R.string.toast_theme_drive_mode_error, msg),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    val applyThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            withContext(Dispatchers.IO) {
                beginThemeApply(uri)
            }
        }
    }

    val driveModeThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        val rawValue = pendingDriveModeRawValue
        if (uri == null || rawValue < 0) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            withContext(Dispatchers.IO) {
                beginDriveModeThemeAssign(uri, rawValue)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.themes_tab_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        Button(
            onClick = rememberWrappedOnClick { showCreateDialog = true },
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.themes_create), style = MaterialTheme.typography.tboxButton)
        }

        SettingsTitle(stringResource(R.string.themes_active_title))
        val activePath = activeThemeUri.trim()
        val activeDisplay = when {
            activePath.isEmpty() -> stringResource(R.string.themes_active_none)
            ThemeCacheKeys.isLikelyCacheKey(activePath) -> {
                if (!ThemeMaterialization.isMaterialized(context, activePath)) {
                    stringResource(R.string.themes_active_cache_missing)
                } else {
                    ThemeMaterialization.readManifest(context, activePath)?.sourceDisplayName
                        ?: ThemeMaterialization.displayNameForCacheKey(activePath)
                }
            }
            !ThemeFileResolver.isAccessible(context, activePath) ->
                stringResource(R.string.themes_active_file_missing)
            else -> ThemeFileResolver.displayName(activePath)
        }
        Text(
            text = activeDisplay,
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (activePath.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = rememberWrappedOnClick {
                        settingsViewModel.clearActiveThemeSelection()
                        Toast.makeText(context, R.string.toast_theme_active_cleared, Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.themes_clear_active),
                        style = MaterialTheme.typography.tboxButton,
                        maxLines = 2,
                    )
                }
                Button(
                    onClick = rememberWrappedOnClick {
                        applyThemeLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        stringResource(R.string.themes_apply),
                        style = MaterialTheme.typography.tboxButton,
                        maxLines = 2,
                    )
                }
            }
        } else {
            Button(
                onClick = rememberWrappedOnClick {
                    applyThemeLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            ) {
                Text(stringResource(R.string.themes_apply), style = MaterialTheme.typography.tboxButton)
            }
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick { showClearCacheDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.themes_clear_cache), style = MaterialTheme.typography.tboxButton)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick { showClearSharedIconsDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.themes_clear_shared_icons),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { showClearSharedTileBackgroundsDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.themes_clear_shared_tile_backgrounds),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                )
            }
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick { showClearSharedHttpRequestIconsDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                stringResource(R.string.themes_clear_shared_http_request_icons),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
            )
        }

        Text(
            text = stringResource(R.string.themes_clear_shared_assets_hint),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        /* Runtime debug panel — uncomment for wallpaper/theme diagnostics
        SettingsTitle(stringResource(R.string.themes_runtime_debug_title))
        Text(
            text = stringResource(R.string.themes_runtime_debug_hint),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick { runtimeJsonRefreshToken += 1 },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.themes_runtime_debug_refresh), style = MaterialTheme.typography.tboxButton)
        }
        SelectionContainer {
            OutlinedTextField(
                value = runtimeJsonDebugText,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                textStyle = MaterialTheme.typography.tboxCaption,
                minLines = 6,
                maxLines = 24,
            )
        }
        */

        SettingsTitle(stringResource(R.string.themes_drive_mode_section))
        Text(
            text = stringResource(R.string.themes_drive_mode_hint),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        DRIVE_MODE_WIDGET_OPTIONS.forEach { option ->
            val path = driveModeThemePaths[option.rawValue].orEmpty()
            val label = resolveDriveModeWidgetOption(option.rawValue).label
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (path.isBlank()) {
                        stringResource(R.string.themes_drive_mode_not_assigned)
                    } else {
                        ThemeFileResolver.displayName(path)
                    },
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            pendingDriveModeRawValue = option.rawValue
                            driveModeThemeLauncher.launch(
                                arrayOf(
                                    "application/zip",
                                    "application/octet-stream",
                                    "application/*",
                                    "*/*",
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.themes_drive_mode_pick), style = MaterialTheme.typography.tboxButton)
                    }
                    if (path.isNotBlank()) {
                        OutlinedButton(
                            onClick = rememberWrappedOnClick {
                                settingsViewModel.clearDriveModeThemePath(option.rawValue)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.themes_drive_mode_clear), style = MaterialTheme.typography.tboxButton)
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_create_dialog_title)) },
            text = {
                Column {
                    AppAlertDialogText(stringResource(R.string.themes_create_dialog_hint))
                    OutlinedTextField(
                        value = themeExportBaseName,
                        textStyle = MaterialTheme.typography.tboxBody,
                        onValueChange = { themeExportBaseName = it },
                        label = {
                            Text(
                                stringResource(R.string.themes_create_file_name_label),
                                style = MaterialTheme.typography.tboxCaption,
                            )
                        },
                        supportingText = {
                            Text(
                                stringResource(R.string.themes_create_file_name_hint),
                                style = MaterialTheme.typography.tboxCaption,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    ThemeApplyTargetCheckboxList(
                        availableTargets = ThemeApplyTarget.entries.toSet(),
                        selectedTargets = buildApplyTargets(
                            mainScreenPanels = includeMainScreenPanels,
                            mainScreenWallpapers = includeMainScreenWallpapers,
                            tileBackgrounds = includeTileBackgrounds,
                            floatingPanels = includeFloatingPanels,
                            appIcons = includeAppIcons,
                        ),
                        onTargetCheckedChange = { target, checked ->
                            when (target) {
                                ThemeApplyTarget.MAIN_SCREEN_PANELS -> includeMainScreenPanels = checked
                                ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS -> includeMainScreenWallpapers = checked
                                ThemeApplyTarget.TILE_BACKGROUNDS -> includeTileBackgrounds = checked
                                ThemeApplyTarget.FLOATING_PANELS -> includeFloatingPanels = checked
                                ThemeApplyTarget.APP_ICONS -> includeAppIcons = checked
                            }
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick { launchThemeExport() },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_create_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { showCreateDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showReplaceDownloadsDialog) {
        val pending = pendingReplaceExport
        val fileName = pending?.baseName?.let {
            ThemeBundleExport.themeFileNameFromBaseName(it)
        }.orEmpty()
        AlertDialog(
            onDismissRequest = {
                showReplaceDownloadsDialog = false
                pendingReplaceExport = null
            },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_create_replace_downloads_title)) },
            text = {
                AppAlertDialogText(
                    stringResource(R.string.themes_create_replace_downloads_message, fileName),
                )
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        val replacePending = pendingReplaceExport ?: return@rememberWrappedOnClick
                        showReplaceDownloadsDialog = false
                        pendingReplaceExport = null
                        exportThemeToDownloads(replacePending, replaceExisting = true)
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_replace))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        showReplaceDownloadsDialog = false
                        pendingReplaceExport = null
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_clear_cache_dialog_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.themes_clear_cache_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        showClearCacheDialog = false
                        scope.launch {
                            settingsViewModel.clearThemeStorage(context)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    R.string.toast_theme_cache_cleared,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_clear_cache_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { showClearCacheDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearSharedIconsDialog) {
        AlertDialog(
            onDismissRequest = { showClearSharedIconsDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_clear_shared_icons_dialog_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.themes_clear_shared_icons_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        showClearSharedIconsDialog = false
                        scope.launch {
                            settingsViewModel.clearSharedLauncherAppIconsFolder()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    R.string.toast_theme_shared_icons_cleared,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_clear_shared_icons_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { showClearSharedIconsDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearSharedHttpRequestIconsDialog) {
        AlertDialog(
            onDismissRequest = { showClearSharedHttpRequestIconsDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_clear_shared_http_request_icons_dialog_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.themes_clear_shared_http_request_icons_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        showClearSharedHttpRequestIconsDialog = false
                        scope.launch {
                            settingsViewModel.clearSharedHttpRequestIconsFolder()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    R.string.toast_theme_shared_http_request_icons_cleared,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_clear_shared_http_request_icons_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { showClearSharedHttpRequestIconsDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showClearSharedTileBackgroundsDialog) {
        AlertDialog(
            onDismissRequest = { showClearSharedTileBackgroundsDialog = false },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_clear_shared_tile_backgrounds_dialog_title)) },
            text = {
                AppAlertDialogText(stringResource(R.string.themes_clear_shared_tile_backgrounds_dialog_message))
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        showClearSharedTileBackgroundsDialog = false
                        scope.launch {
                            settingsViewModel.clearSharedTileBackgroundsFolder()
                            withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    context,
                                    R.string.toast_theme_shared_tile_backgrounds_cleared,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_clear_shared_tile_backgrounds_confirm))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { showClearSharedTileBackgroundsDialog = false }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingThemeApply?.let { pending ->
        AlertDialog(
            onDismissRequest = { pendingThemeApply = null },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_apply_targets_dialog_title)) },
            text = {
                Column {
                    AppAlertDialogText(stringResource(R.string.themes_apply_targets_dialog_hint))
                    ThemeApplyTargetCheckboxList(
                        availableTargets = pending.availableTargets,
                        selectedTargets = pending.selectedTargets,
                        onTargetCheckedChange = { target, checked ->
                            pendingThemeApply = pending.copy(
                                selectedTargets = if (checked) {
                                    pending.selectedTargets + target
                                } else {
                                    pending.selectedTargets - target
                                },
                            )
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        val current = pendingThemeApply ?: return@rememberWrappedOnClick
                        if (current.selectedTargets.isEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.themes_apply_targets_select_one,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@rememberWrappedOnClick
                        }
                        pendingThemeApply = null
                        scope.launch {
                            val result = settingsViewModel.applyThemeFromUri(
                                context = context,
                                uriString = current.uriString,
                                applyTargets = current.selectedTargets,
                            )
                            withContext(Dispatchers.Main) {
                                if (result.isSuccess) {
                                    Toast.makeText(context, R.string.toast_theme_apply_ok, Toast.LENGTH_LONG).show()
                                } else {
                                    val msg = result.exceptionOrNull()?.message.orEmpty()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_theme_apply_error, msg),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_apply))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = rememberWrappedOnClick { pendingThemeApply = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }

    pendingDriveModeApply?.let { pending ->
        AlertDialog(
            onDismissRequest = {
                pendingDriveModeApply = null
                pendingDriveModeRawValue = -1
            },
            title = { AppAlertDialogTitle(stringResource(R.string.themes_apply_targets_dialog_title)) },
            text = {
                Column {
                    AppAlertDialogText(stringResource(R.string.themes_apply_targets_dialog_hint))
                    ThemeApplyTargetCheckboxList(
                        availableTargets = pending.availableTargets,
                        selectedTargets = pending.selectedTargets,
                        onTargetCheckedChange = { target, checked ->
                            pendingDriveModeApply = pending.copy(
                                selectedTargets = if (checked) {
                                    pending.selectedTargets + target
                                } else {
                                    pending.selectedTargets - target
                                },
                            )
                        },
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = rememberWrappedOnClick {
                        val current = pendingDriveModeApply ?: return@rememberWrappedOnClick
                        if (current.selectedTargets.isEmpty()) {
                            Toast.makeText(
                                context,
                                R.string.themes_apply_targets_select_one,
                                Toast.LENGTH_SHORT,
                            ).show()
                            return@rememberWrappedOnClick
                        }
                        pendingDriveModeApply = null
                        pendingDriveModeRawValue = -1
                        scope.launch {
                            val result = settingsViewModel.assignDriveModeTheme(
                                context = context,
                                rawValue = current.rawValue,
                                sourceUri = current.uriString,
                                applyTargets = current.selectedTargets,
                            )
                            withContext(Dispatchers.Main) {
                                if (result.isFailure) {
                                    val msg = result.exceptionOrNull()?.message.orEmpty()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_theme_drive_mode_error, msg),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.themes_drive_mode_pick))
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = rememberWrappedOnClick {
                        pendingDriveModeApply = null
                        pendingDriveModeRawValue = -1
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun buildApplyTargets(
    mainScreenPanels: Boolean,
    mainScreenWallpapers: Boolean,
    tileBackgrounds: Boolean,
    floatingPanels: Boolean,
    appIcons: Boolean,
): Set<ThemeApplyTarget> = buildSet {
    if (mainScreenPanels) add(ThemeApplyTarget.MAIN_SCREEN_PANELS)
    if (mainScreenWallpapers) add(ThemeApplyTarget.MAIN_SCREEN_WALLPAPERS)
    if (tileBackgrounds) add(ThemeApplyTarget.TILE_BACKGROUNDS)
    if (floatingPanels) add(ThemeApplyTarget.FLOATING_PANELS)
    if (appIcons) add(ThemeApplyTarget.APP_ICONS)
}

private data class PendingThemeExport(
    val applyTargets: Set<ThemeApplyTarget>,
    val baseName: String,
)

private data class PendingThemeApply(
    val uriString: String,
    val availableTargets: Set<ThemeApplyTarget>,
    val selectedTargets: Set<ThemeApplyTarget>,
)

private data class PendingDriveModeThemeApply(
    val uriString: String,
    val rawValue: Int,
    val availableTargets: Set<ThemeApplyTarget>,
    val selectedTargets: Set<ThemeApplyTarget>,
)
