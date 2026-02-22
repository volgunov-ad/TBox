package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.utils.MockLocationUtils
import vad.dashing.tbox.utils.canUseMockLocation

@Composable
fun ModemTabContent(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit,
) {
    val netState by viewModel.netState.collectAsStateWithLifecycle()
    val netValues by viewModel.netValues.collectAsStateWithLifecycle()
    val apn1State by viewModel.apn1State.collectAsStateWithLifecycle()
    val apn2State by viewModel.apn2State.collectAsStateWithLifecycle()
    val apnStatus by viewModel.apnStatus.collectAsStateWithLifecycle()
    val modemStatus by viewModel.modemStatus.collectAsStateWithLifecycle()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val noDataLabel = stringResource(R.string.value_no_data)
    val connectedLabel = stringResource(R.string.value_connected)
    val disconnectedLabel = stringResource(R.string.value_disconnected)
    val formattedConnectionChangeTime = remember(netState.connectionChangeTime, noDataLabel) {
        netState.connectionChangeTime?.let { timeFormat.format(it) } ?: noDataLabel
    }
    val formattedAPN1ChangeTime = remember(apn1State.changeTime, noDataLabel) {
        apn1State.changeTime?.let { timeFormat.format(it) } ?: noDataLabel
    }
    val formattedAPN2ChangeTime = remember(apn2State.changeTime, noDataLabel) {
        apn2State.changeTime?.let { timeFormat.format(it) } ?: noDataLabel
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusHeader(stringResource(R.string.modem_sim_data_header)) }
            item { StatusRow(stringResource(R.string.status_imei), netValues.imei) }
            item { StatusRow(stringResource(R.string.status_iccid), netValues.iccid) }
            item { StatusRow(stringResource(R.string.status_imsi), netValues.imsi) }
            item { StatusRow(stringResource(R.string.status_operator), netValues.operator) }

            item { StatusHeader(stringResource(R.string.connection_data_header)) }
            item { StatusRow(stringResource(R.string.status_csq), if (netState.csq != 99) netState.csq.toString() else "-") }
            item { StatusRow(stringResource(R.string.status_registration), netState.regStatus) }
            item { StatusRow(stringResource(R.string.status_sim), netState.simStatus) }
            item { StatusRow(stringResource(R.string.status_network), netState.netStatus) }
            item { StatusRow(stringResource(R.string.status_apn), if (apnStatus) connectedLabel else disconnectedLabel) }
            item { StatusRow(stringResource(R.string.status_connection_time), formattedConnectionChangeTime) }

            item { StatusHeader(stringResource(R.string.status_apn_1_header)) }
            item {
                StatusRow(
                    stringResource(R.string.status_apn_value),
                    valueToString(
                        apn1State.apnStatus,
                        booleanTrue = connectedLabel,
                        booleanFalse = disconnectedLabel
                    )
                )
            }
            item { StatusRow(stringResource(R.string.status_apn_type), apn1State.apnType) }
            item { StatusRow(stringResource(R.string.status_ip_apn), apn1State.apnIP) }
            item { StatusRow(stringResource(R.string.status_apn_gateway), apn1State.apnGate) }
            item { StatusRow(stringResource(R.string.status_dns1_apn), apn1State.apnDNS1) }
            item { StatusRow(stringResource(R.string.status_dns2_apn), apn1State.apnDNS2) }
            item { StatusRow(stringResource(R.string.status_change_time), formattedAPN1ChangeTime) }

            item { StatusHeader(stringResource(R.string.status_apn_2_header)) }
            item {
                StatusRow(
                    stringResource(R.string.status_apn2_value),
                    valueToString(
                        apn2State.apnStatus,
                        booleanTrue = connectedLabel,
                        booleanFalse = disconnectedLabel
                    )
                )
            }
            item { StatusRow(stringResource(R.string.status_apn2_type), apn2State.apnType) }
            item { StatusRow(stringResource(R.string.status_ip_apn2), apn2State.apnIP) }
            item { StatusRow(stringResource(R.string.status_apn2_gateway), apn2State.apnGate) }
            item { StatusRow(stringResource(R.string.status_dns1_apn2), apn2State.apnDNS1) }
            item { StatusRow(stringResource(R.string.status_dns2_apn2), apn2State.apnDNS2) }
            item { StatusRow(stringResource(R.string.status_change_time), formattedAPN2ChangeTime) }
        }

        ModemModeSelectorContent(
            selectedMode = modemStatus,
            onServiceCommand = onServiceCommand,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun ModemModeSelectorContent(
    selectedMode: Int,
    onServiceCommand: (String, String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var buttonsEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(buttonsEnabled) {
        if (!buttonsEnabled) {
            delay(1000)
            buttonsEnabled = true
        }
    }

    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.modem_mode_title),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ModeButton(
                text = stringResource(R.string.modem_mode_enabled),
                isSelected = selectedMode == 1,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_ON,
                            "",
                            ""
                        )
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            ModeButton(
                text = stringResource(R.string.modem_mode_flight),
                isSelected = selectedMode == 4,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_FLY,
                            "",
                            ""
                        )
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            ModeButton(
                text = stringResource(R.string.modem_mode_disabled),
                isSelected = selectedMode == 0,
                onClick = {
                    if (buttonsEnabled) {
                        buttonsEnabled = false
                        onServiceCommand(
                            BackgroundService.ACTION_MODEM_OFF,
                            "",
                            ""
                        )
                    }
                },
                enabled = buttonsEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun SettingsTabContent(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onTboxRestartClick: () -> Unit,
    onMockLocationSettingChanged: (Boolean) -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
) {
    val isAutoRestartEnabled by settingsViewModel.isAutoModemRestartEnabled.collectAsStateWithLifecycle()
    val isAutoTboxRebootEnabled by settingsViewModel.isAutoTboxRebootEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxAppEnabled by settingsViewModel.isAutoSuspendTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxMdcEnabled by settingsViewModel.isAutoSuspendTboxMdcEnabled.collectAsStateWithLifecycle()
    val isAutoSuspendTboxSwdEnabled by settingsViewModel.isAutoSuspendTboxSwdEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxAppEnabled by settingsViewModel.isAutoStopTboxAppEnabled.collectAsStateWithLifecycle()
    val isAutoStopTboxMdcEnabled by settingsViewModel.isAutoStopTboxMdcEnabled.collectAsStateWithLifecycle()
    val isAutoPreventTboxRestartEnabled by settingsViewModel.isAutoPreventTboxRestartEnabled.collectAsStateWithLifecycle()
    val isGetCanFrameEnabled by settingsViewModel.isGetCanFrameEnabled.collectAsStateWithLifecycle()
    val isGetCycleSignalEnabled by settingsViewModel.isGetCycleSignalEnabled.collectAsStateWithLifecycle()
    val isGetLocDataEnabled by settingsViewModel.isGetLocDataEnabled.collectAsStateWithLifecycle()
    val isMockLocationEnabled by settingsViewModel.isMockLocationEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val isWidgetShowLocIndicatorEnabled by settingsViewModel.isWidgetShowLocIndicatorEnabled.collectAsStateWithLifecycle()
    val isExpertModeEnabled by settingsViewModel.isExpertModeEnabled.collectAsStateWithLifecycle()

    val isFloatingDashboardEnabled by settingsViewModel.isFloatingDashboardEnabled.collectAsStateWithLifecycle()
    val isFloatingDashboardBackground by settingsViewModel.isFloatingDashboardBackground.collectAsStateWithLifecycle()
    val isFloatingDashboardClickAction by settingsViewModel.isFloatingDashboardClickAction.collectAsStateWithLifecycle()
    val floatingDashboardRows by settingsViewModel.floatingDashboardRows.collectAsStateWithLifecycle()
    val floatingDashboardCols by settingsViewModel.floatingDashboardCols.collectAsStateWithLifecycle()
    val activeFloatingDashboardId by settingsViewModel.activeFloatingDashboardId.collectAsStateWithLifecycle()

    val isTboxIPRotation by settingsViewModel.tboxIPRotation.collectAsStateWithLifecycle()

    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val canDataSaveCount by settingsViewModel.canDataSaveCount.collectAsStateWithLifecycle()

    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val canUseMockLocation = remember { context.canUseMockLocation() }
    val warningTitle = stringResource(R.string.warning_title)
    val warningSuspendStop = stringResource(R.string.warning_suspend_stop_manual_reboot)
    val expertModeWarning = stringResource(R.string.settings_expert_mode_warning_desc)

    var restartButtonEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(restartButtonEnabled) {
        if (!restartButtonEnabled) {
            delay(15000)
            restartButtonEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        SettingsTitle(stringResource(R.string.settings_network_control_title))
        SettingSwitch(
            isAutoRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoRestartSetting(enabled)
            },
            stringResource(R.string.settings_auto_modem_restart_title),
            stringResource(R.string.settings_auto_modem_restart_desc),
            true
        )
        SettingSwitch(
            isAutoTboxRebootEnabled,
            { enabled ->
                settingsViewModel.saveAutoTboxRebootSetting(enabled)
            },
            stringResource(R.string.settings_auto_tbox_reboot_title),
            stringResource(R.string.settings_auto_tbox_reboot_desc),
            isAutoRestartEnabled
        )

        SettingsTitle(stringResource(R.string.settings_prevent_reboot_title))
        SettingSwitch(
            isAutoSuspendTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxAppSetting(enabled)
                if (enabled && isAutoStopTboxAppEnabled) {
                    settingsViewModel.saveAutoStopTboxAppSetting(false)
                    showAlertDialog(
                        warningTitle,
                        warningSuspendStop,
                        context
                    )
                }
            },
            stringResource(R.string.settings_auto_suspend_app_title),
            stringResource(R.string.settings_auto_suspend_app_desc),
            true
        )
        SettingSwitch(
            isAutoStopTboxAppEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxAppSetting(enabled)
                if (enabled && isAutoSuspendTboxAppEnabled) {
                    settingsViewModel.saveAutoSuspendTboxAppSetting(false)
                    showAlertDialog(
                        warningTitle,
                        warningSuspendStop,
                        context
                    )
                }
            },
            stringResource(R.string.settings_auto_stop_app_title),
            stringResource(R.string.settings_auto_stop_app_desc),
            true
        )

        SettingSwitch(
            isAutoSuspendTboxMdcEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxMdcSetting(enabled)
                if (enabled && isAutoStopTboxMdcEnabled) {
                    settingsViewModel.saveAutoStopTboxMdcSetting(false)
                    showAlertDialog(
                        warningTitle,
                        warningSuspendStop,
                        context
                    )
                }
            },
            stringResource(R.string.settings_auto_suspend_mdc_title),
            stringResource(R.string.settings_auto_suspend_mdc_desc),
            true
        )
        SettingSwitch(
            isAutoStopTboxMdcEnabled,
            { enabled ->
                settingsViewModel.saveAutoStopTboxMdcSetting(enabled)
                if (enabled && isAutoSuspendTboxMdcEnabled) {
                    settingsViewModel.saveAutoSuspendTboxMdcSetting(false)
                    showAlertDialog(
                        warningTitle,
                        warningSuspendStop,
                        context
                    )
                }
            },
            stringResource(R.string.settings_auto_stop_mdc_title),
            stringResource(R.string.settings_auto_stop_mdc_desc),
            true
        )

        SettingSwitch(
            isAutoSuspendTboxSwdEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxSwdSetting(enabled)
            },
            stringResource(R.string.settings_auto_suspend_swd_title),
            "",
            true
        )

        SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            stringResource(R.string.settings_auto_prevent_restart_swd_title),
            stringResource(R.string.settings_auto_prevent_restart_swd_desc),
            true
        )

        SettingsTitle(stringResource(R.string.settings_floating_panels_title))
        FloatingDashboardProfileSelector(
            selectedId = activeFloatingDashboardId,
            onSelect = { panelId ->
                settingsViewModel.saveSelectedFloatingDashboardId(panelId)
            },
            modifier = Modifier.padding(bottom = 8.dp)
        )
        SettingSwitch(
            isFloatingDashboardEnabled,
            { enabled ->
                if (enabled) {
                    if (Settings.canDrawOverlays(context)) {
                        settingsViewModel.saveFloatingDashboardSetting(true)
                    } else {
                        showOverlayRequirementsDialog(context)
                    }
                } else {
                    settingsViewModel.saveFloatingDashboardSetting(false)
                }
            },
            stringResource(R.string.settings_show_floating_panel_title),
            "",
            true
        )
        SettingSwitch(
            isFloatingDashboardBackground,
            { enabled ->
                settingsViewModel.saveFloatingDashboardBackground(enabled)
            },
            stringResource(R.string.settings_enable_floating_bg_title),
            stringResource(R.string.settings_enable_floating_bg_desc),
            true
        )
        SettingSwitch(
            isFloatingDashboardClickAction,
            { enabled ->
                settingsViewModel.saveFloatingDashboardClickAction(enabled)
            },
            stringResource(R.string.settings_open_app_on_panel_click_title),
            "",
            true
        )
        SettingDropdownGeneric(
            floatingDashboardRows,
            { rows ->
                settingsViewModel.saveFloatingDashboardRows(rows)
            },
            stringResource(R.string.settings_floating_rows_title),
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        SettingDropdownGeneric(
            floatingDashboardCols,
            { cols ->
                settingsViewModel.saveFloatingDashboardCols(cols)
            },
            stringResource(R.string.settings_floating_cols_title),
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        FloatingDashboardPositionSizeSettings(settingsViewModel, Modifier)

        SettingsTitle(stringResource(R.string.settings_overlay_widgets_title))
        SettingSwitch(
            isWidgetShowIndicatorEnabled,
            { enabled ->
                settingsViewModel.saveWidgetShowIndicatorSetting(enabled)
            },
            stringResource(R.string.settings_widget_connection_indicator_title),
            stringResource(R.string.settings_widget_connection_indicator_desc),
            true
        )
        SettingSwitch(
            isWidgetShowLocIndicatorEnabled,
            { enabled ->
                settingsViewModel.saveWidgetShowLocIndicatorSetting(enabled)
            },
            stringResource(R.string.settings_widget_location_indicator_title),
            stringResource(R.string.settings_widget_location_indicator_desc),
            isGetLocDataEnabled
        )

        SettingsTitle(stringResource(R.string.settings_dashboard_screen_title))
        SettingSwitch(
            dashboardChart,
            { enabled ->
                settingsViewModel.saveDashboardChart(enabled)
            },
            stringResource(R.string.settings_dashboard_chart_title),
            "",
            true
        )
        SettingDropdownGeneric(
            dashboardRows,
            { rows ->
                settingsViewModel.saveDashboardRows(rows)
            },
            stringResource(R.string.settings_dashboard_rows_title),
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )
        SettingDropdownGeneric(
            dashboardCols,
            { cols ->
                settingsViewModel.saveDashboardCols(cols)
            },
            stringResource(R.string.settings_dashboard_cols_title),
            "",
            true,
            listOf(1, 2, 3, 4, 5, 6)
        )

        SettingsTitle(stringResource(R.string.settings_data_from_tbox_title))
        SettingSwitch(
            isGetCanFrameEnabled,
            { enabled ->
                settingsViewModel.saveGetCanFrameSetting(enabled)
            },
            stringResource(R.string.settings_get_can_data_title),
            "",
            true
        )
        SettingSwitch(
            isGetLocDataEnabled,
            { enabled ->
                settingsViewModel.saveGetLocDataSetting(enabled)
            },
            stringResource(R.string.settings_get_geo_data_title),
            "",
            true
        )

        SettingsTitle(stringResource(R.string.settings_misc_title))
        SettingSwitch(
            isExpertModeEnabled,
            { enabled ->
                settingsViewModel.saveExpertModeSetting(enabled)
                if (!enabled) {
                    settingsViewModel.saveMockLocationSetting(false)
                } else {
                    showAlertDialog(
                        warningTitle,
                        expertModeWarning,
                        context
                    )
                }
            },
            stringResource(R.string.settings_expert_mode_title),
            "",
            true
        )

        if (isExpertModeEnabled) {
            SettingInt(
                canDataSaveCount,
                { value ->
                    settingsViewModel.saveCanDataSaveCount(value)
                },
                stringResource(R.string.settings_can_frames_count_title),
                "",
                1,
                3600
            )
            SettingSwitch(
                isTboxIPRotation,
                { enabled ->
                    settingsViewModel.saveTboxIPRotationSetting(enabled)
                },
                stringResource(R.string.settings_search_other_ip_title),
                "",
                true
            )
            SettingSwitch(
                isMockLocationEnabled,
                { enabled ->
                    onMockLocationSettingChanged(enabled)
                },
                stringResource(R.string.settings_mock_location_title),
                if (canUseMockLocation) {
                    stringResource(R.string.settings_mock_location_ready)
                } else {
                    stringResource(R.string.settings_mock_location_requirements)
                },
                true
            )

            if (!canUseMockLocation) {
                Text(
                    text = stringResource(R.string.settings_mock_location_requirements_link),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { showLocationRequirementsDialog(context) }
                        .padding(top = 4.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = {
                    if (restartButtonEnabled) {
                        restartButtonEnabled = false
                        onTboxRestartClick()
                    }
                },
                enabled = restartButtonEnabled && tboxConnected
            ) {
                Text(
                    text = stringResource(R.string.button_reboot_tbox),
                    fontSize = 24.sp,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun showAlertDialog(title: String, message: String, context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle(title)
        .setMessage(message)
        .setNeutralButton(context.getString(R.string.action_close), null)
        .show()
}

private fun showLocationRequirementsDialog(context: Context) {
    val status = MockLocationUtils.checkMockLocationCapabilities(context)

    val requirements = buildString {
        if (!status.hasLocationPermissions) {
            append(context.getString(R.string.dialog_mock_location_missing_permission))
        }
        if (!status.isMockLocationEnabled) {
            append(context.getString(R.string.dialog_mock_location_not_enabled))
        }
        if (!status.canAddTestProvider) {
            append(context.getString(R.string.dialog_mock_location_provider_missing))
        }
    }

    android.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.dialog_mock_location_requirements_title))
        .setMessage(requirements)
        .setPositiveButton(context.getString(R.string.action_configure)) { _, _ ->
            val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
            context.startActivity(intent)
        }
        .setNegativeButton(context.getString(R.string.action_cancel), null)
        .show()
}

private fun showOverlayRequirementsDialog(context: Context) {
    android.app.AlertDialog.Builder(context)
        .setTitle(context.getString(R.string.dialog_overlay_permission_required_title))
        .setMessage(context.getString(R.string.dialog_overlay_permission_required_message))
        .setPositiveButton(context.getString(R.string.action_configure)) { _, _ ->
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            )
            context.startActivity(intent)
        }
        .setNegativeButton(context.getString(R.string.action_cancel), null)
        .show()
}

@Composable
fun LocationTabContent(
    viewModel: TboxViewModel
) {
    val yesLabel = stringResource(R.string.value_yes)
    val noLabel = stringResource(R.string.value_no)
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val isLocValuesTrue by viewModel.isLocValuesTrue.collectAsStateWithLifecycle()

    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val dateTime = locValues.utcTime?.formatDateTime() ?: ""

    val lastUpdate = remember(locValues.updateTime) {
        locValues.updateTime?.let { updateTime ->
            timeFormat.format(updateTime)
        } ?: ""
    }

    val lastRefresh = remember(locationUpdateTime) {
        locationUpdateTime?.let { locationUpdateTime ->
            timeFormat.format(locationUpdateTime)
        } ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item { StatusRow(stringResource(R.string.location_last_update), lastRefresh) }
            item { StatusRow(stringResource(R.string.location_last_change), lastUpdate) }
            item { StatusRow(stringResource(R.string.location_fixation), if (locValues.locateStatus) yesLabel else noLabel) }
            item { StatusRow(stringResource(R.string.location_truth), if (isLocValuesTrue) yesLabel else noLabel) }
            item { StatusRow(stringResource(R.string.location_longitude), locValues.longitude.toString()) }
            item { StatusRow(stringResource(R.string.location_latitude), locValues.latitude.toString()) }
            item { StatusRow(stringResource(R.string.location_altitude), locValues.altitude.toString()) }
            item { StatusRow(stringResource(R.string.location_visible_satellites), locValues.visibleSatellites.toString()) }
            item { StatusRow(stringResource(R.string.location_used_satellites), locValues.usingSatellites.toString()) }
            item { StatusRow(stringResource(R.string.location_speed), String.format(Locale.getDefault(), "%.1f", locValues.speed)) }
            item { StatusRow(stringResource(R.string.location_true_direction), String.format(Locale.getDefault(), "%.1f", locValues.trueDirection)) }
            item { StatusRow(stringResource(R.string.location_magnetic_direction), String.format(Locale.getDefault(), "%.1f", locValues.magneticDirection)) }
            item { StatusRow(stringResource(R.string.location_utc), dateTime) }
            item { StatusRow(stringResource(R.string.location_raw_data), locValues.rawValue) }
        }
    }
}

@Composable
fun InfoTabContent(
    viewModel: TboxViewModel,
    settingsViewModel: SettingsViewModel,
    onServiceCommand: (String, String, String) -> Unit
) {
    val yesLabel = stringResource(R.string.value_yes)
    val noLabel = stringResource(R.string.value_no)
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val preventRestartSend by viewModel.preventRestartSend.collectAsStateWithLifecycle()
    val tboxAppSuspended by viewModel.tboxAppSuspended.collectAsStateWithLifecycle()
    val tboxMdcSuspended by viewModel.tboxMdcSuspended.collectAsStateWithLifecycle()
    val tboxSwdSuspended by viewModel.tboxSwdSuspended.collectAsStateWithLifecycle()
    val tboxAppStoped by viewModel.tboxAppStoped.collectAsStateWithLifecycle()
    val tboxMdcStoped by viewModel.tboxMdcStoped.collectAsStateWithLifecycle()
    val ipList by viewModel.ipList.collectAsStateWithLifecycle()

    val appVersion by settingsViewModel.appVersion.collectAsStateWithLifecycle()
    val mdcVersion by settingsViewModel.mdcVersion.collectAsStateWithLifecycle()
    val swdVersion by settingsViewModel.swdVersion.collectAsStateWithLifecycle()
    val crtVersion by settingsViewModel.crtVersion.collectAsStateWithLifecycle()
    val locVersion by settingsViewModel.locVersion.collectAsStateWithLifecycle()
    val swVersion by settingsViewModel.swVersion.collectAsStateWithLifecycle()
    val hwVersion by settingsViewModel.hwVersion.collectAsStateWithLifecycle()
    val vinCode by settingsViewModel.vinCode.collectAsStateWithLifecycle()
    val tboxIP by settingsViewModel.tboxIP.collectAsStateWithLifecycle()

    var updateVersionButtonEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(updateVersionButtonEnabled) {
        if (!updateVersionButtonEnabled) {
            delay(30000)
            updateVersionButtonEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_suspend_app),
                    if (tboxAppSuspended) yesLabel else noLabel
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_suspend_mdc),
                    if (tboxMdcSuspended) yesLabel else noLabel
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_suspend_swd),
                    if (tboxSwdSuspended) yesLabel else noLabel
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_stop_app),
                    if (tboxAppStoped) yesLabel else noLabel
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_stop_mdc),
                    if (tboxMdcStoped) yesLabel else noLabel
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.info_confirm_prevent_restart_swd),
                    if (preventRestartSend) yesLabel else noLabel
                )
            }
            item { StatusRow(stringResource(R.string.info_saved_tbox_ip), tboxIP) }
            item { StatusRow(stringResource(R.string.info_possible_tbox_ips), ipList.joinToString("; ")) }
            item { StatusRow(stringResource(R.string.info_app_version_app), appVersion) }
            item { StatusRow(stringResource(R.string.info_app_version_crt), crtVersion) }
            item { StatusRow(stringResource(R.string.info_app_version_loc), locVersion) }
            item { StatusRow(stringResource(R.string.info_app_version_mdc), mdcVersion) }
            item { StatusRow(stringResource(R.string.info_app_version_swd), swdVersion) }
            item { StatusRow(stringResource(R.string.info_sw_version), swVersion) }
            item { StatusRow(stringResource(R.string.info_hw_version), hwVersion) }
            item { StatusRow(stringResource(R.string.info_vin), vinCode) }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (updateVersionButtonEnabled) {
                                updateVersionButtonEnabled = false
                                onServiceCommand(
                                    BackgroundService.ACTION_GET_INFO,
                                    "",
                                    ""
                                )
                            }
                        },
                        enabled = updateVersionButtonEnabled && tboxConnected
                    ) {
                        Text(
                            text = stringResource(R.string.button_request_tbox_info),
                            fontSize = 24.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
