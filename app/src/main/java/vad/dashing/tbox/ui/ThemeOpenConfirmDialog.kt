package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import vad.dashing.tbox.ThemeApply
import vad.dashing.tbox.ThemeApplyTarget
import vad.dashing.tbox.ThemeApplyTargetAvailability
import vad.dashing.tbox.ThemeCacheKeys
import vad.dashing.tbox.ThemeFileResolver
import vad.dashing.tbox.ThemeMaterialization
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
    var availableTargets by remember(request) { mutableStateOf<Set<ThemeApplyTarget>?>(null) }
    var selectedTargets by remember(request) { mutableStateOf<Set<ThemeApplyTarget>>(emptySet()) }
    var isFirstMaterialize by remember(request) { mutableStateOf(false) }

    LaunchedEffect(request) {
        val peek = withContext(Dispatchers.IO) {
            val bytes = ThemeFileResolver.openBytes(context, request.uriString) ?: return@withContext null
            val available = ThemeApply.peekAvailableApplyTargets(bytes).getOrNull() ?: return@withContext null
            val cacheKey = ThemeCacheKeys.resolveUniqueManualCacheKey(context, request.uriString)
            Triple(available, ThemeApplyTargetAvailability.defaultEnabled(available), !ThemeMaterialization.isMaterialized(context, cacheKey))
        }
        if (peek != null) {
            availableTargets = peek.first
            selectedTargets = peek.second
            isFirstMaterialize = peek.third
        }
    }

    AlertDialog(
        onDismissRequest = { if (!applying) onDismiss() },
        title = {
            AppAlertDialogTitle(stringResource(R.string.theme_open_confirm_title))
        },
        text = {
            Column {
                AppAlertDialogText(
                    stringResource(R.string.theme_open_confirm_message, request.displayName),
                )
                val targets = availableTargets
                if (isFirstMaterialize && targets != null) {
                    AppAlertDialogText(stringResource(R.string.themes_apply_targets_dialog_hint))
                    ThemeApplyTargetCheckboxList(
                        availableTargets = targets,
                        selectedTargets = selectedTargets,
                        onTargetCheckedChange = { target, checked ->
                            selectedTargets = if (checked) {
                                selectedTargets + target
                            } else {
                                selectedTargets - target
                            }
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !applying,
                onClick = rememberWrappedOnClick {
                    if (isFirstMaterialize && selectedTargets.isEmpty()) {
                        Toast.makeText(
                            context,
                            R.string.themes_apply_targets_select_one,
                            Toast.LENGTH_SHORT,
                        ).show()
                        return@rememberWrappedOnClick
                    }
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
                            settingsViewModel.applyThemeFromUri(
                                context = context,
                                uriString = request.uriString,
                                applyTargets = selectedTargets.takeIf { isFirstMaterialize },
                            )
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
            OutlinedButton(
                enabled = !applying,
                onClick = rememberWrappedOnClick(onDismiss),
            ) {
                AppAlertDialogButtonLabel(stringResource(R.string.value_no))
            }
        },
    )
}
