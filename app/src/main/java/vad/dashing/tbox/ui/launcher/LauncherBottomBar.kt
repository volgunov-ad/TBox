package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
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
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
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
import vad.dashing.tbox.ui.sendAdjustHvacTemperature
import vad.dashing.tbox.ui.sendToggleHvacAc
import vad.dashing.tbox.ui.sendToggleHvacAirRecirculation
import vad.dashing.tbox.ui.sendToggleHvacAuto
import vad.dashing.tbox.ui.sendToggleRearWindowMirrorsDefrost
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

private val HvacOnColor = Color(0xFF4FC3F7)

@Composable
fun LauncherBottomBar(
    canViewModel: CanDataViewModel,
    onOpenApps: () -> Unit,
    vehicleSettingsOpen: Boolean = false,
    appDrawerOpen: Boolean = false,
    onCloseVehicleSettings: () -> Unit = {},
    onCloseAppDrawer: () -> Unit = {},
    configRevision: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val climateTemp by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val tempText = climateTemp?.let { valueToString(it, 0) } ?: "22"
    val hvacAc by UniversalCanRepository.hvacAcPowerState.collectAsStateWithLifecycle()
    val hvacAuto by UniversalCanRepository.hvacAutoState.collectAsStateWithLifecycle()
    val hvacRecirc by UniversalCanRepository.hvacAirRecirculationState.collectAsStateWithLifecycle()
    val hvacDefrost by UniversalCanRepository.hvacDefrosterState.collectAsStateWithLifecycle()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.bottomBarTopPx = rect.top.toInt()
            }
            .background(LauncherColors.BottomBarBg)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherDockIcon(onClick = { goLauncherHome(context) }) {
                Icon(Icons.Filled.Home, stringResource(R.string.launcher_home_cd), tint = LauncherColors.AccentCyan)
            }
            LauncherDockIcon(onClick = {
                goLauncherBack(
                    context = context,
                    vehicleSettingsOpen = vehicleSettingsOpen,
                    appDrawerOpen = appDrawerOpen,
                    onCloseVehicleSettings = onCloseVehicleSettings,
                    onCloseAppDrawer = onCloseAppDrawer,
                )
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    stringResource(R.string.launcher_back_cd),
                    tint = LauncherColors.TextSecondary,
                )
            }
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherDockIcon(onClick = { sendToggleHvacAc(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_ac, hvacAc)
            }
            LauncherDockIcon(onClick = { sendToggleHvacAuto(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_auto, hvacAuto)
            }
            LauncherDockIcon(onClick = { sendToggleHvacAirRecirculation(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_hvac_air_recirculation, hvacRecirc)
            }
            LauncherDockIcon(onClick = { sendToggleRearWindowMirrorsDefrost(context) }) {
                LauncherHvacIcon(R.drawable.ic_widget_rear_window_mirrors_defrost, hvacDefrost)
            }
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                LauncherDockIcon(onClick = { sendAdjustHvacTemperature(context, climateTemp, -0.5f) }) {
                    Icon(Icons.Filled.KeyboardArrowDown, null, tint = LauncherColors.TextSecondary, modifier = Modifier.size(24.dp))
                }
                Text(
                    text = tempText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = LauncherColors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                LauncherDockIcon(onClick = { sendAdjustHvacTemperature(context, climateTemp, 0.5f) }) {
                    Icon(Icons.Filled.KeyboardArrowUp, null, tint = LauncherColors.TextSecondary, modifier = Modifier.size(24.dp))
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LauncherDockIcon(onClick = { launchLauncherApp(context, "com.autopai.car.dialer") }) {
                Icon(Icons.Filled.Phone, null, tint = LauncherColors.AccentCyan)
            }
            LauncherDockIcon(onClick = onOpenApps) {
                Icon(Icons.Filled.Menu, null, tint = LauncherColors.TextPrimary)
            }
        }
    }
}

private val HvacOffColor = LauncherColors.TextSecondary

@Composable
private fun LauncherHvacIcon(drawableRes: Int, state: MbCanBinaryState) {
    val tint = when (state) {
        is MbCanBinaryState.On -> HvacOnColor
        is MbCanBinaryState.Off -> HvacOffColor
        else -> HvacOffColor.copy(alpha = 0.55f)
    }
    Image(
        painter = painterResource(drawableRes),
        contentDescription = null,
        modifier = Modifier.size(26.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
private fun LauncherDockIcon(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(LauncherColors.CardDark)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
