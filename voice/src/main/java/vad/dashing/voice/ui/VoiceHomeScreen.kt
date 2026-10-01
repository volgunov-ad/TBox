package vad.dashing.voice.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.voice.R

@Composable
fun VoiceHomeScreen(
    viewModel: VoiceHomeViewModel,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_save))
        }
        OutlinedButton(
            onClick = viewModel::checkHealth,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_check_health))
        }
        OutlinedButton(
            onClick = viewModel::refreshCatalog,
            enabled = !state.busy,
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
        )
        Button(
            onClick = viewModel::runPhrase,
            enabled = !state.busy && state.phrase.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_run_phrase))
        }
        Button(
            onClick = { /* STT — later */ },
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_listen))
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (state.catalogSummary.isNotBlank()) {
            Text(
                text = state.catalogSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val statusColor = when (state.lastHealthOk) {
            true -> MaterialTheme.colorScheme.primary
            false -> MaterialTheme.colorScheme.error
            null -> MaterialTheme.colorScheme.onSurfaceVariant
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
