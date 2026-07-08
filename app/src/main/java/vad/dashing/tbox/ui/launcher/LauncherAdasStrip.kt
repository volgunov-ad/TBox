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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

/** Compact ADAS status chips aligned with launcher palette. */
@Composable
fun LauncherAdasStrip(
    canViewModel: CanDataViewModel,
    modifier: Modifier = Modifier,
) {
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val adas by LauncherAdasRepository.state.collectAsStateWithLifecycle()

    val pasOn = parkingRadar is MbCanBinaryState.On
    val cruiseActive = (cruiseSpeed ?: 0u) > 0u
    val showAcc = adas.accActive || adas.accStandby
    val showAlerts = adas.fcwActive || adas.distanceWarning || adas.aebHint ||
        adas.accTakeOver || adas.adasTakeOver || adas.accOverride
    val showObject = adas.frontObject.valid
    if (!pasOn && !cruiseActive && !showAcc && !showAlerts && !showObject) return

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
        if (showAcc) {
            val accTint = when {
                adas.accOverride || adas.accTakeOver -> Color(0xFFF59E0B)
                adas.accStandby -> Color(0xFFEAB308)
                adas.accMode == LauncherAdasAccMode.ActiveBlue -> LauncherColors.AccentCyan
                else -> LauncherColors.AccentBlue
            }
            adas.accSetSpeedKmh?.let { speed ->
                LauncherAdasSpeedBadge(speed = speed.toString(), tint = accTint)
            } ?: LauncherAdasChip(
                text = stringResource(R.string.launcher_adas_acc_standby),
                tint = accTint,
            )
            adas.timeGapLevel?.let { gap ->
                LauncherAdasChip(
                    text = stringResource(R.string.launcher_adas_time_gap, gap + 1),
                    tint = LauncherColors.TextSecondary,
                )
            }
        } else if (cruiseActive) {
            LauncherAdasSpeedBadge(speed = cruiseSpeed.toString())
        }
        if (showObject) {
            LauncherAdasChip(
                text = stringResource(
                    R.string.launcher_adas_object_chip,
                    stringResource(adas.frontObject.type.labelRes),
                    adas.frontObject.displayDistanceM ?: adas.frontObject.targetDxM ?: 0,
                ),
                tint = LauncherColors.AccentCyan,
            )
        }
        if (adas.fcwActive || adas.distanceWarning) {
            LauncherAdasAlertChip(text = stringResource(R.string.launcher_adas_fcw))
        }
        if (adas.aebHint) {
            LauncherAdasAlertChip(text = stringResource(R.string.launcher_adas_aeb))
        }
        if (adas.accTakeOver || adas.adasTakeOver) {
            LauncherAdasAlertChip(text = stringResource(R.string.launcher_adas_takeover))
        }
        if (adas.laneDepartureLeft || adas.laneDepartureRight) {
            LauncherAdasChip(
                text = stringResource(R.string.launcher_adas_lka),
                tint = Color(0xFFF59E0B),
            )
        }
    }
}

private val LauncherAdasFrontObjectType.labelRes: Int
    get() = when (this) {
        LauncherAdasFrontObjectType.None -> R.string.launcher_adas_obj_unknown
        LauncherAdasFrontObjectType.Car -> R.string.launcher_adas_obj_car
        LauncherAdasFrontObjectType.Truck -> R.string.launcher_adas_obj_truck
        LauncherAdasFrontObjectType.Motorcycle -> R.string.launcher_adas_obj_moto
        LauncherAdasFrontObjectType.Pedestrian -> R.string.launcher_adas_obj_pedestrian
        LauncherAdasFrontObjectType.Bicycle -> R.string.launcher_adas_obj_bicycle
        LauncherAdasFrontObjectType.Bus -> R.string.launcher_adas_obj_bus
        LauncherAdasFrontObjectType.Unknown -> R.string.launcher_adas_obj_unknown
    }

@Composable
private fun LauncherAdasIcon(
    contentDescription: String,
    tint: Color,
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
private fun LauncherAdasSpeedBadge(
    speed: String,
    tint: Color = LauncherColors.AccentCyan,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = speed,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun LauncherAdasChip(
    text: String,
    tint: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.tboxCaption,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun LauncherAdasAlertChip(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFEF4444).copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = Color(0xFFEF4444),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.tboxCaption,
                color = Color(0xFFEF4444),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
