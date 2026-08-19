package vad.dashing.tbox.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.HeadlightMode
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.UniversalCanRepository

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
    val defaultTitle = stringResource(R.string.data_title_headlight_mode_cycle_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }
    val hasMode = currentMode != null

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        val modeTextColor = if (hasMode) {
            controls.activeContent
        } else {
            controls.inactiveContent
        }

        DashboardWidgetContentWithOptionalTitle(
            showTitle = showTitle,
            titleText = titleText,
            availableHeight = availableHeight,
            resolvedTextColor = resolvedTextColor,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            WidgetControlChrome(
                background = if (hasMode) {
                    controls.activeBackground
                } else {
                    controls.inactiveBackground
                },
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                val modeStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE
                )
                Text(
                    text = currentMode?.widgetLabel ?: "—",
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(Alignment.CenterVertically),
                    style = modeStyle,
                    color = modeTextColor,
                    textAlign = LocalWidgetTextAlign.current,
                    maxLines = 1,
                    softWrap = true,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
