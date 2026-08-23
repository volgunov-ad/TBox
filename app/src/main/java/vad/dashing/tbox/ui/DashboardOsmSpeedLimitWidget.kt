package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorRepository
import vad.dashing.tbox.location.roadmatch.RoadMatchAnchorState
import vad.dashing.tbox.ui.theme.scaledWidgetText

/** Red ring of a round speed-limit road sign (same visual language as SLA tile). */
private val OsmSignRingColor = Color(0xFFE53935)
private val OsmSignFaceColor = Color.White
private val OsmSignTextColor = Color.Black
private const val OsmInactiveAlpha = 0.4f
/** Fixed red ring thickness (not proportional to diameter). */
private val OsmSignRingWidth = 8.dp
/** Upcoming sign diameter relative to the current sign (layout only; not text scale). */
private const val OsmNextSignDiameterFraction = 0.5f

/**
 * OSM posted speed from the matched road edge + optional "next" limit ahead
 * ([RoadMatchAnchorState] / [vad.dashing.tbox.location.roadmatch.SpeedLimitLookahead]).
 */
@Composable
fun DashboardOsmSpeedLimitWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val anchor by RoadMatchAnchorRepository.state.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_osm_speed_limit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val dashLabel = stringResource(R.string.osm_speed_limit_unknown)
    val context = LocalContext.current
    val display = OsmSpeedLimitDisplay.from(anchor)

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
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            // Main digits: VALUE + widget text scale. Next / distance: half of that size.
            val mainTextStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.VALUE,
            )
            val nextTextStyle = mainTextStyle.scaledWidgetText(0.5f)

            BoxWithConstraints(modifier = contentModifier.fillMaxSize()) {
                val gap = 8.dp
                // Diameters follow the tile box only (not text scale).
                val mainDiameter = if (display.showNext) {
                    minOf(
                        maxHeight,
                        (maxWidth - gap) / (1f + OsmNextSignDiameterFraction),
                    )
                } else {
                    minOf(maxWidth, maxHeight)
                }
                val nextDiameter = mainDiameter * OsmNextSignDiameterFraction
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Box(modifier = Modifier.size(mainDiameter)) {
                        OsmSpeedLimitSign(
                            label = display.currentLabel ?: dashLabel,
                            textStyle = mainTextStyle,
                            alpha = if (display.currentLabel != null) 1f else OsmInactiveAlpha,
                        )
                    }
                    if (display.showNext) {
                        Spacer(modifier = Modifier.width(gap))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(modifier = Modifier.size(nextDiameter)) {
                                OsmSpeedLimitSign(
                                    label = display.nextLabel ?: dashLabel,
                                    textStyle = nextTextStyle,
                                    alpha = 1f,
                                )
                            }
                            val distanceText = display.nextDistanceLabel(context)
                            if (distanceText != null) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = distanceText,
                                    color = resolvedTextColor.copy(alpha = 0.85f),
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    style = nextTextStyle,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OsmSpeedLimitSign(
    label: String,
    textStyle: TextStyle,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = OsmSignRingWidth,
                color = OsmSignRingColor.copy(alpha = alpha),
                shape = CircleShape,
            )
            .background(color = OsmSignFaceColor.copy(alpha = alpha), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = OsmSignTextColor.copy(alpha = alpha),
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = textStyle,
            maxLines = 1,
        )
    }
}

/** Pure display model for the OSM speed-limit tile (unit-testable). */
data class OsmSpeedLimitDisplay(
    val currentLabel: String?,
    val nextLabel: String?,
    val nextDistanceM: Double?,
    val showNext: Boolean,
) {
    fun nextDistanceLabel(context: android.content.Context): String? {
        val meters = nextDistanceM ?: return null
        return formatDistanceAhead(context, meters)
    }

    companion object {
        fun from(anchor: RoadMatchAnchorState): OsmSpeedLimitDisplay {
            val current = anchor.currentLimitKmh?.takeIf { it > 0 }?.toString()
            val nextHidden = anchor.nextLimitHidden
            val next = anchor.nextLimitKmh?.takeIf { it > 0 }?.toString()
            val dist = anchor.nextLimitDistanceM?.takeIf { it.isFinite() && it >= 0.0 }
            val showNext = !nextHidden && next != null
            return OsmSpeedLimitDisplay(
                currentLabel = current,
                nextLabel = next,
                nextDistanceM = dist.takeIf { showNext },
                showNext = showNext,
            )
        }

        fun formatDistanceAhead(context: android.content.Context, meters: Double): String {
            if (!meters.isFinite() || meters < 0.0) {
                return context.getString(R.string.osm_speed_limit_distance_unknown)
            }
            return if (meters >= 1000.0) {
                val km = meters / 1000.0
                val text = if (km >= 10.0) {
                    km.toInt().toString()
                } else {
                    String.format(java.util.Locale.US, "%.1f", km)
                }
                context.getString(R.string.osm_speed_limit_distance_km, text)
            } else {
                context.getString(R.string.osm_speed_limit_distance_m, meters.toInt())
            }
        }
    }
}
