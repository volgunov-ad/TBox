package vad.dashing.voice.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.voice.R

@Composable
fun VoiceHomeScreen(
    viewModel: VoiceHomeViewModel,
    autoListenSource: String? = null,
    onAutoListenConsumed: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.startListen("ui")
        } else {
            viewModel.onMicPermissionDenied()
        }
    }

    fun requestListen(source: String) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            if (state.listening) {
                viewModel.toggleListen(source)
            } else {
                viewModel.startListen(source)
            }
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(autoListenSource) {
        val source = autoListenSource ?: return@LaunchedEffect
        requestListen(source)
        onAutoListenConsumed()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_title),
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.host,
            onValueChange = viewModel::onHostChange,
            label = { Text(stringResource(R.string.settings_host)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.portText,
            onValueChange = viewModel::onPortChange,
            label = { Text(stringResource(R.string.settings_port)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.token,
            onValueChange = viewModel::onTokenChange,
            label = { Text(stringResource(R.string.settings_token)) },
            placeholder = { Text(stringResource(R.string.settings_token_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = viewModel::saveSettings,
            enabled = !state.busy && !state.listening,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(
            onClick = viewModel::checkHealth,
            enabled = !state.busy && !state.listening,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_check_health))
        }
        OutlinedButton(
            onClick = viewModel::refreshCatalog,
            enabled = !state.busy && !state.listening,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_refresh_catalog))
        }

        OutlinedTextField(
            value = state.phrase,
            onValueChange = viewModel::onPhraseChange,
            label = { Text(stringResource(R.string.phrase_label)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            enabled = !state.listening,
        )
        Button(
            onClick = viewModel::runPhrase,
            enabled = !state.busy && !state.listening && state.phrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_run_phrase))
        }
        if (state.speaking) {
            OutlinedButton(
                onClick = viewModel::stopSpeaking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_stop_tts))
            }
        }
        Button(
            onClick = {
                if (state.listening) {
                    viewModel.toggleListen("ui")
                } else {
                    requestListen("ui")
                }
            },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                if (state.listening) {
                    stringResource(R.string.action_stop_listen)
                } else {
                    stringResource(R.string.action_listen)
                },
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (state.catalogSummary.isNotBlank()) {
            Text(
                text = state.catalogSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val statusColor = when {
            state.listening -> MaterialTheme.colorScheme.tertiary
            state.lastHealthOk == true -> MaterialTheme.colorScheme.primary
            state.lastHealthOk == false -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(
            text = state.statusMessage.ifBlank { stringResource(R.string.status_idle) },
            style = MaterialTheme.typography.bodyLarge,
            color = statusColor,
        )
        if (state.answerMessage.isNotBlank()) {
            Text(
                text = state.answerMessage,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
