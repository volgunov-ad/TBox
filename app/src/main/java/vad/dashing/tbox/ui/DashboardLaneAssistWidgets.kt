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
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.ui.theme.WidgetActiveColors

@Composable
fun DashboardLdwWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    LaneModeTextWidget(
        modeValue = MbCanKnownVehiclePropertyId.LAS_MODE_LDW,
        label = "LDW",
        defaultTitleRes = R.string.data_title_ldw_widget,
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
fun DashboardLkaWidgetItem(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    LaneModeTextWidget(
        modeValue = MbCanKnownVehiclePropertyId.LAS_MODE_LKA,
        label = "LKA",
        defaultTitleRes = R.string.data_title_lka_widget,
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
private fun LaneModeTextWidget(
    modeValue: Int,
    label: String,
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
    val lasMode by UniversalCanRepository.lasModeRaw.collectAsStateWithLifecycle()
    val isActive = lasMode == modeValue
    val controls = LocalWidgetControlAppearance.current
    val useDefaults = LocalWidgetControlUsesDefaults.current
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
        val modeTextColor = if (isActive) {
            if (useDefaults) WidgetActiveColors.Primary else controls.activeContent
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
                .widgetControlOuterPadding(controls)
                .wrapContentHeight(Alignment.CenterVertically),
        ) { contentModifier ->
            WidgetControlChrome(
                background = if (isActive) controls.activeBackground else controls.inactiveBackground,
                shapeDp = controls.shapeDp,
                modifier = contentModifier.fillMaxWidth(),
            ) {
                val modeStyle = calculateResponsiveTextStyle(
                    containerHeight = availableHeight,
                    textType = TextType.VALUE
                )
                Text(
                    text = label,
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
