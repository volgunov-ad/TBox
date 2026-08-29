package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import vad.dashing.tbox.DriveModeWidgetOption
import vad.dashing.tbox.R
import vad.dashing.tbox.resolveDriveModeCycleCurrentRaw
import vad.dashing.tbox.resolveDriveModeWidgetOption
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.ui.theme.WidgetActiveColors

private val DriveModeWidgetEcoColor = Color(0xD900A400)
private val DriveModeWidgetSptColor = Color(0xD9FF0000)
private val DriveModeWidgetSandColor = Color(0xD9E6C200)
private val DriveModeWidgetMudColor = Color(0xD98B5A2B)
private val DriveModeWidgetSnowColor = Color(0xD900C8FF)

@Composable
fun DashboardDriveModeWidgetItem(
    selectedDriveModeRawValue: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val selectedMode = resolveDriveModeWidgetOption(selectedDriveModeRawValue)
    val currentDriveMode by when (selectedMode.propertyId) {
        MbCanKnownVehiclePropertyId.VEHICLE_DRIVEMODE_6DCT_WET ->
            UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
        else -> UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    }
    val isSelectedModeActive = currentDriveMode == selectedMode.propertyValue
    DriveModeLabelWidget(
        mode = selectedMode,
        isActive = isSelectedModeActive,
        defaultTitleRes = R.string.data_title_drive_mode_widget,
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
        showTitle = showTitle,
        titleOverride = titleOverride,
    )
}

@Composable
fun DashboardDriveModeCycleWidgetItem(
    selectedDriveModes: List<Int>,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val driveMode by UniversalCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val driveMode6dct by UniversalCanRepository.carSettingsDriveMode6dctWet.collectAsStateWithLifecycle()
    val currentRaw = resolveDriveModeCycleCurrentRaw(driveMode, driveMode6dct, selectedDriveModes)
    val currentMode = currentRaw?.let { resolveDriveModeWidgetOption(it) }
    val defaultTitle = stringResource(R.string.data_title_drive_mode_cycle_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val controls = LocalWidgetControlAppearance.current
    val iconScale = normalizeWidgetScale(LocalWidgetIconScale.current)

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
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
            if (currentMode != null) {
                DriveModeLabelContent(
                    mode = currentMode,
                    isActive = true,
                    modifier = contentModifier.fillMaxWidth(),
                    iconScale = iconScale,
                )
            } else {
                WidgetControlChrome(
                    background = controls.inactiveBackground,
                    shapeDp = controls.shapeDp,
                    modifier = contentModifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "—",
                            style = calculateResponsiveTextStyle(
                                containerHeight = availableHeight,
                                textType = TextType.VALUE,
                            ),
                            color = controls.inactiveContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DriveModeLabelWidget(
    mode: DriveModeWidgetOption,
    isActive: Boolean,
    defaultTitleRes: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean,
    titleOverride: String,
) {
    val controls = LocalWidgetControlAppearance.current
    val iconScale = normalizeWidgetScale(LocalWidgetIconScale.current)
    val defaultTitle = stringResource(defaultTitleRes)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
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
            DriveModeLabelContent(
                mode = mode,
                isActive = isActive,
                modifier = contentModifier.fillMaxWidth(),
                iconScale = iconScale,
            )
        }
    }
}

@Composable
private fun DriveModeLabelContent(
    mode: DriveModeWidgetOption,
    isActive: Boolean,
    modifier: Modifier,
    iconScale: Float,
) {
    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current
    val iconColor = if (isActive) {
        if (useDefaults) mode.activeColor() else controls.activeContent
    } else {
        controls.inactiveContent
    }
    WidgetControlChrome(
        background = if (isActive) controls.activeBackground else controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier,
    ) {
        Image(
            painter = painterResource(driveModeWidgetLabelIconRes(mode)),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().scale(iconScale),
            colorFilter = ColorFilter.tint(iconColor),
        )
    }
}

private fun DriveModeWidgetOption.activeColor(): Color {
    return when {
        label.startsWith("ECO") -> DriveModeWidgetEcoColor
        label.startsWith("NOR") -> WidgetActiveColors.Primary
        label.startsWith("SPT") -> DriveModeWidgetSptColor
        label == "SAND" -> DriveModeWidgetSandColor
        label == "MUD" -> DriveModeWidgetMudColor
        label == "SNOW" -> DriveModeWidgetSnowColor
        else -> Color.Unspecified
    }
}
