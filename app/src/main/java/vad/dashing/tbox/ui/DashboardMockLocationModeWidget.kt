package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.location.MockLocationWidgetCycle
import vad.dashing.tbox.location.MockPowerState

@Composable
fun DashboardMockLocationModeWidgetItem(
    settingsViewModel: SettingsViewModel,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit = {},
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color? = null,
    backgroundColor: Color? = null,
    showTitle: Boolean = true,
    titleOverride: String = "",
) {
    val power by settingsViewModel.mockPowerState.collectAsStateWithLifecycle()
    val mode by settingsViewModel.mockCanSpeedMode.collectAsStateWithLifecycle()
    val locationSource by settingsViewModel.locationSource.collectAsStateWithLifecycle()
    val mockAllowed = locationSource != vad.dashing.tbox.esp.LocationSource.ANDROID

    val index = MockLocationWidgetCycle.indexOf(power, mode)
    val centerLabel = when {
        power == MockPowerState.WHEN_NO_FIX ->
            stringResource(R.string.widget_mock_location_mode_when_no_fix_short)
        index != null -> index.toString()
        else -> "0"
    }
    val subtitle = when (power) {
        MockPowerState.OFF -> stringResource(R.string.settings_mock_power_off_short)
        MockPowerState.WHEN_NO_FIX ->
            stringResource(R.string.settings_mock_power_when_no_fix_short)
        MockPowerState.ALWAYS_ON -> when (mode) {
            vad.dashing.tbox.location.MockCanSpeedMode.NONE ->
                stringResource(R.string.settings_mock_can_speed_direct_short)
            vad.dashing.tbox.location.MockCanSpeedMode.WHEN_FIX_LOST ->
                stringResource(R.string.settings_mock_can_speed_when_fix_lost_short)
            vad.dashing.tbox.location.MockCanSpeedMode.ALWAYS ->
                stringResource(R.string.settings_mock_can_speed_always_short)
            vad.dashing.tbox.location.MockCanSpeedMode.CONSTANT ->
                stringResource(R.string.settings_mock_can_speed_constant_short)
        }
    }

    val defaultTitle = stringResource(R.string.data_title_mock_location_mode_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val resolvedTextColor = textColor ?: MaterialTheme.colorScheme.onSurface
    val resolvedBackgroundColor = backgroundColor ?: MaterialTheme.colorScheme.surface

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                if (mockAllowed) {
                    settingsViewModel.cycleMockLocationWidgetMode()
                }
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        elevation = elevation,
        shape = shape,
        textColor = resolvedTextColor,
        backgroundColor = resolvedBackgroundColor,
    ) { availableHeight, color ->
        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = color,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            androidx.compose.foundation.layout.Column(
                modifier = contentModifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = centerLabel,
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.VALUE,
                    ),
                    color = color,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = calculateResponsiveTextStyle(
                        containerHeight = availableHeight,
                        textType = TextType.UNIT,
                    ),
                    color = color,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
