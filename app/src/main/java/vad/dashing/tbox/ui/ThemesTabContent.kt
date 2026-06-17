package vad.dashing.tbox.ui

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ThemeBundleExport
import vad.dashing.tbox.ThemeCacheKeys
import vad.dashing.tbox.ThemeFileResolver
import vad.dashing.tbox.ThemeMaterialization
import vad.dashing.tbox.ThemeSection
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

    var showCreateDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showClearSharedIconsDialog by remember { mutableStateOf(false) }
    var showClearSharedTileBackgroundsDialog by remember { mutableStateOf(false) }
    var includeMainScreen by remember { mutableStateOf(true) }
    var includeFloatingPanels by remember { mutableStateOf(true) }
    var includeAppIcons by remember { mutableStateOf(true) }
    var themeExportBaseName by remember { mutableStateOf("") }

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
                    pending.sections,
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
        val sections = buildThemeSections(includeMainScreen, includeFloatingPanels, includeAppIcons)
        if (sections.isEmpty()) {
            Toast.makeText(context, R.string.themes_create_select_section, Toast.LENGTH_SHORT).show()
            return
        }
        val baseName = ThemeBundleExport.sanitizeThemeExportBaseName(themeExportBaseName)
        if (baseName == null) {
            Toast.makeText(context, R.string.themes_create_invalid_name, Toast.LENGTH_SHORT).show()
            return
        }
        val pending = PendingThemeExport(sections = sections, baseName = baseName)
        showCreateDialog = false
        exportThemeToDownloads(pending)
    }

    val applyThemeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                settingsViewModel.applyThemeFromUri(context, uri.toString())
            }
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
            val result = settingsViewModel.assignDriveModeTheme(context, rawValue, uri.toString())
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
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.themes_tab_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        OutlinedButton(
            onClick = rememberWrappedOnClick { showCreateDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.themes_create), fontSize = 22.sp)
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
        Text(text = activeDisplay, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))

        if (activePath.isNotEmpty()) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.clearActiveThemeSelection()
                    Toast.makeText(context, R.string.toast_theme_active_cleared, Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.themes_clear_active), fontSize = 22.sp)
            }
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick {
                applyThemeLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*"))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(stringResource(R.string.themes_apply), fontSize = 22.sp)
        }

        OutlinedButton(
            onClick = rememberWrappedOnClick { showClearCacheDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.themes_clear_cache), fontSize = 22.sp)
        }

        Text(
            text = stringResource(R.string.themes_clear_shared_assets_hint),
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

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
                    fontSize = 18.sp,
                    maxLines = 2,
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { showClearSharedTileBackgroundsDialog = true },
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    stringResource(R.string.themes_clear_shared_tile_backgrounds),
                    fontSize = 18.sp,
                    maxLines = 2,
                )
            }
        }

        SettingsTitle(stringResource(R.string.themes_drive_mode_section))
        Text(
            text = stringResource(R.string.themes_drive_mode_hint),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        DRIVE_MODE_WIDGET_OPTIONS.forEach { option ->
            val path = driveModeThemePaths[option.rawValue].orEmpty()
            val label = resolveDriveModeWidgetOption(option.rawValue).label
            Column(modifier = Modifier.padding(bottom = 12.dp)) {
                Text(text = label, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                Text(
                    text = if (path.isBlank()) {
                        stringResource(R.string.themes_drive_mode_not_assigned)
                    } else {
                        ThemeFileResolver.displayName(path)
                    },
                    fontSize = 18.sp,
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
                        Text(stringResource(R.string.themes_drive_mode_pick), fontSize = 18.sp)
                    }
                    if (path.isNotBlank()) {
                        OutlinedButton(
                            onClick = rememberWrappedOnClick {
                                settingsViewModel.clearDriveModeThemePath(option.rawValue)
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.themes_drive_mode_clear), fontSize = 18.sp)
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
                        textStyle = TextStyle(fontSize = 20.sp),
                        onValueChange = { themeExportBaseName = it },
                        label = {
                            Text(stringResource(R.string.themes_create_file_name_label),
                                style = TextStyle(fontSize = 18.sp)
                        )},
                        supportingText = {
                            Text(stringResource(R.string.themes_create_file_name_hint),
                                style = TextStyle(fontSize = 18.sp))
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    )
                    ThemeSectionCheckboxRow(
                        checked = includeMainScreen,
                        onCheckedChange = { includeMainScreen = it },
                        label = stringResource(R.string.themes_create_section_main_screen),
                    )
                    ThemeSectionCheckboxRow(
                        checked = includeFloatingPanels,
                        onCheckedChange = { includeFloatingPanels = it },
                        label = stringResource(R.string.themes_create_section_floating_panels),
                    )
                    ThemeSectionCheckboxRow(
                        checked = includeAppIcons,
                        onCheckedChange = { includeAppIcons = it },
                        label = stringResource(R.string.themes_create_section_app_icons),
                    )
                    Text(
                        text = stringResource(R.string.themes_create_section_app_icons_hint),
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 48.dp, top = 4.dp),
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
}

@Composable
private fun ThemeSectionCheckboxRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(text = label, fontSize = 20.sp)
    }
}

private fun buildThemeSections(
    mainScreen: Boolean,
    floatingPanels: Boolean,
    appIcons: Boolean,
): Set<ThemeSection> = buildSet {
    if (mainScreen) add(ThemeSection.MAIN_SCREEN)
    if (floatingPanels) add(ThemeSection.FLOATING_PANELS)
    if (appIcons) add(ThemeSection.APP_ICONS)
}

private data class PendingThemeExport(
    val sections: Set<ThemeSection>,
    val baseName: String,
)
