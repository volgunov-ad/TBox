package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.MbCanRepository

private val HvacDefrosterFrontOnColor = Color(0xFF0066CC)

@Composable
fun DashboardHvacDefrosterFrontWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    scale: Float = 1f
) {
    val state by MbCanRepository.hvacDefrosterFrontState.collectAsStateWithLifecycle()
    val iconColor = when (state) {
        is MbCanBinaryState.On -> HvacDefrosterFrontOnColor
        is MbCanBinaryState.Off -> textColor
        else -> textColor.copy(alpha = 0.25f)
    }
    val defaultTitle = stringResource(R.string.data_title_hvac_defroster_front_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            DashboardWidgetTitleRowIfVisible(
                showTitle = showTitle,
                titleText = titleText,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(if (showTitle) 2f else 1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_widget_hvac_defroster_front),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize().scale(scale),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}
