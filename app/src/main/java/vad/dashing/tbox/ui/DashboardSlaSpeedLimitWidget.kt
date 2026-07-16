package vad.dashing.tbox.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.UniversalCanRepository

/** Red ring of a round speed-limit road sign (R.3 / 3.24). */
private val SlaSignRingColor = Color(0xFFE53935)
private val SlaSignFaceColor = Color.White
private val SlaSignTextColor = Color.Black
/** Ring thickness as a fraction of the sign diameter (approx. real sign proportions). */
private const val SlaSignRingFraction = 0.12f

@Composable
fun DashboardSlaSpeedLimitWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val recognizedLimitKmh by UniversalCanRepository.slaRecognizedSpeedLimitKmh.collectAsStateWithLifecycle()
    // Sign km/h only; slaOnOffState (settings toggle) is intentionally not used here.
    val defaultTitle = stringResource(R.string.data_title_sla_speed_limit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val limitLabel = recognizedLimitKmh?.toString() ?: stringResource(R.string.sla_speed_limit_no_sign)

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DashboardWidgetTitleRowIfVisible(
                showTitle = showTitle,
                titleText = titleText,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight(0.82f)
                        .aspectRatio(1f),
                ) {
                    val ringWidth = maxWidth * SlaSignRingFraction
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(width = ringWidth, color = SlaSignRingColor, shape = CircleShape)
                            .background(color = SlaSignFaceColor, shape = CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = limitLabel,
                            color = SlaSignTextColor,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            style = calculateResponsiveTextStyle(
                                containerHeight = availableHeight,
                                textType = TextType.VALUE,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
