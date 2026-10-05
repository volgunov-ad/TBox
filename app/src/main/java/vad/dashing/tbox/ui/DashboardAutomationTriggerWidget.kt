package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.automation.AutomationTriggerWidgetPressEventBus
import vad.dashing.tbox.automation.AutomationTriggerWidgetState

@Composable
internal fun DashboardAutomationTriggerWidgetItem(
    isEditMode: Boolean,
    onEditClick: () -> Unit,
    onLongClick: () -> Unit,
    elevation: Dp,
    shape: Dp,
    backgroundColor: Color,
    textColor: Color,
    showTitle: Boolean = false,
    titleOverride: String = "",
    defaultTitle: String,
    automationTriggerId: String,
) {
    val activeIds by AutomationTriggerWidgetState.activeIds.collectAsStateWithLifecycle()
    val active = automationTriggerId.isNotBlank() && automationTriggerId in activeIds
    val controls = LocalWidgetControlAppearance.current
    val contentColor = if (active) controls.activeContent else controls.inactiveContent
    val titleText = titleOverride.trim().ifBlank { defaultTitle }

    DashboardWidgetScaffold(
        modifier = Modifier.fillMaxSize(),
        onClick = {
            if (isEditMode) {
                onEditClick()
                return@DashboardWidgetScaffold
            }
            val triggerId = automationTriggerId.trim()
            if (triggerId.isEmpty()) return@DashboardWidgetScaffold
            // Raw tap: AutomationEngine runs exclusive single/double recognition with the
            // same ViewConfiguration double-tap timeout as other dashboard widgets.
            AutomationTriggerWidgetPressEventBus.publish(triggerId)
        },
        onLongClick = onLongClick,
        elevation = elevation,
        shape = shape,
        textColor = textColor,
        backgroundColor = backgroundColor
    ) { availableHeight, _ ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val titleStyle = calculateResponsiveTextStyle(
                containerHeight = availableHeight,
                textType = TextType.TITLE,
                forWidgetTitle = true,
            )
            Text(
                text = titleText,
                style = titleStyle,
                color = contentColor,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
            )
        }
    }
}
