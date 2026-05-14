package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownAudioPropertyId
import vad.dashing.tbox.mbcan.MbCanRepository
import vad.dashing.tbox.mbcan.MbCanSignal

/** [MbCanRepository.setSourceSignals] / [MbCanRepository.clearSource] key for this tab. */
const val CAR_SETTINGS_MB_CAN_SOURCE_ID = "car-settings-tab"

@Composable
fun CarSettingsTab(
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    val volumeSpeedState by MbCanRepository.audioVolumeSpeedState.collectAsStateWithLifecycle()
    val switchChecked = volumeSpeedState is MbCanBinaryState.On
    val switchEnabled = volumeSpeedState is MbCanBinaryState.On || volumeSpeedState is MbCanBinaryState.Off

    LaunchedEffect(Unit) {
        MbCanRepository.setSourceSignals(
            CAR_SETTINGS_MB_CAN_SOURCE_ID,
            setOf(MbCanSignal.AudioVolumeSpeed),
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
            enabled = switchEnabled
        )
    }
}
