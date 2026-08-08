package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody

/**
 * Shared road-calib progress block: **Speed → Turns left → Turns right**.
 * Used by both gyro (drive) and steer hubs so order/labels stay aligned.
 */
@Composable
fun CalibrationSpeedTurnProgressBars(
    speedFill: Float,
    leftFill: Float,
    rightFill: Float,
) {
    Text(
        text = stringResource(R.string.location_drive_calib_speed_fill),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { speedFill.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(bottom = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
    )
    Text(
        text = stringResource(R.string.location_calib_turns_left),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { leftFill.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(bottom = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
    )
    Text(
        text = stringResource(R.string.location_calib_turns_right),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurface,
    )
    LinearProgressIndicator(
        progress = { rightFill.coerceIn(0f, 1f) },
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .padding(bottom = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
    )
}
