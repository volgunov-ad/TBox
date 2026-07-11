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
import androidx.compose.runtime.rememberCoroutineScope
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
import vad.dashing.tbox.mbcan.TrunkDoorDomain
import vad.dashing.tbox.mbcan.TrunkDoorRepository
import vad.dashing.tbox.mbcan.UniversalCanRepository
import vad.dashing.tbox.mbcan.launchTrunkCommand
import vad.dashing.tbox.mbcan.trunkPulseFromStopped
import vad.dashing.tbox.mbcan.trunkPulseStop
import vad.dashing.tbox.ui.theme.WidgetActiveColors

@Composable
fun DashboardTrunkDoorWidgetItem(
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
    val trunkState by TrunkDoorRepository.displayState.collectAsStateWithLifecycle()
    val iconColor = if (TrunkDoorDomain.iconUsesActiveColor(trunkState)) {
        WidgetActiveColors.Primary
    } else {
        textColor
    }
    val defaultTitle = stringResource(R.string.data_title_trunk_door_widget)
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    fun runTrunkInteraction(isDoubleTap: Boolean) {
        if (TrunkDoorDomain.shouldPulseStop(trunkState)) {
            UniversalCanRepository.launchTrunkCommand(scope) { trunkPulseStop() }
            return
        }
        if (!isDoubleTap) return
        UniversalCanRepository.launchTrunkCommand(scope) { trunkPulseFromStopped(trunkState) }
    }

    DashboardWidgetScaffold(
        onClick = {
            if (!enableInnerInteractions) {
                onClick()
            } else {
                runTrunkInteraction(isDoubleTap = false)
            }
        },
        onLongClick = onLongClick,
        onDoubleClick = {
            if (enableInnerInteractions) {
                runTrunkInteraction(isDoubleTap = true)
            }
            onDoubleClick()
        },
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, resolvedTextColor ->
        Column(
            modifier = Modifier.fillMaxSize().padding(4.dp).wrapContentHeight(Alignment.CenterVertically),
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
                    .weight(if (showTitle) 1f else 1f),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_widget_trunk),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().scale(scale),
                    colorFilter = ColorFilter.tint(iconColor)
                )
            }
        }
    }
}
