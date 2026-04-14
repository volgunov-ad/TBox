package vad.dashing.tbox.ui

import android.net.Uri
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
import vad.dashing.tbox.DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_DARK
import vad.dashing.tbox.DEFAULT_WIDGET_TEXT_COLOR_LIGHT
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.SettingsViewModel
import kotlin.math.roundToInt
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.DARK_THEME_TEXT_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_1_INT
import vad.dashing.tbox.ui.theme.LIGHT_THEME_TEXT_COLOR_PRESET_2_INT

@Composable
fun MainScreenSettingsTab(
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
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
    val isMainScreenOpenOnBootEnabled by
        settingsViewModel.isMainScreenOpenOnBootEnabled.collectAsStateWithLifecycle()
    val isMainScreenWallpaperLightSet by
        settingsViewModel.isMainScreenWallpaperLightSet.collectAsStateWithLifecycle()
    val isMainScreenWallpaperDarkSet by
        settingsViewModel.isMainScreenWallpaperDarkSet.collectAsStateWithLifecycle()
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
    var cornerColorSegment by remember { mutableIntStateOf(0) }

    val pickWallpaperLight = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            settingsViewModel.setMainScreenWallpaperLight(uri)
        }
    }
    val pickWallpaperDark = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            settingsViewModel.setMainScreenWallpaperDark(uri)
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
        SettingsTitle(stringResource(R.string.settings_main_screen_wallpaper_title))
        Text(
            text = stringResource(R.string.settings_main_screen_wallpaper_light),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { pickWallpaperLight.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_pick), fontSize = 22.sp)
            }
            OutlinedButton(
                onClick = { settingsViewModel.setMainScreenWallpaperLight(null) },
                enabled = isMainScreenWallpaperLightSet,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_delete), fontSize = 22.sp)
            }
        }
        Text(
            text = stringResource(R.string.settings_main_screen_wallpaper_dark),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = { pickWallpaperDark.launch("image/*") },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.settings_main_screen_wallpaper_pick), fontSize = 22.sp)
            }
            OutlinedButton(
                onClick = { settingsViewModel.setMainScreenWallpaperDark(null) },
                enabled = isMainScreenWallpaperDarkSet,
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.action_delete), fontSize = 22.sp)
            }
        }
        SettingSwitch(
            isMainScreenWallpaperCrop,
            { crop -> settingsViewModel.saveMainScreenWallpaperCrop(crop) },
            stringResource(R.string.settings_main_screen_wallpaper_scale_crop_title),
            stringResource(R.string.settings_main_screen_wallpaper_scale_crop_desc),
            true
        )
        SettingsTitle(stringResource(R.string.settings_main_screen_corner_buttons_title))
        Text(
            text = stringResource(R.string.settings_main_screen_corner_buttons_size, mainScreenCornerButtonSizeDp),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.settings_main_screen_corner_buttons_size_hint),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Slider(
            value = mainScreenCornerButtonSizeDp.toFloat(),
            onValueChange = { v ->
                settingsViewModel.saveMainScreenCornerButtonSizeDp(v.roundToInt().coerceIn(1, 100))
            },
            valueRange = 1f..100f,
            steps = 98,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        WidgetColorThemeSegmentRow(
            selectedSegment = cornerColorSegment,
            onSegmentSelected = { cornerColorSegment = it },
            enabled = true
        )
        if (cornerColorSegment == 0) {
            WidgetTextColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_bg_light),
                colorValue = mainScreenCornerBtnBgLight,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonBackgroundLight(it) },
                presetColors = listOf(
                    LIGHT_THEME_BACKGROUND_COLOR_PRESET_1_INT,
                    LIGHT_THEME_BACKGROUND_COLOR_PRESET_2_INT
                )
            )
            WidgetTextColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_icon_light),
                colorValue = mainScreenCornerBtnIconLight,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonIconLight(it) },
                presetColors = listOf(
                    LIGHT_THEME_TEXT_COLOR_PRESET_1_INT,
                    LIGHT_THEME_TEXT_COLOR_PRESET_2_INT
                )
            )
        } else {
            WidgetTextColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_bg_dark),
                colorValue = mainScreenCornerBtnBgDark,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonBackgroundDark(it) },
                presetColors = listOf(
                    DARK_THEME_BACKGROUND_COLOR_PRESET_1_INT,
                    DARK_THEME_BACKGROUND_COLOR_PRESET_2_INT
                )
            )
            WidgetTextColorSetting(
                title = stringResource(R.string.settings_main_screen_corner_buttons_icon_dark),
                colorValue = mainScreenCornerBtnIconDark,
                enabled = true,
                onColorChange = { settingsViewModel.saveMainScreenCornerButtonIconDark(it) },
                presetColors = listOf(
                    DARK_THEME_TEXT_COLOR_PRESET_1_INT,
                    DARK_THEME_TEXT_COLOR_PRESET_2_INT
                )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    settingsViewModel.saveMainScreenCornerButtonSizeDp(32)
                    settingsViewModel.saveMainScreenCornerButtonBackgroundLight(
                        DEFAULT_WIDGET_BACKGROUND_COLOR_LIGHT_MAIN
                    )
                    settingsViewModel.saveMainScreenCornerButtonBackgroundDark(
                        DEFAULT_WIDGET_BACKGROUND_COLOR_DARK_MAIN
                    )
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
                    fontSize = 20.sp,
                    maxLines = 2
                )
            }
        }
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
        } else {
            Button(
                onClick = {
                    settingsViewModel.addMainScreenDashboard(newMainPanelDefaultName)
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(stringResource(R.string.action_add), fontSize = 22.sp)
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
        MainScreenPanelRelativeLayoutSettings(
            settingsViewModel = settingsViewModel,
            modifier = Modifier.padding(top = 8.dp),
            enabled = hasMainScreenPanels
        )
    }
}
