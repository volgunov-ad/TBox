package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MAX
import vad.dashing.tbox.ACC_CRUISE_STEP_INTERVAL_MS_MIN
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MAX
import vad.dashing.tbox.ACC_CRUISE_TARGET_KMH_MIN
import vad.dashing.tbox.APP_LAUNCHER_WIDGET_DATA_KEY
import vad.dashing.tbox.AUTOMATION_TRIGGER_WIDGET_DATA_KEY
import vad.dashing.tbox.AVG_FUEL_CONSUMPTION_SOURCE_CURRENT_TRIP
import vad.dashing.tbox.AVG_FUEL_CONSUMPTION_SOURCE_DAILY_TRIP
import vad.dashing.tbox.AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL
import vad.dashing.tbox.BuildConfig
import vad.dashing.tbox.DRIVE_MODE_WIDGET_DATA_KEY
import vad.dashing.tbox.DRIVE_MODE_WIDGET_OPTIONS
import vad.dashing.tbox.EspRelayWidgetMode
import vad.dashing.tbox.FUEL_LITERS_DISPLAY_REMAINING
import vad.dashing.tbox.FUEL_LITERS_DISPLAY_TO_FULL
import vad.dashing.tbox.HTTP_REQUEST_WIDGET_DATA_KEY
import vad.dashing.tbox.HVAC_TEMP_WIDGET_STEP_HALF_TENTHS
import vad.dashing.tbox.HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS
import vad.dashing.tbox.MAPS_CAM_DISTANCE_STEP_M
import vad.dashing.tbox.MAX_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.MAX_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.MIN_MAPS_CAM_LOOKAHEAD_M
import vad.dashing.tbox.MIN_MAPS_CAM_RADAR_HOLD_M
import vad.dashing.tbox.MUSIC_COVER_WIDGET_DATA_KEY
import vad.dashing.tbox.MusicWidgetAlbumArtDisplay
import vad.dashing.tbox.MusicWidgetControlsDisplay
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_ARROWS
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_CURRENT
import vad.dashing.tbox.TRIP_WIDGET_SOURCE_PERSISTENT
import vad.dashing.tbox.WIDGET_TITLE_POSITION_BOTTOM
import vad.dashing.tbox.WIDGET_TITLE_POSITION_TOP
import vad.dashing.tbox.WidgetsRepository
import vad.dashing.tbox.isAccCruiseWidgetDataKey
import vad.dashing.tbox.isActiveTripWidgetDataKey
import vad.dashing.tbox.isAverageFuelConsumptionWidgetDataKey
import vad.dashing.tbox.isDriveModeCycleWidgetDataKey
import vad.dashing.tbox.isFuelLitersDisplayModeWidgetDataKey
import vad.dashing.tbox.isMusicWidgetDataKey
import vad.dashing.tbox.isObdMetricWidgetDataKey
import vad.dashing.tbox.isOsmSpeedLimitWidgetDataKey
import vad.dashing.tbox.isRoadMatchMapWidgetDataKey
import vad.dashing.tbox.isTripMetricWidgetDataKey
import vad.dashing.tbox.isValidDateTimeWidgetFormat
import vad.dashing.tbox.normalizeAccCruiseStepIntervalMs
import vad.dashing.tbox.normalizeAccCruiseTargetKmh
import vad.dashing.tbox.normalizeAvgFuelConsumptionSource
import vad.dashing.tbox.normalizeDriveModeWidgetRawValue
import vad.dashing.tbox.normalizeFuelLitersDisplayMode
import vad.dashing.tbox.normalizeMapsCamLookaheadM
import vad.dashing.tbox.normalizeMapsCamRadarHoldM
import vad.dashing.tbox.normalizeTripWidgetSource
import vad.dashing.tbox.normalizeWidgetTitlePosition
import vad.dashing.tbox.obd.ObdPid
import vad.dashing.tbox.previewDateTimeWidgetFormat
import vad.dashing.tbox.speedcam.normalizeSpeedCamOverageKmh
import vad.dashing.tbox.supportsMusicAlbumArtLayoutSettings
import vad.dashing.tbox.supportsMusicAlbumArtToggle
import vad.dashing.tbox.supportsMusicControlsHeightSetting
import vad.dashing.tbox.supportsMusicPlayerHeaderIconSetting
import vad.dashing.tbox.supportsWidgetButtonBinding
import vad.dashing.tbox.toggleDriveModeCycleSelection
import vad.dashing.tbox.trip.ActiveTripCustomWidgetField
import vad.dashing.tbox.trip.TripMetricFormatter
import vad.dashing.tbox.trip.TripWidgetTileDisplay
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

/** Type-specific options of the edited tile (editor tab «Поведение»). */
@Composable
internal fun TileBehaviorSettings(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    tileBackgroundPanelStorageId: String,
    widgetIndex: Int,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
            if (state.isMusicWidgetSelected) {
                MediaPlayersInlineSelection(
                    settingsViewModel = settingsViewModel,
                    selectedPlayers = state.selectedMediaPlayers,
                    onSelectionChange = { state.selectedMediaPlayers = it },
                    enabled = state.togglesEnabled,
                )
                if (state.selectedMediaPlayers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.widget_music_players_required),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.tboxBody
                    )
                }
                SettingSwitch(
                    state.mediaAutoPlayOnInit,
                    {
                        state.mediaAutoPlayOnInit = it
                        if (!it) {
                            state.mediaAutoPlayOnlyWhenEngineRunning = false
                        }
                    },
                    stringResource(R.string.widget_music_auto_play_on_init),
                    "",
                    state.togglesEnabled
                )
                SettingSwitch(
                    state.mediaAutoPlayOnlyWhenEngineRunning,
                    { state.mediaAutoPlayOnlyWhenEngineRunning = it },
                    stringResource(R.string.widget_music_auto_play_only_engine),
                    "",
                    state.togglesEnabled && state.mediaAutoPlayOnInit
                )
                // anymani: новая опция для контроля возврата лаунчера
                SettingSwitch(
                    state.mediaKeepPlayerForeground,
                    { state.mediaKeepPlayerForeground = it },
                    stringResource(R.string.widget_music_keep_player_foreground),
                    stringResource(R.string.widget_music_keep_player_foreground_desc),
                    state.togglesEnabled
                )
                SettingSwitch(
                    state.mediaFollowPlayback,
                    { state.mediaFollowPlayback = it },
                    stringResource(R.string.widget_music_follow_playback),
                    stringResource(R.string.widget_music_follow_playback_desc),
                    state.togglesEnabled
                )
                SettingSwitch(
                    state.mediaShowLikeButton,
                    { state.mediaShowLikeButton = it },
                    stringResource(R.string.widget_music_show_like_button),
                    stringResource(R.string.widget_music_show_like_button_desc),
                    state.togglesEnabled
                )
                if (supportsMusicAlbumArtToggle(state.selectedDataKey)) {
                    SettingSwitch(
                        state.mediaShowAlbumArt,
                        { state.mediaShowAlbumArt = it },
                        stringResource(R.string.widget_music_show_album_art),
                        stringResource(R.string.widget_music_show_album_art_desc),
                        state.togglesEnabled
                    )
                    if (state.mediaShowAlbumArt &&
                        supportsMusicAlbumArtLayoutSettings(state.selectedDataKey)
                    ) {
                        SettingInt(
                            value = state.mediaAlbumArtColumnWidthPercent,
                            onValueChange = { state.mediaAlbumArtColumnWidthPercent = it },
                            text = stringResource(R.string.widget_music_album_art_column_width_title),
                            description = stringResource(
                                R.string.widget_music_album_art_column_width_desc
                            ),
                            minValue = MusicWidgetAlbumArtDisplay.MIN_ALBUM_ART_COLUMN_WIDTH_PERCENT,
                            maxValue = MusicWidgetAlbumArtDisplay.MAX_ALBUM_ART_COLUMN_WIDTH_PERCENT,
                        )
                        val albumArtSideEntries = listOf(
                            MusicAlbumArtSideDropdownEntry(
                                MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_LEFT,
                                stringResource(R.string.widget_music_album_art_side_left),
                            ),
                            MusicAlbumArtSideDropdownEntry(
                                MusicWidgetAlbumArtDisplay.ALBUM_ART_SIDE_RIGHT,
                                stringResource(R.string.widget_music_album_art_side_right),
                            ),
                        )
                        val selectedAlbumArtSide = albumArtSideEntries.firstOrNull {
                            it.side == MusicWidgetAlbumArtDisplay.normalizeAlbumArtSide(
                                state.mediaAlbumArtSide
                            )
                        } ?: albumArtSideEntries.first()
                        SettingDropdownGeneric(
                            selectedValue = selectedAlbumArtSide,
                            onValueChange = { state.mediaAlbumArtSide = it.side },
                            text = stringResource(R.string.widget_music_album_art_side_title),
                            description = "",
                            enabled = state.togglesEnabled,
                            options = albumArtSideEntries,
                            selectorWidth = WidgetDialogDropdownSelectorWidth,
                        )
                    }
                }
                if (supportsMusicPlayerHeaderIconSetting(state.selectedDataKey)) {
                    SettingSwitch(
                        state.mediaShowPlayerHeaderIcon,
                        { state.mediaShowPlayerHeaderIcon = it },
                        stringResource(R.string.widget_music_show_player_header_icon),
                        stringResource(R.string.widget_music_show_player_header_icon_desc),
                        state.togglesEnabled
                    )
                }
                if (state.selectedDataKey == MUSIC_COVER_WIDGET_DATA_KEY) {
                    SettingSwitch(
                        state.mediaShowTrackInfo,
                        { state.mediaShowTrackInfo = it },
                        stringResource(R.string.widget_music_show_track_info),
                        stringResource(R.string.widget_music_show_track_info_desc),
                        state.togglesEnabled
                    )
                }
                if (supportsMusicControlsHeightSetting(state.selectedDataKey)) {
                    SettingInt(
                        value = state.mediaControlsHeightPercent,
                        onValueChange = { state.mediaControlsHeightPercent = it },
                        text = stringResource(R.string.widget_music_controls_height_title),
                        description = stringResource(R.string.widget_music_controls_height_desc),
                        minValue = MusicWidgetControlsDisplay.MIN_CONTROLS_HEIGHT_PERCENT,
                        maxValue = MusicWidgetControlsDisplay.MAX_CONTROLS_HEIGHT_PERCENT,
                    )
                }
            }
            AppLauncherWidgetSettingsSection(
                state = state,
                settingsViewModel = settingsViewModel,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            HttpRequestWidgetSettingsSection(
                state = state,
                settingsViewModel = settingsViewModel,
                panelStorageId = tileBackgroundPanelStorageId,
                widgetIndex = widgetIndex,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            AutomationTriggerWidgetSettingsSection(
                state = state,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
            )
            WidgetButtonBindingSettingsSection(
                state = state,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (WidgetsRepository.supportsUseMbCanVhal(state.selectedDataKey)) {
                SettingSwitch(
                    state.useMbCanVhal,
                    { state.useMbCanVhal = it },
                    stringResource(R.string.widget_media_volume_use_mbcan_vhal),
                    "",
                    state.togglesEnabled
                )
            }
            if (isAverageFuelConsumptionWidgetDataKey(state.selectedDataKey)) {
                val avgFuelSourceOptions = listOf(
                    AvgFuelConsumptionSourceDropdownEntry(
                        AVG_FUEL_CONSUMPTION_SOURCE_MBCAN_VHAL,
                        stringResource(R.string.widget_avg_fuel_source_mbcan_vhal),
                    ),
                    AvgFuelConsumptionSourceDropdownEntry(
                        AVG_FUEL_CONSUMPTION_SOURCE_CURRENT_TRIP,
                        stringResource(R.string.trips_widget_source_current),
                    ),
                    AvgFuelConsumptionSourceDropdownEntry(
                        AVG_FUEL_CONSUMPTION_SOURCE_DAILY_TRIP,
                        stringResource(R.string.trips_widget_source_persistent),
                    ),
                )
                val selectedAvgFuelSource = avgFuelSourceOptions.firstOrNull {
                    it.source == normalizeAvgFuelConsumptionSource(state.avgFuelConsumptionSource)
                } ?: avgFuelSourceOptions.first()
                SettingDropdownGeneric(
                    selectedValue = selectedAvgFuelSource,
                    onValueChange = { state.avgFuelConsumptionSource = it.source },
                    text = stringResource(R.string.trips_widget_source_title),
                    description = "",
                    enabled = state.togglesEnabled,
                    options = avgFuelSourceOptions,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (isFuelLitersDisplayModeWidgetDataKey(state.selectedDataKey)) {
                val fuelLitersDisplayOptions = listOf(
                    FuelLitersDisplayModeDropdownEntry(
                        FUEL_LITERS_DISPLAY_REMAINING,
                        stringResource(R.string.widget_fuel_liters_display_remaining),
                    ),
                    FuelLitersDisplayModeDropdownEntry(
                        FUEL_LITERS_DISPLAY_TO_FULL,
                        stringResource(R.string.widget_fuel_liters_display_to_full),
                    ),
                )
                val selectedFuelLitersDisplay = fuelLitersDisplayOptions.firstOrNull {
                    it.mode == normalizeFuelLitersDisplayMode(state.fuelLitersDisplayMode)
                } ?: fuelLitersDisplayOptions.first()
                SettingDropdownGeneric(
                    selectedValue = selectedFuelLitersDisplay,
                    onValueChange = { state.fuelLitersDisplayMode = it.mode },
                    text = stringResource(R.string.widget_fuel_liters_display_title),
                    description = "",
                    enabled = state.togglesEnabled,
                    options = fuelLitersDisplayOptions,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (WidgetsRepository.supportsStepperAdjustIconStyle(state.selectedDataKey)) {
                val stepperIconEntries = listOf(
                    StepperAdjustIconStyleDropdownEntry(
                        stringResource(R.string.widget_stepper_adjust_icon_plus_minus),
                        STEPPER_ADJUST_ICON_PLUS_MINUS,
                    ),
                    StepperAdjustIconStyleDropdownEntry(
                        stringResource(R.string.widget_stepper_adjust_icon_arrows),
                        STEPPER_ADJUST_ICON_ARROWS,
                    ),
                )
                val selectedStepperIconEntry = stepperIconEntries.find {
                    it.stored == state.stepperAdjustIconStyle
                } ?: stepperIconEntries.first()
                SettingDropdownGeneric(
                    selectedValue = selectedStepperIconEntry,
                    onValueChange = { state.stepperAdjustIconStyle = it.stored },
                    text = stringResource(R.string.widget_stepper_adjust_icon_style_title),
                    description = stringResource(R.string.widget_stepper_adjust_icon_style_desc),
                    enabled = state.togglesEnabled,
                    options = stepperIconEntries,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (WidgetsRepository.supportsHvacTempStep(state.selectedDataKey)) {
                val tempStepEntries = listOf(
                    HvacTempStepDropdownEntry(
                        stringResource(R.string.widget_hvac_temp_step_0_5),
                        HVAC_TEMP_WIDGET_STEP_HALF_TENTHS,
                    ),
                    HvacTempStepDropdownEntry(
                        stringResource(R.string.widget_hvac_temp_step_1_0),
                        HVAC_TEMP_WIDGET_STEP_WHOLE_TENTHS,
                    ),
                )
                val selectedTempStepEntry = tempStepEntries.find {
                    it.stored == state.hvacTempStepTenths
                } ?: tempStepEntries.first()
                SettingDropdownGeneric(
                    selectedValue = selectedTempStepEntry,
                    onValueChange = { state.hvacTempStepTenths = it.stored },
                    text = stringResource(R.string.widget_hvac_temp_step_title),
                    description = stringResource(R.string.widget_hvac_temp_step_desc),
                    enabled = state.togglesEnabled,
                    options = tempStepEntries,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (WidgetsRepository.supportsEspRelayMode(state.selectedDataKey)) {
                val relayModeEntries = listOf(
                    EspRelayModeDropdownEntry(
                        EspRelayWidgetMode.BUTTON,
                        stringResource(R.string.widget_esp_relay_mode_button),
                    ),
                    EspRelayModeDropdownEntry(
                        EspRelayWidgetMode.RELAY,
                        stringResource(R.string.widget_esp_relay_mode_relay),
                    ),
                )
                val selectedRelayMode = relayModeEntries.find {
                    it.mode == state.espRelayMode
                } ?: relayModeEntries.first { it.mode == EspRelayWidgetMode.DEFAULT }
                SettingDropdownGeneric(
                    selectedValue = selectedRelayMode,
                    onValueChange = { state.espRelayMode = it.mode },
                    text = stringResource(R.string.widget_esp_relay_mode_title),
                    description = stringResource(R.string.widget_esp_relay_mode_desc),
                    enabled = state.togglesEnabled,
                    options = relayModeEntries,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (isRoadMatchMapWidgetDataKey(state.selectedDataKey)) {
                SettingSwitch(
                    isChecked = state.speedCamShowOnMap,
                    onCheckedChange = { state.speedCamShowOnMap = it },
                    text = stringResource(R.string.speed_cam_show_on_map_title),
                    description = stringResource(R.string.speed_cam_show_on_map_desc),
                    enabled = state.togglesEnabled,
                )
            }
            if (BuildConfig.MAPKIT_ENABLED &&
                isRoadMatchMapWidgetDataKey(state.selectedDataKey)
            ) {
                SettingSwitch(
                    isChecked = state.roadMatchMapKitBasemap,
                    onCheckedChange = { state.roadMatchMapKitBasemap = it },
                    text = stringResource(R.string.widget_road_match_mapkit_basemap_title),
                    description = stringResource(R.string.widget_road_match_mapkit_basemap_desc),
                    enabled = state.togglesEnabled,
                )
                if (state.roadMatchMapKitBasemap) {
                    val opacityEntries = vad.dashing.tbox.location.roadmatch.RoadMatchBasemapOpacity
                        .STEPS
                        .map { percent ->
                            RoadMatchBasemapTransparencyDropdownEntry(
                                percent = percent,
                                label = if (percent == 0) {
                                    stringResource(R.string.widget_road_match_basemap_opaque)
                                } else {
                                    stringResource(
                                        R.string.widget_road_match_basemap_transparency_percent,
                                        percent,
                                    )
                                },
                            )
                        }
                    val selectedOpacity = opacityEntries.firstOrNull {
                        it.percent == state.roadMatchBasemapTransparencyPercent
                    } ?: opacityEntries.first()
                    SettingDropdownGeneric(
                        selectedValue = selectedOpacity,
                        onValueChange = {
                            state.roadMatchBasemapTransparencyPercent = it.percent
                        },
                        text = stringResource(R.string.widget_road_match_basemap_opacity_title),
                        description = stringResource(
                            R.string.widget_road_match_basemap_opacity_desc,
                        ),
                        enabled = state.togglesEnabled,
                        options = opacityEntries,
                        selectorWidth = WidgetDialogDropdownSelectorWidth,
                    )
                }
            }
            if (isOsmSpeedLimitWidgetDataKey(state.selectedDataKey)) {
                SettingSwitch(
                    isChecked = state.mapsCamShowCameras,
                    onCheckedChange = { state.mapsCamShowCameras = it },
                    text = stringResource(R.string.maps_cam_show_cameras_title),
                    description = stringResource(R.string.maps_cam_show_cameras_desc),
                    enabled = state.togglesEnabled,
                )
                SettingSwitch(
                    isChecked = state.mapsCamShowCurrentLimit,
                    onCheckedChange = { state.mapsCamShowCurrentLimit = it },
                    text = stringResource(R.string.maps_cam_show_current_title),
                    description = stringResource(R.string.maps_cam_show_current_desc),
                    enabled = state.togglesEnabled,
                )
                SettingSwitch(
                    isChecked = state.mapsCamShowAheadLimit,
                    onCheckedChange = { state.mapsCamShowAheadLimit = it },
                    text = stringResource(R.string.maps_cam_show_ahead_title),
                    description = stringResource(R.string.maps_cam_show_ahead_desc),
                    enabled = state.togglesEnabled,
                )
                SettingSliderInt(
                    value = state.mapsCamLookaheadDistanceM,
                    onValueChange = {
                        state.mapsCamLookaheadDistanceM = normalizeMapsCamLookaheadM(it)
                    },
                    text = stringResource(
                        R.string.maps_cam_lookahead_title,
                        state.mapsCamLookaheadDistanceM,
                    ),
                    description = stringResource(R.string.maps_cam_lookahead_desc),
                    minValue = MIN_MAPS_CAM_LOOKAHEAD_M,
                    maxValue = MAX_MAPS_CAM_LOOKAHEAD_M,
                    enabled = state.togglesEnabled,
                    step = MAPS_CAM_DISTANCE_STEP_M,
                )
                SettingSliderInt(
                    value = state.mapsCamRadarHoldDistanceM,
                    onValueChange = {
                        state.mapsCamRadarHoldDistanceM = normalizeMapsCamRadarHoldM(it)
                    },
                    text = stringResource(
                        R.string.maps_cam_radar_hold_title,
                        state.mapsCamRadarHoldDistanceM,
                    ),
                    description = stringResource(R.string.maps_cam_radar_hold_desc),
                    minValue = MIN_MAPS_CAM_RADAR_HOLD_M,
                    maxValue = MAX_MAPS_CAM_RADAR_HOLD_M,
                    enabled = state.togglesEnabled,
                    step = MAPS_CAM_DISTANCE_STEP_M,
                )
                SettingSliderInt(
                    value = state.speedCamOverageKmh,
                    onValueChange = {
                        state.speedCamOverageKmh = normalizeSpeedCamOverageKmh(it)
                    },
                    text = stringResource(
                        R.string.speed_cam_overage_title,
                        state.speedCamOverageKmh,
                    ),
                    description = stringResource(R.string.speed_cam_overage_desc),
                    minValue = vad.dashing.tbox.speedcam.MIN_SPEED_CAM_OVERAGE_KMH,
                    maxValue = vad.dashing.tbox.speedcam.MAX_SPEED_CAM_OVERAGE_KMH,
                    enabled = state.togglesEnabled,
                    step = 1,
                )
            }
            if (isAccCruiseWidgetDataKey(state.selectedDataKey)) {
                SettingInt(
                    value = state.accCruiseTargetKmh,
                    onValueChange = {
                        state.accCruiseTargetKmh = normalizeAccCruiseTargetKmh(it)
                    },
                    text = stringResource(R.string.widget_acc_cruise_target_title),
                    description = stringResource(R.string.widget_acc_cruise_target_desc),
                    minValue = ACC_CRUISE_TARGET_KMH_MIN,
                    maxValue = ACC_CRUISE_TARGET_KMH_MAX,
                )
                SettingInt(
                    value = state.accCruiseIncreaseIntervalMs,
                    onValueChange = {
                        state.accCruiseIncreaseIntervalMs =
                            normalizeAccCruiseStepIntervalMs(it)
                    },
                    text = stringResource(R.string.widget_acc_cruise_increase_interval_title),
                    description = stringResource(R.string.widget_acc_cruise_increase_interval_desc),
                    minValue = ACC_CRUISE_STEP_INTERVAL_MS_MIN,
                    maxValue = ACC_CRUISE_STEP_INTERVAL_MS_MAX,
                )
                SettingInt(
                    value = state.accCruiseDecreaseIntervalMs,
                    onValueChange = {
                        state.accCruiseDecreaseIntervalMs =
                            normalizeAccCruiseStepIntervalMs(it)
                    },
                    text = stringResource(R.string.widget_acc_cruise_decrease_interval_title),
                    description = stringResource(R.string.widget_acc_cruise_decrease_interval_desc),
                    minValue = ACC_CRUISE_STEP_INTERVAL_MS_MIN,
                    maxValue = ACC_CRUISE_STEP_INTERVAL_MS_MAX,
                )
            }
            if (state.selectedDataKey == DRIVE_MODE_WIDGET_DATA_KEY) {
                val selectedOption = DRIVE_MODE_WIDGET_OPTIONS.firstOrNull {
                    it.rawValue == normalizeDriveModeWidgetRawValue(state.selectedDriveMode)
                } ?: DRIVE_MODE_WIDGET_OPTIONS.first()
                SettingDropdownGeneric(
                    selectedValue = selectedOption,
                    onValueChange = { state.selectedDriveMode = it.rawValue },
                    text = stringResource(R.string.widget_drive_mode_target_title),
                    description = stringResource(R.string.widget_drive_mode_target_desc),
                    enabled = state.togglesEnabled,
                    options = DRIVE_MODE_WIDGET_OPTIONS,
                    selectorWidth = WidgetDialogDropdownSelectorWidth
                )
            }
            if (state.isDriveModeCycleWidgetSelected) {
                Text(
                    text = stringResource(R.string.widget_drive_mode_cycle_modes_title),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Text(
                    text = stringResource(R.string.widget_drive_mode_cycle_modes_desc),
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                val selectedSet = state.selectedDriveModes.toSet()
                DRIVE_MODE_WIDGET_OPTIONS.forEach { option ->
                    val checked = option.rawValue in selectedSet
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = {
                                state.selectedDriveModes = toggleDriveModeCycleSelection(
                                    state.selectedDriveModes,
                                    option.rawValue,
                                )
                            },
                            enabled = state.togglesEnabled,
                        )
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (isActiveTripWidgetDataKey(state.selectedDataKey) ||
                isTripMetricWidgetDataKey(state.selectedDataKey)
            ) {
                val sourceOptions = listOf(
                    TripWidgetSourceDropdownEntry(
                        TRIP_WIDGET_SOURCE_CURRENT,
                        stringResource(R.string.trips_widget_source_current),
                    ),
                    TripWidgetSourceDropdownEntry(
                        TRIP_WIDGET_SOURCE_PERSISTENT,
                        stringResource(R.string.trips_widget_source_persistent),
                    ),
                )
                val selectedSource = sourceOptions.firstOrNull {
                    it.source == normalizeTripWidgetSource(state.tripWidgetSource)
                } ?: sourceOptions.first()
                SettingDropdownGeneric(
                    selectedValue = selectedSource,
                    onValueChange = { state.tripWidgetSource = it.source },
                    text = stringResource(R.string.trips_widget_source_title),
                    description = "",
                    enabled = state.togglesEnabled,
                    options = sourceOptions,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (isTripMetricWidgetDataKey(state.selectedDataKey)) {
                val fieldOptions = ActiveTripCustomWidgetField.entries.map { field ->
                    TripMetricFieldDropdownEntry(
                        fieldId = field.id,
                        display = stringResource(field.labelRes),
                    )
                }
                val selectedField = fieldOptions.firstOrNull {
                    it.fieldId == TripMetricFormatter.normalizeFieldId(state.tripMetricFieldId)
                } ?: fieldOptions.first()
                SettingDropdownGeneric(
                    selectedValue = selectedField,
                    onValueChange = { state.tripMetricFieldId = it.fieldId },
                    text = stringResource(R.string.trips_metric_field_title),
                    description = "",
                    enabled = state.togglesEnabled,
                    options = fieldOptions,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            if (isObdMetricWidgetDataKey(state.selectedDataKey)) {
                val supportedRaw by settingsViewModel.elm327SupportedPids
                    .collectAsStateWithLifecycle()
                val discoveryAtMs by settingsViewModel.elm327DiscoveryAtMs
                    .collectAsStateWithLifecycle()
                val supportedMode01 = remember(supportedRaw) {
                    vad.dashing.tbox.obd.Elm327Protocol.decodeSupportedPids(supportedRaw)
                }
                val discoveryDone = discoveryAtMs > 0L
                val unsupportedHint =
                    stringResource(R.string.obd_metric_pid_unsupported_hint)
                val pidOptions = ObdPid.entries.map { pid ->
                    val base = stringResource(pid.labelRes)
                    val mark = when {
                        pid.mode01Pid == null -> ""
                        !discoveryDone -> ""
                        pid.mode01Pid in supportedMode01 -> ""
                        else -> " — $unsupportedHint"
                    }
                    ObdPidDropdownEntry(
                        pidId = pid.id,
                        display = base + mark,
                    )
                }
                val selectedPid = pidOptions.firstOrNull {
                    it.pidId == ObdPid.normalizeId(state.obdPidId)
                } ?: pidOptions.first()
                SettingDropdownGeneric(
                    selectedValue = selectedPid,
                    onValueChange = { state.obdPidId = it.pidId },
                    text = stringResource(R.string.obd_metric_pid_title),
                    description = if (discoveryDone) {
                        stringResource(R.string.obd_metric_pid_discovery_hint)
                    } else {
                        ""
                    },
                    enabled = state.togglesEnabled,
                    options = pidOptions,
                    selectorWidth = WidgetDialogObdPidDropdownSelectorWidth,
                )
            }
            if (isActiveTripWidgetDataKey(state.selectedDataKey)) {
                SettingSwitch(
                    state.tripWidgetShowRowDividers,
                    { state.tripWidgetShowRowDividers = it },
                    stringResource(R.string.trips_widget_show_row_dividers_title),
                    "",
                    state.togglesEnabled,
                )
                SettingInt(
                    value = state.tripWidgetLabelColumnWidthPercent,
                    onValueChange = { state.tripWidgetLabelColumnWidthPercent = it },
                    text = stringResource(R.string.trips_widget_label_column_width_title),
                    description = stringResource(R.string.trips_widget_label_column_width_desc),
                    minValue = TripWidgetTileDisplay.MIN_LABEL_COLUMN_WIDTH_PERCENT,
                    maxValue = TripWidgetTileDisplay.MAX_LABEL_COLUMN_WIDTH_PERCENT,
                )
            }
    }
}

/** Title, unit and value format of the edited tile (editor tab «Вид»). */
@Composable
internal fun TileTitleAndValueSettings(state: WidgetSelectionDialogState) {
    Column(modifier = Modifier.fillMaxWidth()) {
            SettingSwitch(
                state.showTitle,
                { state.showTitle = it },
                stringResource(R.string.widget_show_title),
                "",
                state.togglesEnabled
            )
            if (state.showTitle) {
                val titlePositionEntries = listOf(
                    WidgetTitlePositionDropdownEntry(
                        stringResource(R.string.widget_title_position_top),
                        WIDGET_TITLE_POSITION_TOP,
                    ),
                    WidgetTitlePositionDropdownEntry(
                        stringResource(R.string.widget_title_position_bottom),
                        WIDGET_TITLE_POSITION_BOTTOM,
                    ),
                )
                val selectedTitlePosition = titlePositionEntries.firstOrNull {
                    it.stored == normalizeWidgetTitlePosition(state.titlePosition)
                } ?: titlePositionEntries.first()
                SettingDropdownGeneric(
                    selectedValue = selectedTitlePosition,
                    onValueChange = { state.titlePosition = it.stored },
                    text = stringResource(R.string.widget_title_position_title),
                    description = "",
                    enabled = state.togglesEnabled,
                    options = titlePositionEntries,
                    selectorWidth = WidgetDialogDropdownSelectorWidth,
                )
            }
            OutlinedTextField(
                value = state.customTitle,
                onValueChange = { state.customTitle = it },
                enabled = state.togglesEnabled,
                textStyle = MaterialTheme.typography.tboxTitle,
                label = {
                    Text(
                        stringResource(R.string.widget_custom_title_label),
                        style = MaterialTheme.typography.tboxBody
                    )
                },
                placeholder = {
                    Text(
                        stringResource(R.string.widget_custom_title_hint),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 4.dp)
            )
            if (WidgetsRepository.supportsShowUnit(state.selectedDataKey)) {
                SettingSwitch(
                    state.showUnit,
                    { state.showUnit = it },
                    stringResource(R.string.widget_show_unit),
                    "",
                    state.togglesEnabled
                )
            }
            if (WidgetsRepository.supportsSingleLineDualMetrics(state.selectedDataKey)) {
                SettingSwitch(
                    state.singleLineDualMetrics,
                    { state.singleLineDualMetrics = it },
                    stringResource(R.string.widget_single_line_dual_metrics),
                    "",
                    state.togglesEnabled
                )
            }
            if (WidgetsRepository.supportsValueAccuracy(state.selectedDataKey)) {
                val accuracyEntries = listOf(
                    ValueAccuracyDropdownEntry(
                        stringResource(R.string.widget_value_accuracy_default),
                        null
                    ),
                    ValueAccuracyDropdownEntry(
                        stringResource(R.string.widget_value_accuracy_0),
                        0
                    ),
                    ValueAccuracyDropdownEntry(
                        stringResource(R.string.widget_value_accuracy_1),
                        1
                    ),
                    ValueAccuracyDropdownEntry(
                        stringResource(R.string.widget_value_accuracy_2),
                        2
                    ),
                )
                val selectedAccuracyEntry = accuracyEntries.find { it.stored == state.valueAccuracy }
                    ?: accuracyEntries.first()
                SettingDropdownGeneric(
                    selectedValue = selectedAccuracyEntry,
                    onValueChange = { state.valueAccuracy = it.stored },
                    text = stringResource(R.string.widget_value_accuracy_title),
                    description = stringResource(R.string.widget_value_accuracy_desc),
                    enabled = state.togglesEnabled,
                    options = accuracyEntries,
                    selectorWidth = WidgetDialogDropdownSelectorWidth
                )
            }
            if (WidgetsRepository.supportsDateTimeFormat(state.selectedDataKey)) {
                val dateTimeFormatError = !isValidDateTimeWidgetFormat(
                    state.selectedDataKey,
                    state.dateTimeFormat,
                )
                val dateTimeFormatPreview = previewDateTimeWidgetFormat(
                    state.selectedDataKey,
                    state.dateTimeFormat,
                ).orEmpty()
                OutlinedTextField(
                    value = state.dateTimeFormat,
                    onValueChange = { state.dateTimeFormat = it },
                    enabled = state.togglesEnabled,
                    isError = dateTimeFormatError,
                    textStyle = MaterialTheme.typography.tboxTitle,
                    label = {
                        Text(
                            stringResource(R.string.widget_datetime_format_label),
                            style = MaterialTheme.typography.tboxBody,
                        )
                    },
                    placeholder = {
                        Text(
                            stringResource(R.string.widget_datetime_format_hint),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    supportingText = {
                        Text(
                            text = if (dateTimeFormatError) {
                                stringResource(R.string.widget_datetime_format_error)
                            } else {
                                stringResource(
                                    R.string.widget_datetime_format_preview,
                                    dateTimeFormatPreview,
                                )
                            },
                            style = MaterialTheme.typography.tboxCaption,
                        )
                    },
                    singleLine = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                )
            }
    }
}

/** True when [TileBehaviorSettings] shows at least one option for [dataKey]. */
internal fun tileHasBehaviorSettings(dataKey: String): Boolean =
    dataKey == WidgetsRepository.EXTERNAL_WIDGET_DATA_KEY ||
        isMusicWidgetDataKey(dataKey) ||
        dataKey == APP_LAUNCHER_WIDGET_DATA_KEY ||
        dataKey == HTTP_REQUEST_WIDGET_DATA_KEY ||
        dataKey == AUTOMATION_TRIGGER_WIDGET_DATA_KEY ||
        supportsWidgetButtonBinding(dataKey) ||
        WidgetsRepository.supportsUseMbCanVhal(dataKey) ||
        isAverageFuelConsumptionWidgetDataKey(dataKey) ||
        isFuelLitersDisplayModeWidgetDataKey(dataKey) ||
        WidgetsRepository.supportsStepperAdjustIconStyle(dataKey) ||
        WidgetsRepository.supportsHvacTempStep(dataKey) ||
        WidgetsRepository.supportsEspRelayMode(dataKey) ||
        isRoadMatchMapWidgetDataKey(dataKey) ||
        isOsmSpeedLimitWidgetDataKey(dataKey) ||
        isAccCruiseWidgetDataKey(dataKey) ||
        dataKey == DRIVE_MODE_WIDGET_DATA_KEY ||
        isDriveModeCycleWidgetDataKey(dataKey) ||
        isActiveTripWidgetDataKey(dataKey) ||
        isTripMetricWidgetDataKey(dataKey) ||
        isObdMetricWidgetDataKey(dataKey)
