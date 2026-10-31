package vad.dashing.tbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import vad.dashing.tbox.R
import vad.dashing.tbox.dexopt.AppDexoptCompiler

@Composable
fun AppDexoptHintDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val close = {
        AppDexoptCompiler.markHintShown(context)
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = close,
        title = { AppAlertDialogTitle(stringResource(R.string.app_dexopt_hint_title)) },
        text = { AppAlertDialogText(stringResource(R.string.app_dexopt_hint_message)) },
        confirmButton = {
            Button(
                onClick = rememberWrappedOnClick {
                    AppDexoptCompiler.compile(context)
                    close()
                },
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.app_dexopt_hint_compile))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = rememberWrappedOnClick(close)) {
                AppAlertDialogButtonLabel(stringResource(R.string.app_dexopt_hint_later))
            }
        },
    )
}
