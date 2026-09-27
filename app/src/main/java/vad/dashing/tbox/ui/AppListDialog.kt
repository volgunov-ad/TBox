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
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.adb.PackageAdbActions
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
    val canOpen: Boolean = true,
    val adbStatus: PackageAdbActions.PackageStatus? = null,
)

internal data class PendingAdbAction(
    val row: AppListRow,
    val action: PackageAdbActions.Action,
    val actionLabel: String,
    val selfPackage: Boolean,
    val homeLauncher: Boolean,
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

internal fun formatAppListAdbStatusLine(
    status: PackageAdbActions.PackageStatus?,
    hiddenLabel: String,
    disabledLabel: String,
    runningLabel: String,
): String? {
    if (status == null) return null
    val parts = buildList {
        if (status.systemHidden) add(hiddenLabel)
        if (status.disabled) add(disabledLabel)
        if (status.running) add(runningLabel)
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

internal fun adbActionLabelRes(action: PackageAdbActions.Action, status: PackageAdbActions.PackageStatus?): Int {
    return when (action) {
        PackageAdbActions.Action.Hide,
        PackageAdbActions.Action.Unhide,
        -> if (status?.systemHidden == true) {
            R.string.app_list_adb_unhide
        } else {
            R.string.app_list_adb_hide
        }
        PackageAdbActions.Action.Disable,
        PackageAdbActions.Action.Enable,
        -> if (status?.disabled == true) {
            R.string.app_list_adb_enable
        } else {
            R.string.app_list_adb_disable
        }
        PackageAdbActions.Action.ForceStop -> R.string.app_list_adb_force_stop
    }
}

internal fun adbToggleAction(
    hideOrUnhide: Boolean,
    status: PackageAdbActions.PackageStatus?,
): PackageAdbActions.Action {
    return if (hideOrUnhide) {
        if (status?.systemHidden == true) {
            PackageAdbActions.Action.Unhide
        } else {
            PackageAdbActions.Action.Hide
        }
    } else {
        if (status?.disabled == true) {
            PackageAdbActions.Action.Enable
        } else {
            PackageAdbActions.Action.Disable
        }
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
    val scope = rememberCoroutineScope()
    val uninstallFailedToast = stringResource(R.string.app_list_uninstall_failed)
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val hiddenPackages by settingsViewModel.appListHiddenPackages.collectAsStateWithLifecycle()
    val launchableApps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val installedApps = rememberInstalledAppEntries(settingsViewModel, iconRevision)
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var advancedMode by rememberSaveable { mutableStateOf(false) }
    var pendingUninstall by remember { mutableStateOf<AppListRow?>(null) }
    var pendingAdb by remember { mutableStateOf<PendingAdbAction?>(null) }
    var adbStatuses by remember { mutableStateOf<Map<String, PackageAdbActions.PackageStatus>>(emptyMap()) }
    var adbBusy by remember { mutableStateOf(false) }
    val pm = remember(context) { context.packageManager }
    val homePackage = remember(pm) { resolveHomeLauncherPackage(pm) }
    val selfPackage = remember(context) { context.packageName }

    val apps = if (advancedMode) installedApps else launchableApps

    val rows = remember(apps, hiddenPackages, showHidden, pm, advancedMode, adbStatuses) {
        apps.map { entry ->
            val hidden = entry.packageName in hiddenPackages
            AppListRow(
                packageName = entry.packageName,
                label = entry.label,
                icon = entry.icon,
                hidden = hidden,
                canUninstall = canRequestUninstall(pm, entry.packageName),
                canOpen = if (advancedMode) {
                    packageHasLaunchIntent(pm, entry.packageName)
                } else {
                    true
                },
                adbStatus = adbStatuses[entry.packageName],
            )
        }.filter { showHidden || !it.hidden }
            // Advanced mode keeps ADB-hidden/disabled packages visible even when not launchable.
            .let { list ->
                if (!advancedMode) return@let list
                val known = list.mapTo(linkedSetOf()) { it.packageName }
                val extras = adbStatuses.values
                    .asSequence()
                    .filter { it.packageName !in known && (it.systemHidden || it.disabled) }
                    .map { status ->
                        AppListRow(
                            packageName = status.packageName,
                            label = status.packageName,
                            icon = null,
                            hidden = status.packageName in hiddenPackages,
                            canUninstall = false,
                            canOpen = false,
                            adbStatus = status,
                        )
                    }
                    .filter { showHidden || !it.hidden }
                    .toList()
                if (extras.isEmpty()) list else (list + extras).sortedBy { it.label.lowercase() }
            }
    }

    fun tearDownAdvancedAndDismiss() {
        scope.launch {
            if (PackageAdbActions.isAdvancedSessionActive()) {
                PackageAdbActions.exitAdvancedSession(context)
            }
            adbStatuses = emptyMap()
            advancedMode = false
            onDismiss()
        }
    }

    LaunchedEffect(advancedMode) {
        if (advancedMode) {
            adbBusy = true
            when (val outcome = PackageAdbActions.enterAdvancedSession(context)) {
                is PackageAdbActions.StatusLoadOutcome.Success -> {
                    adbStatuses = outcome.statuses
                }
                is PackageAdbActions.StatusLoadOutcome.Failed -> {
                    val detail = outcome.detail.ifBlank {
                        outcome.reason.name
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.app_list_adb_session_fail, detail),
                        Toast.LENGTH_LONG,
                    ).show()
                    advancedMode = false
                    adbStatuses = emptyMap()
                }
            }
            adbBusy = false
        } else if (PackageAdbActions.isAdvancedSessionActive()) {
            PackageAdbActions.exitAdvancedSession(context)
            adbStatuses = emptyMap()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (PackageAdbActions.isAdvancedSessionActive()) {
                // Best-effort restore if composition is torn down without Close.
                runBlocking {
                    PackageAdbActions.exitAdvancedSession(context)
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { tearDownAdvancedAndDismiss() },
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
                                advancedMode = advancedMode,
                                adbBusy = adbBusy,
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
                                onRequestAdbAction = { action, label ->
                                    pendingAdb = PendingAdbAction(
                                        row = row,
                                        action = action,
                                        actionLabel = label,
                                        selfPackage = row.packageName == selfPackage,
                                        homeLauncher = homePackage != null &&
                                            row.packageName == homePackage,
                                    )
                                },
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
                    if (advancedMode) {
                        Button(
                            onClick = { advancedMode = false },
                            enabled = !adbBusy,
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.app_list_advanced))
                        }
                    } else {
                        OutlinedButton(
                            onClick = { advancedMode = true },
                            enabled = !adbBusy,
                        ) {
                            AppAlertDialogButtonLabel(stringResource(R.string.app_list_advanced))
                        }
                    }
                    Button(onClick = { tearDownAdvancedAndDismiss() }) {
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

    pendingAdb?.let { pending ->
        val warningExtras = buildString {
            if (pending.selfPackage) {
                append("\n\n")
                append(stringResource(R.string.app_list_adb_confirm_self_warn))
            }
            if (pending.homeLauncher) {
                append("\n\n")
                append(stringResource(R.string.app_list_adb_confirm_home_warn))
            }
        }
        AlertDialog(
            onDismissRequest = { if (!adbBusy) pendingAdb = null },
            title = { AppAlertDialogTitle(stringResource(R.string.app_list_adb_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.app_list_adb_confirm_message,
                        pending.actionLabel,
                        pending.row.label,
                        pending.row.packageName,
                    ) + warningExtras,
                    style = MaterialTheme.typography.tboxBody,
                )
            },
            confirmButton = {
                Button(
                    enabled = !adbBusy,
                    onClick = {
                        val action = pending.action
                        val pkg = pending.row.packageName
                        pendingAdb = null
                        scope.launch {
                            adbBusy = true
                            val outcome = PackageAdbActions.runAction(context, action, pkg)
                            when (outcome) {
                                is PackageAdbActions.Outcome.Success -> {
                                    val toastRes = if (action == PackageAdbActions.Action.ForceStop) {
                                        R.string.app_list_adb_toast_ok_force_stop
                                    } else {
                                        R.string.app_list_adb_toast_ok
                                    }
                                    val detail = outcome.detail.ifBlank { pkg }
                                    Toast.makeText(
                                        context,
                                        context.getString(toastRes, detail),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                    when (
                                        val refresh = PackageAdbActions.refreshStatuses(context)
                                    ) {
                                        is PackageAdbActions.StatusLoadOutcome.Success -> {
                                            adbStatuses = refresh.statuses
                                        }
                                        is PackageAdbActions.StatusLoadOutcome.Failed -> Unit
                                    }
                                }
                                is PackageAdbActions.Outcome.Failed -> {
                                    val detail = outcome.detail.ifBlank {
                                        outcome.reason.name
                                    }
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.app_list_adb_toast_fail, detail),
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            }
                            adbBusy = false
                        }
                    },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.app_list_adb_confirm_yes))
                }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !adbBusy,
                    onClick = { pendingAdb = null },
                ) {
                    AppAlertDialogButtonLabel(stringResource(R.string.app_list_adb_confirm_no))
                }
            },
        )
    }
}

@Composable
private fun AppListDialogRow(
    row: AppListRow,
    showHidden: Boolean,
    advancedMode: Boolean,
    adbBusy: Boolean,
    onOpen: () -> Unit,
    onToggleHidden: () -> Unit,
    onDelete: () -> Unit,
    onRequestAdbAction: (PackageAdbActions.Action, String) -> Unit,
) {
    val statusLine = formatAppListAdbStatusLine(
        status = row.adbStatus,
        hiddenLabel = stringResource(R.string.app_list_status_hidden),
        disabledLabel = stringResource(R.string.app_list_status_disabled),
        runningLabel = stringResource(R.string.app_list_status_running),
    )
    var menuExpanded by remember { mutableStateOf(false) }
    val hideAction = adbToggleAction(hideOrUnhide = true, status = row.adbStatus)
    val disableAction = adbToggleAction(hideOrUnhide = false, status = row.adbStatus)
    val hideLabel = stringResource(adbActionLabelRes(hideAction, row.adbStatus))
    val disableLabel = stringResource(adbActionLabelRes(disableAction, row.adbStatus))
    val forceStopLabel = stringResource(R.string.app_list_adb_force_stop)

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
            if (advancedMode && statusLine != null) {
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(
            onClick = onOpen,
            enabled = row.canOpen,
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
        if (advancedMode) {
            Box {
                OutlinedButton(
                    onClick = { menuExpanded = true },
                    enabled = !adbBusy,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_list_advanced_actions),
                        style = MaterialTheme.typography.tboxButton,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = hideLabel,
                                style = MaterialTheme.typography.tboxBody,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestAdbAction(hideAction, hideLabel)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = disableLabel,
                                style = MaterialTheme.typography.tboxBody,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestAdbAction(disableAction, disableLabel)
                        },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = forceStopLabel,
                                style = MaterialTheme.typography.tboxBody,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestAdbAction(
                                PackageAdbActions.Action.ForceStop,
                                forceStopLabel,
                            )
                        },
                    )
                }
            }
        }
    }
}
