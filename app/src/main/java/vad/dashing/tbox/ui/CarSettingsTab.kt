package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.TboxTextStyles
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.HeadUnitCanMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.MbCanSignal

/** [UniversalCanRepository.setSourceSignals] / [UniversalCanRepository.enqueueClearSource] key for this tab. */
const val CAR_SETTINGS_MB_CAN_SOURCE_ID = "car-settings-tab"

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

@Composable
fun CarSettingsTab(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val mbCanOk = availability is MbCanAvailability.Available
    val headUnitCanMode by UniversalCanRepository.mode.collectAsStateWithLifecycle()

    val speedVolumeMode by UniversalCanRepository.audioVolumeSpeedModeState.collectAsStateWithLifecycle()
    val speedVolumeModeOptions = if (headUnitCanMode == HeadUnitCanMode.Android10Vhal) {
        speedVolumeModeOptionsAll
    } else {
        speedVolumeModeOptionsAll.take(3)
    }

    val epsMode by UniversalCanRepository.carSettingsEpsMode.collectAsStateWithLifecycle()
    val driveMode by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveMode6dctWet by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        UniversalCanRepository.setSourceSignals(
            CAR_SETTINGS_MB_CAN_SOURCE_ID,
            setOf(MbCanSignal.AudioVolumeSpeed, MbCanSignal.CarSettingsVehicleParams),
        )
    }
    DisposableEffect(Unit) {
        onDispose {
            UniversalCanRepository.enqueueClearSource(CAR_SETTINGS_MB_CAN_SOURCE_ID)
        }
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(18.dp)
    ) {
        Text(
            text = stringResource(R.string.car_settings_screen_title),
            style = MaterialTheme.typography.tboxHeadline,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )
        SettingsTitle(stringResource(R.string.car_settings_audio_section_title))
        CarSettingsModeButtonsRow(
            text = stringResource(R.string.car_settings_audio_volume_speed_title),
            options = speedVolumeModeOptions,
            selectedRawValue = speedVolumeMode,
            enabled = mbCanOk,
            onValueChange = { rawValue ->
                coroutineScope.launch {
                    UniversalCanRepository.execute(
                        MbCanCommand.SetAudioProperty(
                            MbCanKnownAudioPropertyId.VOLUME_SPEED,
                            rawValue
                        )
                    )
                }
            },
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        SettingsTitle(stringResource(R.string.car_settings_vehicle_section_title))

        StatusRow(
            label = stringResource(R.string.car_settings_eps_mode_title),
            value = epsMode?.toString() ?: stringResource(R.string.value_no_data),
        )
        CarSettingsModeButtonsRow(
            text = stringResource(R.string.car_settings_drive_mode_title),
            options = vehicleDriveModeOptions,
            selectedRawValue = driveMode,
            enabled = mbCanOk,
            onValueChange = { rawValue ->
                coroutineScope.launch {
                    UniversalCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE, rawValue)
                    )
                }
            }
        )
        CarSettingsModeButtonsRow(
            text = stringResource(R.string.car_settings_drive_mode_6dct_wet_title),
            options = gearboxModeOptions,
            selectedRawValue = driveMode6dctWet,
            enabled = mbCanOk,
            onValueChange = { rawValue ->
                coroutineScope.launch {
                    UniversalCanRepository.execute(
                        MbCanCommand.SetProperty(MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET, rawValue)
                    )
                }
            }
        )
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
            modifier = Modifier.weight(0.65f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                ModeButton(
                    text = option.label,
                    isSelected = selectedRawValue == option.rawValue,
                    onClick = { onValueChange(option.rawValue) },
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
