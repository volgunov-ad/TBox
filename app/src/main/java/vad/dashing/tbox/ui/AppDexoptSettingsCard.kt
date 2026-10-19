package vad.dashing.tbox.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.dexopt.AppDexoptCompiler
import vad.dashing.tbox.dexopt.AppDexoptPhase
import vad.dashing.tbox.dexopt.AppDexoptState
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton

@Composable
fun AppDexoptSettingsCard(
    autoCompile: Boolean,
    onAutoCompileChange: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    val state by AppDexoptCompiler.state.collectAsStateWithLifecycle()
    val busy = state.phase == AppDexoptPhase.Checking ||
        state.phase == AppDexoptPhase.Compiling ||
        state.externalCompile

    LaunchedEffect(Unit) {
        AppDexoptCompiler.refreshMarked(context)
    }

    SettingsTitle(stringResource(R.string.settings_app_dexopt_title))
    Text(
        text = stringResource(R.string.settings_app_dexopt_desc),
        style = MaterialTheme.typography.tboxBody,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Text(
        text = appDexoptStatusText(state),
        style = MaterialTheme.typography.tboxBody,
        color = if (state.phase == AppDexoptPhase.Failed) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.padding(bottom = 4.dp),
    )
    Button(
        onClick = rememberWrappedOnClick { AppDexoptCompiler.compile(context) },
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
    ) {
        Text(
            text = stringResource(
                if (state.phase == AppDexoptPhase.Compiling) {
                    R.string.settings_app_dexopt_compiling_button
                } else {
                    R.string.settings_app_dexopt_compile
                },
            ),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    OutlinedButton(
        onClick = rememberWrappedOnClick { AppDexoptCompiler.checkStatus(context) },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = stringResource(R.string.settings_app_dexopt_check),
            style = MaterialTheme.typography.tboxButton,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
    SettingSwitch(
        autoCompile,
        onAutoCompileChange,
        stringResource(R.string.settings_app_dexopt_auto_title),
        stringResource(R.string.settings_app_dexopt_auto_desc),
        true,
    )
}

@Composable
private fun appDexoptStatusText(state: AppDexoptState): String = when (state.phase) {
    AppDexoptPhase.Unknown -> stringResource(
        if (state.markedCompiled) {
            R.string.settings_app_dexopt_status_marked
        } else {
            R.string.settings_app_dexopt_status_unknown
        },
    )
    AppDexoptPhase.Checking -> stringResource(R.string.settings_app_dexopt_status_checking)
    AppDexoptPhase.NotCompiled -> stringResource(R.string.settings_app_dexopt_status_not_compiled)
    AppDexoptPhase.Compiling -> stringResource(R.string.settings_app_dexopt_status_compiling)
    AppDexoptPhase.Compiled -> stringResource(R.string.settings_app_dexopt_status_compiled)
    AppDexoptPhase.Failed -> if (state.detail == "timeout") {
        stringResource(R.string.settings_app_dexopt_status_timeout)
    } else {
        stringResource(R.string.settings_app_dexopt_status_failed, state.detail)
    }
}
