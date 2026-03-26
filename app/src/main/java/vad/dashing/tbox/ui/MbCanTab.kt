package vad.dashing.tbox.ui

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import vad.dashing.tbox.R
import vad.dashing.tbox.mbcan.MbCanViewModel

@Composable
fun MbCanTab() {
    val context = LocalContext.current
    val vm: MbCanViewModel = viewModel(
        factory = ViewModelProvider.AndroidViewModelFactory.getInstance(
            context.applicationContext as Application
        )
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    var intervalText by remember { mutableStateOf("500") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.mb_can_tab_intro),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = state.statusText,
            style = MaterialTheme.typography.titleMedium,
        )
        state.lastError?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        OutlinedTextField(
            value = intervalText,
            onValueChange = { intervalText = it.filter { ch -> ch.isDigit() } },
            label = { Text(stringResource(R.string.mb_can_interval_ms)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
        )
        Text(
            text = stringResource(
                R.string.mb_can_codes,
                state.initReturnCode?.toString() ?: "—",
                state.subscribeReturnCode?.toString() ?: "—",
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.mb_can_speed_gear,
                state.speedKmh?.toString() ?: "—",
                state.gear?.toString() ?: "—",
            ),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = {
            val interval = intervalText.toIntOrNull()?.coerceIn(50, 60_000) ?: 500
            vm.connect(interval)
        }) {
            Text(stringResource(R.string.mb_can_connect))
        }
        Button(onClick = { vm.disconnect() }) {
            Text(stringResource(R.string.mb_can_disconnect))
        }
    }
}
