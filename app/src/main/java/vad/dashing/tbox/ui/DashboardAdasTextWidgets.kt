package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
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
import kotlinx.coroutines.flow.StateFlow
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.normalizeWidgetScale
import vad.dashing.tbox.ui.theme.WidgetActiveColors

@Composable
fun DashboardTjaIcaWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    AdasBinaryLabelWidget(
        stateFlow = UniversalCanRepository.tjaIcaState,
        iconRes = R.drawable.ic_widget_label_tja_ica,
        defaultTitleRes = R.string.data_title_tja_ica_widget,
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
fun DashboardHmaWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    AdasBinaryLabelWidget(
        stateFlow = UniversalCanRepository.hmaState,
        iconRes = R.drawable.ic_widget_label_hma,
        defaultTitleRes = R.string.data_title_hma_widget,
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
private fun AdasBinaryLabelWidget(
    stateFlow: StateFlow<MbCanBinaryState>,
    iconRes: Int,
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
    val state by stateFlow.collectAsStateWithLifecycle()
    val isActive = state is MbCanBinaryState.On
    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current
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
        val iconColor = when {
            state !is MbCanBinaryState.On && state !is MbCanBinaryState.Off ->
                controls.inactiveContent.copy(alpha = 0.25f)
            isActive ->
                if (useDefaults) WidgetActiveColors.Primary else controls.activeContent
            else -> controls.inactiveContent
        }
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
                background = if (isActive) controls.activeBackground else controls.inactiveBackground,
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().scale(iconScale),
                    colorFilter = ColorFilter.tint(iconColor),
                )
            }
        }
    }
}
