package vad.dashing.tbox.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.HttpRequestIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.SetLauncherAppCustomIconResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.parseHttpRequestWidgetYaml
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

@Composable
internal fun HttpRequestWidgetSettingsSection(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    panelStorageId: String,
    widgetIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (!state.isHttpRequestWidgetSelected) return
    val context = LocalContext.current
    val iconKey = remember(panelStorageId, widgetIndex) {
        HttpRequestIconPaths.iconKey(panelStorageId, widgetIndex)
    }
    val iconRevision by settingsViewModel.httpRequestIconRevision.collectAsStateWithLifecycle()
    var selectedHasCustomIcon by remember { mutableStateOf(false) }
    LaunchedEffect(iconKey, iconRevision) {
        selectedHasCustomIcon = settingsViewModel.hasCustomHttpRequestIcon(iconKey)
    }
    val canPickImage = remember(context) {
        Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            .resolveActivity(context.packageManager) != null
    }
    var pendingIconKey by rememberSaveable { mutableStateOf<String?>(null) }
    val pickCustomIcon = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val key = pendingIconKey ?: iconKey
        pendingIconKey = null
        if (uri == null) return@rememberLauncherForActivityResult
        settingsViewModel.setCustomHttpRequestIconFromUri(key, uri) { result ->
            val msg = when (result) {
                SetLauncherAppCustomIconResult.Success ->
                    context.getString(R.string.widget_app_launcher_icon_saved)
                SetLauncherAppCustomIconResult.DimensionsTooLarge ->
                    context.getString(R.string.widget_app_launcher_icon_too_large)
                SetLauncherAppCustomIconResult.NotImageOrUnreadable ->
                    context.getString(R.string.widget_app_launcher_icon_invalid)
                SetLauncherAppCustomIconResult.CopyFailed ->
                    context.getString(R.string.widget_app_launcher_icon_copy_failed)
                SetLauncherAppCustomIconResult.InvalidPackage -> null
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val parseError = remember(state.httpRequestYaml) {
        parseHttpRequestWidgetYaml(state.httpRequestYaml).exceptionOrNull()?.message.orEmpty()
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.widget_http_request_settings_title),
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        OutlinedTextField(
            value = state.httpRequestYaml,
            onValueChange = { state.httpRequestYaml = it },
            enabled = state.togglesEnabled,
            textStyle = MaterialTheme.typography.tboxBody,
            label = {
                Text(
                    stringResource(R.string.widget_http_request_yaml_label),
                    style = MaterialTheme.typography.tboxCaption
                )
            },
            minLines = 6,
            maxLines = 14,
            isError = parseError.isNotBlank(),
            supportingText = {
                Text(
                    text = parseError.ifBlank {
                        stringResource(R.string.widget_http_request_yaml_hint)
                    },
                    style = MaterialTheme.typography.tboxCaption
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        )
        SettingSwitch(
            state.httpOpenBrowser,
            { state.httpOpenBrowser = it },
            stringResource(R.string.widget_http_request_open_browser),
            stringResource(R.string.widget_http_request_open_browser_desc),
            state.togglesEnabled
        )
        Text(
            text = stringResource(R.string.widget_http_request_secret_warning),
            style = MaterialTheme.typography.tboxCaption,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    if (canPickImage) {
                        pendingIconKey = iconKey
                        pickCustomIcon.launch("image/*")
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.settings_main_screen_wallpaper_no_picker),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                },
                enabled = state.togglesEnabled,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.widget_app_launcher_change_icon),
                    style = MaterialTheme.typography.tboxCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.clearCustomHttpRequestIcon(iconKey)
                },
                enabled = state.togglesEnabled && selectedHasCustomIcon,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.widget_app_launcher_remove_icon),
                    style = MaterialTheme.typography.tboxCaption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
