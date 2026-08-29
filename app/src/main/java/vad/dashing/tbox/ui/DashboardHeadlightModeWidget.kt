package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
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
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.normalizeWidgetScale

@Composable
fun DashboardHeadlightModeCycleWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val modeRaw by UniversalCanRepository.headlightModeRaw.collectAsStateWithLifecycle()
    val currentMode = modeRaw?.let(HeadlightMode::fromRaw)
    val controls = LocalWidgetControlAppearance.current
    val iconScale = normalizeWidgetScale(LocalWidgetIconScale.current)
    val defaultTitle = stringResource(R.string.data_title_headlight_mode_cycle_widget)
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
            WidgetControlChrome(
                background = if (currentMode != null) {
                    controls.activeBackground
                } else {
                    controls.inactiveBackground
                },
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                if (currentMode != null) {
                    Image(
                        painter = painterResource(headlightModeWidgetLabelIconRes(currentMode)),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().scale(iconScale),
                        colorFilter = ColorFilter.tint(controls.activeContent),
                    )
                } else {
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
