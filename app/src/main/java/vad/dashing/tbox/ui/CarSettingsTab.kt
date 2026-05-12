package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.MbCanSignal

/** [MbCanRepository.setSourceSignals] / [MbCanRepository.clearSource] key for this tab. */
const val CAR_SETTINGS_MB_CAN_SOURCE_ID = "car-settings-tab"

private val carSettingsZeroToSixOptions = (0..6).toList()

@Composable
fun CarSettingsTab(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val availability by MbCanRepository.availability.collectAsStateWithLifecycle()
    val mbCanOk = availability is MbCanAvailability.Available

    val volumeSpeedState by MbCanRepository.audioVolumeSpeedState.collectAsStateWithLifecycle()
    val switchChecked = volumeSpeedState is MbCanBinaryState.On
    val switchEnabled = volumeSpeedState is MbCanBinaryState.On || volumeSpeedState is MbCanBinaryState.Off

    val steeringMode by MbCanRepository.carSettingsSteeringMode.collectAsStateWithLifecycle()
    val epsMode by MbCanRepository.carSettingsEpsMode.collectAsStateWithLifecycle()
    val systemMode by MbCanRepository.carSettingsSystemMode.collectAsStateWithLifecycle()
    val driveMode by MbCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val powerMode by MbCanRepository.carSettingsPowerMode.collectAsStateWithLifecycle()
    val driveMode6dctWet by MbCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val brakePedalFeel by MbCanRepository.carSettingsBrakePedalFeelMode.collectAsStateWithLifecycle()
    val sourceStationMode by MbCanRepository.carSettingsSourceStationMode.collectAsStateWithLifecycle()
    val vehWashMode by MbCanRepository.carSettingsVehWashMode.collectAsStateWithLifecycle()

    val sourceStationChecked = sourceStationMode is MbCanBinaryState.On
    val sourceStationSwitchEnabled =
        sourceStationMode is MbCanBinaryState.On || sourceStationMode is MbCanBinaryState.Off
    val vehWashChecked = vehWashMode is MbCanBinaryState.On
    val vehWashSwitchEnabled = vehWashMode is MbCanBinaryState.On || vehWashMode is MbCanBinaryState.Off

    LaunchedEffect(Unit) {
        MbCanRepository.setSourceSignals(
            CAR_SETTINGS_MB_CAN_SOURCE_ID,
            setOf(MbCanSignal.AudioVolumeSpeed, MbCanSignal.CarSettingsVehicleParams),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            coroutineScope.launch {
                MbCanRepository.clearSource(CAR_SETTINGS_MB_CAN_SOURCE_ID)
            }
        }
    }

    val scrollState = rememberScrollState()
    val options = remember { carSettingsZeroToSixOptions }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        Text(
            text = stringResource(R.string.car_settings_screen_title),
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        SettingsTitle(stringResource(R.string.car_settings_audio_section_title))
        SettingSwitch(
            isChecked = switchChecked,
            onCheckedChange = {
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.ToggleAudioProperty(MbCanKnownAudioPropertyId.VOLUME_SPEED)
                    )
                }
            },
            text = stringResource(R.string.car_settings_audio_volume_speed_title),
            description = stringResource(R.string.car_settings_audio_volume_speed_desc),
            enabled = switchEnabled && mbCanOk
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingsTitle(stringResource(R.string.car_settings_vehicle_section_title))

        SettingDropdownGeneric(
            selectedValue = steeringMode ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_STEERING_MODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_steering_mode_title),
            description = stringResource(R.string.car_settings_steering_mode_desc),
            enabled = mbCanOk && steeringMode != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = epsMode ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_PROPERTY_EPS_MODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_eps_mode_title),
            description = stringResource(R.string.car_settings_eps_mode_desc),
            enabled = mbCanOk && epsMode != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = systemMode ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.SYSTEM_MODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_system_mode_title),
            description = stringResource(R.string.car_settings_system_mode_desc),
            enabled = mbCanOk && systemMode != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = driveMode ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_drive_mode_title),
            description = stringResource(R.string.car_settings_drive_mode_desc),
            enabled = mbCanOk && driveMode != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = powerMode ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_POWERMODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_power_mode_title),
            description = stringResource(R.string.car_settings_power_mode_desc),
            enabled = mbCanOk && powerMode != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = driveMode6dctWet ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_drive_mode_6dct_wet_title),
            description = stringResource(R.string.car_settings_drive_mode_6dct_wet_desc),
            enabled = mbCanOk && driveMode6dctWet != null,
            options = options,
        )
        SettingDropdownGeneric(
            selectedValue = brakePedalFeel ?: 0,
            onValueChange = { v ->
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICEL_BRAKE_PEDA_FEEL_MODE, v)
                    )
                }
            },
            text = stringResource(R.string.car_settings_brake_pedal_feel_title),
            description = stringResource(R.string.car_settings_brake_pedal_feel_desc),
            enabled = mbCanOk && brakePedalFeel != null,
            options = options,
        )

        SettingSwitch(
            isChecked = sourceStationChecked,
            onCheckedChange = {
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.ToggleProperty(MbCanKnownVehiclePropertyId.SOURCE_STATION_MODE)
                    )
                }
            },
            text = stringResource(R.string.car_settings_source_station_mode_title),
            description = stringResource(R.string.car_settings_source_station_mode_desc),
            enabled = sourceStationSwitchEnabled && mbCanOk
        )
        SettingSwitch(
            isChecked = vehWashChecked,
            onCheckedChange = {
                coroutineScope.launch {
                    MbCanRepository.execute(
                        MbCanCommand.ToggleProperty(MbCanKnownVehiclePropertyId.VEHICLE_VEHWASH_MODESET)
                    )
                }
            },
            text = stringResource(R.string.car_settings_vehwash_mode_title),
            description = stringResource(R.string.car_settings_vehwash_mode_desc),
            enabled = vehWashSwitchEnabled && mbCanOk
        )
    }
}
