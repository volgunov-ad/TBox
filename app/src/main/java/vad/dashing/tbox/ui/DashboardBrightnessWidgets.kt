package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.BrightnessStepperDomain
import vad.dashing.tbox.HeadUnitBrightnessRepository
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository

@Composable
fun DashboardHuScreenBrightnessWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val context = LocalContext.current
    DisposableEffect(context) {
        HeadUnitBrightnessRepository.startObserving(context)
        onDispose { HeadUnitBrightnessRepository.stopObserving(context) }
    }
    val level by HeadUnitBrightnessRepository.brightnessUiLevel.collectAsStateWithLifecycle()
    val auto by HeadUnitBrightnessRepository.autoBrightness.collectAsStateWithLifecycle()
    val available = HeadUnitBrightnessRepository.canWrite(context)
    val autoOn = auto == true
    val displayLevel = BrightnessStepperDomain.normalizeLevel(level)
    val defaultTitle = stringResource(R.string.widget_hu_screen_brightness_title)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val interactions = enableInnerInteractions && available

    fun applyDelta(increase: Boolean) {
        if (!available) return
        val result = BrightnessStepperDomain.stepWithAutoOff(level, autoOn, increase)
        if (result.turnAutoOff) {
            HeadUnitBrightnessRepository.writeAutoBrightness(context, enabled = false)
        }
        HeadUnitBrightnessRepository.writeBrightnessUiLevel(context, result.level)
    }

    fun toggleAuto() {
        if (!available) return
        if (autoOn) {
            HeadUnitBrightnessRepository.writeBrightnessUiLevel(context, displayLevel)
            HeadUnitBrightnessRepository.writeAutoBrightness(context, enabled = false)
        } else {
            HeadUnitBrightnessRepository.writeAutoBrightness(context, enabled = true)
        }
    }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = displayLevel.toString(),
        decreaseContentDescriptionRes = R.string.widget_hu_screen_brightness_action_decrease,
        increaseContentDescriptionRes = R.string.widget_hu_screen_brightness_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = available,
        centerIcon = { contentColor ->
            CustomizableUiIcon(
                drawableRes = if (autoOn) {
                    R.drawable.ic_widget_brightness_hu_auto
                } else {
                    R.drawable.ic_widget_brightness_hu
                },
                contentDescription = stringResource(R.string.widget_hu_screen_brightness_action_auto),
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
        enableInnerInteractions = interactions,
        onDecrease = { applyDelta(increase = false) },
        onIncrease = { applyDelta(increase = true) },
        onCenterClick = { toggleAuto() },
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
fun DashboardIcmBrightnessWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val scope = rememberCoroutineScope()
    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val mode by UniversalCanRepository.icmBrightnessMode.collectAsStateWithLifecycle()
    val level by UniversalCanRepository.icmManualBrightness.collectAsStateWithLifecycle()
    val available = availability is MbCanAvailability.Available
    val autoOn = BrightnessStepperDomain.isIcmAuto(mode)
    val displayLevel = BrightnessStepperDomain.normalizeLevel(level)
    val defaultTitle = stringResource(R.string.widget_icm_brightness_title)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val interactions = enableInnerInteractions && available

    fun setProperty(propertyId: Int, value: Int) {
        scope.launch {
            UniversalCanRepository.execute(MbCanCommand.SetProperty(propertyId, value))
        }
    }

    fun applyDelta(increase: Boolean) {
        if (!available) return
        val result = BrightnessStepperDomain.stepWithAutoOff(level, autoOn, increase)
        if (result.turnAutoOff) {
            setProperty(
                MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
                BrightnessStepperDomain.ICM_MODE_MANUAL,
            )
        }
        setProperty(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL, result.level)
    }

    fun toggleAuto() {
        if (!available) return
        if (autoOn) {
            setProperty(MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MANUAL, displayLevel)
            setProperty(
                MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
                BrightnessStepperDomain.ICM_MODE_MANUAL,
            )
        } else {
            setProperty(
                MbCanKnownVehiclePropertyId.ICM_BRIGHTNESS_MODE,
                BrightnessStepperDomain.ICM_MODE_AUTO,
            )
        }
    }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = displayLevel.toString(),
        decreaseContentDescriptionRes = R.string.widget_icm_brightness_action_decrease,
        increaseContentDescriptionRes = R.string.widget_icm_brightness_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = available,
        centerIcon = { contentColor ->
            CustomizableUiIcon(
                drawableRes = if (autoOn) {
                    R.drawable.ic_widget_brightness_settings_auto
                } else {
                    R.drawable.ic_widget_brightness_settings
                },
                contentDescription = stringResource(R.string.widget_icm_brightness_action_auto),
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
        enableInnerInteractions = interactions,
        onDecrease = { applyDelta(increase = false) },
        onIncrease = { applyDelta(increase = true) },
        onCenterClick = { toggleAuto() },
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
fun DashboardHudBrightnessWidgetItem(
    isVertical: Boolean,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = true,
    titleOverride: String = "",
    stepperAdjustIconStyle: Int = STEPPER_ADJUST_ICON_PLUS_MINUS,
) {
    val scope = rememberCoroutineScope()
    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val level by UniversalCanRepository.hudBrightness.collectAsStateWithLifecycle()
    val autoState by UniversalCanRepository.hudAutoBrightnessState.collectAsStateWithLifecycle()
    val available = availability is MbCanAvailability.Available
    val autoOn = autoState is MbCanBinaryState.On
    val displayLevel = BrightnessStepperDomain.normalizeLevel(level)
    val defaultTitle = stringResource(R.string.widget_hud_brightness_title)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val interactions = enableInnerInteractions && available

    fun setProperty(propertyId: Int, value: Int) {
        scope.launch {
            UniversalCanRepository.execute(MbCanCommand.SetProperty(propertyId, value))
        }
    }

    fun applyDelta(increase: Boolean) {
        if (!available) return
        val result = BrightnessStepperDomain.stepWithAutoOff(level, autoOn, increase)
        if (result.turnAutoOff) {
            setProperty(
                MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
                BrightnessStepperDomain.HUD_AUTO_OFF,
            )
        }
        setProperty(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS, result.level)
    }

    fun toggleAuto() {
        if (!available) return
        if (autoOn) {
            setProperty(MbCanKnownVehiclePropertyId.HUD_BRIGHTNESS, displayLevel)
            setProperty(
                MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
                BrightnessStepperDomain.HUD_AUTO_OFF,
            )
        } else {
            setProperty(
                MbCanKnownVehiclePropertyId.HUD_AUTO_BRIGHTNESS,
                BrightnessStepperDomain.HUD_AUTO_ON,
            )
        }
    }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = displayLevel.toString(),
        decreaseContentDescriptionRes = R.string.widget_hud_brightness_action_decrease,
        increaseContentDescriptionRes = R.string.widget_hud_brightness_action_increase,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = available,
        centerIcon = { contentColor ->
            CustomizableUiIcon(
                // Same settings_brightness / frame+A scheme as ICM (HU uses monitor+stand).
                drawableRes = if (autoOn) {
                    R.drawable.ic_widget_brightness_settings_auto
                } else {
                    R.drawable.ic_widget_brightness_settings
                },
                contentDescription = stringResource(R.string.widget_hud_brightness_action_auto),
                tint = contentColor,
                modifier = Modifier.fillMaxSize(),
            )
        },
        enableInnerInteractions = interactions,
        onDecrease = { applyDelta(increase = false) },
        onIncrease = { applyDelta(increase = true) },
        onCenterClick = { toggleAuto() },
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
