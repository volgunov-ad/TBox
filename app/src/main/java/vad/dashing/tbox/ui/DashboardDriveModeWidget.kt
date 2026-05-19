package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import vad.dashing.tbox.DriveModeWidgetOption
import vad.dashing.tbox.R
import vad.dashing.tbox.resolveDriveModeWidgetOption
import vad.dashing.tbox.mbcan.MbCanRepository

private val DriveModeWidgetEcoColor = Color(0xD900A400)
private val DriveModeWidgetNorColor = Color(0xD9004DFF)
private val DriveModeWidgetSptColor = Color(0xD9FF0000)
private val DriveModeWidgetSandColor = Color(0xD9E6C200)
private val DriveModeWidgetMudColor = Color(0xD98B5A2B)
private val DriveModeWidgetSnowColor = Color(0xD900C8FF)

@Composable
fun DashboardDriveModeWidgetItem(
    selectedDriveModeRawValue: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    textColor: Color,
    backgroundColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
) {
    val currentDriveMode by MbCanRepository.carSettingsDriveMode.collectAsStateWithLifecycle()
    val selectedMode = resolveDriveModeWidgetOption(selectedDriveModeRawValue)
    val isSelectedModeActive = currentDriveMode == selectedMode.rawValue
    val defaultTitle = stringResource(R.string.data_title_drive_mode_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        onClick = onClick,
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor,
    ) { availableHeight, resolvedTextColor ->
        val modeTextColor = if (isSelectedModeActive) {
            selectedMode.activeColor()
        } else {
            resolvedTextColor
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .wrapContentHeight(Alignment.CenterVertically),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DashboardWidgetTitleRowIfVisible(
                showTitle = showTitle,
                titleText = titleText,
                availableHeight = availableHeight,
                resolvedTextColor = resolvedTextColor
            )
            val modeFont = calculateResponsiveFontSize(
                containerHeight = availableHeight,
                textType = TextType.VALUE
            ) * 1.3f
            Text(
                text = selectedMode.label,
                modifier = Modifier
                    .weight(if (showTitle) 2f else 1f)
                    .fillMaxWidth()
                    .wrapContentHeight(Alignment.CenterVertically),
                fontSize = modeFont,
                lineHeight = modeFont * 1.3f,
                fontWeight = FontWeight.Medium,
                color = modeTextColor,
                textAlign = TextAlign.Center,
                maxLines = 2,
                softWrap = true,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun DriveModeWidgetOption.activeColor(): Color {
    return when (label) {
        "ECO" -> DriveModeWidgetEcoColor
        "NOR" -> DriveModeWidgetNorColor
        "SPT" -> DriveModeWidgetSptColor
        "SAND" -> DriveModeWidgetSandColor
        "MUD" -> DriveModeWidgetMudColor
        "SNOW" -> DriveModeWidgetSnowColor
        else -> Color.Unspecified
    }
}
