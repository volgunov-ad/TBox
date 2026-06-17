package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ThemeOpenRequest

@Composable
fun ThemeOpenConfirmDialog(
    request: ThemeOpenRequest,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var applying by remember(request) { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = {
            AppAlertDialogTitle(stringResource(R.string.theme_open_confirm_title))
        },
        text = {
            AppAlertDialogText(
                stringResource(R.string.theme_open_confirm_message, request.displayName),
            )
        },
        confirmButton = {
            TextButton(
                enabled = !applying,
                onClick = rememberWrappedOnClick {
                    applying = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                val uri = Uri.parse(request.uriString)
                                context.contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                                )
                            }
                            settingsViewModel.applyThemeFromUri(context, request.uriString)
                        }
                        withContext(Dispatchers.Main) {
                            if (result.isSuccess) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_theme_apply_ok),
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                val msg = result.exceptionOrNull()?.message.orEmpty()
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.toast_theme_apply_error, msg),
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                            onDismiss()
                        }
                    }
                },
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.value_yes))
            }
        },
        dismissButton = {
            TextButton(
                enabled = !applying,
                onClick = rememberWrappedOnClick(onDismiss),
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.value_no))
            }
        },
    )
}
