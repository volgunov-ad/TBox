package vad.dashing.tbox.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.SlaSignUiState
import vad.dashing.tbox.mbcan.UniversalCanRepository

/** Red ring of a round speed-limit road sign (R.3 / 3.24). */
private val SlaSignRingColor = Color(0xFFE53935)
private val SlaSignFaceColor = Color.White
private val SlaSignTextColor = Color.Black
/** End-of-restriction (release) sign — grey circle + slash. */
private val SlaEndRestrictionColor = Color(0xFF9E9E9E)
private val SlaEndRestrictionFaceColor = Color(0xFFF5F5F5)
/** Dimmed inactive sign opacity. */
private const val SlaInactiveAlpha = 0.4f
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
    val signUi by UniversalCanRepository.slaSignUiState.collectAsStateWithLifecycle()
    val defaultTitle = stringResource(R.string.data_title_sla_speed_limit_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val dashLabel = stringResource(R.string.sla_speed_limit_no_sign)

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
            titleWeight = 1f,
            contentWeight = 1f,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            Box(
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight(0.82f)
                        .aspectRatio(1f),
                ) {
                    val ringWidth = maxWidth * SlaSignRingFraction
                    when (val state = signUi) {
                        is SlaSignUiState.Limit -> {
                            SlaSpeedLimitSign(
                                label = state.kmh.toString(),
                                ringColor = SlaSignRingColor,
                                faceColor = SlaSignFaceColor,
                                textColor = SlaSignTextColor,
                                ringWidth = ringWidth,
                                availableHeight = availableHeight,
                                alpha = 1f,
                            )
                        }
                        SlaSignUiState.EndOfRestriction -> {
                            SlaEndOfRestrictionSign(ringWidth = ringWidth)
                        }
                        SlaSignUiState.Inactive -> {
                            SlaSpeedLimitSign(
                                label = dashLabel,
                                ringColor = SlaSignRingColor,
                                faceColor = SlaSignFaceColor,
                                textColor = SlaSignTextColor,
                                ringWidth = ringWidth,
                                availableHeight = availableHeight,
                                alpha = SlaInactiveAlpha,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SlaSpeedLimitSign(
    label: String,
    ringColor: Color,
    faceColor: Color,
    textColor: Color,
    ringWidth: Dp,
    availableHeight: Dp,
    alpha: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .border(
                width = ringWidth,
                color = ringColor.copy(alpha = alpha),
                shape = CircleShape,
            )
            .background(color = faceColor.copy(alpha = alpha), shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = textColor.copy(alpha = alpha),
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

@Composable
private fun SlaEndOfRestrictionSign(ringWidth: Dp) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(width = ringWidth, color = SlaEndRestrictionColor, shape = CircleShape)
                .background(color = SlaEndRestrictionFaceColor, shape = CircleShape),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = ringWidth.toPx()
            val inset = size.minDimension * 0.22f
            drawLine(
                color = SlaEndRestrictionColor,
                start = Offset(inset, size.height - inset),
                end = Offset(size.width - inset, inset),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}
