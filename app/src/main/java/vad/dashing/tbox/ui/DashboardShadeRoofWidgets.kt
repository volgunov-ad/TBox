package vad.dashing.tbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.STEPPER_ADJUST_ICON_PLUS_MINUS
import vad.dashing.tbox.ShadeRoofStepperDomain
import vad.dashing.tbox.mbcan.BodyComfortRawRead
import vad.dashing.tbox.mbcan.MbCanAvailability
import vad.dashing.tbox.mbcan.MbCanCommand
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository

@Composable
fun DashboardSunshadePositionWidgetItem(
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
    ShadeRoofPositionStepperWidget(
        isVertical = isVertical,
        allowTilt = false,
        propertyId = MbCanKnownVehiclePropertyId.SUNSHADE_POS,
        rawSelector = { it.sunshade },
        defaultTitleRes = R.string.widget_sunshade_position_title,
        decreaseContentDescriptionRes = R.string.widget_sunshade_position_action_decrease,
        increaseContentDescriptionRes = R.string.widget_sunshade_position_action_increase,
        onClick = onClick,
        onLongClick = onLongClick,
        enableInnerInteractions = enableInnerInteractions,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleOverride = titleOverride,
        stepperAdjustIconStyle = stepperAdjustIconStyle,
    )
}

@Composable
fun DashboardSunroofPositionWidgetItem(
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
    ShadeRoofPositionStepperWidget(
        isVertical = isVertical,
        allowTilt = true,
        propertyId = MbCanKnownVehiclePropertyId.SUNROOF_CONTROL,
        rawSelector = { it.sunroof },
        defaultTitleRes = R.string.widget_sunroof_position_title,
        decreaseContentDescriptionRes = R.string.widget_sunroof_position_action_decrease,
        increaseContentDescriptionRes = R.string.widget_sunroof_position_action_increase,
        onClick = onClick,
        onLongClick = onLongClick,
        enableInnerInteractions = enableInnerInteractions,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleOverride = titleOverride,
        stepperAdjustIconStyle = stepperAdjustIconStyle,
    )
}

@Composable
private fun ShadeRoofPositionStepperWidget(
    isVertical: Boolean,
    allowTilt: Boolean,
    propertyId: Int,
    rawSelector: (BodyComfortRawRead) -> Int?,
    defaultTitleRes: Int,
    decreaseContentDescriptionRes: Int,
    increaseContentDescriptionRes: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean,
    titleOverride: String,
    stepperAdjustIconStyle: Int,
) {
    val scope = rememberCoroutineScope()
    val availability by UniversalCanRepository.availability.collectAsStateWithLifecycle()
    val bodyComfort by UniversalCanRepository.bodyComfortRaw.collectAsStateWithLifecycle()
    val available = availability is MbCanAvailability.Available
    val raw = rawSelector(bodyComfort)
    var pendingPercent by remember(propertyId) { mutableStateOf<Int?>(null) }
    val debounceHost = rememberDebouncedCanCommandHost(SHADE_ROOF_POSITION_DEBOUNCE_MS) {
        val target = pendingPercent ?: return@rememberDebouncedCanCommandHost
        val write = ShadeRoofStepperDomain.writeValue(target)
        UniversalCanRepository.execute(MbCanCommand.SetProperty(propertyId, write))
        pendingPercent = null
    }
    val displayPercent = ShadeRoofStepperDomain.displayPercent(pendingPercent, raw, allowTilt)
    val unknown = ShadeRoofStepperDomain.isUnknownDisplay(pendingPercent, raw)
    val centerLabel = if (unknown) {
        stringResource(R.string.widget_shade_roof_position_unknown)
    } else {
        stringResource(R.string.widget_shade_roof_position_percent, displayPercent)
    }
    val defaultTitle = stringResource(defaultTitleRes)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val interactions = enableInnerInteractions && available

    fun applyDelta(increase: Boolean) {
        if (!available) return
        val next = ShadeRoofStepperDomain.stepFromPercent(displayPercent, increase)
        pendingPercent = next
        debounceHost.schedule(scope)
    }

    DashboardStepperControlWidget(
        isVertical = isVertical,
        centerLabel = centerLabel,
        showCenterIcon = false,
        decreaseContentDescriptionRes = decreaseContentDescriptionRes,
        increaseContentDescriptionRes = increaseContentDescriptionRes,
        adjustIconStyle = stepperAdjustIconStyle,
        controlsActive = available,
        enableInnerInteractions = interactions,
        onDecrease = { applyDelta(increase = false) },
        onIncrease = { applyDelta(increase = true) },
        onCenterClick = {},
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
