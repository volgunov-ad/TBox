package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeWidgetScale

@Composable
fun DashboardAppListWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val controls = LocalWidgetControlAppearance.current
    val defaultTitle = stringResource(R.string.data_title_app_list_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
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
            Box(
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                WidgetControlChrome(
                    background = controls.inactiveBackground,
                    shapeDp = controls.shapeDp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Image(
                        painter = customizableUiPainter(R.drawable.ic_widget_app_list),
                        contentDescription = titleText,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .matchParentSize()
                            .scale(iconScale),
                        colorFilter = uiIconColorFilter(R.drawable.ic_widget_app_list, controls.inactiveContent),
                    )
                }
            }
        }
    }
}
