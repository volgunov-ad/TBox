package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.MirrorAdjustModeRepository
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanBinaryState
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.launchMirrorFoldDoubleTap
import vad.dashing.tbox.mbcan.launchMirrorFoldSingleTap

@Composable
fun DashboardMirrorAdjustModeWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    scale: Float = 1f,
) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        MirrorAdjustModeRepository.startObserving(context)
        onDispose { MirrorAdjustModeRepository.stopObserving(context) }
    }
    val state by MirrorAdjustModeRepository.mirrorAdjustModeState.collectAsStateWithLifecycle()
    val controls = LocalWidgetControlAppearance.current
    val iconColor = when (state) {
        is MbCanBinaryState.On -> controls.activeContent
        is MbCanBinaryState.Off -> controls.inactiveContent
        else -> controls.inactiveContent.copy(alpha = 0.25f)
    }
    val defaultTitle = stringResource(R.string.data_title_mirror_adjust_mode_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
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
                background = if (state is MbCanBinaryState.On) controls.activeBackground else controls.inactiveBackground,
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_widget_mirror_adjust),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().scale(scale),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}

@Composable
fun DashboardMirrorFoldWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDoubleClick: () -> Unit,
    enableInnerInteractions: Boolean,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    scale: Float = 1f,
) {
    val scope = rememberCoroutineScope()
    val controls = LocalWidgetControlAppearance.current
    val defaultTitle = stringResource(R.string.data_title_mirror_fold_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                UniversalCanRepository.launchMirrorFoldSingleTap(scope)
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                UniversalCanRepository.launchMirrorFoldDoubleTap(scope)
            }
            onDoubleClick()
        },
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
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
            // No on-state: only inactive control colors apply.
            WidgetControlChrome(
                background = controls.inactiveBackground,
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_widget_mirror_fold),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().scale(scale),
                    colorFilter = ColorFilter.tint(controls.inactiveContent)
                )
            }
        }
    }
}
