package vad.dashing.tbox.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.HvacClimateCanRepository
import vad.dashing.tbox.mbcan.HvacCustomMode
import vad.dashing.tbox.mbcan.CarSettingsHudDomain
import vad.dashing.tbox.mbcan.CarSettingsAdasDomain
import vad.dashing.tbox.mbcan.LdwSensitivity
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanSignal
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxTitle

/** [UniversalCanRepository.setSourceSignals] / [UniversalCanRepository.enqueueClearSource] key for this tab. */
const val CAR_SETTINGS_MB_CAN_SOURCE_ID = "car-settings-tab"

private enum class CarSettingsSection {
    Audio,
    Chassis,
    DriverAssist,
    Locks,
    Lights,
    WipersMirrors,
    ClimateExtra,
    Hud,
}

private data class CarSettingsModeOption(
    val rawValue: Int,
    val label: String
) {
    override fun toString(): String = label
}

private val vehicleDriveModeOptions = listOf(
    CarSettingsModeOption(2, "ECO"),
    CarSettingsModeOption(0, "NOR"),
    CarSettingsModeOption(1, "SPT"),
    CarSettingsModeOption(3, "SNOW"),
    CarSettingsModeOption(4, "MUD"),
    CarSettingsModeOption(5, "SAND"),
)

private val gearboxModeOptions = listOf(
    CarSettingsModeOption(0, "SPT"),
    CarSettingsModeOption(1, "ECO"),
    CarSettingsModeOption(2, "NOR"),
)

private val speedVolumeModeOptionsAll = listOf(
    CarSettingsModeOption(1, "Выкл"),
    CarSettingsModeOption(2, "Низкий"),
    CarSettingsModeOption(3, "Средний"),
    CarSettingsModeOption(4, "Высокий"),
)

private val epsModeOptions = listOf(
    CarSettingsModeOption(1, "ECO"),
    CarSettingsModeOption(2, "Comfort"),
    CarSettingsModeOption(3, "Sport"),
)

private val lasModeOptions = listOf(
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.LAS_MODE_LDW, "LDW"),
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.LAS_MODE_LKA, "LKA"),
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.LAS_MODE_OFF, "OFF"),
)

private val headlightModeOptions = HeadlightMode.settingsOrder.map {
    CarSettingsModeOption(it.rawValue, it.widgetLabel)
}

private val hvacCustomModeOptions = listOf(
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.HVAC_CUSTOM_ECO, "ECO"),
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.HVAC_CUSTOM_COMFORT, "Comfort"),
    CarSettingsModeOption(MbCanKnownVehiclePropertyId.HVAC_CUSTOM_STRONG, "Strong"),
)
private val followMeHomeOptions = listOf(
    CarSettingsModeOption(30, "30 s"), CarSettingsModeOption(60, "60 s"), CarSettingsModeOption(3, "Off"),
)
private val twoModeOptions = listOf(CarSettingsModeOption(1, "Driver"), CarSettingsModeOption(2, "All"))
private val remoteFeedbackOptions = listOf(
    CarSettingsModeOption(1, "Light + horn"), CarSettingsModeOption(2, "Light"), CarSettingsModeOption(3, "Horn"),
)
private val fourLevelOptions = (1..4).map { CarSettingsModeOption(it, it.toString()) }
private val threeLevelOptions = (1..3).map { CarSettingsModeOption(it, it.toString()) }
private val fcwSensitivityOptions = listOf(
    CarSettingsModeOption(2, "Far"),
    CarSettingsModeOption(1, "Standard"),
    CarSettingsModeOption(3, "Near"),
)
private val ldwSensitivityOptions = listOf(
    CarSettingsModeOption(1, "High"),
    CarSettingsModeOption(0, "Low"),
)
private val hudLevelOptions = (1..10).map { CarSettingsModeOption(it, it.toString()) }
private val hudDisplayModeOptions = listOf(
    CarSettingsModeOption(1, "Standard"),
    CarSettingsModeOption(2, "Snow"),
)
private val overspeedAlarmOptions = CarSettingsHudDomain.OVERSPEED_RAW_RANGE.mapNotNull { raw ->
    CarSettingsHudDomain.decodeOverspeedKmh(raw)?.let { CarSettingsModeOption(it, "$it") }
}

private fun signalsForSection(section: CarSettingsSection): Set<MbCanSignal> = when (section) {
    CarSettingsSection.Audio -> setOf(MbCanSignal.AudioVolumeSpeed)
    CarSettingsSection.Chassis -> setOf(
        MbCanSignal.CarSettingsVehicleParams,
        MbCanSignal.AvhSwitch,
        MbCanSignal.HdcSwitch,
        MbCanSignal.EspOffSwitch,
    )
    CarSettingsSection.DriverAssist -> setOf(
        MbCanSignal.SlaSpeedLimit,
        MbCanSignal.LasModeSelection,
        MbCanSignal.TjaIca,
        MbCanSignal.HmaSwitch,
        MbCanSignal.Bsd,
        MbCanSignal.Dow,
        MbCanSignal.Fcw,
        MbCanSignal.FcwSensitivity,
        MbCanSignal.LdwSensitivity,
    )
    CarSettingsSection.Locks -> setOf(MbCanSignal.AutoLock, MbCanSignal.AutoUnlock, MbCanSignal.FollowMeHome, MbCanSignal.DriverUnlockMode, MbCanSignal.RemoteLockFeedback)
    CarSettingsSection.Lights -> setOf(
        MbCanSignal.LightControl,
        MbCanSignal.RearFogLight,
        MbCanSignal.LowBeamHeight,
        MbCanSignal.TurnFlashCount,
    )
    CarSettingsSection.WipersMirrors -> setOf(
        MbCanSignal.WiperMaintenance,
        MbCanSignal.ParkingRadar,
        MbCanSignal.WiperSensitivity,
        MbCanSignal.RearWiper,
    )
    CarSettingsSection.ClimateExtra -> setOf(
        MbCanSignal.HvacCustomMode,
        MbCanSignal.HvacAcMax,
        MbCanSignal.HvacAcPower,
        MbCanSignal.HvacAutoState,
        MbCanSignal.HvacAirRecirculation,
        MbCanSignal.HvacDefroster,
        MbCanSignal.HvacSync,
        MbCanSignal.HvacFanSpeed,
        MbCanSignal.HvacTempLeft,
        MbCanSignal.HvacTempRight,
        MbCanSignal.HvacBlowMode,
        MbCanSignal.HvacDefrosterFront,
        MbCanSignal.HvacAcCleanWhenLocked,
        MbCanSignal.FrontWindscreenHeat,
        MbCanSignal.FrontLeftSeatMode,
        MbCanSignal.FrontRightSeatMode,
        MbCanSignal.RearLeftSeatMode,
        MbCanSignal.RearRightSeatMode,
        MbCanSignal.SteeringWheelHeat,
        MbCanSignal.FirstBlowing,
        MbCanSignal.BtReduceFan,
        MbCanSignal.AutoVentilation,
    )
    CarSettingsSection.Hud -> setOf(
        MbCanSignal.HudSwitch,
        MbCanSignal.HudHeight,
        MbCanSignal.HudBrightness,
        MbCanSignal.HudDisplayMode,
        MbCanSignal.HudAutoBrightness,
        MbCanSignal.OverspeedAlarm,
    )
}

@Composable
fun CarSettingsTab(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val mbCanOk = availability is MbCanAvailability.Available
    val headUnitCanMode by UniversalCanRepository.mode.collectAsStateWithLifecycle()

    val sections = CarSettingsSection.entries
    var selectedSectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedSection = sections[selectedSectionIndex.coerceIn(0, sections.lastIndex)]

    LaunchedEffect(selectedSection) {
        UniversalCanRepository.setSourceSignals(
            CAR_SETTINGS_MB_CAN_SOURCE_ID,
            signalsForSection(selectedSection),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource(CAR_SETTINGS_MB_CAN_SOURCE_ID)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp)
    ) {
        Text(
            text = stringResource(R.string.car_settings_screen_title),
            style = MaterialTheme.typography.tboxHeadline,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        ScrollableTabRow(
            selectedTabIndex = selectedSectionIndex,
            edgePadding = 0.dp,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            divider = {},
        ) {
            sections.forEachIndexed { index, section ->
                Tab(
                    selected = selectedSectionIndex == index,
                    onClick = { selectedSectionIndex = index },
                    text = {
                        Text(
                            text = stringResource(sectionTitleRes(section)),
                            style = MaterialTheme.typography.tboxTabLabel,
                        )
                    },
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(bottom = 18.dp)
        ) {
            when (selectedSection) {
                CarSettingsSection.Audio -> CarSettingsAudioSection(
                    mbCanOk = mbCanOk,
                    headUnitCanMode = headUnitCanMode,
                    onAudioVolumeSpeed = { raw ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(
                                MbCanCommand.SetAudioProperty(
                                    MbCanKnownAudioPropertyId.VOLUME_SPEED,
                                    raw
                                )
                            )
                        }
                    },
                )
                CarSettingsSection.Chassis -> CarSettingsChassisSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value))
                        }
                    },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                )
                CarSettingsSection.DriverAssist -> CarSettingsDriverAssistSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value))
                        }
                    },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                    onSla = { enabled ->
                        coroutineScope.launch {
                            UniversalCanRepository.setSlaRecognitionEnabled(enabled)
                        }
                    },
                    onSetFcw = { enabled ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetFcwEnabled(enabled))
                        }
                    },
                )
                CarSettingsSection.Locks -> CarSettingsLocksSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value -> coroutineScope.launch { UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value)) } },
                    onToggleProperty = { id -> coroutineScope.launch { UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id)) } },
                )
                CarSettingsSection.Lights -> CarSettingsLightsSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value))
                        }
                    },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                )
                CarSettingsSection.WipersMirrors -> CarSettingsWipersMirrorsSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value -> coroutineScope.launch { UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value)) } },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                )
                CarSettingsSection.ClimateExtra -> CarSettingsClimateExtraSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value))
                        }
                    },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                )
                CarSettingsSection.Hud -> CarSettingsHudSection(
                    mbCanOk = mbCanOk,
                    onSetProperty = { id, value ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.SetProperty(id, value))
                        }
                    },
                    onToggleProperty = { id ->
                        coroutineScope.launch {
                            UniversalCanRepository.execute(MbCanCommand.ToggleProperty(id))
                        }
                    },
                )
            }
        }
    }
}

private fun sectionTitleRes(section: CarSettingsSection): Int = when (section) {
    CarSettingsSection.Audio -> R.string.car_settings_audio_section_title
    CarSettingsSection.Chassis -> R.string.car_settings_chassis_section_title
    CarSettingsSection.DriverAssist -> R.string.car_settings_driver_assist_section_title
    CarSettingsSection.Locks -> R.string.car_settings_locks_section_title
    CarSettingsSection.Lights -> R.string.car_settings_lights_section_title
    CarSettingsSection.WipersMirrors -> R.string.car_settings_wipers_mirrors_section_title
    CarSettingsSection.ClimateExtra -> R.string.car_settings_climate_section_title
    CarSettingsSection.Hud -> R.string.car_settings_hud_section_title
}

@Composable
private fun CarSettingsPlaceholderSection(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.tboxTitle,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun CarSettingsAudioSection(
    mbCanOk: Boolean,
    headUnitCanMode: HeadUnitCanMode,
    onAudioVolumeSpeed: (Int) -> Unit,
) {
    val speedVolumeMode by UniversalCanRepository.audioVolumeSpeedModeState.collectAsStateWithLifecycle()
    val speedVolumeModeOptions = if (headUnitCanMode == HeadUnitCanMode.Android10Vhal) {
        speedVolumeModeOptionsAll
    } else {
        speedVolumeModeOptionsAll.take(3)
    }
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_audio_volume_speed_title),
        options = speedVolumeModeOptions,
        selectedRawValue = speedVolumeMode,
        enabled = mbCanOk,
        onValueChange = onAudioVolumeSpeed,
    )
}

@Composable
private fun CarSettingsChassisSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
) {
    val epsMode by UniversalCanRepository.carSettingsEpsMode.collectAsStateWithLifecycle()
    val driveMode by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveMode6dctWet by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val avhState by UniversalCanRepository.avhState.collectAsStateWithLifecycle()
    val hdcState by UniversalCanRepository.hdcState.collectAsStateWithLifecycle()
    val espOffState by UniversalCanRepository.espOffState.collectAsStateWithLifecycle()

    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_eps_mode_title),
        options = epsModeOptions,
        selectedRawValue = epsMode,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE, it) },
    )
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_drive_mode_title),
        options = vehicleDriveModeOptions,
        selectedRawValue = driveMode,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE, it) },
    )
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_drive_mode_6dct_wet_title),
        options = gearboxModeOptions,
        selectedRawValue = driveMode6dctWet,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET, it) },
    )
    SettingSwitch(
        isChecked = avhState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.AVH_SWITCH) },
        text = stringResource(R.string.car_settings_avh_title),
        description = stringResource(R.string.car_settings_avh_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = hdcState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HDC_SWITCH) },
        text = stringResource(R.string.car_settings_hdc_title),
        description = stringResource(R.string.car_settings_hdc_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = espOffState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.ESP_OFF_SWITCH) },
        text = stringResource(R.string.car_settings_esp_off_title),
        description = stringResource(R.string.car_settings_esp_off_desc),
        enabled = mbCanOk,
    )
}

@Composable
private fun CarSettingsDriverAssistSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
    onSla: (Boolean) -> Unit,
    onSetFcw: (Boolean) -> Unit,
) {
    val slaOnOffState by UniversalCanRepository.slaOnOffState.collectAsStateWithLifecycle()
    val lasModeRaw by UniversalCanRepository.lasModeRaw.collectAsStateWithLifecycle()
    val tjaIcaState by UniversalCanRepository.tjaIcaState.collectAsStateWithLifecycle()
    val hmaState by UniversalCanRepository.hmaState.collectAsStateWithLifecycle()
    val bsdState by UniversalCanRepository.bsdState.collectAsStateWithLifecycle()
    val dowState by UniversalCanRepository.dowState.collectAsStateWithLifecycle()
    val fcwState by UniversalCanRepository.fcwState.collectAsStateWithLifecycle()
    val fcwSensitivity by UniversalCanRepository.fcwSensitivity.collectAsStateWithLifecycle()
    val ldwSensitivity by UniversalCanRepository.ldwSensitivity.collectAsStateWithLifecycle()

    SettingSwitch(
        isChecked = slaOnOffState is MbCanBinaryState.On,
        onCheckedChange = onSla,
        text = stringResource(R.string.car_settings_sla_recognition_title),
        description = stringResource(R.string.car_settings_sla_recognition_desc),
        enabled = mbCanOk,
    )
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_las_mode_title),
        options = lasModeOptions,
        selectedRawValue = lasModeRaw,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.LAS_MODE_SELECTION, it) },
    )
    SettingSwitch(
        isChecked = tjaIcaState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.TJA_ICA_SWITCH) },
        text = stringResource(R.string.car_settings_tja_ica_title),
        description = stringResource(R.string.car_settings_tja_ica_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = hmaState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HMA_SWITCH) },
        text = stringResource(R.string.car_settings_hma_title),
        description = stringResource(R.string.car_settings_hma_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = bsdState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.BLIND_AREA_DETECTION) },
        text = stringResource(R.string.car_settings_bsd_title),
        description = stringResource(R.string.car_settings_bsd_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = dowState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.DOOR_OPEN_WARNING) },
        text = stringResource(R.string.car_settings_dow_title),
        description = stringResource(R.string.car_settings_dow_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = fcwState is MbCanBinaryState.On,
        onCheckedChange = onSetFcw,
        text = stringResource(R.string.car_settings_fcw_title),
        description = stringResource(R.string.car_settings_fcw_desc),
        enabled = mbCanOk,
    )
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_fcw_sensitivity_title),
        options = fcwSensitivityOptions,
        selectedRawValue = fcwSensitivity?.let(CarSettingsAdasDomain::encodeFcwSensitivityMbCan),
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.FCW_SENSITIVITY, it) },
    )
    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_ldw_sensitivity_title),
        options = ldwSensitivityOptions,
        selectedRawValue = ldwSensitivity?.let { if (it == LdwSensitivity.High) 1 else 0 },
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.LAS_SENSITIVITY_LEVEL, it) },
    )
}

@Composable
private fun CarSettingsLightsSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
) {
    val headlightModeRaw by UniversalCanRepository.headlightModeRaw.collectAsStateWithLifecycle()
    val rearFog by UniversalCanRepository.rearFogState.collectAsStateWithLifecycle()
    val lowBeamHeight by UniversalCanRepository.lowBeamHeight.collectAsStateWithLifecycle()
    val turnFlashCount by UniversalCanRepository.turnFlashCount.collectAsStateWithLifecycle()

    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_headlight_mode_title),
        options = headlightModeOptions,
        selectedRawValue = headlightModeRaw,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.LIGHTCONTROL, it) },
    )
    SettingSwitch(
        isChecked = rearFog is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.REAR_FOG_LIGHT) },
        text = stringResource(R.string.car_settings_rear_fog_title),
        description = stringResource(R.string.car_settings_rear_fog_desc),
        enabled = mbCanOk,
    )
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_low_beam_height_title), fourLevelOptions, lowBeamHeight, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.HIGHBEAM_ADJUST, it)
    }
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_turn_flash_count_title), threeLevelOptions, turnFlashCount, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.TURN_FLASH_COUNT, it)
    }
}

@Composable
private fun CarSettingsLocksSection(mbCanOk: Boolean, onSetProperty: (Int, Int) -> Unit, onToggleProperty: (Int) -> Unit) {
    val autoLock by UniversalCanRepository.autoLockState.collectAsStateWithLifecycle()
    val autoUnlock by UniversalCanRepository.autoUnlockState.collectAsStateWithLifecycle()
    val followMeHome by UniversalCanRepository.followMeHomeMode.collectAsStateWithLifecycle()
    val unlockMode by UniversalCanRepository.driverUnlockMode.collectAsStateWithLifecycle()
    val feedback by UniversalCanRepository.remoteLockFeedback.collectAsStateWithLifecycle()
    SettingSwitch(autoLock is MbCanBinaryState.On, { onToggleProperty(MbCanKnownVehiclePropertyId.DOOR_AUTO_LOCK) }, stringResource(R.string.car_settings_auto_lock_title), "", enabled = mbCanOk)
    SettingSwitch(autoUnlock is MbCanBinaryState.On, { onToggleProperty(MbCanKnownVehiclePropertyId.DOOR_IGNOFF_UNLOCK) }, stringResource(R.string.car_settings_auto_unlock_title), "", enabled = mbCanOk)
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_follow_me_home_title), followMeHomeOptions, followMeHome?.mbCanWriteValue, mbCanOk) { onSetProperty(MbCanKnownVehiclePropertyId.HEADLIGHTS_HOMELIGHT_DELAY, it) }
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_driver_unlock_title), twoModeOptions, unlockMode, mbCanOk) { onSetProperty(MbCanKnownVehiclePropertyId.DRIVER_UNLOCK_MODE, it) }
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_remote_lock_feedback_title), remoteFeedbackOptions, feedback, mbCanOk) { onSetProperty(MbCanKnownVehiclePropertyId.DEFENCES_PROMPT, it) }
}

@Composable
private fun CarSettingsWipersMirrorsSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
) {
    val wiperMaintenance by UniversalCanRepository.wiperMaintenanceState.collectAsStateWithLifecycle()
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val sensitivity by UniversalCanRepository.wiperSensitivity.collectAsStateWithLifecycle()
    val rearWiper by UniversalCanRepository.rearWiperState.collectAsStateWithLifecycle()

    SettingSwitch(
        isChecked = wiperMaintenance is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.WIPER_MAINTENANCE_SWITCH) },
        text = stringResource(R.string.car_settings_wiper_maintenance_title),
        description = stringResource(R.string.car_settings_wiper_maintenance_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = parkingRadar is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.PARKING_RADAR_SWITCH) },
        text = stringResource(R.string.car_settings_parking_radar_title),
        description = stringResource(R.string.car_settings_parking_radar_desc),
        enabled = mbCanOk,
    )
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_wiper_sensitivity_title), fourLevelOptions, sensitivity, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.WIPER_SENSITIVITY, it)
    }
    SettingSwitch(rearWiper is MbCanBinaryState.On, { onToggleProperty(MbCanKnownVehiclePropertyId.REAR_WIPER) }, stringResource(R.string.car_settings_rear_wiper_title), "", enabled = mbCanOk)
}

@Composable
private fun CarSettingsClimateExtraSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
) {
    val customMode by HvacClimateCanRepository.hvacCustomMode.collectAsStateWithLifecycle()
    val acMax by UniversalCanRepository.hvacAcMaxState.collectAsStateWithLifecycle()
    val acPower by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
    val autoState by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val recirculation by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val rearDefrost by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()
    val sync by HvacClimateCanRepository.hvacSyncState.collectAsStateWithLifecycle()
    val steeringHeat by UniversalCanRepository.steeringWheelHeatState.collectAsStateWithLifecycle()
    val frontWindscreenHeat by UniversalCanRepository.frontWindscreenHeatState.collectAsStateWithLifecycle()
    val firstBlowing by UniversalCanRepository.firstBlowingState.collectAsStateWithLifecycle()
    val btReduceFan by UniversalCanRepository.btReduceFanState.collectAsStateWithLifecycle()
    val autoVentilation by UniversalCanRepository.autoVentilationState.collectAsStateWithLifecycle()

    CarSettingsModeButtonsRow(
        text = stringResource(R.string.car_settings_hvac_custom_title),
        options = hvacCustomModeOptions,
        selectedRawValue = customMode?.mbCanValue,
        enabled = mbCanOk,
        onValueChange = { onSetProperty(MbCanKnownVehiclePropertyId.HVAC_CUSTOM, it) },
    )
    SettingSwitch(
        isChecked = acMax is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_AC_MAX) },
        text = stringResource(R.string.car_settings_hvac_ac_max_title),
        description = stringResource(R.string.car_settings_hvac_ac_max_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = acPower is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_POWER) },
        text = stringResource(R.string.car_settings_hvac_ac_title),
        description = stringResource(R.string.car_settings_hvac_ac_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = autoState is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_AUTO_STATE) },
        text = stringResource(R.string.car_settings_hvac_auto_title),
        description = stringResource(R.string.car_settings_hvac_auto_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = recirculation is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_AIR_RECIRCULATION) },
        text = stringResource(R.string.car_settings_hvac_recirc_title),
        description = stringResource(R.string.car_settings_hvac_recirc_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = sync is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_SYNC_SWITCH) },
        text = stringResource(R.string.car_settings_hvac_sync_title),
        description = stringResource(R.string.car_settings_hvac_sync_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = rearDefrost is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_DEFROSTER_SWITCH) },
        text = stringResource(R.string.car_settings_hvac_rear_defrost_title),
        description = stringResource(R.string.car_settings_hvac_rear_defrost_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = frontWindscreenHeat is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.FRONT_WINDSCREEN_HEAT_SWITCH) },
        text = stringResource(R.string.car_settings_front_windscreen_heat_title),
        description = stringResource(R.string.car_settings_front_windscreen_heat_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = steeringHeat is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.STEERING_WHEEL_HEAT_SWITCH) },
        text = stringResource(R.string.car_settings_steering_heat_title),
        description = stringResource(R.string.car_settings_steering_heat_desc),
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = firstBlowing is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.POWER_FIRST_BREATH) },
        text = stringResource(R.string.car_settings_first_blowing_title),
        description = "",
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = btReduceFan is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.BT_REDUCED_WIND_SPEED) },
        text = stringResource(R.string.car_settings_bt_reduce_fan_title),
        description = "",
        enabled = mbCanOk,
    )
    SettingSwitch(
        isChecked = autoVentilation is MbCanBinaryState.On,
        onCheckedChange = { onToggleProperty(MbCanKnownVehiclePropertyId.HVAC_VENTILATION_AUTO_SWITCH) },
        text = stringResource(R.string.car_settings_auto_ventilation_title),
        description = "",
        enabled = mbCanOk,
    )
}

@Composable
private fun CarSettingsHudSection(
    mbCanOk: Boolean,
    onSetProperty: (Int, Int) -> Unit,
    onToggleProperty: (Int) -> Unit,
) {
    val hudSwitch by UniversalCanRepository.hudSwitchState.collectAsStateWithLifecycle()
    val hudHeight by UniversalCanRepository.hudHeight.collectAsStateWithLifecycle()
    val hudBrightness by UniversalCanRepository.hudBrightness.collectAsStateWithLifecycle()
    val hudDisplayMode by UniversalCanRepository.hudDisplayMode.collectAsStateWithLifecycle()
    val autoBrightness by UniversalCanRepository.hudAutoBrightnessState.collectAsStateWithLifecycle()
    val overspeedKmh by UniversalCanRepository.overspeedAlarmKmh.collectAsStateWithLifecycle()

    SettingSwitch(hudSwitch is MbCanBinaryState.On, { onToggleProperty(MbCanKnownVehiclePropertyId.HUD_SWITCH) }, stringResource(R.string.car_settings_hud_switch_title), "", enabled = mbCanOk)
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_hud_height_title), hudLevelOptions, hudHeight, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.HUD_HEIGHT, it)
    }
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_hud_brightness_title), hudLevelOptions, hudBrightness, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS, it)
    }
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_hud_display_mode_title), hudDisplayModeOptions, hudDisplayMode, mbCanOk) {
        onSetProperty(MbCanKnownVehiclePropertyId.HUD_DISPLAY_MODE, it)
    }
    SettingSwitch(autoBrightness is MbCanBinaryState.On, { onToggleProperty(MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS) }, stringResource(R.string.car_settings_hud_auto_brightness_title), "", enabled = mbCanOk)
    CarSettingsModeButtonsRow(stringResource(R.string.car_settings_overspeed_alarm_title), overspeedAlarmOptions, overspeedKmh, mbCanOk) {
        CarSettingsHudDomain.encodeOverspeedKmh(it)?.let { raw ->
            onSetProperty(MbCanKnownVehiclePropertyId.OVERSPEED_ALARM_SET, raw)
        }
    }
}

@Composable
private fun CarSettingsModeButtonsRow(
    text: String,
    options: List<CarSettingsModeOption>,
    selectedRawValue: Int?,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(0.35f),
            style = MaterialTheme.typography.tboxTitle,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier
                .weight(0.65f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                ModeButton(
                    text = option.label,
                    isSelected = selectedRawValue == option.rawValue,
                    onClick = { onValueChange(option.rawValue) },
                    enabled = enabled,
                )
            }
        }
    }
}
