package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.mbcan.HvacBlowMode
import vad.dashing.tbox.mbcan.HvacCustomMode
import vad.dashing.tbox.mbcan.HvacClimateCanRepository
import vad.dashing.tbox.mbcan.HvacClimateDomain
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.adjustHvacFanSpeed
import vad.dashing.tbox.mbcan.adjustHvacTempLeft
import vad.dashing.tbox.mbcan.adjustHvacTempRight
import vad.dashing.tbox.mbcan.launchHvacClimateCommand
import vad.dashing.tbox.mbcan.setHvacBlowMode
import vad.dashing.tbox.mbcan.setHvacCustomMode
import vad.dashing.tbox.mbcan.toggleHvacFrontOff
import vad.dashing.tbox.ui.theme.WidgetActiveColors

private val HvacCustomEcoColor = Color(0xD900A400)
private val HvacCustomComfortColor = Color(0xD900C8FF)

private fun hvacBlowModeIconRes(mode: HvacBlowMode): Int = when (mode) {
    HvacBlowMode.Face -> R.drawable.ic_widget_hvac_blow_face
    HvacBlowMode.Foot -> R.drawable.ic_widget_hvac_blow_foot
    HvacBlowMode.FaceFoot -> R.drawable.ic_widget_hvac_blow_face_foot
    HvacBlowMode.DefrostFoot -> R.drawable.ic_widget_hvac_blow_defrost_foot
    HvacBlowMode.Defrost -> R.drawable.ic_widget_hvac_defroster_front
}

@Composable
fun DashboardHvacSyncWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f
) {
    val state by HvacClimateCanRepository.hvacSyncState.collectAsStateWithLifecycle()
    val controls = LocalWidgetControlAppearance.current
    val iconColor = when (state) {
        is MbCanBinaryState.On -> controls.activeContent
        is MbCanBinaryState.Off -> controls.inactiveContent
        else -> controls.inactiveContent.copy(alpha = 0.25f)
    }
    val defaultTitle = stringResource(R.string.data_title_hvac_sync_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .widgetControlOuterPadding(controls)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            WidgetControlChrome(
                background = if (state is MbCanBinaryState.On) controls.activeBackground else controls.inactiveBackground,
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_widget_hvac_sync),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().scale(iconScale),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}

@Composable
fun DashboardHvacFanWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val scope = rememberCoroutineScope()
    val fanSpeed by HvacClimateCanRepository.hvacFanSpeed.collectAsStateWithLifecycle()
    val frontOff by HvacClimateCanRepository.hvacFrontOffState.collectAsStateWithLifecycle()
    val centerLabel = (fanSpeed ?: 0).toString()
    val frontOffActive = frontOff is MbCanBinaryState.On
    val defaultTitle = stringResource(R.string.data_title_hvac_fan_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = centerLabel,
        decreaseContentDescriptionRes = R.string.widget_hvac_fan_decrease,
        increaseContentDescriptionRes = R.string.widget_hvac_fan_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = !frontOffActive,
        centerIcon = { contentColor ->
            Icon(
                painter = painterResource(R.drawable.ic_widget_hvac_fan),
                contentDescription = stringResource(R.string.widget_hvac_front_off_toggle),
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
        enableInnerInteractions = enableInnerInteractions,
        onDecrease = {
            UniversalCanRepository.launchHvacClimateCommand(scope) { adjustHvacFanSpeed(increase = false) }
        },
        onIncrease = {
            UniversalCanRepository.launchHvacClimateCommand(scope) { adjustHvacFanSpeed(increase = true) }
        },
        onCenterClick = {
            UniversalCanRepository.launchHvacClimateCommand(scope) { toggleHvacFrontOff() }
        },
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleText = titleText,
    )
}

@Composable
fun DashboardHvacTempLeftWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
    hvacTempStepTenths: Int = HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT,
) {
    HvacTempStepperWidget(
        isVertical = isVertical,
        isLeftZone = true,
        onClick = onClick,
        onLongClick = onLongClick,
        enableInnerInteractions = enableInnerInteractions,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitleRes = R.string.data_title_hvac_temp_left_widget,
        stepperAdjustIconStyle = stepperAdjustIconStyle,
        hvacTempStepTenths = hvacTempStepTenths,
    )
}

@Composable
fun DashboardHvacTempRightWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
    hvacTempStepTenths: Int = HVAC_TEMP_WIDGET_STEP_TENTHS_DEFAULT,
) {
    HvacTempStepperWidget(
        isVertical = isVertical,
        isLeftZone = false,
        onClick = onClick,
        onLongClick = onLongClick,
        enableInnerInteractions = enableInnerInteractions,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleOverride = titleOverride,
        defaultTitleRes = R.string.data_title_hvac_temp_right_widget,
        stepperAdjustIconStyle = stepperAdjustIconStyle,
        hvacTempStepTenths = hvacTempStepTenths,
    )
}

@Composable
private fun HvacTempStepperWidget(
    isVertical: Boolean,
    isLeftZone: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean,
    titleOverride: String,
    defaultTitleRes: Int,
    stepperAdjustIconStyle: Int,
    hvacTempStepTenths: Int,
) {
    val scope = rememberCoroutineScope()
    val tempLeft by HvacClimateCanRepository.hvacTempLeftCelsius.collectAsStateWithLifecycle()
    val tempRight by HvacClimateCanRepository.hvacTempRightCelsius.collectAsStateWithLifecycle()
    val tempCelsius = if (isLeftZone) tempLeft else tempRight
    val frontOff by HvacClimateCanRepository.hvacFrontOffState.collectAsStateWithLifecycle()
    val centerLabel = tempCelsius?.let(HvacClimateDomain::formatCelsius) ?: "--"
    val frontOffActive = frontOff is MbCanBinaryState.On
    val defaultTitle = stringResource(defaultTitleRes)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = centerLabel,
        showCenterIcon = false,
        decreaseContentDescriptionRes = R.string.widget_hvac_temp_decrease,
        increaseContentDescriptionRes = R.string.widget_hvac_temp_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = !frontOffActive,
        enableInnerInteractions = enableInnerInteractions,
        onDecrease = {
            UniversalCanRepository.launchHvacClimateCommand(scope) {
                if (isLeftZone) {
                    adjustHvacTempLeft(increase = false, stepTenths = hvacTempStepTenths)
                } else {
                    adjustHvacTempRight(increase = false, stepTenths = hvacTempStepTenths)
                }
            }
        },
        onIncrease = {
            UniversalCanRepository.launchHvacClimateCommand(scope) {
                if (isLeftZone) {
                    adjustHvacTempLeft(increase = true, stepTenths = hvacTempStepTenths)
                } else {
                    adjustHvacTempRight(increase = true, stepTenths = hvacTempStepTenths)
                }
            }
        },
        onCenterClick = {
            UniversalCanRepository.launchHvacClimateCommand(scope) { toggleHvacFrontOff() }
        },
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleText = titleText,
    )
}

@Composable
fun DashboardHvacBlowModeCycleWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val blowMode by HvacClimateCanRepository.hvacBlowMode.collectAsStateWithLifecycle()
    var pendingMode by remember { mutableStateOf<HvacBlowMode?>(null) }
    val displayMode = pendingMode ?: blowMode
    val debounceHost = rememberDebouncedCanCommandHost(HVAC_BLOW_MODE_DEBOUNCE_MS) {
        val target = pendingMode ?: return@rememberDebouncedCanCommandHost
        UniversalCanRepository.setHvacBlowMode(target)
        pendingMode = null
    }

    val defaultTitle = stringResource(R.string.data_title_hvac_blow_mode_cycle_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                val next = HvacBlowMode.nextInCycle(displayMode)
                pendingMode = next
                debounceHost.schedule(scope)
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                pendingMode = HvacBlowMode.Defrost
                debounceHost.schedule(scope)
            }
            onDoubleClick()
        },
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier.fillMaxSize().padding(LocalWidgetControlAppearance.current.paddingDp),
        ) { contentModifier ->
            Box(
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val controls = LocalWidgetControlAppearance.current
                val mode = displayMode
                val iconColor = if (mode != null) {
                    controls.activeContent
                } else {
                    controls.inactiveContent.copy(alpha = 0.25f)
                }
                WidgetControlChrome(
                    background = if (mode != null) {
                        controls.activeBackground
                    } else {
                        controls.inactiveBackground
                    },
                    shapeDp = controls.shapeDp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painterResource(
                            if (mode != null) hvacBlowModeIconRes(mode) else R.drawable.ic_widget_hvac_blow_face
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize().scale(iconScale),
                        colorFilter = ColorFilter.tint(iconColor)
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardHvacBlowModePanelWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    iconScale: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val blowMode by HvacClimateCanRepository.hvacBlowMode.collectAsStateWithLifecycle()
    var pendingMode by remember { mutableStateOf<HvacBlowMode?>(null) }
    val displayMode = pendingMode ?: blowMode
    val debounceHost = rememberDebouncedCanCommandHost(HVAC_BLOW_MODE_DEBOUNCE_MS) {
        val target = pendingMode ?: return@rememberDebouncedCanCommandHost
        UniversalCanRepository.setHvacBlowMode(target)
        pendingMode = null
    }

    val defaultTitle = stringResource(R.string.data_title_hvac_blow_mode_panel_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val modes = HvacBlowMode.cycleOrder

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier.fillMaxSize().padding(LocalWidgetControlAppearance.current.paddingDp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { contentModifier ->
            if (isVertical) {
                Column(
                    modifier = contentModifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modes.forEach { mode ->
                        BlowModePanelButton(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            mode = mode,
                            selected = displayMode == mode,
                            enabled = enableInnerInteractions,
                            textColor = LocalWidgetControlAppearance.current.inactiveContent,
                            iconScale = iconScale,
                            onClick = {
                                pendingMode = mode
                                debounceHost.schedule(scope)
                            },
                            onLongClick = onLongClick
                        )
                    }
                }
            } else {
                Row(
                    modifier = contentModifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modes.forEach { mode ->
                        BlowModePanelButton(
                            modifier = Modifier.fillMaxHeight().weight(1f),
                            mode = mode,
                            selected = displayMode == mode,
                            enabled = enableInnerInteractions,
                            textColor = LocalWidgetControlAppearance.current.inactiveContent,
                            iconScale = iconScale,
                            onClick = {
                                pendingMode = mode
                                debounceHost.schedule(scope)
                            },
                            onLongClick = onLongClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlowModePanelButton(
    modifier: Modifier,
    mode: HvacBlowMode,
    selected: Boolean,
    enabled: Boolean,
    textColor: Color,
    iconScale: Float,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val controls = LocalWidgetControlAppearance.current
    val iconColor = if (selected) controls.activeContent else controls.inactiveContent
    WidgetControlChrome(
        background = if (selected) controls.activeBackground else controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier
            .combinedClickableWithSound(enabled = enabled, onClick = onClick, onLongClick = onLongClick),
    ) {
        Image(
            painter = painterResource(hvacBlowModeIconRes(mode)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .scale(iconScale),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}

private fun hvacCustomModeIconRes(mode: HvacCustomMode): Int = when (mode) {
    HvacCustomMode.Eco -> R.drawable.ic_widget_hvac_mode_eco
    HvacCustomMode.Comfort -> R.drawable.ic_widget_hvac_mode_comfort
    HvacCustomMode.Strong -> R.drawable.ic_widget_hvac_mode_strong
}

private fun HvacCustomMode.activeColor(): Color = when (this) {
    HvacCustomMode.Eco -> HvacCustomEcoColor
    HvacCustomMode.Comfort -> HvacCustomComfortColor
    HvacCustomMode.Strong -> WidgetActiveColors.Secondary
}

@Composable
fun DashboardHvacCustomModeCycleWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    iconScale: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val customMode by HvacClimateCanRepository.hvacCustomMode.collectAsStateWithLifecycle()
    var pendingMode by remember { mutableStateOf<HvacCustomMode?>(null) }
    val displayMode = pendingMode ?: customMode
    val debounceHost = rememberDebouncedCanCommandHost(HVAC_BLOW_MODE_DEBOUNCE_MS) {
        val target = pendingMode ?: return@rememberDebouncedCanCommandHost
        UniversalCanRepository.setHvacCustomMode(target)
        pendingMode = null
    }

    val defaultTitle = stringResource(R.string.data_title_hvac_custom_mode_cycle_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                val next = HvacCustomMode.nextInCycle(displayMode)
                pendingMode = next
                debounceHost.schedule(scope)
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier.fillMaxSize().padding(LocalWidgetControlAppearance.current.paddingDp),
        ) { contentModifier ->
            Box(
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                val mode = displayMode
                val iconColor = when {
                    mode == null -> controls.inactiveContent.copy(alpha = 0.25f)
                    useDefaults -> mode.activeColor()
                    else -> controls.activeContent
                }
                WidgetControlChrome(
                    background = if (mode != null) {
                        controls.activeBackground
                    } else {
                        controls.inactiveBackground
                    },
                    shapeDp = controls.shapeDp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = painterResource(
                            if (mode != null) hvacCustomModeIconRes(mode)
                            else R.drawable.ic_widget_hvac_mode_eco
                        ),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.matchParentSize().scale(iconScale),
                        colorFilter = ColorFilter.tint(iconColor)
                    )
                }
            }
        }
    }
}

