package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import vad.dashing.tbox.BuildConfig

/** Debug UI scale + vehicle state simulation for launcher testing. */
@Composable
fun LauncherDevScaleControls(
    modifier: Modifier = Modifier,
) {
    if (!BuildConfig.DEBUG) return
    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val motionPreview = LauncherDevVehicleState.motionPreviewEnabled
    val speedKmh = LauncherDevVehicleState.speedKmh
    val steerDeg = LauncherDevVehicleState.steerAngleDeg

    Column(
        modifier = modifier
            .zIndex(100f)
            .clip(RoundedCornerShape(10.dp))
            .background(LauncherColors.CardDark.copy(alpha = 0.92f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "UI ${String.format("%.0f", LauncherDevScaleState.scale * 100)}%",
            color = LauncherColors.AccentCyan,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
        )
        LauncherDevScaleButton("+") { LauncherDevScaleState.increase() }
        LauncherDevScaleButton("−") { LauncherDevScaleState.decrease() }
        LauncherDevScaleButton("↺") { LauncherDevScaleState.reset() }
        Text(
            text = when {
                motionPreview -> "ПРЕВЬЮ руля"
                simulateEnabled -> "SIM вкл"
                else -> "SIM выкл"
            },
            color = if (simulateEnabled || motionPreview) LauncherColors.AccentCyan else LauncherColors.TextMuted,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
        LauncherDevScaleButton("SIM") { LauncherDevVehicleState.toggleSimulate() }
        LauncherDevScaleButton("ПРЕВ") { LauncherDevVehicleState.toggleMotionPreview() }
        LauncherDevScaleButton("V+") { LauncherDevVehicleState.bumpSpeed(10f) }
        LauncherDevScaleButton("V−") { LauncherDevVehicleState.bumpSpeed(-10f) }
        LauncherDevScaleButton("V0") {
            LauncherDevVehicleState.simulateEnabled = true
            LauncherDevVehicleState.speedKmh = 0f
        }
        Text(
            text = "${speedKmh.toInt()} km/h",
            color = LauncherColors.TextMuted,
            fontSize = 9.sp,
        )
        LauncherDevScaleButton("←") { LauncherDevVehicleState.bumpSteer(-15f) }
        LauncherDevScaleButton("→") { LauncherDevVehicleState.bumpSteer(15f) }
        LauncherDevScaleButton("R0") {
            LauncherDevVehicleState.steerAngleDeg = 0f
        }
        Text(
            text = "${steerDeg.toInt()}°",
            color = LauncherColors.TextMuted,
            fontSize = 9.sp,
        )
        LauncherDevScaleButton("ДПЛ") { LauncherDevVehicleState.toggleDoorFl() }
        LauncherDevScaleButton("ДПП") { LauncherDevVehicleState.toggleDoorFr() }
        LauncherDevScaleButton("Баг") { LauncherDevVehicleState.toggleTailgate() }
    }
}

@Composable
private fun LauncherDevScaleButton(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(LauncherColors.CardDarkElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        color = LauncherColors.TextPrimary,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}
