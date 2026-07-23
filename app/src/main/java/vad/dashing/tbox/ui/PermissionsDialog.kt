package vad.dashing.tbox.ui

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.launch
import vad.dashing.tbox.AppPermissionGrantKind
import vad.dashing.tbox.AppPermissionId
import vad.dashing.tbox.AppPermissionStatus
import vad.dashing.tbox.AppPermissions
import vad.dashing.tbox.R
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

@Composable
fun PermissionsDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshTick++
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        refreshTick++
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val items = remember(refreshTick) { AppPermissions.snapshot(context) }
    val copiedToast = stringResource(R.string.permissions_adb_copied)
    val openFailedToast = stringResource(R.string.permissions_open_settings_failed)

    fun openSettingsFor(id: AppPermissionId) {
        val intent = AppPermissions.createGrantIntent(context, id) ?: return
        runCatching {
            settingsLauncher.launch(intent)
        }.onFailure {
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure {
                    Toast.makeText(context, openFailedToast, Toast.LENGTH_LONG).show()
                }
        }
    }

    fun requestRuntimeFor(id: AppPermissionId) {
        val perms = AppPermissions.runtimePermissionsFor(id)
        if (perms.isEmpty()) {
            openSettingsFor(id)
            return
        }
        runtimeLauncher.launch(perms)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            AppAlertDialogTitle(stringResource(R.string.permissions_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.permissions_dialog_intro),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    PermissionRow(
                        item = item,
                        onGrantClick = {
                            when (item.grantKind) {
                                AppPermissionGrantKind.OpenSettings -> openSettingsFor(item.id)
                                AppPermissionGrantKind.RequestRuntime -> requestRuntimeFor(item.id)
                                AppPermissionGrantKind.AdbOnly -> Unit
                            }
                        },
                        onCopyAdbClick = {
                            scope.launch {
                                clipboard.setClipEntry(
                                    ClipEntry(ClipData.newPlainText("adb", item.adbCommand)),
                                )
                            }
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = rememberWrappedOnClick(onDismiss)) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun PermissionRow(
    item: AppPermissionStatus,
    onGrantClick: () -> Unit,
    onCopyAdbClick: () -> Unit,
) {
    val statusText = if (item.granted) {
        stringResource(R.string.permissions_status_granted)
    } else {
        stringResource(R.string.permissions_status_missing)
    }
    val statusColor = if (item.granted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.error
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(item.titleRes),
                style = MaterialTheme.typography.tboxTitle,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.tboxCaption,
                color = statusColor,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = stringResource(item.descriptionRes),
            style = MaterialTheme.typography.tboxBody,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!item.granted) {
            when (item.grantKind) {
                AppPermissionGrantKind.OpenSettings,
                AppPermissionGrantKind.RequestRuntime -> {
                    Button(
                        onClick = rememberWrappedOnClick(onGrantClick),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.permissions_action_grant),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
                AppPermissionGrantKind.AdbOnly -> {
                    Text(
                        text = item.adbCommand,
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick(onCopyAdbClick),
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_copy),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                }
            }
        } else if (item.grantKind == AppPermissionGrantKind.AdbOnly && item.adbCommand.isNotBlank()) {
            // Keep ADB command visible even when already granted (useful to re-run after reinstall).
            Text(
                text = item.adbCommand,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
