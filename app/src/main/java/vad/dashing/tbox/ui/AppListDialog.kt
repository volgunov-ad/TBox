package vad.dashing.tbox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxTitle

internal data class AppListRow(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val hidden: Boolean,
    val canUninstall: Boolean,
)

internal fun canRequestUninstall(pm: PackageManager, packageName: String): Boolean {
    return runCatching {
        val info = pm.getApplicationInfo(packageName, 0)
        val isSystem = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        !isSystem || isUpdatedSystem
    }.getOrDefault(false)
}

private const val APP_LIST_UNINSTALL_TAG = "AppListUninstall"

/**
 * Builds the system uninstall intent (`ACTION_DELETE` / `package:` URI).
 * Requires [android.Manifest.permission.REQUEST_DELETE_PACKAGES] (declared in the manifest).
 */
internal fun buildSystemUninstallIntent(packageName: String): Intent {
    return Intent(Intent.ACTION_DELETE).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

/**
 * Opens the system uninstall confirmation UI for [packageName].
 * Keeps the App List dialog open; only the in-app confirm is dismissed by the caller.
 * Returns false if no handler could be started (caller may show a toast).
 */
internal fun requestSystemUninstall(context: Context, packageName: String): Boolean {
    val appContext = context.applicationContext
    val pm = appContext.packageManager
    val primary = buildSystemUninstallIntent(packageName)
    val fallback = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val intent = when {
        primary.resolveActivity(pm) != null -> primary
        fallback.resolveActivity(pm) != null -> fallback
        else -> primary
    }
    return try {
        // Prefer Activity when available so the installer stacks on the same task when possible.
        val launchContext: Context = context as? Activity ?: appContext
        if (launchContext !is Activity) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchContext.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.w(APP_LIST_UNINSTALL_TAG, "Failed to start uninstall for $packageName", e)
        false
    }
}

@Composable
internal fun AppListDialog(
    visible: Boolean,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val uninstallFailedToast = stringResource(R.string.app_list_uninstall_failed)
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val hiddenPackages by settingsViewModel.appListHiddenPackages.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<AppListRow?>(null) }
    val pm = remember(context) { context.packageManager }

    val rows = remember(apps, hiddenPackages, showHidden, pm) {
        apps.map { entry ->
            val hidden = entry.packageName in hiddenPackages
            AppListRow(
                packageName = entry.packageName,
                label = entry.label,
                icon = entry.icon,
                hidden = hidden,
                canUninstall = canRequestUninstall(pm, entry.packageName),
            )
        }.filter { showHidden || !it.hidden }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                AppAlertDialogTitle(stringResource(R.string.app_list_dialog_title))
                Spacer(modifier = Modifier.height(12.dp))
                if (rows.isEmpty()) {
                    Text(
                        text = stringResource(R.string.app_list_empty),
                        style = MaterialTheme.typography.tboxBody,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 8.dp),
                    ) {
                        items(rows, key = { it.packageName }) { row ->
                            AppListDialogRow(
                                row = row,
                                showHidden = showHidden,
                                onOpen = {
                                    launchAppFromWidget(context, row.packageName)
                                },
                                onToggleHidden = {
                                    settingsViewModel.setAppListPackageHidden(
                                        row.packageName,
                                        hidden = !row.hidden,
                                    )
                                },
                                onDelete = { pendingUninstall = row },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Same Material3 Button / OutlinedButton shape as «Закрыть» (not FilterChip).
                    if (showHidden) {
                        Button(onClick = { showHidden = false }) {
                            AppAlertDialogButtonLabel(stringResource(R.string.app_list_show_hidden))
                        }
                    } else {
                        OutlinedButton(onClick = { showHidden = true }) {
                            AppAlertDialogButtonLabel(stringResource(R.string.app_list_show_hidden))
                        }
                    }
                    Button(onClick = onDismiss) {
                        AppAlertDialogButtonLabel(stringResource(R.string.app_list_close))
                    }
                }
            }
        }
    }

    pendingUninstall?.let { row ->
        AlertDialog(
            onDismissRequest = { pendingUninstall = null },
            title = { AppAlertDialogTitle(stringResource(R.string.app_list_delete_confirm_title)) },
            text = {
                Text(
                    text = stringResource(R.string.app_list_delete_confirm_message, row.label),
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val packageName = row.packageName
                        pendingUninstall = null
                        // Keep the App List dialog open; only this confirm closes.
                        if (!requestSystemUninstall(context, packageName)) {
                            Toast.makeText(
                                context,
                                uninstallFailedToast,
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.app_list_delete_confirm_yes))
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingUninstall = null }) {
                    AppAlertDialogButtonLabel(stringResource(R.string.app_list_delete_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun AppListDialogRow(
    row: AppListRow,
    showHidden: Boolean,
    onOpen: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (row.icon != null) {
            Image(
                bitmap = row.icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.tboxTitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.packageName,
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onOpen,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_list_action_open),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = onToggleHidden,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(
                    if (showHidden && row.hidden) {
                        R.string.app_list_action_show
                    } else {
                        R.string.app_list_action_hide
                    },
                ),
                style = MaterialTheme.typography.tboxButton,
            )
        }
        OutlinedButton(
            onClick = onDelete,
            enabled = row.canUninstall,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = stringResource(R.string.app_list_action_delete),
                style = MaterialTheme.typography.tboxButton,
            )
        }
    }
}
