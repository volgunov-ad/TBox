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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import vad.dashing.tbox.R
import vad.dashing.tbox.normalizeDialPhoneNumber
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
internal fun PhoneDialWidgetSettingsSection(
    state: WidgetSelectionDialogState,
    modifier: Modifier = Modifier,
) {
    if (!state.isPhoneDialWidgetSelected) return
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_phone_dial_settings_title),
            style = MaterialTheme.typography.tboxBody,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = state.dialPhoneNumber,
            onValueChange = { state.dialPhoneNumber = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(stringResource(R.string.widget_phone_dial_number_label)) },
            placeholder = { Text(stringResource(R.string.widget_phone_dial_number_hint)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
        if (normalizeDialPhoneNumber(state.dialPhoneNumber).isEmpty()) {
            Text(
                text = stringResource(R.string.widget_phone_dial_number_required),
                style = MaterialTheme.typography.tboxCaption,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        SettingSwitch(
            state.dialShowNumber,
            { state.dialShowNumber = it },
            stringResource(R.string.widget_phone_dial_show_number),
            stringResource(R.string.widget_phone_dial_show_number_desc),
            state.togglesEnabled,
        )
        SettingSwitch(
            state.dialConfirmBeforeCall,
            { state.dialConfirmBeforeCall = it },
            stringResource(R.string.widget_phone_dial_confirm_before_call),
            stringResource(R.string.widget_phone_dial_confirm_before_call_desc),
            state.togglesEnabled,
        )
    }
}
