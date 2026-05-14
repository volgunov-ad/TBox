package vad.dashing.tbox.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import vad.dashing.tbox.FloatingDashboardConfig
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.UsageStatsHideFloatingHelper

@Composable
fun UsageStatsHideFloatingPanelsDialog(
    settingsViewModel: SettingsViewModel,
    floatingPanels: List<FloatingDashboardConfig>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val savedWatch by settingsViewModel.usageStatsHideFloatingWatchPackages.collectAsStateWithLifecycle()
    val savedPanels by settingsViewModel.usageStatsHideFloatingPanelIds.collectAsStateWithLifecycle()
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)

    var draftWatch by remember { mutableStateOf(savedWatch) }
    var draftPanels by remember { mutableStateOf(savedPanels) }

    var filterText by rememberSaveable { mutableStateOf("") }
    val needle = filterText.trim().lowercase()
    val filteredApps = remember(apps, needle) {
        if (needle.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(needle) ||
                it.packageName.lowercase().contains(needle)
        }
    }

    val hasUsageAccess = UsageStatsHideFloatingHelper.hasUsageAccessPermission(context)
    val dialogCoroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            AppAlertDialogTitle(stringResource(R.string.settings_floating_usage_stats_hide_dialog_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                AppAlertDialogText(stringResource(R.string.settings_floating_usage_stats_hide_dialog_intro))
                if (!hasUsageAccess) {
                    Text(
                        text = stringResource(R.string.settings_floating_usage_stats_hide_permission_hint),
                        modifier = Modifier.padding(top = 8.dp),
                        fontSize = 22.sp,
                        lineHeight = 22.sp * 1.3f,
                        color = MaterialTheme.colorScheme.error,
                    )
                    OutlinedButton(
                        onClick = rememberWrappedOnClick {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.settings_floating_usage_stats_open_usage_settings),
                            fontSize = 20.sp,
                            maxLines = 2,
                        )
                    }
                }
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(R.string.settings_floating_usage_stats_hide_filter_apps),
                            fontSize = 18.sp,
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .height(400.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_floating_usage_stats_hide_column_apps),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        filteredApps.forEach { app ->
                            val checked = draftWatch.contains(app.packageName)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = { on ->
                                        draftWatch = if (on) {
                                            draftWatch + app.packageName
                                        } else {
                                            draftWatch - app.packageName
                                        }
                                    },
                                )
                                Text(
                                    text = app.label,
                                    fontSize = 20.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_floating_usage_stats_hide_column_panels),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        if (floatingPanels.isEmpty()) {
                            Text(
                                text = stringResource(R.string.settings_floating_usage_stats_hide_no_panels),
                                fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            floatingPanels.forEach { panel ->
                                val checked = draftPanels.contains(panel.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(
                                        checked = checked,
                                        onCheckedChange = { on ->
                                            draftPanels = if (on) {
                                                draftPanels + panel.id
                                            } else {
                                                draftPanels - panel.id
                                            }
                                        },
                                    )
                                    Text(
                                        text = panel.name.ifBlank { panel.id },
                                        fontSize = 20.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val onSaveClick = rememberWrappedOnClick {
                dialogCoroutineScope.launch {
                    settingsViewModel.persistUsageStatsHideFloatingRules(draftWatch, draftPanels)
                    onDismiss()
                }
            }
            Button(onClick = onSaveClick) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = rememberWrappedOnClick { onDismiss() }) {
                AppAlertDialogButtonLabel(stringResource(R.string.action_cancel))
            }
        },
    )
}
