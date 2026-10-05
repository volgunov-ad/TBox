package vad.dashing.tbox.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.R
import vad.dashing.tbox.location.GeoDebugLogRecorder
import vad.dashing.tbox.location.SimulatedLocationSourceLoss
import vad.dashing.tbox.ui.theme.WidgetActiveColors

/**
 * Dual text toggles (seat heat/vent row layout, drive-mode text style):
 * GNSS ON/OFF → [SimulatedLocationSourceLoss]; LOG ON/OFF → geo debug recorder.
 */
@Composable
fun DashboardGnssDebugWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    enableInnerInteractions: Boolean = true,
    elevation: Dp = 4.dp,
    shape: Dp = 12.dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val context = LocalContext.current
    val simulatedLoss by SimulatedLocationSourceLoss.enabled.collectAsStateWithLifecycle()
    val geoDebug by GeoDebugLogRecorder.uiState.collectAsStateWithLifecycle()
    val gnssOn = !simulatedLoss
    val logOn = geoDebug.recording

    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current
    val defaultTitle = stringResource(R.string.data_title_gnss_debug_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    fun toggleSimulatedLoss() {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = BackgroundService.ACTION_SET_SIMULATED_LOCATION_SOURCE_LOSS
                putExtra(
                    BackgroundService.EXTRA_SIMULATED_LOCATION_SOURCE_LOSS_ENABLED,
                    gnssOn, // ON → enable loss; OFF → clear loss
                )
            },
        )
    }

    fun toggleGeoDebugLog() {
        context.startService(
            Intent(context, BackgroundService::class.java).apply {
                action = if (logOn) {
                    BackgroundService.ACTION_GEO_DEBUG_LOG_STOP
                } else {
                    BackgroundService.ACTION_GEO_DEBUG_LOG_START
                }
            },
        )
    }

    DashboardWidgetScaffold(
        onClick = if (enableInnerInteractions) {
            {}
        } else {
            onClick
        },
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
            Row(
                modifier = contentModifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                GnssDebugTextToggle(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    label = if (gnssOn) {
                        stringResource(R.string.widget_gnss_debug_gnss_on)
                    } else {
                        stringResource(R.string.widget_gnss_debug_gnss_off)
                    },
                    active = gnssOn,
                    availableHeight = availableHeight,
                    useDefaults = useDefaults,
                    controls = controls,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { toggleSimulatedLoss() }
                    } else {
                        onClick
                    },
                )
                GnssDebugTextToggle(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    label = if (logOn) {
                        stringResource(R.string.widget_gnss_debug_log_on)
                    } else {
                        stringResource(R.string.widget_gnss_debug_log_off)
                    },
                    active = logOn,
                    availableHeight = availableHeight,
                    useDefaults = useDefaults,
                    controls = controls,
                    onLongClick = onLongClick,
                    onClick = if (enableInnerInteractions) {
                        { toggleGeoDebugLog() }
                    } else {
                        onClick
                    },
                )
            }
        }
    }
}

@Composable
private fun GnssDebugTextToggle(
    modifier: Modifier,
    label: String,
    active: Boolean,
    availableHeight: Dp,
    useDefaults: Boolean,
    controls: ResolvedControlColors,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val labelColor = if (active) {
        if (useDefaults) WidgetActiveColors.Primary else controls.activeContent
    } else {
        controls.inactiveContent
    }
    WidgetControlChrome(
        background = if (active) controls.activeBackground else controls.inactiveBackground,
        shapeDp = controls.shapeDp,
        modifier = modifier.combinedClickableWithSound(
            onClick = onClick,
            onLongClick = onLongClick,
        ),
    ) {
        val style = calculateResponsiveTextStyle(
            containerHeight = availableHeight,
            textType = TextType.VALUE,
        )
        Text(
            text = label,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(Alignment.CenterVertically),
            style = style,
            color = labelColor,
            textAlign = LocalWidgetTextAlign.current,
            maxLines = 1,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
