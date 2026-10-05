package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.AUTOMATION_TRIGGER_ID_MAX_CHARS
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeAutomationTriggerId
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
internal fun AutomationTriggerWidgetSettingsSection(
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
) {
    if (!state.isAutomationTriggerWidgetSelected) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_automation_trigger_settings_title),
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = state.automationTriggerId,
            onValueChange = { raw ->
                state.automationTriggerId = raw.take(AUTOMATION_TRIGGER_ID_MAX_CHARS)
            },
            enabled = state.togglesEnabled,
            textStyle = MaterialTheme.typography.tboxBody,
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            label = {
                Text(
                    stringResource(R.string.widget_automation_trigger_id_label),
                    style = MaterialTheme.typography.tboxCaption
                )
            },
            supportingText = {
                Text(
                    text = stringResource(R.string.widget_automation_trigger_id_hint),
                    style = MaterialTheme.typography.tboxCaption
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        )
    }
}
