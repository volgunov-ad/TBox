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
import androidx.compose.runtime.mutableStateOf
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
import vad.dashing.tbox.adb.PermissionsAutoGrant
import vad.dashing.tbox.adb.WriteSecureSettingsAutoGrant
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
    var autoGrantRunning by remember { mutableStateOf(false) }

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
    val anyMissing = items.any { !it.granted }
    val copiedToast = stringResource(R.string.permissions_adb_copied)
    val openFailedToast = stringResource(R.string.permissions_open_settings_failed)
    val autoGrantOk = stringResource(R.string.permissions_write_secure_auto_ok)
    val autoGrantAlready = stringResource(R.string.permissions_write_secure_auto_already)
    val failTcpEnable = stringResource(R.string.permissions_write_secure_auto_fail_tcp_enable)
    val failTcpReady = stringResource(R.string.permissions_write_secure_auto_fail_tcp_ready)
    val failAdb = stringResource(R.string.permissions_write_secure_auto_fail_adb)
    val failGrant = stringResource(R.string.permissions_write_secure_auto_fail_grant)
    val failMissing = stringResource(R.string.permissions_write_secure_auto_fail_missing)
    val grantAllOkTemplate = stringResource(R.string.permissions_auto_grant_all_ok)
    val grantAllAlready = stringResource(R.string.permissions_auto_grant_all_already)
    val grantAllPartialTemplate = stringResource(R.string.permissions_auto_grant_all_partial)
    val grantAllNone = stringResource(R.string.permissions_auto_grant_all_none)

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

    fun messageForWriteSecure(outcome: WriteSecureSettingsAutoGrant.Outcome): String {
        return when (outcome) {
            WriteSecureSettingsAutoGrant.Outcome.AlreadyGranted -> autoGrantAlready
            WriteSecureSettingsAutoGrant.Outcome.Success -> autoGrantOk
            is WriteSecureSettingsAutoGrant.Outcome.Failed -> {
                val base = when (outcome.reason) {
                    WriteSecureSettingsAutoGrant.Reason.TcpEnableFailed -> failTcpEnable
                    WriteSecureSettingsAutoGrant.Reason.TcpNotReady -> failTcpReady
                    WriteSecureSettingsAutoGrant.Reason.AdbConnectFailed -> failAdb
                    WriteSecureSettingsAutoGrant.Reason.GrantCommandFailed -> failGrant
                    WriteSecureSettingsAutoGrant.Reason.StillMissingAfterGrant -> failMissing
                }
                if (outcome.detail.isBlank()) base else "$base: ${outcome.detail}"
            }
        }
    }

    fun messageForGrantAll(outcome: PermissionsAutoGrant.Outcome): String {
        return when (outcome) {
            PermissionsAutoGrant.Outcome.AlreadyAllGranted -> grantAllAlready
            is PermissionsAutoGrant.Outcome.Success ->
                grantAllOkTemplate.format(outcome.newlyGranted.size)
            is PermissionsAutoGrant.Outcome.Partial -> {
                if (outcome.newlyGranted.isEmpty()) {
                    grantAllNone
                } else {
                    grantAllPartialTemplate.format(
                        outcome.newlyGranted.size,
                        outcome.stillMissing.size,
                    )
                }
            }
            is PermissionsAutoGrant.Outcome.Failed -> {
                val base = when (outcome.reason) {
                    PermissionsAutoGrant.Reason.TcpEnableFailed -> failTcpEnable
                    PermissionsAutoGrant.Reason.TcpNotReady -> failTcpReady
                    PermissionsAutoGrant.Reason.AdbConnectFailed -> failAdb
                }
                if (outcome.detail.isBlank()) base else "$base: ${outcome.detail}"
            }
        }
    }

    fun runAutoGrantWriteSecure() {
        if (autoGrantRunning) return
        autoGrantRunning = true
        scope.launch {
            val outcome = runCatching {
                WriteSecureSettingsAutoGrant.grant(context)
            }.getOrElse { error ->
                WriteSecureSettingsAutoGrant.Outcome.Failed(
                    WriteSecureSettingsAutoGrant.Reason.AdbConnectFailed,
                    error.message ?: error.javaClass.simpleName,
                )
            }
            autoGrantRunning = false
            refreshTick++
            Toast.makeText(context, messageForWriteSecure(outcome), Toast.LENGTH_LONG).show()
        }
    }

    fun runAutoGrantAll() {
        if (autoGrantRunning) return
        autoGrantRunning = true
        scope.launch {
            val outcome = runCatching {
                PermissionsAutoGrant.grantMissing(context)
            }.getOrElse { error ->
                PermissionsAutoGrant.Outcome.Failed(
                    PermissionsAutoGrant.Reason.AdbConnectFailed,
                    error.message ?: error.javaClass.simpleName,
                )
            }
            autoGrantRunning = false
            refreshTick++
            Toast.makeText(context, messageForGrantAll(outcome), Toast.LENGTH_LONG).show()
        }
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
                if (anyMissing) {
                    Text(
                        text = stringResource(R.string.permissions_auto_grant_all_hint),
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = rememberWrappedOnClick(::runAutoGrantAll),
                        enabled = !autoGrantRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(
                                if (autoGrantRunning) {
                                    R.string.permissions_auto_grant_all_running
                                } else {
                                    R.string.permissions_auto_grant_all
                                },
                            ),
                            style = MaterialTheme.typography.tboxButton,
                        )
                    }
                    HorizontalDivider()
                }
                items.forEachIndexed { index, item ->
                    if (index > 0) {
                        HorizontalDivider()
                    }
                    PermissionRow(
                        item = item,
                        autoGrantRunning = autoGrantRunning,
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
                        onAutoGrantClick = ::runAutoGrantWriteSecure,
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
    autoGrantRunning: Boolean,
    onGrantClick: () -> Unit,
    onCopyAdbClick: () -> Unit,
    onAutoGrantClick: () -> Unit,
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
                        enabled = !autoGrantRunning,
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
                    if (item.id == AppPermissionId.WriteSecureSettings) {
                        Text(
                            text = stringResource(R.string.permissions_write_secure_auto_hint),
                            style = MaterialTheme.typography.tboxCaption,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Button(
                            onClick = rememberWrappedOnClick(onAutoGrantClick),
                            enabled = !autoGrantRunning,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            Text(
                                text = stringResource(
                                    if (autoGrantRunning) {
                                        R.string.permissions_write_secure_auto_grant_running
                                    } else {
                                        R.string.permissions_write_secure_auto_grant
                                    },
                                ),
                                style = MaterialTheme.typography.tboxButton,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = rememberWrappedOnClick(onCopyAdbClick),
                        enabled = !autoGrantRunning,
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
