package vad.dashing.tbox.ui.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherLeftPanel(
    tboxViewModel: TboxViewModel,
    canViewModel: CanDataViewModel,
    onOpenVehicleSettings: () -> Unit,
    modelRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    paintRevision: Int = 0,
    onPaintChanged: (String) -> Unit = {},
    onCarBoundsChanged: (Rect) -> Unit = {},
    colorPickerVisible: Boolean = false,
    roadVisible: Boolean = true,
    carHidden: Boolean = false,
    onColorPickerOpen: () -> Unit = {},
    onColorPickerDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val gearBoxMode by canViewModel.gearBoxMode.collectAsStateWithLifecycle()
    val gearBoxCurrentGear by canViewModel.gearBoxCurrentGear.collectAsStateWithLifecycle()
    val fuelPct by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val motion = rememberLauncherVehicleMotion(tboxConnected, canViewModel)

    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val effectiveSpeed = motion.speedKmh
    val effectiveSteer = rememberLauncherVisualSteer(motion)
    val steerPreview = motion.steerPreviewActive
    val motionPreviewOn = LauncherDevVehicleState.motionPreviewEnabled

    val effectiveBody = if (simulateEnabled) {
        LauncherDevVehicleState.bodyState()
    } else {
        vehicleBody
    }

    val rigState = LauncherCarRigState(
        doorFlOpen = effectiveBody.doorFlOpen,
        doorFrOpen = effectiveBody.doorFrOpen,
        doorRlOpen = effectiveBody.doorRlOpen,
        doorRrOpen = effectiveBody.doorRrOpen,
        tailgateOpen = effectiveBody.tailgateOpen,
        speedKmh = effectiveSpeed,
        steeringDeg = effectiveSteer,
    )

    val activeGear = resolveActiveGearSlot(gearBoxMode, gearBoxCurrentGear)
    val fuelText = fuelPct?.toInt()?.let { "$it%" } ?: "—"
    val speedText = valueToString(effectiveSpeed, 0, default = "0")
    val steerText = valueToString(motion.steerAngleDeg, 0, default = "0")

    Column(
        modifier = modifier
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                val rect = coordinates.boundsInWindow()
                LauncherEmbeddedBoundsState.leftPanelBounds = android.graphics.Rect(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt(),
                )
                onCarBoundsChanged(rect)
            }
            .background(LauncherColors.LeftPanelBg)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LauncherGearSelector(activeSlot = activeGear)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "⛽",
                        fontSize = 16.sp,
                    )
                    Text(
                        text = fuelText,
                        style = MaterialTheme.typography.tboxCaption,
                        color = LauncherColors.LeftTextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            Text(
                text = "$speedText ${stringResource(R.string.unit_kmh)}",
                style = MaterialTheme.typography.tboxCaption,
                color = LauncherColors.LeftTextSecondary,
                fontSize = 13.sp,
            )
            Text(
                text = "${stringResource(R.string.data_title_steer_angle)}: $steerText${stringResource(R.string.unit_degree)}",
                style = MaterialTheme.typography.tboxCaption,
                color = LauncherColors.LeftTextSecondary,
                fontSize = 13.sp,
            )
            Text(
                text = if (motionPreviewOn) {
                    stringResource(R.string.launcher_motion_preview_on)
                } else {
                    stringResource(R.string.launcher_motion_preview_off)
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (motionPreviewOn) LauncherColors.AccentCyan.copy(alpha = 0.2f)
                        else LauncherColors.CardDark,
                    )
                    .clickable { LauncherDevVehicleState.toggleMotionPreview() }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                color = if (motionPreviewOn) LauncherColors.AccentCyan else LauncherColors.TextMuted,
                fontSize = 11.sp,
            )
            if (!tboxConnected) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(LauncherColors.GearInactive),
                    )
                    Text(
                        text = stringResource(R.string.value_disconnected),
                        fontSize = 12.sp,
                        color = LauncherColors.LeftTextSecondary,
                    )
                }
            }
            LauncherAdasStrip(canViewModel = canViewModel)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (roadVisible) {
                LauncherVirtualRoad(
                    speedKmh = effectiveSpeed,
                    steerAngleDeg = effectiveSteer,
                    steerPreview = steerPreview,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            if (!carHidden) {
                LauncherCar3DModel(
                    rigState = rigState,
                    speedKmh = effectiveSpeed,
                    steeringDeg = effectiveSteer,
                    steerPreview = steerPreview,
                    modelRevision = modelRevision,
                    paintRevision = paintRevision,
                    paintId = paintId,
                    showRoad = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onOpenVehicleSettings,
                        onLongClick = onColorPickerOpen,
                    ),
            )
            if (colorPickerVisible) {
                LauncherCarColorPicker(
                    selectedId = paintId,
                    onSelect = { id ->
                        onPaintChanged(id)
                        LauncherAppConfigStore.setCarPaintId(context, id)
                    },
                    onDismiss = onColorPickerDismiss,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp),
                )
            }
        }

        LauncherMediaMiniPlayer()
    }
}
