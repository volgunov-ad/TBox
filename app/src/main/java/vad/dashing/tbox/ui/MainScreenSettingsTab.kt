package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.TboxTextStyles
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_DARK
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_LIGHT
import vad.dashing.tbox.MIN_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MAX_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MIN_PANEL_LAYOUT_SNAP_DP
import vad.dashing.tbox.MAX_PANEL_LAYOUT_SNAP_DP
import vad.dashing.tbox.MainScreenWindowModeGeometry
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.displayWallpaperFolderSummary
import vad.dashing.tbox.hasManageAllFilesAccess
import vad.dashing.tbox.normalizeFilesystemWallpaperFolderPath
import java.io.File
import kotlin.math.roundToInt
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT

@Composable
fun MainScreenSettingsTab(
    settingsViewModel: SettingsViewModel,
    currentTheme: Int,
    onRequestWallpaperStorageAccess: ((() -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val mainScreenPanelsList by settingsViewModel.mainScreenDashboards.collectAsStateWithLifecycle()
    val hasMainScreenPanels = mainScreenPanelsList.isNotEmpty()
    val activeMainScreenPanelId by settingsViewModel.activeMainScreenPanelId.collectAsStateWithLifecycle()
    val mainScreenPanelDeleteInProgressId by
        settingsViewModel.mainScreenPanelDeleteInProgressId.collectAsStateWithLifecycle()
    val isMainScreenPanelEnabled by settingsViewModel.isMainScreenPanelEnabled.collectAsStateWithLifecycle()
    val isMainScreenPanelClickAction by
        settingsViewModel.isMainScreenPanelClickAction.collectAsStateWithLifecycle()
    val isMainScreenPanelShowTboxDisconnectIndicator by
        settingsViewModel.isMainScreenPanelShowTboxDisconnectIndicator.collectAsStateWithLifecycle()
    val mainScreenPanelRows by settingsViewModel.mainScreenPanelRows.collectAsStateWithLifecycle()
    val mainScreenPanelCols by settingsViewModel.mainScreenPanelCols.collectAsStateWithLifecycle()
    val mainScreenPanelGridSpacingDp by
        settingsViewModel.mainScreenPanelGridSpacingDp.collectAsStateWithLifecycle()
    val mainScreenPanelsLayoutSnapDp by
        settingsViewModel.mainScreenPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val mainScreenPanelsLayoutSnapEnabled by
        settingsViewModel.mainScreenPanelsLayoutSnapEnabled.collectAsStateWithLifecycle()
    val mainScreenShowLayoutGrid by
        settingsViewModel.mainScreenShowLayoutGrid.collectAsStateWithLifecycle()
    val isMainScreenOpenOnBootEnabled by
        settingsViewModel.isMainScreenOpenOnBootEnabled.collectAsStateWithLifecycle()
    val mainScreenOpenOnBootDelaySeconds by
        settingsViewModel.mainScreenOpenOnBootDelaySeconds.collectAsStateWithLifecycle()
    val mainScreenWindowModeGeometry by
        settingsViewModel.mainScreenWindowModeGeometry.collectAsStateWithLifecycle()
    val mainScreenWallpaperLightFolderUri by
        settingsViewModel.mainScreenWallpaperLightFolderUri.collectAsStateWithLifecycle()
    val mainScreenWallpaperDarkFolderUri by
        settingsViewModel.mainScreenWallpaperDarkFolderUri.collectAsStateWithLifecycle()
    val isMainScreenWallpaperCrop by
        settingsViewModel.isMainScreenWallpaperCrop.collectAsStateWithLifecycle()
    val mainScreenCornerButtonSizeDp by
        settingsViewModel.mainScreenCornerButtonSizeDp.collectAsStateWithLifecycle()
    val mainScreenCornerBtnBgLight by
        settingsViewModel.mainScreenCornerButtonBackgroundLight.collectAsStateWithLifecycle()
    val mainScreenCornerBtnBgDark by
        settingsViewModel.mainScreenCornerButtonBackgroundDark.collectAsStateWithLifecycle()
    val mainScreenCornerBtnIconLight by
        settingsViewModel.mainScreenCornerButtonIconLight.collectAsStateWithLifecycle()
    val mainScreenCornerBtnIconDark by
        settingsViewModel.mainScreenCornerButtonIconDark.collectAsStateWithLifecycle()
    val mainScreenPageCount by settingsViewModel.mainScreenPageCount.collectAsStateWithLifecycle()
    val mainScreenPanelPageNumber by settingsViewModel.mainScreenPanelPageNumber.collectAsStateWithLifecycle()
    val mainScreenCanvasBgLight by
        settingsViewModel.mainScreenCanvasBackgroundLight.collectAsStateWithLifecycle()
    val mainScreenCanvasBgDark by
        settingsViewModel.mainScreenCanvasBackgroundDark.collectAsStateWithLifecycle()
    val widgetColorPresetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()
    var cornerColorSegment by remember { mutableIntStateOf(0) }
    var mainScreenBgSegment by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        val segment = colorThemeSegmentFor(currentTheme)
        cornerColorSegment = segment
        mainScreenBgSegment = segment
    }
    var lightFolderPathInput by remember { mutableStateOf("") }
    var darkFolderPathInput by remember { mutableStateOf("") }
    var showMainScreenPanelOrderDialog by remember { mutableStateOf(false) }

    LaunchedEffect(mainScreenWallpaperLightFolderUri) {
        val u = mainScreenWallpaperLightFolderUri
        lightFolderPathInput = if (u.startsWith("file://", ignoreCase = true)) {
            runCatching { File(Uri.parse(u).path ?: "").absolutePath }.getOrDefault("")
        } else {
            ""
        }
    }
    LaunchedEffect(mainScreenWallpaperDarkFolderUri) {
        val u = mainScreenWallpaperDarkFolderUri
        darkFolderPathInput = if (u.startsWith("file://", ignoreCase = true)) {
            runCatching { File(Uri.parse(u).path ?: "").absolutePath }.getOrDefault("")
        } else {
            ""
        }
    }

    var allFilesAccess by remember { mutableStateOf(hasManageAllFilesAccess()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        allFilesAccess = hasManageAllFilesAccess()
    }
    fun openManageAllFilesSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    val pickWallpaperLightFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsViewModel.saveMainScreenWallpaperLightFolderUri(uri.toString())
        }
    }
    val pickWallpaperDarkFolder = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsViewModel.saveMainScreenWallpaperDarkFolderUri(uri.toString())
        }
    }
    val pickWallpaperLightImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsViewModel.applyMainScreenWallpaperFromPickedImage(context, uri, forLightTheme = true)
        }
    }
    val pickWallpaperDarkImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            settingsViewModel.applyMainScreenWallpaperFromPickedImage(context, uri, forLightTheme = false)
        }
    }
    val openDocumentTreeIntent = remember {
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
    }
    val canOpenDocumentTree = remember {
        openDocumentTreeIntent.resolveActivity(context.packageManager) != null
    }
    fun launchLightWallpaperPicker() {
        if (canOpenDocumentTree) {
            pickWallpaperLightFolder.launch(null)
        } else {
            val getContent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            if (getContent.resolveActivity(context.packageManager) != null) {
                val open = { pickWallpaperLightImage.launch("image/*") }
                if (onRequestWallpaperStorageAccess != null) {
                    onRequestWallpaperStorageAccess(open)
                } else {
                    open()
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_main_screen_wallpaper_no_picker),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    fun launchDarkWallpaperPicker() {
        if (canOpenDocumentTree) {
            pickWallpaperDarkFolder.launch(null)
        } else {
            val getContent = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            if (getContent.resolveActivity(context.packageManager) != null) {
                val open = { pickWallpaperDarkImage.launch("image/*") }
                if (onRequestWallpaperStorageAccess != null) {
                    onRequestWallpaperStorageAccess(open)
                } else {
                    open()
                }
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.settings_main_screen_wallpaper_no_picker),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    val newMainPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        SettingSwitch(
            isMainScreenOpenOnBootEnabled,
            { enabled ->
                settingsViewModel.saveMainScreenOpenOnBoot(enabled)
            },
            stringResource(R.string.settings_main_screen_open_on_boot_title),
            stringResource(R.string.settings_main_screen_open_on_boot_desc),
            true
        )
        SettingInt(
            value = mainScreenOpenOnBootDelaySeconds,
            onValueChange = { value ->
                settingsViewModel.saveMainScreenOpenOnBootDelaySeconds(value)
            },
            text = stringResource(R.string.settings_main_screen_open_on_boot_delay_title),
            description = stringResource(R.string.settings_main_screen_open_on_boot_delay_desc),
            minValue = SettingsManager.MIN_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS,
            maxValue = SettingsManager.MAX_MAIN_SCREEN_OPEN_ON_BOOT_DELAY_SECONDS
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_window_mode_title))
        Text(
            text = stringResource(R.string.settings_main_screen_window_mode_desc),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        val mainScreenWindowModeAutoGeometry by
            settingsViewModel.mainScreenWindowModeAutoGeometry.collectAsStateWithLifecycle()
        SettingSwitch(
            mainScreenWindowModeAutoGeometry,
            { enabled -> settingsViewModel.saveMainScreenWindowModeAutoGeometry(enabled) },
            stringResource(R.string.settings_main_screen_window_mode_auto_title),
            stringResource(R.string.settings_main_screen_window_mode_auto_desc),
            true,
        )
        if (!mainScreenWindowModeAutoGeometry) {
            val displayMetrics = remember(context) {
                val metrics = DisplayMetrics()
                @Suppress("DEPRECATION")
                (context.getSystemService(WindowManager::class.java))
                    .defaultDisplay.getRealMetrics(metrics)
                metrics
            }
            val windowGeom = mainScreenWindowModeGeometry
                ?: MainScreenWindowModeGeometry.defaultForDisplay(
                    displayMetrics.widthPixels,
                    displayMetrics.heightPixels,
                )
            var draftX by remember(windowGeom.startX) { mutableIntStateOf(windowGeom.startX) }
            var draftY by remember(windowGeom.startY) { mutableIntStateOf(windowGeom.startY) }
            var draftW by remember(windowGeom.width) { mutableIntStateOf(windowGeom.width) }
            var draftH by remember(windowGeom.height) { mutableIntStateOf(windowGeom.height) }
            fun persistGeometry(x: Int, y: Int, w: Int, h: Int) {
                draftX = x
                draftY = y
                draftW = w
                draftH = h
                settingsViewModel.saveMainScreenWindowModeGeometry(
                    MainScreenWindowModeGeometry(x, y, w, h),
                )
            }
            Column(modifier = Modifier.padding(top = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_main_screen_window_mode_width),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        IntInputField(
                            value = draftW,
                            onValueChange = { newValue ->
                                if (newValue >= MainScreenWindowModeGeometry.MIN_SIZE) {
                                    persistGeometry(draftX, draftY, newValue, draftH)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_main_screen_window_mode_height),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        IntInputField(
                            value = draftH,
                            onValueChange = { newValue ->
                                if (newValue >= MainScreenWindowModeGeometry.MIN_SIZE) {
                                    persistGeometry(draftX, draftY, draftW, newValue)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_main_screen_window_mode_x),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        IntInputField(
                            value = draftX,
                            onValueChange = { newValue ->
                                if (newValue >= 0) {
                                    persistGeometry(newValue, draftY, draftW, draftH)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_main_screen_window_mode_y),
                            style = MaterialTheme.typography.tboxTitle,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                        IntInputField(
                            value = draftY,
                            onValueChange = { newValue ->
                                if (newValue >= 0) {
                                    persistGeometry(draftX, newValue, draftW, draftH)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_page_count_title))
        SettingDropdownGeneric(
            selectedValue = mainScreenPageCount,
            onValueChange = { settingsViewModel.saveMainScreenPageCount(it) },
            text = "",
            description = stringResource(R.string.settings_main_screen_page_count_hint),
            enabled = true,
            options = SettingsManager.MAIN_SCREEN_PAGE_COUNT_OPTIONS,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_wallpaper_title))
        Text(
            text = stringResource(R.string.settings_main_screen_wallpaper_folder_hint),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !allFilesAccess) {
            Text(
                text = stringResource(R.string.settings_main_screen_wallpaper_all_files_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick { openManageAllFilesSettings() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_open_all_files_settings), style = MaterialTheme.typography.tboxBody)
            }
        }
        if (allFilesAccess) {
            Text(
                text = stringResource(R.string.settings_main_screen_wallpaper_path_mode_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Text(
            text = stringResource(R.string.settings_main_screen_wallpaper_light),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        if (mainScreenWallpaperLightFolderUri.isNotBlank()) {
            Text(
                text = stringResource(
                    R.string.settings_main_screen_wallpaper_current_path,
                    displayWallpaperFolderSummary(mainScreenWallpaperLightFolderUri)
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick { launchLightWallpaperPicker() },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_pick_source), style = MaterialTheme.typography.tboxButton)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { settingsViewModel.saveMainScreenWallpaperLightFolderUri(null) },
                enabled = mainScreenWallpaperLightFolderUri.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.tboxButton)
            }
        }
        if (allFilesAccess) {
            OutlinedTextField(
                value = lightFolderPathInput,
                onValueChange = { lightFolderPathInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                label = {
                    Text(
                        stringResource(R.string.settings_main_screen_wallpaper_path_label),
                        style = MaterialTheme.typography.tboxCaption,
                    )
                },
                singleLine = true,
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    if (normalizeFilesystemWallpaperFolderPath(lightFolderPathInput) != null) {
                        settingsViewModel.applyMainScreenWallpaperFilesystemFolderPath(
                            lightFolderPathInput,
                            forLightTheme = true
                        )
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_main_screen_wallpaper_path_invalid),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_apply_path), style = MaterialTheme.typography.tboxBody)
            }
        }
        Text(
            text = stringResource(R.string.settings_main_screen_wallpaper_dark),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        if (mainScreenWallpaperDarkFolderUri.isNotBlank()) {
            Text(
                text = stringResource(
                    R.string.settings_main_screen_wallpaper_current_path,
                    displayWallpaperFolderSummary(mainScreenWallpaperDarkFolderUri)
                ),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick { launchDarkWallpaperPicker() },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_pick_source), style = MaterialTheme.typography.tboxButton)
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick { settingsViewModel.saveMainScreenWallpaperDarkFolderUri(null) },
                enabled = mainScreenWallpaperDarkFolderUri.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_delete), style = MaterialTheme.typography.tboxButton)
            }
        }
        if (allFilesAccess) {
            OutlinedTextField(
                value = darkFolderPathInput,
                onValueChange = { darkFolderPathInput = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                label = {
                    Text(
                        stringResource(R.string.settings_main_screen_wallpaper_path_label),
                        style = MaterialTheme.typography.tboxCaption,
                    )
                },
                singleLine = true,
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    if (normalizeFilesystemWallpaperFolderPath(darkFolderPathInput) != null) {
                        settingsViewModel.applyMainScreenWallpaperFilesystemFolderPath(
                            darkFolderPathInput,
                            forLightTheme = false
                        )
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_main_screen_wallpaper_path_invalid),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_apply_path), style = MaterialTheme.typography.tboxBody)
            }
        }
        SettingSwitch(
            isMainScreenWallpaperCrop,
            { crop -> settingsViewModel.saveMainScreenWallpaperCrop(crop) },
            stringResource(R.string.settings_main_screen_wallpaper_scale_crop_title),
            stringResource(R.string.settings_main_screen_wallpaper_scale_crop_desc),
            true
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_canvas_bg_title))
        Text(
            text = stringResource(R.string.settings_main_screen_canvas_bg_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        WidgetColorThemeSegmentRow(
            selectedSegment = mainScreenBgSegment,
            onSegmentSelected = { mainScreenBgSegment = it },
            enabled = true
        )
        if (mainScreenBgSegment == 0) {
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_canvas_bg_light),
                colorValue = mainScreenCanvasBgLight,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCanvasBackgroundLight(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
        } else {
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_canvas_bg_dark),
                colorValue = mainScreenCanvasBgDark,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCanvasBackgroundDark(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
        }
        OutlinedButton(
            onClick = rememberWrappedOnClick {
                settingsViewModel.saveMainScreenCanvasBackgroundLight(LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT)
                settingsViewModel.saveMainScreenCanvasBackgroundDark(DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Text(stringResource(R.string.settings_main_screen_canvas_bg_reset), style = MaterialTheme.typography.tboxBody)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_corner_buttons_title))
        Text(
            text = stringResource(R.string.settings_main_screen_corner_buttons_size, mainScreenCornerButtonSizeDp),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.settings_main_screen_corner_buttons_size_hint),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Slider(
            value = mainScreenCornerButtonSizeDp.toFloat(),
            onValueChange = { v ->
                settingsViewModel.saveMainScreenCornerButtonSizeDp(v.roundToInt().coerceIn(10, 100))
            },
            valueRange = 10f..100f,
            steps = 89,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        WidgetColorThemeSegmentRow(
            selectedSegment = cornerColorSegment,
            onSegmentSelected = { cornerColorSegment = it },
            enabled = true
        )
        if (cornerColorSegment == 0) {
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_bg_light),
                colorValue = mainScreenCornerBtnBgLight,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonBackgroundLight(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_icon_light),
                colorValue = mainScreenCornerBtnIconLight,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonIconLight(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
        } else {
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_bg_dark),
                colorValue = mainScreenCornerBtnBgDark,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonBackgroundDark(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
            WidgetColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_icon_dark),
                colorValue = mainScreenCornerBtnIconDark,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonIconDark(it) },
                presetSlots = widgetColorPresetSlots,
                onPresetSlotColorSave = settingsViewModel::saveWidgetColorPresetSlot,
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.saveMainScreenCornerButtonSizeDp(32)
                    settingsViewModel.saveMainScreenCornerButtonBackgroundLight(0x00000000)
                    settingsViewModel.saveMainScreenCornerButtonBackgroundDark(0x00000000)
                    settingsViewModel.saveMainScreenCornerButtonIconLight(
                        DEFAULT_WIDGET_TEXT_COLOR_LIGHT
                    )
                    settingsViewModel.saveMainScreenCornerButtonIconDark(
                        DEFAULT_WIDGET_TEXT_COLOR_DARK
                    )
                },
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    stringResource(R.string.settings_main_screen_corner_buttons_reset),
                    style = MaterialTheme.typography.tboxBody,
                    maxLines = 2
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_main_screen_panels_title))
        if (hasMainScreenPanels) {
            MainScreenPanelEditor(
                panels = mainScreenPanelsList,
                selectedPanelId = activeMainScreenPanelId,
                onSelectPanelId = { panelId ->
                    settingsViewModel.saveSelectedMainScreenPanelId(panelId)
                },
                onRenamePanel = { panelId, name ->
                    settingsViewModel.saveMainScreenPanelName(panelId, name)
                },
                onAddPanel = {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
                },
                onDeletePanel = { panelId ->
                    settingsViewModel.deleteMainScreenPanelFromSettings(panelId)
                },
                deleteInProgressPanelId = mainScreenPanelDeleteInProgressId,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick { showMainScreenPanelOrderDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_panels_order_button),
                    style = MaterialTheme.typography.tboxButton,
                )
            }
        } else {
            Button(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.action_add), style = MaterialTheme.typography.tboxButton)
            }
        }
        SettingSwitch(
            isMainScreenPanelEnabled,
            { enabled ->
                settingsViewModel.saveMainScreenPanelSetting(enabled)
            },
            stringResource(R.string.settings_show_main_screen_panel_title),
            "",
            hasMainScreenPanels
        )
        SettingSwitch(
            isMainScreenPanelClickAction,
            { enabled ->
                settingsViewModel.saveMainScreenPanelClickAction(enabled)
            },
            stringResource(R.string.settings_open_app_on_main_screen_panel_click_title),
            "",
            hasMainScreenPanels
        )
        SettingSwitch(
            isMainScreenPanelShowTboxDisconnectIndicator,
            { enabled ->
                settingsViewModel.saveMainScreenPanelShowTboxDisconnectIndicator(enabled)
            },
            stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
            "",
            hasMainScreenPanels
        )
        SettingDropdownGeneric(
            mainScreenPanelRows,
            { rows ->
                settingsViewModel.saveMainScreenPanelRows(rows)
            },
            stringResource(R.string.settings_main_screen_panel_rows_title),
            "",
            hasMainScreenPanels,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingDropdownGeneric(
            mainScreenPanelCols,
            { cols ->
                settingsViewModel.saveMainScreenPanelCols(cols)
            },
            stringResource(R.string.settings_main_screen_panel_cols_title),
            "",
            hasMainScreenPanels,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS
        )
        SettingSliderInt(
            value = mainScreenPanelGridSpacingDp,
            onValueChange = { settingsViewModel.saveMainScreenPanelGridSpacingDp(it) },
            text = stringResource(
                R.string.settings_panel_grid_spacing_title,
                mainScreenPanelGridSpacingDp,
            ),
            description = stringResource(R.string.settings_panel_grid_spacing_desc),
            minValue = MIN_PANEL_GRID_SPACING_DP,
            maxValue = MAX_PANEL_GRID_SPACING_DP,
            enabled = hasMainScreenPanels,
        )
        SettingDropdownGeneric(
            mainScreenPanelPageNumber,
            { page -> settingsViewModel.saveMainScreenPanelPageNumber(page) },
            stringResource(R.string.settings_main_screen_panel_page_title),
            stringResource(R.string.settings_main_screen_panel_page_desc),
            hasMainScreenPanels,
            (1..mainScreenPageCount).toList(),
        )
        MainScreenPanelRelativeLayoutSettings(
            settingsViewModel = settingsViewModel,
            modifier = Modifier.padding(top = 8.dp),
            enabled = hasMainScreenPanels
        )
        SettingSliderInt(
            value = mainScreenPanelsLayoutSnapDp,
            onValueChange = { settingsViewModel.saveMainScreenPanelsLayoutSnapDp(it) },
            text = stringResource(
                R.string.settings_main_screen_layout_snap_step_title,
                mainScreenPanelsLayoutSnapDp,
            ),
            description = stringResource(R.string.settings_main_screen_layout_snap_step_desc),
            minValue = MIN_PANEL_LAYOUT_SNAP_DP,
            maxValue = MAX_PANEL_LAYOUT_SNAP_DP,
        )
        SettingSwitch(
            mainScreenPanelsLayoutSnapEnabled,
            { enabled -> settingsViewModel.saveMainScreenPanelsLayoutSnapEnabled(enabled) },
            stringResource(R.string.settings_main_screen_layout_snap_enabled_title),
            stringResource(R.string.settings_main_screen_layout_snap_enabled_desc),
            true,
        )
        SettingSwitch(
            mainScreenShowLayoutGrid,
            { enabled -> settingsViewModel.saveMainScreenShowLayoutGrid(enabled) },
            stringResource(R.string.settings_main_screen_show_layout_grid_title),
            stringResource(R.string.settings_main_screen_show_layout_grid_desc),
            true,
        )
        if (showMainScreenPanelOrderDialog) {
            PanelOrderConfigDialog(
                visible = true,
                title = stringResource(R.string.settings_main_screen_panels_order_dialog_title),
                hint = stringResource(R.string.settings_panels_order_dialog_hint),
                items = mainScreenPanelsList.map { panel ->
                    PanelOrderItem(
                        id = panel.id,
                        name = panel.name.ifBlank { panel.id },
                    )
                },
                onDismiss = { showMainScreenPanelOrderDialog = false },
                onSave = { orderedIds ->
                    val byId = mainScreenPanelsList.associateBy { it.id }
                    val reordered = buildList {
                        orderedIds.forEach { panelId ->
                            byId[panelId]?.let { add(it) }
                        }
                        mainScreenPanelsList.forEach { panel ->
                            if (orderedIds.none { it == panel.id }) add(panel)
                        }
                    }
                    settingsViewModel.saveMainScreenDashboards(reordered)
                },
            )
        }
    }
}
