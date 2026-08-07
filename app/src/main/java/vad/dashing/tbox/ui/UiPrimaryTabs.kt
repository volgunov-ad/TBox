package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.TboxTextStyles
import android.content.Context
import android.content.Intent
import android.provider.Settings
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import vad.dashing.tbox.AppDataViewModel
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.CanDataRepository
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsManager
import vad.dashing.tbox.MIN_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MAX_PANEL_GRID_SPACING_DP
import vad.dashing.tbox.MIN_PANEL_LAYOUT_SNAP_DP
import vad.dashing.tbox.MAX_PANEL_LAYOUT_SNAP_DP
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.update.UpdateChannel
import vad.dashing.tbox.update.UpdateViewModel
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanDiagnostics
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.valueToString
import java.text.SimpleDateFormat
import java.util.Locale
import vad.dashing.tbox.utils.MockLocationUtils
import vad.dashing.tbox.utils.canUseMockLocation
import vad.dashing.tbox.utils.isAppSelectedAsMockProvider
import vad.dashing.tbox.esp.EspCompanionProtocol
import vad.dashing.tbox.esp.EspCompanionRepository
import vad.dashing.tbox.esp.LocationSource
import vad.dashing.tbox.usbgnss.GnssModuleCommands
import vad.dashing.tbox.usbgnss.GnssModuleFamily
import vad.dashing.tbox.usbgnss.UsbGnssDevice
import vad.dashing.tbox.usbgnss.UsbGnssDeviceIds
import vad.dashing.tbox.usbgnss.UsbGnssDeviceScanner
import vad.dashing.tbox.usbgnss.UsbGnssRepository
import vad.dashing.tbox.drsensor.DrSensorRepository
import vad.dashing.tbox.location.GeoDisplayRepository
import vad.dashing.tbox.location.LocationIncomingBitRate
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.SystemClock
import kotlinx.coroutines.isActive

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
            .padding(18.dp)
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
            style = MaterialTheme.typography.tboxTitle,
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
    appDataViewModel: AppDataViewModel,
    updateViewModel: UpdateViewModel,
    onTboxRestartClick: () -> Unit,
    onServiceCommand: (String, String, String) -> Unit,
    onExportSettingsBackup: () -> Unit,
    onExportSettingsBackupWithoutTrips: () -> Unit,
    onImportSettingsBackup: () -> Unit,
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
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    val isGetCycleSignalEnabled by settingsViewModel.isGetCycleSignalEnabled.collectAsStateWithLifecycle()
    val isWidgetShowIndicatorEnabled by settingsViewModel.isWidgetShowIndicatorEnabled.collectAsStateWithLifecycle()
    val isWidgetShowLocIndicatorEnabled by settingsViewModel.isWidgetShowLocIndicatorEnabled.collectAsStateWithLifecycle()
    val isExpertModeEnabled by settingsViewModel.isExpertModeEnabled.collectAsStateWithLifecycle()
    val headUnitCanMode by settingsViewModel.headUnitCanMode.collectAsStateWithLifecycle()
    val isMbCanDiagnosticsEnabled by MbCanDiagnostics.enabled.collectAsStateWithLifecycle()

    val dashboardCols by settingsViewModel.dashboardCols.collectAsStateWithLifecycle()
    val dashboardRows by settingsViewModel.dashboardRows.collectAsStateWithLifecycle()
    val dashboardGridSpacingDp by settingsViewModel.dashboardGridSpacingDp.collectAsStateWithLifecycle()
    val dashboardChart by settingsViewModel.dashboardChart.collectAsStateWithLifecycle()

    val canDataSaveCount by settingsViewModel.canDataSaveCount.collectAsStateWithLifecycle()
    val fuelTankLiters by settingsViewModel.fuelTankLiters.collectAsStateWithLifecycle()
    var miscTankLitersDraft by remember { mutableStateOf(fuelTankLiters.toString()) }
    LaunchedEffect(fuelTankLiters) {
        miscTankLitersDraft = fuelTankLiters.toString()
    }
    val splitTripTimeMinutes by settingsViewModel.splitTripTimeMinutes.collectAsStateWithLifecycle()
    val trackRefuels by settingsViewModel.trackRefuels.collectAsStateWithLifecycle()
    val wheelPressurePersistAcrossStops by settingsViewModel.wheelPressurePersistAcrossStops.collectAsStateWithLifecycle()
    val uiClickSoundsEnabled by settingsViewModel.uiClickSoundsEnabled.collectAsStateWithLifecycle()
    val appFontFamilyId by settingsViewModel.appFontFamilyId.collectAsStateWithLifecycle()
    val updateChannel by settingsViewModel.updateChannel.collectAsStateWithLifecycle()
    val updateCheckEnabled by settingsViewModel.updateCheckEnabled.collectAsStateWithLifecycle()

    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    val context = LocalContext.current
    val warningTitle = stringResource(R.string.warning_title)
    val warningSuspendStop = stringResource(R.string.warning_suspend_stop_manual_reboot)
    val expertModeWarning = stringResource(R.string.settings_expert_mode_warning_desc)

    LaunchedEffect(headUnitCanMode) {
        UniversalCanRepository.setMode(headUnitCanMode)
        UniversalCanRepository.warmUpAvailabilityForUi()
    }

    var restartButtonEnabled by remember { mutableStateOf(true) }

    var backgroundServiceRestartButtonEnabled by remember { mutableStateOf(true) }

    var huRebootButtonEnabled by remember { mutableStateOf(true) }
    val mbCanAvailability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val mbCanAvailable = mbCanAvailability is MbCanAvailability.Available

    var showExportBackupDialog by remember { mutableStateOf(false) }
    var showExportBackupNoTripsDialog by remember { mutableStateOf(false) }
    var showImportBackupDialog by remember { mutableStateOf(false) }
    var showLeftMenuConfigDialog by remember { mutableStateOf(false) }
    var showNoTboxConnectCanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(restartButtonEnabled) {
        if (!restartButtonEnabled) {
            delay(15000)
            restartButtonEnabled = true
        }
    }

    LaunchedEffect(backgroundServiceRestartButtonEnabled) {
        if (!backgroundServiceRestartButtonEnabled) {
            delay(15000)
            backgroundServiceRestartButtonEnabled = true
        }
    }

    LaunchedEffect(huRebootButtonEnabled) {
        if (!huRebootButtonEnabled) {
            delay(300_000L)
            huRebootButtonEnabled = true
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        Button(
            onClick = rememberWrappedOnClick { settingsViewModel.openPermissionsDialog() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_permissions_button),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        Text(
            text = stringResource(R.string.settings_permissions_button_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsTitle(stringResource(R.string.settings_hu_type_title))
        Text(
            text = stringResource(R.string.settings_hu_type_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ModeButton(
                text = stringResource(R.string.settings_hu_type_android9),
                isSelected = headUnitCanMode == HeadUnitCanMode.Android9MbCan,
                onClick = { settingsViewModel.saveHeadUnitCanMode(HeadUnitCanMode.Android9MbCan) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )
            ModeButton(
                text = stringResource(R.string.settings_hu_type_android10),
                isSelected = headUnitCanMode == HeadUnitCanMode.Android10Vhal,
                onClick = { settingsViewModel.saveHeadUnitCanMode(HeadUnitCanMode.Android10Vhal) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        SettingsTitle(stringResource(R.string.settings_network_control_title))
        SettingSwitch(
            isAutoRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoRestartSetting(enabled)
            },
            stringResource(R.string.settings_auto_modem_restart_title),
            stringResource(R.string.settings_auto_modem_restart_desc),
            !noTboxConnect
        )
        SettingSwitch(
            isAutoTboxRebootEnabled,
            { enabled ->
                settingsViewModel.saveAutoTboxRebootSetting(enabled)
            },
            stringResource(R.string.settings_auto_tbox_reboot_title),
            stringResource(R.string.settings_auto_tbox_reboot_desc),
            !noTboxConnect && isAutoRestartEnabled
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
            !noTboxConnect
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
            !noTboxConnect
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
            !noTboxConnect
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
            !noTboxConnect
        )

        SettingSwitch(
            isAutoSuspendTboxSwdEnabled,
            { enabled ->
                settingsViewModel.saveAutoSuspendTboxSwdSetting(enabled)
            },
            stringResource(R.string.settings_auto_suspend_swd_title),
            "",
            !noTboxConnect
        )

        SettingSwitch(
            isAutoPreventTboxRestartEnabled,
            { enabled ->
                settingsViewModel.saveAutoPreventTboxRestartSetting(enabled)
            },
            stringResource(R.string.settings_auto_prevent_restart_swd_title),
            stringResource(R.string.settings_auto_prevent_restart_swd_desc),
            !noTboxConnect
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
            true
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
            SettingsManager.MAIN_TAB_DASHBOARD_GRID_OPTIONS
        )
        SettingDropdownGeneric(
            dashboardCols,
            { cols ->
                settingsViewModel.saveDashboardCols(cols)
            },
            stringResource(R.string.settings_dashboard_cols_title),
            "",
            true,
            SettingsManager.MAIN_TAB_DASHBOARD_GRID_OPTIONS
        )
        SettingSliderInt(
            value = dashboardGridSpacingDp,
            onValueChange = { settingsViewModel.saveDashboardGridSpacingDp(it) },
            text = stringResource(
                R.string.settings_dashboard_grid_spacing_title,
                dashboardGridSpacingDp,
            ),
            description = stringResource(R.string.settings_dashboard_grid_spacing_desc),
            minValue = MIN_PANEL_GRID_SPACING_DP,
            maxValue = MAX_PANEL_GRID_SPACING_DP,
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_data_from_tbox_title))
        SettingSwitch(
            noTboxConnect,
            { enabled ->
                if (enabled) {
                    showNoTboxConnectCanDialog = true
                } else {
                    settingsViewModel.saveNoTboxConnectSetting(false)
                }
            },
            stringResource(R.string.settings_no_tbox_connect_title),
            stringResource(R.string.settings_no_tbox_connect_desc),
            true
        )
        SettingSwitch(
            isGetCanFrameEnabled,
            { enabled ->
                settingsViewModel.saveGetCanFrameSetting(enabled)
            },
            stringResource(R.string.settings_get_can_data_title),
            "",
            !noTboxConnect
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_left_menu_title))
        Button(
            onClick = rememberWrappedOnClick { showLeftMenuConfigDialog = true },
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Text(stringResource(R.string.settings_left_menu_edit), style = MaterialTheme.typography.tboxButton)
        }
        SettingAppFontFamily(
            selectedFontFamilyId = appFontFamilyId,
            onFontFamilyIdChange = { settingsViewModel.saveAppFontFamilyId(it) },
            text = stringResource(R.string.settings_app_font_family_title),
            description = stringResource(R.string.settings_app_font_family_desc),
            enabled = true,
            selectorWidth = 250.dp
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_misc_title))
        CalibrationIntCommitField(
            title = stringResource(R.string.settings_fuel_tank_liters_title),
            description = stringResource(R.string.refuels_calibration_tank_hint),
            draft = miscTankLitersDraft,
            onDraftChange = { miscTankLitersDraft = it },
            savedValue = fuelTankLiters,
            minValue = 1,
            maxValue = 500,
            onCommit = { value ->
                appDataViewModel.applyFuelTankChangeWithCalibrationReset(value)
            },
        )
        SettingInt(
            splitTripTimeMinutes,
            { value -> settingsViewModel.saveSplitTripTimeMinutes(value) },
            stringResource(R.string.settings_split_trip_time_title),
            "",
            1,
            100000
        )
        SettingSwitch(
            trackRefuels,
            { enabled -> settingsViewModel.saveTrackRefuels(enabled) },
            stringResource(R.string.settings_track_refuels_title),
            stringResource(R.string.settings_track_refuels_desc),
            true
        )
        SettingSwitch(
            wheelPressurePersistAcrossStops,
            { enabled -> settingsViewModel.saveWheelPressurePersistAcrossStops(enabled) },
            stringResource(R.string.settings_wheel_pressure_persist_title),
            stringResource(R.string.settings_wheel_pressure_persist_desc),
            true
        )
        SettingSwitch(
            uiClickSoundsEnabled,
            { enabled -> settingsViewModel.saveUiClickSoundsEnabled(enabled) },
            stringResource(R.string.settings_ui_click_sounds_title),
            stringResource(R.string.settings_ui_click_sounds_desc),
            true
        )
        SettingSwitch(
            isExpertModeEnabled,
            { enabled ->
                settingsViewModel.saveExpertModeSetting(enabled)
                if (enabled) {
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
            SettingSwitch(
                isMbCanDiagnosticsEnabled,
                { enabled ->
                    setMbCanDiagnostics(context, enabled)
                },
                stringResource(R.string.settings_mbcan_diagnostics_title),
                stringResource(R.string.settings_mbcan_diagnostics_desc),
                true
            )
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
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_update_title))
        SettingSwitch(
            updateCheckEnabled,
            { enabled ->
                settingsViewModel.saveUpdateCheckEnabled(enabled)
            },
            stringResource(R.string.settings_update_check_enabled_title),
            stringResource(R.string.settings_update_check_enabled_desc),
            true,
        )
        val releaseChannelLabel = stringResource(R.string.update_channel_release)
        val developmentChannelLabel = stringResource(R.string.update_channel_development)
        val updateChannelOptions = remember(releaseChannelLabel, developmentChannelLabel) {
            listOf(
                UpdateChannelDropdownOption(UpdateChannel.RELEASE, releaseChannelLabel),
                UpdateChannelDropdownOption(UpdateChannel.DEVELOPMENT, developmentChannelLabel),
            )
        }
        val selectedUpdateChannelOption = remember(updateChannel, updateChannelOptions) {
            updateChannelOptions.first { it.channel == updateChannel }
        }
        SettingDropdownGeneric(
            selectedValue = selectedUpdateChannelOption,
            onValueChange = { option ->
                updateViewModel.saveUpdateChannel(option.channel)
            },
            text = stringResource(R.string.settings_update_channel_title),
            description = stringResource(R.string.settings_update_channel_desc),
            enabled = true,
            options = updateChannelOptions,
            selectorWidth = 250.dp,
        )
        Button(
            onClick = rememberWrappedOnClick { updateViewModel.checkForUpdate(force = true) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_update_check_button),
                style = MaterialTheme.typography.tboxButton,
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SettingsTitle(stringResource(R.string.settings_backup_title))
        Text(
            text = stringResource(R.string.settings_backup_desc),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = rememberWrappedOnClick { showExportBackupDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_export),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = rememberWrappedOnClick { showImportBackupDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.settings_backup_import),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
        Button(
            onClick = rememberWrappedOnClick { showExportBackupNoTripsDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_backup_export_without_trips),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
                textAlign = TextAlign.Center
            )
        }

        if (showNoTboxConnectCanDialog) {
            AlertDialog(
                onDismissRequest = { showNoTboxConnectCanDialog = false },
                title = {
                    AppAlertDialogTitle(stringResource(R.string.settings_no_tbox_connect_can_dialog_title))
                },
                text = {
                    AppAlertDialogText(stringResource(R.string.settings_no_tbox_connect_can_dialog_message))
                },
                confirmButton = {
                    Button(
                        onClick = rememberWrappedOnClick {
                            settingsViewModel.saveNoTboxConnectSetting(
                                enabled = true,
                                enableUseMbCanVhalOnTiles = true,
                            )
                            showNoTboxConnectCanDialog = false
                        }
                    ) {
                        AppAlertDialogButtonLabel(
                            stringResource(R.string.settings_no_tbox_connect_can_dialog_yes),
                        )
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            settingsViewModel.saveNoTboxConnectSetting(
                                enabled = true,
                                enableUseMbCanVhalOnTiles = false,
                            )
                            showNoTboxConnectCanDialog = false
                        }
                    ) {
                        AppAlertDialogButtonLabel(
                            stringResource(R.string.settings_no_tbox_connect_can_dialog_no),
                        )
                    }
                }
            )
        }

        if (showExportBackupDialog) {
            AlertDialog(
                onDismissRequest = { showExportBackupDialog = false },
                title = { AppAlertDialogTitle(stringResource(R.string.dialog_file_saving_title)) },
                text = { AppAlertDialogText(stringResource(R.string.dialog_save_backup_downloads)) },
                confirmButton = {
                    Button(
                        onClick = rememberWrappedOnClick {
                            onExportSettingsBackup()
                            showExportBackupDialog = false
                        }
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = rememberWrappedOnClick { showExportBackupDialog = false }) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showExportBackupNoTripsDialog) {
            AlertDialog(
                onDismissRequest = { showExportBackupNoTripsDialog = false },
                title = { AppAlertDialogTitle(stringResource(R.string.dialog_file_saving_title)) },
                text = { AppAlertDialogText(stringResource(R.string.dialog_save_backup_downloads_no_trips)) },
                confirmButton = {
                    Button(
                        onClick = rememberWrappedOnClick {
                            onExportSettingsBackupWithoutTrips()
                            showExportBackupNoTripsDialog = false
                        }
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_save))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = rememberWrappedOnClick { showExportBackupNoTripsDialog = false }) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        if (showImportBackupDialog) {
            AlertDialog(
                onDismissRequest = { showImportBackupDialog = false },
                title = { AppAlertDialogTitle(stringResource(R.string.dialog_backup_import_title)) },
                text = { AppAlertDialogText(stringResource(R.string.dialog_backup_import_message)) },
                confirmButton = {
                    Button(
                        onClick = rememberWrappedOnClick {
                            onImportSettingsBackup()
                            showImportBackupDialog = false
                        }
                    ) {
                        AppAlertDialogButtonLabel(stringResource(R.string.settings_backup_import_choose_file))
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = rememberWrappedOnClick { showImportBackupDialog = false }) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
                    }
                }
            )
        }

        LeftMenuConfigDialog(
            settingsViewModel = settingsViewModel,
            visible = showLeftMenuConfigDialog,
            onDismiss = { showLeftMenuConfigDialog = false },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        Button(
            onClick = rememberWrappedOnClick {
                if (backgroundServiceRestartButtonEnabled) {
                    backgroundServiceRestartButtonEnabled = false
                    onServiceCommand(
                        BackgroundService.ACTION_RESTART,
                        "",
                        "",
                    )
                }
            },
            enabled = backgroundServiceRestartButtonEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.button_restart_background_service),
                style = MaterialTheme.typography.tboxButton,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = rememberWrappedOnClick {
                    if (restartButtonEnabled) {
                        restartButtonEnabled = false
                        onTboxRestartClick()
                    }
                },
                enabled = restartButtonEnabled && tboxConnected,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.button_reboot_tbox),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
            Button(
                onClick = rememberWrappedOnClick {
                    if (huRebootButtonEnabled) {
                        huRebootButtonEnabled = false
                        sendSetMbCanProperty(
                            context,
                            MbCanKnownVehiclePropertyId.SYSTEM_REBOOT,
                            MbCanKnownVehiclePropertyId.SYSTEM_REBOOT_VALUE,
                        )
                    }
                },
                enabled = huRebootButtonEnabled &&
                    mbCanAvailable &&
                    headUnitCanMode == HeadUnitCanMode.Android9MbCan,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.button_reboot_hu),
                    style = MaterialTheme.typography.tboxButton,
                    maxLines = 2,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun FloatingPanelsSettingsTabContent(
    settingsViewModel: SettingsViewModel,
) {
    val isFloatingDashboardEnabled by settingsViewModel.isFloatingDashboardEnabled.collectAsStateWithLifecycle()
    val isFloatingDashboardClickAction by settingsViewModel.isFloatingDashboardClickAction.collectAsStateWithLifecycle()
    val isFloatingDashboardShowTboxDisconnectIndicator by
        settingsViewModel.isFloatingDashboardShowTboxDisconnectIndicator.collectAsStateWithLifecycle()
    val floatingDashboardsList by settingsViewModel.floatingDashboards.collectAsStateWithLifecycle()
    val hasFloatingPanels = floatingDashboardsList.isNotEmpty()
    val floatingDashboardRows by settingsViewModel.floatingDashboardRows.collectAsStateWithLifecycle()
    val floatingDashboardCols by settingsViewModel.floatingDashboardCols.collectAsStateWithLifecycle()
    val floatingDashboardGridSpacingDp by
        settingsViewModel.floatingDashboardGridSpacingDp.collectAsStateWithLifecycle()
    val floatingPanelsLayoutSnapDp by
        settingsViewModel.floatingPanelsLayoutSnapDp.collectAsStateWithLifecycle()
    val activeFloatingDashboardId by settingsViewModel.activeFloatingDashboardId.collectAsStateWithLifecycle()
    val floatingPanelDeleteInProgressId by settingsViewModel.floatingPanelDeleteInProgressId.collectAsStateWithLifecycle()
    val widgetColorPresetSlots by settingsViewModel.widgetColorPresetSlots.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val newFloatingPanelDefaultName = stringResource(R.string.floating_dashboard_new_panel_default)

    var showUsageStatsHideFloatingDialog by remember { mutableStateOf(false) }
    var showFloatingPanelOrderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp),
    ) {
        SettingsTitle(stringResource(R.string.settings_floating_panels_title))
        if (hasFloatingPanels) {
            FloatingDashboardPanelEditor(
                panels = floatingDashboardsList,
                selectedPanelId = activeFloatingDashboardId,
                onSelectPanelId = { panelId ->
                    settingsViewModel.saveSelectedFloatingDashboardId(panelId)
                },
                onRenamePanel = { panelId, name ->
                    settingsViewModel.saveFloatingDashboardName(panelId, name)
                },
                onAddPanel = {
                    settingsViewModel.addFloatingDashboard(newFloatingPanelDefaultName)
                },
                onDeletePanel = { panelId ->
                    settingsViewModel.deleteFloatingDashboard(panelId)
                },
                deleteInProgressPanelId = floatingPanelDeleteInProgressId,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedButton(
                onClick = rememberWrappedOnClick { showFloatingPanelOrderDialog = true },
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
                    settingsViewModel.addFloatingDashboard(newFloatingPanelDefaultName)
                },
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                Text(stringResource(R.string.action_add), style = MaterialTheme.typography.tboxButton)
            }
        }
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
            hasFloatingPanels,
        )
        SettingSwitch(
            isFloatingDashboardClickAction,
            { enabled ->
                settingsViewModel.saveFloatingDashboardClickAction(enabled)
            },
            stringResource(R.string.settings_open_app_on_panel_click_title),
            "",
            hasFloatingPanels,
        )
        SettingSwitch(
            isFloatingDashboardShowTboxDisconnectIndicator,
            { enabled ->
                settingsViewModel.saveFloatingDashboardShowTboxDisconnectIndicator(enabled)
            },
            stringResource(R.string.settings_floating_tbox_disconnect_indicator_title),
            "",
            hasFloatingPanels,
        )
        SettingDropdownGeneric(
            floatingDashboardRows,
            { rows ->
                settingsViewModel.saveFloatingDashboardRows(rows)
            },
            stringResource(R.string.settings_floating_rows_title),
            "",
            hasFloatingPanels,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS,
        )
        SettingDropdownGeneric(
            floatingDashboardCols,
            { cols ->
                settingsViewModel.saveFloatingDashboardCols(cols)
            },
            stringResource(R.string.settings_floating_cols_title),
            "",
            hasFloatingPanels,
            SettingsManager.DASHBOARD_PANEL_GRID_OPTIONS,
        )
        SettingSliderInt(
            value = floatingDashboardGridSpacingDp,
            onValueChange = { settingsViewModel.saveFloatingDashboardGridSpacingDp(it) },
            text = stringResource(
                R.string.settings_panel_grid_spacing_title,
                floatingDashboardGridSpacingDp,
            ),
            description = stringResource(R.string.settings_panel_grid_spacing_desc),
            minValue = MIN_PANEL_GRID_SPACING_DP,
            maxValue = MAX_PANEL_GRID_SPACING_DP,
            enabled = hasFloatingPanels,
        )
        val selectedFloatingPanel = remember(floatingDashboardsList, activeFloatingDashboardId) {
            floatingDashboardsList.firstOrNull { it.id == activeFloatingDashboardId }
                ?: floatingDashboardsList.firstOrNull()
        }
        if (selectedFloatingPanel != null) {
            var panelBgThemeSegment by remember(selectedFloatingPanel.id) { mutableIntStateOf(0) }
            PanelBackgroundAppearanceSettingsSection(
                panelStorageId = selectedFloatingPanel.id,
                enabled = hasFloatingPanels,
                colorThemeSegment = panelBgThemeSegment,
                onColorThemeSegmentChange = { panelBgThemeSegment = it },
                backgroundColorLight = selectedFloatingPanel.panelBackgroundColorLight,
                backgroundColorDark = selectedFloatingPanel.panelBackgroundColorDark,
                onBackgroundColorLightChange = { color ->
                    settingsViewModel.saveFloatingDashboardBackgroundStyle(
                        backgroundColorLight = color,
                        backgroundColorDark = selectedFloatingPanel.panelBackgroundColorDark,
                        backgroundImageRelPathLight = selectedFloatingPanel.panelBackgroundImageRelPathLight,
                        backgroundImageRelPathDark = selectedFloatingPanel.panelBackgroundImageRelPathDark,
                        panelShape = selectedFloatingPanel.panelShape,
                        panelId = selectedFloatingPanel.id,
                    )
                },
                onBackgroundColorDarkChange = { color ->
                    settingsViewModel.saveFloatingDashboardBackgroundStyle(
                        backgroundColorLight = selectedFloatingPanel.panelBackgroundColorLight,
                        backgroundColorDark = color,
                        backgroundImageRelPathLight = selectedFloatingPanel.panelBackgroundImageRelPathLight,
                        backgroundImageRelPathDark = selectedFloatingPanel.panelBackgroundImageRelPathDark,
                        panelShape = selectedFloatingPanel.panelShape,
                        panelId = selectedFloatingPanel.id,
                    )
                },
                backgroundImageRelPathLight = selectedFloatingPanel.panelBackgroundImageRelPathLight,
                backgroundImageRelPathDark = selectedFloatingPanel.panelBackgroundImageRelPathDark,
                onBackgroundImageRelPathLightChange = { path ->
                    settingsViewModel.saveFloatingDashboardBackgroundStyle(
                        backgroundColorLight = selectedFloatingPanel.panelBackgroundColorLight,
                        backgroundColorDark = selectedFloatingPanel.panelBackgroundColorDark,
                        backgroundImageRelPathLight = path,
                        backgroundImageRelPathDark = selectedFloatingPanel.panelBackgroundImageRelPathDark,
                        panelShape = selectedFloatingPanel.panelShape,
                        panelId = selectedFloatingPanel.id,
                    )
                },
                onBackgroundImageRelPathDarkChange = { path ->
                    settingsViewModel.saveFloatingDashboardBackgroundStyle(
                        backgroundColorLight = selectedFloatingPanel.panelBackgroundColorLight,
                        backgroundColorDark = selectedFloatingPanel.panelBackgroundColorDark,
                        backgroundImageRelPathLight = selectedFloatingPanel.panelBackgroundImageRelPathLight,
                        backgroundImageRelPathDark = path,
                        panelShape = selectedFloatingPanel.panelShape,
                        panelId = selectedFloatingPanel.id,
                    )
                },
                panelShape = selectedFloatingPanel.panelShape,
                onPanelShapeChange = { shape ->
                    settingsViewModel.saveFloatingDashboardBackgroundStyle(
                        backgroundColorLight = selectedFloatingPanel.panelBackgroundColorLight,
                        backgroundColorDark = selectedFloatingPanel.panelBackgroundColorDark,
                        backgroundImageRelPathLight = selectedFloatingPanel.panelBackgroundImageRelPathLight,
                        backgroundImageRelPathDark = selectedFloatingPanel.panelBackgroundImageRelPathDark,
                        panelShape = shape,
                        panelId = selectedFloatingPanel.id,
                    )
                },
                settingsViewModel = settingsViewModel,
                presetSlots = widgetColorPresetSlots,
            )
        }
        FloatingDashboardPositionSizeSettings(
            settingsViewModel,
            Modifier,
            enabled = hasFloatingPanels,
        )


        SettingSliderInt(
            value = floatingPanelsLayoutSnapDp,
            onValueChange = { settingsViewModel.saveFloatingPanelsLayoutSnapDp(it) },
            text = stringResource(
                R.string.settings_panel_layout_snap_title,
                floatingPanelsLayoutSnapDp,
            ),
            description = stringResource(R.string.settings_panel_layout_snap_desc),
            minValue = MIN_PANEL_LAYOUT_SNAP_DP,
            maxValue = MAX_PANEL_LAYOUT_SNAP_DP,
        )

        Text(
            text = stringResource(R.string.settings_floating_usage_stats_hide_title),
            modifier = Modifier.padding(top = 12.dp),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_floating_usage_stats_hide_explanation),
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            style = MaterialTheme.typography.tboxBody.copy(
                lineHeight = MaterialTheme.typography.tboxBody.fontSize * 1.35f,
            ),
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedButton(
            onClick = rememberWrappedOnClick { showUsageStatsHideFloatingDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_floating_usage_stats_hide_configure),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }

    if (showUsageStatsHideFloatingDialog) {
        UsageStatsHideFloatingPanelsDialog(
            settingsViewModel = settingsViewModel,
            floatingPanels = floatingDashboardsList,
            onDismiss = { showUsageStatsHideFloatingDialog = false },
        )
    }
    if (showFloatingPanelOrderDialog) {
        PanelOrderConfigDialog(
            visible = true,
            title = stringResource(R.string.settings_floating_panels_order_dialog_title),
            hint = stringResource(R.string.settings_panels_order_dialog_hint),
            items = floatingDashboardsList.map { panel ->
                PanelOrderItem(
                    id = panel.id,
                    name = panel.name.ifBlank { panel.id },
                )
            },
            onDismiss = { showFloatingPanelOrderDialog = false },
            onSave = { orderedIds ->
                val byId = floatingDashboardsList.associateBy { it.id }
                val reordered = buildList {
                    orderedIds.forEach { panelId ->
                        byId[panelId]?.let { add(it) }
                    }
                    floatingDashboardsList.forEach { panel ->
                        if (orderedIds.none { it == panel.id }) add(panel)
                    }
                }
                settingsViewModel.saveFloatingDashboards(reordered)
            },
        )
    }
}

private fun setMbCanDiagnostics(context: Context, enabled: Boolean) {
    val intent = Intent(context, BackgroundService::class.java).apply {
        action = BackgroundService.ACTION_SET_MBCAN_DIAGNOSTICS
        putExtra(BackgroundService.EXTRA_MBCAN_DIAGNOSTICS_ENABLED, enabled)
    }
    context.startService(intent)
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
        append(context.getString(R.string.dialog_mock_location_select_app_hint))
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

private fun formatDrFloat(value: Float?): String =
    if (value == null || !value.isFinite()) {
        "—"
    } else {
        String.format(Locale.getDefault(), "%.1f", value)
    }

private fun formatDrAccel(x: Float?, y: Float?, z: Float?): String {
    if (x == null && y == null && z == null) return "—"
    return String.format(
        Locale.getDefault(),
        "%s / %s / %s",
        formatDrFloat(x),
        formatDrFloat(y),
        formatDrFloat(z),
    )
}

@Composable
fun LocationTabContent(
    viewModel: TboxViewModel,
    onServiceCommand: (String, String, String) -> Unit,
    settingsViewModel: SettingsViewModel,
    onMockLocationSettingChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val yesLabel = stringResource(R.string.value_yes)
    val noLabel = stringResource(R.string.value_no)
    val locValues by viewModel.locValues.collectAsStateWithLifecycle()
    val geoDisplay by GeoDisplayRepository.state.collectAsStateWithLifecycle()
    val locationUpdateTime by viewModel.locationUpdateTime.collectAsStateWithLifecycle()
    val tboxConnected by viewModel.tboxConnected.collectAsStateWithLifecycle()
    val locationSource by settingsViewModel.locationSource.collectAsStateWithLifecycle()
    val usbGnssDeviceId by settingsViewModel.usbGnssDeviceId.collectAsStateWithLifecycle()
    val usbGnssBaud by settingsViewModel.usbGnssBaud.collectAsStateWithLifecycle()
    val usbGnssConnected by UsbGnssRepository.connected.collectAsStateWithLifecycle()
    val usbGnssLastError by UsbGnssRepository.lastError.collectAsStateWithLifecycle()
    val usbGnssLastNmeaAtMs by UsbGnssRepository.lastNmeaAtMs.collectAsStateWithLifecycle()
    val usbGnssAutoBaudPhase by UsbGnssRepository.autoBaudPhase.collectAsStateWithLifecycle()
    val usbGnssAutoBaudTrying by UsbGnssRepository.autoBaudTryingBaud.collectAsStateWithLifecycle()
    val usbGnssAutoBaudFound by UsbGnssRepository.autoBaudFoundBaud.collectAsStateWithLifecycle()
    val usbGnssModuleByDevice by settingsViewModel.usbGnssModuleByDevice.collectAsStateWithLifecycle()
    val usbGnssModuleProbePhase by UsbGnssRepository.moduleProbePhase.collectAsStateWithLifecycle()
    val espLastGpsAtMs by EspCompanionRepository.lastGpsAtMs.collectAsStateWithLifecycle()
    var showUm980UsbSettings by remember { mutableStateOf(false) }
    var gnssRebootGuardUntilMs by remember { mutableLongStateOf(0L) }
    val isAutoSuspendTboxLocEnabled by settingsViewModel.isAutoSuspendTboxLocEnabled.collectAsStateWithLifecycle()
    val noTboxConnect by settingsViewModel.noTboxConnect.collectAsStateWithLifecycle()
    val isMockLocationEnabled by settingsViewModel.isMockLocationEnabled.collectAsStateWithLifecycle()
    val mockPeriodMs by settingsViewModel.mockLocationPeriodMs.collectAsStateWithLifecycle()
    val mockCanSpeedMode by settingsViewModel.mockCanSpeedMode.collectAsStateWithLifecycle()
    val mockHeadingSource by settingsViewModel.mockHeadingSource.collectAsStateWithLifecycle()
    val mockJunkFixFilter by settingsViewModel.mockJunkFixFilter.collectAsStateWithLifecycle()
    val constantAutoCalibEnabled by settingsViewModel.constantAutoCalibEnabled.collectAsStateWithLifecycle()
    val mockConsiderReverse by settingsViewModel.mockConsiderReverse.collectAsStateWithLifecycle()
    val geoCalibNeeds by vad.dashing.tbox.location.GeoCalibrationState.needsCalibration.collectAsStateWithLifecycle()
    val geoCalibLastAtMs by vad.dashing.tbox.location.GeoCalibrationState.lastCalibratedAtEpochMs.collectAsStateWithLifecycle()
    val drSensor by DrSensorRepository.snapshot.collectAsStateWithLifecycle()
    val reverseGearSwitch by UniversalCanRepository.reverseGearSwitchState.collectAsStateWithLifecycle()
    val huGearBoxMode by UniversalCanRepository.gearBoxModeState.collectAsStateWithLifecycle()
    val tboxGearBoxMode by CanDataRepository.gearBoxMode.collectAsStateWithLifecycle()
    val mockEnabledForSource = locationSource != LocationSource.ANDROID
    val canUseMockLocation = remember(context) { context.canUseMockLocation() }
    var mockAppSelected by remember { mutableStateOf(context.isAppSelectedAsMockProvider()) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var usbDevices by remember { mutableStateOf(emptyList<UsbGnssDevice>()) }
    val refreshUsbDevices: () -> Unit = {
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
        usbDevices = UsbGnssDeviceScanner.listCandidates(usbManager)
    }
    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            "geo-tab-reverse-gear",
            setOf(MbCanSignal.VehicleGear, MbCanSignal.ReverseGearSwitch),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource("geo-tab-reverse-gear")
        }
    }
    DisposableEffect(lifecycleOwner) {
        refreshUsbDevices()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                mockAppSelected = context.isAppSelectedAsMockProvider()
                refreshUsbDevices()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        val usbFilter = IntentFilter().apply {
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        val usbReceiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshUsbDevices()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(usbReceiver, usbFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(usbReceiver, usbFilter)
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { context.unregisterReceiver(usbReceiver) }
        }
    }

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

    var locCommandButtonsEnabled by remember { mutableStateOf(true) }
    var usbNmeaAgeTick by remember { mutableStateOf(0L) }
    var incomingBitRateTick by remember { mutableStateOf(0L) }
    var locationSourceBlockedDialog by remember { mutableStateOf<Pair<String, String>?>(null) }
    val espCompanionEnabled by settingsViewModel.espCompanionEnabled.collectAsStateWithLifecycle()
    LaunchedEffect(locationSource) {
        if (locationSource != LocationSource.USB) return@LaunchedEffect
        while (isActive) {
            usbNmeaAgeTick = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(Unit) {
        while (isActive) {
            incomingBitRateTick = SystemClock.elapsedRealtime()
            delay(1_000)
        }
    }

    LaunchedEffect(locCommandButtonsEnabled) {
        if (!locCommandButtonsEnabled) {
            delay(5000)
            locCommandButtonsEnabled = true
        }
    }

    locationSourceBlockedDialog?.let { (title, message) ->
        AlertDialog(
            onDismissRequest = { locationSourceBlockedDialog = null },
            title = { AppAlertDialogTitle(title) },
            text = { AppAlertDialogText(message) },
            confirmButton = {
                Button(onClick = { locationSourceBlockedDialog = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.widget_external_bind_failed_ok))
                }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(18.dp)
    ) {
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                val locationSourceOptions = buildList {
                    if (!noTboxConnect) {
                        add(
                            LocationSourceOption(
                                LocationSource.TBOX,
                                stringResource(R.string.settings_location_source_tbox),
                            ),
                        )
                    }
                    add(
                        LocationSourceOption(
                            LocationSource.ESP32,
                            stringResource(R.string.settings_location_source_esp32),
                        ),
                    )
                    add(
                        LocationSourceOption(
                            LocationSource.ANDROID,
                            stringResource(R.string.settings_location_source_android),
                        ),
                    )
                    add(
                        LocationSourceOption(
                            LocationSource.USB,
                            stringResource(R.string.settings_location_source_usb),
                        ),
                    )
                }
                val selectedLocationSourceOption = locationSourceOptions.firstOrNull { it.source == locationSource }
                    ?: locationSourceOptions.first()
                val espNeedUsbTitle = stringResource(R.string.settings_location_source_esp32_need_usb_title)
                val espNeedUsbMsg = stringResource(R.string.settings_location_source_esp32_need_usb)
                val espNeedEnabledMsg = stringResource(R.string.settings_location_source_esp32_need_enabled)
                val usbNeedTitle = stringResource(R.string.settings_location_source_usb_need_device_title)
                val usbNeedMsg = stringResource(R.string.settings_location_source_usb_need_device)
                SettingDropdownGeneric(
                    selectedValue = selectedLocationSourceOption,
                    onValueChange = { option ->
                        when (option.source) {
                            LocationSource.ESP32 -> {
                                val usbManager =
                                    context.getSystemService(Context.USB_SERVICE) as UsbManager
                                when {
                                    !UsbGnssDeviceScanner.isEspressifPresent(usbManager) -> {
                                        locationSourceBlockedDialog =
                                            espNeedUsbTitle to espNeedUsbMsg
                                    }
                                    !espCompanionEnabled -> {
                                        locationSourceBlockedDialog =
                                            espNeedUsbTitle to espNeedEnabledMsg
                                    }
                                    else -> settingsViewModel.saveLocationSourceSetting(option.source)
                                }
                            }
                            LocationSource.USB -> {
                                refreshUsbDevices()
                                val hasSelectedId = usbGnssDeviceId.isNotBlank()
                                val hasPresentSelected = hasSelectedId &&
                                    usbDevices.any { it.stableId == usbGnssDeviceId }
                                when {
                                    !hasSelectedId -> {
                                        locationSourceBlockedDialog = usbNeedTitle to usbNeedMsg
                                    }
                                    usbDevices.isEmpty() && !hasPresentSelected -> {
                                        locationSourceBlockedDialog = usbNeedTitle to usbNeedMsg
                                    }
                                    else -> settingsViewModel.saveLocationSourceSetting(option.source)
                                }
                            }
                            else -> settingsViewModel.saveLocationSourceSetting(option.source)
                        }
                    },
                    text = stringResource(R.string.settings_location_source_title),
                    description = stringResource(R.string.settings_location_source_desc),
                    enabled = true,
                    options = locationSourceOptions,
                    selectorWidth = 300.dp,
                )
            }
            // Device list is always visible so the user can pick a receiver before
            // switching the location source to USB (otherwise the gate blocks USB
            // while the picker stays hidden).
            item {
                val noneLabel = stringResource(R.string.settings_usb_gnss_device_none)
                val notSelectedLabel = stringResource(R.string.settings_usb_gnss_device_not_selected)
                val placeholder = UsbGnssDeviceOption(
                    device = null,
                    label = if (usbDevices.isEmpty() && usbGnssDeviceId.isBlank()) {
                        noneLabel
                    } else {
                        notSelectedLabel
                    },
                )
                val orphan = if (usbGnssDeviceId.isNotBlank() &&
                    usbDevices.none { it.stableId == usbGnssDeviceId }
                ) {
                    UsbGnssDevice(
                        stableId = usbGnssDeviceId,
                        label = usbGnssDeviceId,
                        vendorId = 0,
                        productId = 0,
                        deviceName = "",
                        serial = null,
                    )
                } else {
                    null
                }
                val deviceOptions = buildList {
                    add(placeholder)
                    orphan?.let { add(UsbGnssDeviceOption(it, it.label)) }
                    usbDevices.forEach { add(UsbGnssDeviceOption(it, it.label)) }
                }
                val selectedDevice = deviceOptions.firstOrNull {
                    it.device?.stableId == usbGnssDeviceId
                } ?: placeholder
                SettingDropdownGeneric(
                    selectedValue = selectedDevice,
                    onValueChange = { option ->
                        settingsViewModel.saveUsbGnssDeviceIdSetting(
                            option.device?.stableId.orEmpty(),
                        )
                    },
                    text = stringResource(R.string.settings_usb_gnss_device_title),
                    description = stringResource(R.string.settings_usb_gnss_device_desc),
                    enabled = true,
                    options = deviceOptions,
                    selectorWidth = 360.dp,
                )
            }
            if (locationSource == LocationSource.USB) {
                item {
                    val autoBaudRunning =
                        usbGnssAutoBaudPhase == UsbGnssRepository.AutoBaudPhase.RUNNING
                    val baudOptions = UsbGnssDeviceIds.BAUD_OPTIONS.map { baud ->
                        UsbGnssBaudOption(baud, baud.toString())
                    }
                    val selectedBaud = baudOptions.firstOrNull { it.baud == usbGnssBaud }
                        ?: baudOptions.first { it.baud == UsbGnssDeviceIds.DEFAULT_BAUD }
                    SettingDropdownGeneric(
                        selectedValue = selectedBaud,
                        onValueChange = { option ->
                            settingsViewModel.saveUsbGnssBaudSetting(option.baud)
                        },
                        text = stringResource(R.string.settings_usb_gnss_baud_title),
                        description = stringResource(R.string.settings_usb_gnss_baud_desc),
                        enabled = !autoBaudRunning,
                        options = baudOptions,
                        selectorWidth = 300.dp,
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            settingsViewModel.requestUsbGnssAutoBaudDetect()
                        },
                        enabled = usbGnssDeviceId.isNotBlank() && !autoBaudRunning,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_usb_gnss_auto_baud_title),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_usb_gnss_auto_baud_desc),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                    val autoBaudStatus = when (usbGnssAutoBaudPhase) {
                        UsbGnssRepository.AutoBaudPhase.RUNNING ->
                            stringResource(
                                R.string.settings_usb_gnss_auto_baud_running,
                                usbGnssAutoBaudTrying,
                            )
                        UsbGnssRepository.AutoBaudPhase.SUCCESS ->
                            stringResource(
                                R.string.settings_usb_gnss_auto_baud_success,
                                usbGnssAutoBaudFound,
                            )
                        UsbGnssRepository.AutoBaudPhase.FAILED ->
                            stringResource(R.string.settings_usb_gnss_auto_baud_failed)
                        UsbGnssRepository.AutoBaudPhase.IDLE -> null
                    }
                    if (autoBaudStatus != null) {
                        Text(
                            text = autoBaudStatus,
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                        )
                    }
                }
                item {
                    val statusText = when {
                        usbGnssDeviceId.isBlank() ->
                            stringResource(R.string.settings_usb_gnss_status_waiting_device)
                        usbGnssConnected ->
                            stringResource(R.string.settings_usb_gnss_status_connected)
                        else ->
                            stringResource(R.string.settings_usb_gnss_status_disconnected)
                    }
                    val nmeaText = if (usbGnssLastNmeaAtMs <= 0L) {
                        stringResource(R.string.settings_usb_gnss_nmea_none)
                    } else {
                        val ageSec = ((usbNmeaAgeTick - usbGnssLastNmeaAtMs).coerceAtLeast(0L) / 1000L)
                        stringResource(
                            R.string.settings_usb_gnss_nmea_age,
                            timeFormat.format(java.util.Date(usbGnssLastNmeaAtMs)) +
                                " (${ageSec}s)",
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.tboxBody,
                        color = if (usbGnssConnected) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = nmeaText,
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    if (!usbGnssLastError.isNullOrBlank()) {
                        Text(
                            text = usbGnssLastError.orEmpty(),
                            style = MaterialTheme.typography.tboxBody,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                }
                item {
                    val moduleIdentity = usbGnssModuleByDevice[usbGnssDeviceId]
                    val moduleLabel = when {
                        usbGnssModuleProbePhase == UsbGnssRepository.ModuleProbePhase.RUNNING ->
                            stringResource(R.string.settings_gnss_module_probing)
                        moduleIdentity == null ->
                            stringResource(R.string.settings_gnss_module_unknown)
                        !moduleIdentity.isKnown ->
                            stringResource(R.string.settings_gnss_module_unknown)
                        else ->
                            stringResource(
                                R.string.settings_gnss_module_known,
                                moduleIdentity.displayLabel().ifBlank { moduleIdentity.family.name },
                            )
                    }
                    Text(
                        text = moduleLabel,
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                    val probeBusy =
                        usbGnssModuleProbePhase == UsbGnssRepository.ModuleProbePhase.RUNNING ||
                            usbGnssAutoBaudPhase == UsbGnssRepository.AutoBaudPhase.RUNNING
                    val canReboot = moduleIdentity != null && (
                        moduleIdentity.family == GnssModuleFamily.UBLOX ||
                            GnssModuleCommands.softRebootAscii(moduleIdentity.family) != null
                        )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = rememberWrappedOnClick {
                                settingsViewModel.requestUsbGnssModuleProbe()
                            },
                            enabled = usbGnssDeviceId.isNotBlank() && usbGnssConnected && !probeBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.settings_gnss_module_probe),
                                style = MaterialTheme.typography.tboxButton,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                        Button(
                            onClick = rememberWrappedOnClick {
                                val now = System.currentTimeMillis()
                                if (now >= gnssRebootGuardUntilMs) {
                                    gnssRebootGuardUntilMs = now + 3_000L
                                    context.startService(
                                        Intent(context, BackgroundService::class.java).apply {
                                            action = BackgroundService.ACTION_GNSS_MODULE_REBOOT
                                        },
                                    )
                                }
                            },
                            enabled = canReboot && usbGnssConnected && !probeBusy,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                stringResource(R.string.settings_gnss_module_reboot),
                                style = MaterialTheme.typography.tboxButton,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                            )
                        }
                    }
                    if (moduleIdentity?.isUm980 == true) {
                        OutlinedButton(
                            onClick = rememberWrappedOnClick { showUm980UsbSettings = true },
                            enabled = usbGnssConnected && !probeBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        ) {
                            Text(
                                stringResource(R.string.esp_um980_open_settings),
                                style = MaterialTheme.typography.tboxButton,
                            )
                        }
                    }
                }
            }
            if (locationSource == LocationSource.ESP32) {
                item {
                    var nowTick by remember { mutableLongStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(Unit) {
                        while (isActive) {
                            nowTick = System.currentTimeMillis()
                            delay(500)
                        }
                    }
                    val um980Online = espLastGpsAtMs > 0L &&
                        nowTick - espLastGpsAtMs <= EspCompanionProtocol.UM980_ONLINE_TIMEOUT_MS
                    Button(
                        onClick = rememberWrappedOnClick {
                            val now = System.currentTimeMillis()
                            if (now >= gnssRebootGuardUntilMs) {
                                gnssRebootGuardUntilMs = now + 3_000L
                                context.startService(
                                    Intent(context, BackgroundService::class.java).apply {
                                        action = BackgroundService.ACTION_GNSS_MODULE_REBOOT
                                    },
                                )
                            }
                        },
                        enabled = um980Online,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    ) {
                        Text(
                            stringResource(R.string.settings_gnss_um980_reboot),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_gnss_um980_reboot_desc),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            item {
                SettingSwitch(
                    isChecked = mockJunkFixFilter,
                    onCheckedChange = { enabled ->
                        settingsViewModel.saveMockJunkFixFilterSetting(enabled)
                    },
                    text = stringResource(R.string.settings_mock_junk_fix_filter_title),
                    description = if (mockCanSpeedMode.isConstantCalc) {
                        stringResource(R.string.settings_mock_junk_fix_filter_constant_inactive_desc)
                    } else {
                        stringResource(R.string.settings_mock_junk_fix_filter_desc)
                    },
                    enabled = !mockCanSpeedMode.isConstantCalc,
                )
            }
            item {
                SettingSwitch(
                    isChecked = isAutoSuspendTboxLocEnabled,
                    onCheckedChange = { enabled ->
                        settingsViewModel.saveAutoSuspendTboxLocSetting(enabled)
                    },
                    text = stringResource(R.string.settings_auto_suspend_loc_title),
                    description = "",
                    enabled = !noTboxConnect,
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = rememberWrappedOnClick {
                            if (locCommandButtonsEnabled) {
                                locCommandButtonsEnabled = false
                                onServiceCommand(
                                    BackgroundService.ACTION_TBOX_APP_RESUME,
                                    BackgroundService.EXTRA_APP_NAME,
                                    "LOC",
                                )
                            }
                        },
                        enabled = locCommandButtonsEnabled && tboxConnected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.location_button_resume_loc),
                            style = MaterialTheme.typography.tboxButton,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = rememberWrappedOnClick {
                            if (locCommandButtonsEnabled) {
                                locCommandButtonsEnabled = false
                                onServiceCommand(
                                    BackgroundService.ACTION_TBOX_APP_SUSPEND,
                                    BackgroundService.EXTRA_APP_NAME,
                                    "LOC",
                                )
                            }
                        },
                        enabled = locCommandButtonsEnabled && tboxConnected,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = stringResource(R.string.location_button_suspend_loc),
                            style = MaterialTheme.typography.tboxButton,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item {
                SettingSwitch(
                    isChecked = if (mockEnabledForSource) isMockLocationEnabled else false,
                    onCheckedChange = { enabled ->
                        if (mockEnabledForSource) {
                            onMockLocationSettingChanged(enabled)
                        }
                    },
                    text = stringResource(R.string.settings_mock_location_title),
                    description = when {
                        !mockEnabledForSource -> stringResource(R.string.settings_mock_location_android_disabled)
                        canUseMockLocation && mockAppSelected ->
                            stringResource(R.string.settings_mock_location_ready)
                        else -> stringResource(R.string.settings_mock_location_requirements)
                    },
                    enabled = mockEnabledForSource,
                )
            }
            item {
                val mockPeriodOptionsLocalized = listOf(
                    MockPeriodOption(500L, stringResource(R.string.settings_mock_location_period_0_5s)),
                    MockPeriodOption(1000L, stringResource(R.string.settings_mock_location_period_1s)),
                    MockPeriodOption(2000L, stringResource(R.string.settings_mock_location_period_2s)),
                    MockPeriodOption(5000L, stringResource(R.string.settings_mock_location_period_5s)),
                )
                val selectedMockPeriod = mockPeriodOptionsLocalized.firstOrNull { it.periodMs == mockPeriodMs }
                    ?: mockPeriodOptionsLocalized.first { it.periodMs == 1000L }
                SettingDropdownGeneric(
                    selectedValue = selectedMockPeriod,
                    onValueChange = { option ->
                        settingsViewModel.saveMockLocationPeriodMs(option.periodMs)
                    },
                    text = stringResource(R.string.settings_mock_location_period_title),
                    description = stringResource(R.string.settings_mock_location_period_desc),
                    enabled = mockEnabledForSource && isMockLocationEnabled,
                    options = mockPeriodOptionsLocalized,
                    selectorWidth = 300.dp,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.settings_mock_fix_retention_note),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
            }
            item {
                val mockModeEditable = mockEnabledForSource
                val modes = listOf(
                    vad.dashing.tbox.location.MockCanSpeedMode.NONE,
                    vad.dashing.tbox.location.MockCanSpeedMode.WHEN_FIX_LOST,
                    vad.dashing.tbox.location.MockCanSpeedMode.ALWAYS,
                    vad.dashing.tbox.location.MockCanSpeedMode.CONSTANT,
                )
                val modeLabels = listOf(
                    stringResource(R.string.settings_mock_can_speed_direct_short),
                    stringResource(R.string.settings_mock_can_speed_when_fix_lost_short),
                    stringResource(R.string.settings_mock_can_speed_always_short),
                    stringResource(R.string.settings_mock_can_speed_constant_short),
                )
                Text(
                    text = stringResource(R.string.settings_mock_can_speed_mode_title),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    modes.forEachIndexed { index, mode ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                            onClick = {
                                if (mockModeEditable) {
                                    settingsViewModel.saveMockCanSpeedModeSetting(mode)
                                }
                            },
                            selected = mockCanSpeedMode == mode,
                            enabled = mockModeEditable,
                            label = {
                                Text(
                                    text = modeLabels[index],
                                    style = MaterialTheme.typography.tboxButton,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                )
                            },
                        )
                    }
                }
                val modeDesc = when (mockCanSpeedMode) {
                    vad.dashing.tbox.location.MockCanSpeedMode.NONE ->
                        stringResource(R.string.settings_mock_can_speed_direct_desc)
                    vad.dashing.tbox.location.MockCanSpeedMode.ALWAYS ->
                        stringResource(R.string.settings_mock_can_speed_always_desc)
                    vad.dashing.tbox.location.MockCanSpeedMode.WHEN_FIX_LOST ->
                        stringResource(R.string.settings_mock_can_speed_when_fix_lost_desc)
                    vad.dashing.tbox.location.MockCanSpeedMode.CONSTANT ->
                        stringResource(R.string.settings_mock_can_speed_constant_desc)
                }
                Text(
                    text = modeDesc,
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                data class HeadingSourceOption(
                    val source: vad.dashing.tbox.location.MockHeadingSource,
                    val label: String,
                ) {
                    override fun toString(): String = label
                }
                val headingOptions = listOf(
                    HeadingSourceOption(
                        vad.dashing.tbox.location.MockHeadingSource.GYRO,
                        stringResource(R.string.settings_mock_heading_source_gyro),
                    ),
                    HeadingSourceOption(
                        vad.dashing.tbox.location.MockHeadingSource.STEER,
                        stringResource(R.string.settings_mock_heading_source_steer),
                    ),
                )
                val selectedHeading = headingOptions.firstOrNull { it.source == mockHeadingSource }
                    ?: headingOptions.first()
                val steerLive by UniversalCanRepository.steerAngleState.collectAsStateWithLifecycle()
                SettingDropdownGeneric(
                    selectedValue = selectedHeading,
                    onValueChange = { opt ->
                        settingsViewModel.saveMockHeadingSourceSetting(opt.source)
                    },
                    text = stringResource(R.string.settings_mock_heading_source_title),
                    description = stringResource(R.string.settings_mock_heading_source_desc),
                    enabled = mockModeEditable && mockCanSpeedMode.enhancesMock,
                    options = headingOptions,
                    selectorWidth = 300.dp,
                )
                if (mockHeadingSource == vad.dashing.tbox.location.MockHeadingSource.STEER &&
                    mockCanSpeedMode.enhancesMock &&
                    steerLive == null
                ) {
                    Text(
                        text = stringResource(R.string.settings_mock_heading_source_steer_unavailable),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
                    )
                }
                SettingSwitch(
                    isChecked = mockConsiderReverse,
                    onCheckedChange = { enabled ->
                        settingsViewModel.saveMockConsiderReverseSetting(enabled)
                    },
                    text = stringResource(R.string.settings_mock_consider_reverse_title),
                    description = stringResource(R.string.settings_mock_consider_reverse_desc),
                    enabled = mockModeEditable && mockCanSpeedMode.enhancesMock,
                )
                SettingSwitch(
                    isChecked = constantAutoCalibEnabled,
                    onCheckedChange = { enabled ->
                        settingsViewModel.saveConstantAutoCalibEnabledSetting(enabled)
                    },
                    text = stringResource(R.string.settings_mock_constant_auto_calib_title),
                    description = stringResource(R.string.settings_mock_constant_auto_calib_desc),
                    enabled = mockModeEditable && mockCanSpeedMode.isConstantCalc,
                )
                val hasEverDriveCalibrated =
                    geoCalibLastAtMs > 0L ||
                        vad.dashing.tbox.location.DriveCalibrationStore.offsets.calibratedAtEpochMs > 0L
                val showGeoCalibBanner = mockCanSpeedMode.isConstantCalc && (
                    (constantAutoCalibEnabled && geoCalibNeeds) ||
                        !hasEverDriveCalibrated
                    )
                if (showGeoCalibBanner) {
                    Text(
                        text = if (constantAutoCalibEnabled && geoCalibNeeds) {
                            stringResource(R.string.settings_mock_geo_calib_needs)
                        } else {
                            stringResource(R.string.settings_mock_geo_calib_never)
                        },
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }
            item {
                Text(
                    text = if (mockAppSelected) {
                        stringResource(R.string.location_mock_app_selected)
                    } else {
                        stringResource(R.string.location_mock_app_not_selected)
                    },
                    style = MaterialTheme.typography.tboxBody,
                    color = if (mockAppSelected) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
                )
                Text(
                    text = stringResource(R.string.location_mock_app_open_settings),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickableWithSound {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
                                )
                            }.onFailure {
                                showLocationRequirementsDialog(context)
                            }
                        }
                        .padding(bottom = 8.dp),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_last_update),
                    lastRefresh,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_last_change),
                    lastUpdate,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_fixation),
                    if (geoDisplay.locateStatus) yesLabel else noLabel,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_truth),
                    if (geoDisplay.isTruthful) yesLabel else noLabel,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_retention),
                    if (geoDisplay.retaining) yesLabel else noLabel,
                    labelColumnWidthPercent = 25,
                )
            }
            if (mockCanSpeedMode.isConstantCalc) {
                item {
                    val dist = remember(incomingBitRateTick, geoDisplay) {
                        vad.dashing.tbox.location.ConstantDrRuntimeDebug.snapshot.shadowDistM
                    }
                    val distText = if (dist != null && dist.isFinite()) {
                        String.format(Locale.getDefault(), "%.1f", dist)
                    } else {
                        "—"
                    }
                    StatusRow(
                        stringResource(R.string.location_shadow_dist),
                        distText,
                        labelColumnWidthPercent = 25,
                    )
                }
            }
            item {
                // Debug: HU ReverseGearSwitch, HU PRND, TBox PRND (comma-separated).
                val switchText = when (reverseGearSwitch) {
                    true -> "true"
                    false -> "false"
                    null -> "—"
                }
                val huPrnd = huGearBoxMode?.trim()?.takeIf { it.isNotEmpty() } ?: "—"
                val tboxPrnd = tboxGearBoxMode.trim().takeIf { it.isNotEmpty() } ?: "—"
                StatusRow(
                    stringResource(R.string.location_reverse_gear),
                    "$switchText, $huPrnd, $tboxPrnd",
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_utc),
                    dateTime,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                val bitRateText = remember(locationSource, incomingBitRateTick) {
                    LocationIncomingBitRate.formatBitsPerSec(
                        LocationIncomingBitRate.bitsPerSec(locationSource),
                    )
                }
                StatusRow(
                    stringResource(R.string.location_incoming_bitrate),
                    bitRateText,
                    labelColumnWidthPercent = 25,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_raw_data),
                    locValues.rawValue.ifBlank { "—" },
                    labelColumnWidthPercent = 25,
                    valueMaxLines = Int.MAX_VALUE,
                )
            }
            item {
                val showMockCol = mockEnabledForSource && isMockLocationEnabled
                val mockBearing = geoDisplay.bearingDeg
                    ?.takeIf { it != 0f && it.isFinite() }
                    ?.let { String.format(Locale.getDefault(), "%.1f", it) }
                    ?: "—"
                GeoSourceCompareTable(
                    showMockColumn = showMockCol,
                    labelColumnWidthPercent = 25,
                    rows = listOf(
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_longitude),
                            gnssValue = String.format(Locale.getDefault(), "%.6f", locValues.longitude),
                            mockValue = String.format(Locale.getDefault(), "%.6f", geoDisplay.longitude),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_latitude),
                            gnssValue = String.format(Locale.getDefault(), "%.6f", locValues.latitude),
                            mockValue = String.format(Locale.getDefault(), "%.6f", geoDisplay.latitude),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_altitude),
                            gnssValue = String.format(Locale.getDefault(), "%.1f", locValues.altitude),
                            mockValue = String.format(Locale.getDefault(), "%.1f", geoDisplay.altitude),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_visible_satellites),
                            gnssValue = locValues.visibleSatellites.toString(),
                            mockValue = geoDisplay.visibleSatellites.toString(),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_used_satellites),
                            gnssValue = locValues.usingSatellites.toString(),
                            mockValue = geoDisplay.usingSatellites.toString(),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_speed),
                            gnssValue = String.format(Locale.getDefault(), "%.1f", locValues.speed),
                            mockValue = String.format(Locale.getDefault(), "%.1f", geoDisplay.speedKmh),
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_true_direction),
                            gnssValue = String.format(
                                Locale.getDefault(),
                                "%.1f",
                                locValues.trueDirection,
                            ),
                            mockValue = mockBearing,
                        ),
                        GeoSourceCompareRow(
                            label = stringResource(R.string.location_magnetic_direction),
                            gnssValue = String.format(
                                Locale.getDefault(),
                                "%.1f",
                                locValues.magneticDirection,
                            ),
                            mockValue = "—",
                        ),
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_source),
                    stringResource(drSensor.source.labelResId()),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_status),
                    drSensor.statusText.ifBlank { "—" },
                    valueMaxLines = 3,
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_gyro_yaw),
                    formatDrFloat(
                        vad.dashing.tbox.location.GyroBiasStore.applyYaw(drSensor.gyroYaw),
                    ),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_gyro_pitch),
                    formatDrFloat(
                        vad.dashing.tbox.location.GyroBiasStore.applyPitch(drSensor.gyroPitch),
                    ),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_gyro_roll),
                    formatDrFloat(
                        vad.dashing.tbox.location.GyroBiasStore.applyRoll(drSensor.gyroRoll),
                    ),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_gyro_temp),
                    formatDrFloat(drSensor.gyroTemp),
                )
            }
            item {
                StatusRow(
                    stringResource(R.string.location_dr_accel),
                    formatDrAccel(
                        vad.dashing.tbox.location.GyroBiasStore.applyAccelX(drSensor.accelX),
                        vad.dashing.tbox.location.GyroBiasStore.applyAccelY(drSensor.accelY),
                        vad.dashing.tbox.location.GyroBiasStore.applyAccelZ(drSensor.accelZ),
                    ),
                )
            }
            item {
                LocationCalibrationEntryButtons(settingsViewModel = settingsViewModel)
            }
            item {
                val geoDebug by vad.dashing.tbox.location.GeoDebugLogRecorder.uiState
                    .collectAsStateWithLifecycle()
                Text(
                    text = stringResource(R.string.location_geo_debug_log_title),
                    style = MaterialTheme.typography.tboxTitle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Text(
                    text = stringResource(R.string.location_geo_debug_log_desc),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Text(
                    text = if (geoDebug.recording) {
                        stringResource(R.string.location_geo_debug_log_recording, geoDebug.ticks)
                    } else {
                        stringResource(R.string.location_geo_debug_log_idle)
                    },
                    style = MaterialTheme.typography.tboxBody,
                    color = if (geoDebug.recording) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = rememberWrappedOnClick {
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_GEO_DEBUG_LOG_START
                                },
                            )
                        },
                        enabled = !geoDebug.recording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.location_geo_debug_log_start),
                            style = MaterialTheme.typography.tboxButton,
                            textAlign = TextAlign.Center,
                        )
                    }
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            context.startService(
                                Intent(context, BackgroundService::class.java).apply {
                                    action = BackgroundService.ACTION_GEO_DEBUG_LOG_STOP
                                },
                            )
                        },
                        enabled = geoDebug.recording,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(R.string.location_geo_debug_log_stop),
                            style = MaterialTheme.typography.tboxButton,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    if (showUm980UsbSettings) {
        Um980SettingsDialog(
            transport = Um980SettingsTransport.USB,
            controlsEnabled = usbGnssConnected,
            settingsViewModel = settingsViewModel,
            onDismiss = { showUm980UsbSettings = false },
        )
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
    val tboxLocSuspended by viewModel.tboxLocSuspended.collectAsStateWithLifecycle()
    val tboxAppStoped by viewModel.tboxAppStoped.collectAsStateWithLifecycle()
    val tboxMdcStoped by viewModel.tboxMdcStoped.collectAsStateWithLifecycle()
    val appVersion by settingsViewModel.appVersion.collectAsStateWithLifecycle()
    val mdcVersion by settingsViewModel.mdcVersion.collectAsStateWithLifecycle()
    val swdVersion by settingsViewModel.swdVersion.collectAsStateWithLifecycle()
    val crtVersion by settingsViewModel.crtVersion.collectAsStateWithLifecycle()
    val locVersion by settingsViewModel.locVersion.collectAsStateWithLifecycle()
    val swVersion by settingsViewModel.swVersion.collectAsStateWithLifecycle()
    val hwVersion by settingsViewModel.hwVersion.collectAsStateWithLifecycle()
    val vinCode by settingsViewModel.vinCode.collectAsStateWithLifecycle()
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
                    stringResource(R.string.info_confirm_suspend_loc),
                    if (tboxLocSuspended) yesLabel else noLabel
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
                        onClick = rememberWrappedOnClick {
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
                            style = MaterialTheme.typography.tboxButton,
                            maxLines = 2,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

private data class UpdateChannelDropdownOption(
    val channel: UpdateChannel,
    val label: String,
) {
    override fun toString(): String = label
}

private data class MockPeriodOption(
    val periodMs: Long,
    val label: String,
) {
    override fun toString(): String = label
}

private data class LocationSourceOption(
    val source: LocationSource,
    val label: String,
) {
    override fun toString(): String = label
}

private data class UsbGnssDeviceOption(
    val device: UsbGnssDevice?,
    val label: String,
) {
    override fun toString(): String = label
}

private data class UsbGnssBaudOption(
    val baud: Int,
    val label: String,
) {
    override fun toString(): String = label
}
