package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.CAMD_WINDOW_CAMERA_IDS
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeCamdWindowGeometry
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CamdWindowWidgetSettingsSection(
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
) {
    if (!state.isCamdWindowWidgetSelected) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_camd_window_camera),
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            CAMD_WINDOW_CAMERA_IDS.forEach { cameraId ->
                FilterChip(
                    selected = state.camdWindowCameraId == cameraId,
                    onClick = { state.camdWindowCameraId = cameraId },
                    enabled = state.togglesEnabled,
                    label = {
                        Text(
                            stringResource(camdWindowCameraTitle(cameraId)),
                            style = MaterialTheme.typography.tboxCaption,
                        )
                    },
                )
            }
        }
        SettingInt(
            value = state.camdWindowX,
            onValueChange = { state.applyCamdGeometry(x = it) },
            text = stringResource(R.string.widget_camd_window_x),
            description = "",
            minValue = 0,
            maxValue = 95,
        )
        SettingInt(
            value = state.camdWindowY,
            onValueChange = { state.applyCamdGeometry(y = it) },
            text = stringResource(R.string.widget_camd_window_y),
            description = "",
            minValue = 0,
            maxValue = 95,
        )
        SettingInt(
            value = state.camdWindowW,
            onValueChange = { state.applyCamdGeometry(w = it) },
            text = stringResource(R.string.widget_camd_window_w),
            description = "",
            minValue = 5,
            maxValue = 100,
        )
        SettingInt(
            value = state.camdWindowH,
            onValueChange = { state.applyCamdGeometry(h = it) },
            text = stringResource(R.string.widget_camd_window_h),
            description = "",
            minValue = 5,
            maxValue = 100,
        )
    }
}

private fun WidgetSelectionDialogState.applyCamdGeometry(
    x: Int = camdWindowX,
    y: Int = camdWindowY,
    w: Int = camdWindowW,
    h: Int = camdWindowH,
) {
    val geometry = normalizeCamdWindowGeometry(x, y, w, h)
    camdWindowX = geometry.x
    camdWindowY = geometry.y
    camdWindowW = geometry.w
    camdWindowH = geometry.h
}
