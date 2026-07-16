package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
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
import vad.dashing.tbox.HeadUnitDayNightRepository
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.WidgetActiveColors

@Composable
fun DashboardDayNightThemeWidgetItem(
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
    val context = LocalContext.current
    DisposableEffect(Unit) {
        HeadUnitDayNightRepository.startObserving(context)
        onDispose { HeadUnitDayNightRepository.stopObserving(context) }
    }
    val mode = HeadUnitDayNightRepository.modeState.collectAsStateWithLifecycle().value
        ?: HeadUnitDayNightRepository.readMode(context)

    val (iconRes, iconColor) = when (mode) {
        HeadUnitDayNightRepository.Mode.LightManual -> {
            R.drawable.ic_widget_day_night_light_mode to WidgetActiveColors.Secondary
        }

        HeadUnitDayNightRepository.Mode.LightAuto -> {
            R.drawable.ic_widget_day_night_light_mode_auto to WidgetActiveColors.Secondary
        }

        HeadUnitDayNightRepository.Mode.DarkManual -> {
            R.drawable.ic_widget_day_night_dark_mode to WidgetActiveColors.Primary
        }

        HeadUnitDayNightRepository.Mode.DarkAuto -> {
            R.drawable.ic_widget_day_night_dark_mode_auto to WidgetActiveColors.Primary
        }
    }

    val defaultTitle = stringResource(R.string.data_title_day_night_theme_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = {
            if (enableInnerInteractions) {
                HeadUnitDayNightRepository.toggleManualTheme(context)
            } else {
                onClick()
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                HeadUnitDayNightRepository.enableAutoMode(context)
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
            titleWeight = 1f,
            contentWeight = if (showTitle) 2f else 1f,
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            Box(
                modifier = contentModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize().scale(scale),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}
