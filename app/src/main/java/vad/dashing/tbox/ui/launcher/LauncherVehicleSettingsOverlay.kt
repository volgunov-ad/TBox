package vad.dashing.tbox.ui.launcher

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.CanDataViewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxViewModel
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.seatModeToString
import vad.dashing.tbox.ui.sendToggleFrontWindscreenHeat
import vad.dashing.tbox.ui.sendToggleParkingRadar
import vad.dashing.tbox.ui.sendToggleSteeringWheelHeat
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.valueToString

@Composable
fun LauncherVehicleSettingsOverlay(
    visible: Boolean,
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    bodyState: LauncherCarRigState = LauncherCarRigState(),
    onBodyStateChanged: (LauncherCarRigState) -> Unit = {},
    modelRevision: Int = 0,
    paintId: String = LauncherCarPaint.defaultId,
    paintRevision: Int = 0,
    onPaintChanged: (String) -> Unit = {},
    onCarPaneBoundsChanged: (Rect) -> Unit = {},
    transitionProgress: Float = 1f,
    settingsUserYawDeg: Float = 0f,
    onCarRotate: (Float) -> Unit = {},
) {
    if (!visible) return

    var expandedSection by remember { mutableStateOf<VehicleSettingsSection?>(VehicleSettingsSection.Status) }
    val progress = transitionProgress.coerceIn(0f, 1f)
    val settingsAlpha by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.92f, stiffness = 650f),
        label = "vehicleSettingsContent",
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(20f),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.5f * progress)
                .background(LauncherColors.CanvasDark)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = 0.92f + 0.08f * progress
                    scaleY = 0.92f + 0.08f * progress
                    alpha = progress
                }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            LauncherVehicleSettingsCarPane(
                canViewModel = canViewModel,
                tboxViewModel = tboxViewModel,
                bodyState = bodyState,
                expandedSection = expandedSection,
                progress = progress,
                modelRevision = modelRevision,
                paintId = paintId,
                paintRevision = paintRevision,
                onCarPaneBoundsChanged = onCarPaneBoundsChanged,
                settingsUserYawDeg = settingsUserYawDeg,
                onCarRotate = onCarRotate,
                modifier = Modifier
                    .weight(0.46f)
                    .fillMaxHeight(),
            )
            Column(
                modifier = Modifier
                    .weight(0.54f)
                    .fillMaxHeight()
                    .alpha(settingsAlpha)
                    .clip(RoundedCornerShape(22.dp))
                    .background(LauncherColors.SurfaceDark)
                    .padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.launcher_vehicle_settings_title),
                            style = MaterialTheme.typography.tboxCaption,
                            color = LauncherColors.TextPrimary,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.launcher_vs_overlay_hint),
                            color = LauncherColors.TextSecondary,
                            fontSize = 12.sp,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, null, tint = LauncherColors.TextSecondary)
                    }
                }
                Spacer(Modifier.height(10.dp))
                LauncherVehicleSettingsContent(
                    canViewModel = canViewModel,
                    tboxViewModel = tboxViewModel,
                    bodyState = bodyState,
                    onBodyStateChanged = onBodyStateChanged,
                    paintId = paintId,
                    onPaintChanged = onPaintChanged,
                    expandedSection = expandedSection,
                    onSectionToggle = { section ->
                        expandedSection = if (expandedSection == section) null else section
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun LauncherVehicleSettingsCarPane(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    bodyState: LauncherCarRigState,
    expandedSection: VehicleSettingsSection?,
    progress: Float,
    modelRevision: Int,
    paintId: String,
    paintRevision: Int,
    onCarPaneBoundsChanged: (Rect) -> Unit,
    settingsUserYawDeg: Float,
    onCarRotate: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val carSpeed by canViewModel.carSpeed.collectAsStateWithLifecycle()
    val steerAngle by canViewModel.steerAngle.collectAsStateWithLifecycle()
    val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
    val fuelPct by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val pasOn = parkingRadar is MbCanBinaryState.On
    val cruiseActive = (cruiseSpeed ?: 0u) > 0u

    val simulateEnabled = LauncherDevVehicleState.simulateEnabled
    val effectiveSpeed = if (simulateEnabled) LauncherDevVehicleState.speedKmh else (carSpeed ?: 0f)
    val effectiveSteer = if (simulateEnabled) LauncherDevVehicleState.steerAngleDeg else (steerAngle ?: 0f)
    val effectiveBody = if (simulateEnabled) LauncherDevVehicleState.bodyState() else vehicleBody
    val rigState = bodyState.copy(
        doorFlOpen = effectiveBody.doorFlOpen,
        doorFrOpen = effectiveBody.doorFrOpen,
        doorRlOpen = effectiveBody.doorRlOpen,
        doorRrOpen = effectiveBody.doorRrOpen,
        tailgateOpen = effectiveBody.tailgateOpen,
        speedKmh = effectiveSpeed,
        steeringDeg = effectiveSteer,
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(LauncherColors.LeftPanelBg)
            .onGloballyPositioned { coordinates ->
                onCarPaneBoundsChanged(coordinates.boundsInWindow())
            }
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onCarRotate(dragAmount.x / 4f)
                }
            },
    ) {
        LauncherCar3DModel(
            rigState = rigState,
            speedKmh = 0f,
            steeringDeg = 0f,
            modelRevision = modelRevision,
            paintRevision = paintRevision,
            paintId = paintId,
            showRoad = false,
            settingsView = true,
            settingsProgress = progress,
            settingsUserYawDeg = settingsUserYawDeg,
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .graphicsLayer {
                    val eased = progress.coerceIn(0f, 1f)
                    scaleX = 0.64f + 0.36f * eased
                    scaleY = 0.64f + 0.36f * eased
                    translationX = -180f * (1f - eased)
                    translationY = 78f * (1f - eased)
                    alpha = eased
                },
        )
        fuelPct?.let { pct ->
            LauncherCarStatusBadge(
                text = "$pct%",
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                color = LauncherColors.LeftTextPrimary,
            )
        }
        if (pasOn) {
            LauncherCarStatusBadge(
                text = "PAS",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                color = LauncherColors.AccentBlue,
            )
        }
        if (cruiseActive) {
            LauncherCarStatusBadge(
                text = "${cruiseSpeed} km/h",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                color = LauncherColors.AccentCyan,
            )
        }
    }
}

@Composable
private fun LauncherCarStatusBadge(
    text: String,
    modifier: Modifier = Modifier,
    color: androidx.compose.ui.graphics.Color,
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun LauncherVehicleSettingsContent(
    canViewModel: CanDataViewModel,
    tboxViewModel: TboxViewModel,
    bodyState: LauncherCarRigState,
    onBodyStateChanged: (LauncherCarRigState) -> Unit,
    paintId: String,
    onPaintChanged: (String) -> Unit,
    expandedSection: VehicleSettingsSection?,
    onSectionToggle: (VehicleSettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val tboxConnected by tboxViewModel.tboxConnected.collectAsStateWithLifecycle()
    val fuelPct by canViewModel.fuelLevelPercentageFiltered.collectAsStateWithLifecycle()
    val rangeKm by canViewModel.distanceToFuelEmpty.collectAsStateWithLifecycle()
    val insideTemp by canViewModel.insideTemperature.collectAsStateWithLifecycle()
    val outsideTemp by canViewModel.outsideTemperature.collectAsStateWithLifecycle()
    val climateSet by canViewModel.climateSetTemperature1.collectAsStateWithLifecycle()
    val driveMode by canViewModel.gearBoxDriveMode.collectAsStateWithLifecycle()
    val wheelPressure by canViewModel.wheelsPressure.collectAsStateWithLifecycle()
    val wheelTemp by canViewModel.wheelsTemperature.collectAsStateWithLifecycle()
    val leftSeat by canViewModel.frontLeftSeatMode.collectAsStateWithLifecycle()
    val rightSeat by canViewModel.frontRightSeatMode.collectAsStateWithLifecycle()
    val parkingRadar by UniversalCanRepository.parkingRadarState.collectAsStateWithLifecycle()
    val cruiseSpeed by canViewModel.cruiseSetSpeed.collectAsStateWithLifecycle()
    val pasOn = parkingRadar is MbCanBinaryState.On

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_status),
            subtitle = stringResource(R.string.launcher_vs_section_status_sub),
            icon = VehicleSettingsSectionIcons.Status,
            expanded = expandedSection == VehicleSettingsSection.Status,
            onToggle = { onSectionToggle(VehicleSettingsSection.Status) },
        ) {
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_tbox),
                stringResource(if (tboxConnected) R.string.value_connected else R.string.value_disconnected),
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_metric_range),
                rangeKm?.let { "$it km" } ?: "—",
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_fuel),
                fuelPct?.let { "$it%" } ?: "—",
            )
            if (driveMode.isNotBlank()) {
                LauncherSettingsValueRow(stringResource(R.string.launcher_drive_mode_label), driveMode)
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_wheels),
            subtitle = stringResource(R.string.launcher_vs_section_wheels_sub),
            icon = VehicleSettingsSectionIcons.Wheels,
            expanded = expandedSection == VehicleSettingsSection.Wheels,
            onToggle = { onSectionToggle(VehicleSettingsSection.Wheels) },
        ) {
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_fl), wheelPressure.wheel1, wheelTemp.wheel1)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_fr), wheelPressure.wheel2, wheelTemp.wheel2)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_rl), wheelPressure.wheel3, wheelTemp.wheel3)
            LauncherWheelRow(stringResource(R.string.launcher_vs_wheel_rr), wheelPressure.wheel4, wheelTemp.wheel4)
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_body),
            subtitle = stringResource(R.string.launcher_vs_section_body_sub),
            icon = VehicleSettingsSectionIcons.Body,
            expanded = expandedSection == VehicleSettingsSection.Body,
            onToggle = { onSectionToggle(VehicleSettingsSection.Body) },
        ) {
            val simulateEnabled = LauncherDevVehicleState.simulateEnabled
            val vehicleBody by LauncherVehicleBodyRepository.state.collectAsStateWithLifecycle()
            val displayBody = if (simulateEnabled) LauncherDevVehicleState.bodyState() else vehicleBody
            val togglesEnabled = simulateEnabled

            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_tailgate),
                active = displayBody.tailgateOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.tailgateOpen = !LauncherDevVehicleState.tailgateOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_fl),
                active = displayBody.doorFlOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorFlOpen = !LauncherDevVehicleState.doorFlOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_fr),
                active = displayBody.doorFrOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorFrOpen = !LauncherDevVehicleState.doorFrOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_rl),
                active = displayBody.doorRlOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorRlOpen = !LauncherDevVehicleState.doorRlOpen }
                } else {
                    null
                },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_door_rr),
                active = displayBody.doorRrOpen,
                onClick = if (togglesEnabled) {
                    { LauncherDevVehicleState.doorRrOpen = !LauncherDevVehicleState.doorRrOpen }
                } else {
                    null
                },
            )
            Text(
                text = stringResource(R.string.launcher_paint_picker_title),
                color = LauncherColors.TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LauncherCarPaint.options.forEach { option ->
                    val selected = option.id == paintId
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(androidx.compose.ui.graphics.Color(option.colorArgb))
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) {
                                    LauncherColors.AccentCyan
                                } else {
                                    LauncherColors.TextMuted.copy(alpha = 0.4f)
                                },
                                shape = RoundedCornerShape(15.dp),
                            )
                            .clickable {
                                onPaintChanged(option.id)
                                LauncherAppConfigStore.setCarPaintId(context, option.id)
                            },
                    )
                }
            }
            Text(
                text = stringResource(
                    if (simulateEnabled) {
                        R.string.launcher_vs_body_sim_note
                    } else {
                        R.string.launcher_vs_body_can_note
                    },
                ),
                color = LauncherColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_metric_cabin),
            subtitle = stringResource(R.string.launcher_vs_section_cabin_sub),
            icon = VehicleSettingsSectionIcons.Cabin,
            expanded = expandedSection == VehicleSettingsSection.Cabin,
            onToggle = { onSectionToggle(VehicleSettingsSection.Cabin) },
        ) {
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_inside_temp),
                insideTemp?.let { valueToString(it, 1, default = "—") + "°" } ?: "—",
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_outside_temp),
                outsideTemp?.let { valueToString(it, 1, default = "—") + "°" } ?: "—",
            )
            LauncherSettingsValueRow(
                stringResource(R.string.launcher_vs_climate_set),
                climateSet?.let { valueToString(it, 0) + "°" } ?: "—",
                onClick = { launchClimateApp(context) },
            )
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_adas_title),
            subtitle = stringResource(R.string.launcher_vs_section_adas_sub),
            icon = VehicleSettingsSectionIcons.Adas,
            expanded = expandedSection == VehicleSettingsSection.Adas,
            onToggle = { onSectionToggle(VehicleSettingsSection.Adas) },
        ) {
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_adas_pas_on),
                active = pasOn,
                onClick = { sendToggleParkingRadar(context) },
            )
            cruiseSpeed?.takeIf { it > 0u }?.let {
                LauncherSettingsValueRow(
                    stringResource(R.string.launcher_adas_cruise, it.toString()),
                    "${it} km/h",
                )
            }
        }

        LauncherVehicleSectionCard(
            title = stringResource(R.string.launcher_vs_section_comfort),
            subtitle = stringResource(R.string.launcher_vs_section_comfort_sub),
            icon = VehicleSettingsSectionIcons.Comfort,
            expanded = expandedSection == VehicleSettingsSection.Comfort,
            onToggle = { onSectionToggle(VehicleSettingsSection.Comfort) },
        ) {
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_steering_heat),
                active = false,
                onClick = { sendToggleSteeringWheelHeat(context) },
            )
            LauncherSettingsToggleRow(
                label = stringResource(R.string.launcher_vs_windscreen_heat),
                active = false,
                onClick = { sendToggleFrontWindscreenHeat(context) },
            )
            leftSeat?.let {
                LauncherSettingsValueRow(
                    stringResource(R.string.launcher_vs_seat_left),
                    seatModeToString(context, it).ifBlank { "—" },
                )
            }
            rightSeat?.let {
                LauncherSettingsValueRow(
                    stringResource(R.string.launcher_vs_seat_right),
                    seatModeToString(context, it).ifBlank { "—" },
                )
            }
        }
    }
}

@Composable
private fun LauncherWheelRow(
    label: String,
    pressure: Float?,
    temperature: Float?,
) {
    val pressureText = pressure?.let { valueToString(it, 1) + " bar" } ?: "—"
    val tempText = temperature?.let { valueToString(it, 0) + "°" } ?: "—"
    LauncherSettingsValueRow(label, "$pressureText · $tempText")
}
