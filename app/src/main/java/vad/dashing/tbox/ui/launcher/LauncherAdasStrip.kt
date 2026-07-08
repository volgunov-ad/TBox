package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.tboxCaption

/** Shows compact ADAS icons only when assistants are active. */
@Composable
fun LauncherAdasStrip(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
) {
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()

    val pasOn = parkingRadar is MbCanBinaryState.On
    val cruiseActive = (cruiseSpeed ?: 0u) > 0u
    if (!pasOn && !cruiseActive) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pasOn) {
            LauncherAdasIcon(
                contentDescription = stringResource(R.string.launcher_adas_pas_on),
                tint = LauncherColors.AccentBlue,
                iconRes = R.drawable.ic_widget_parking_radar,
            )
        }
        if (cruiseActive) {
            LauncherAdasSpeedBadge(speed = cruiseSpeed.toString())
        }
    }
}

@Composable
private fun LauncherAdasIcon(
    contentDescription: String,
    tint: androidx.compose.ui.graphics.Color,
    iconRes: Int,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp),
            colorFilter = ColorFilter.tint(tint),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun LauncherAdasSpeedBadge(speed: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(LauncherColors.AccentCyan.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speed,
            style = MaterialTheme.typography.tboxCaption,
            color = LauncherColors.AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
