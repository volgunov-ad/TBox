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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    val savedWatchHide by settingsViewModel.usageStatsHideFloatingWatchPackages.collectAsStateWithLifecycle()
    val savedPanelsHide by settingsViewModel.usageStatsHideFloatingPanelIds.collectAsStateWithLifecycle()
    val savedWatchShow by settingsViewModel.usageStatsForceShowFloatingWatchPackages.collectAsStateWithLifecycle()
    val savedPanelsShow by settingsViewModel.usageStatsForceShowFloatingPanelIds.collectAsStateWithLifecycle()
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)

    // Key by saved sets so when DataStore emits after first frame (flows start empty), drafts refresh.
    var draftWatchHide by remember(savedWatchHide) { mutableStateOf(savedWatchHide) }
    var draftPanelsHide by remember(savedPanelsHide) { mutableStateOf(savedPanelsHide) }
    var draftWatchShow by remember(savedWatchShow) { mutableStateOf(savedWatchShow) }
    var draftPanelsShow by remember(savedPanelsShow) { mutableStateOf(savedPanelsShow) }

    var filterHideText by rememberSaveable { mutableStateOf("") }
    var filterShowText by rememberSaveable { mutableStateOf("") }
    val needleHide = filterHideText.trim().lowercase()
    val needleShow = filterShowText.trim().lowercase()
    val filteredAppsHide = remember(apps, needleHide) {
        if (needleHide.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(needleHide) ||
                it.packageName.lowercase().contains(needleHide)
        }
    }
    val filteredAppsShow = remember(apps, needleShow) {
        if (needleShow.isEmpty()) apps
        else apps.filter {
            it.label.lowercase().contains(needleShow) ||
                it.packageName.lowercase().contains(needleShow)
        }
    }

    val hasUsageAccess = UsageStatsHideFloatingHelper.hasUsageAccessPermission(context)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth(0.92f),
        title = {
            AppAlertDialogTitle(stringResource(R.string.settings_floating_usage_stats_hide_dialog_title))
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
                Text(
                    text = stringResource(R.string.settings_floating_usage_stats_hide_section_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                AppAlertDialogText(stringResource(R.string.settings_floating_usage_stats_hide_section_body))
                OutlinedTextField(
                    value = filterHideText,
                    onValueChange = { filterHideText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
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
                        .padding(top = 8.dp)
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UsageStatsAppsColumn(
                        modifier = Modifier.weight(1f),
                        apps = filteredAppsHide,
                        selectedPackages = draftWatchHide,
                        onTogglePackage = { pkg, on ->
                            draftWatchHide = if (on) draftWatchHide + pkg else draftWatchHide - pkg
                        },
                        columnAppsLabel = stringResource(R.string.settings_floating_usage_stats_hide_column_apps),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    UsageStatsPanelsColumn(
                        modifier = Modifier.weight(1f),
                        panels = floatingPanels,
                        selectedIds = draftPanelsHide,
                        onTogglePanel = { id, on ->
                            draftPanelsHide = if (on) draftPanelsHide + id else draftPanelsHide - id
                        },
                        columnPanelsLabel = stringResource(R.string.settings_floating_usage_stats_hide_column_panels),
                        noPanelsText = stringResource(R.string.settings_floating_usage_stats_hide_no_panels),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(
                    text = stringResource(R.string.settings_floating_usage_stats_show_section_title),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                AppAlertDialogText(stringResource(R.string.settings_floating_usage_stats_show_section_body))
                OutlinedTextField(
                    value = filterShowText,
                    onValueChange = { filterShowText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    label = {
                        Text(
                            stringResource(R.string.settings_floating_usage_stats_show_filter_apps),
                            fontSize = 18.sp,
                        )
                    },
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UsageStatsAppsColumn(
                        modifier = Modifier.weight(1f),
                        apps = filteredAppsShow,
                        selectedPackages = draftWatchShow,
                        onTogglePackage = { pkg, on ->
                            draftWatchShow = if (on) draftWatchShow + pkg else draftWatchShow - pkg
                        },
                        columnAppsLabel = stringResource(R.string.settings_floating_usage_stats_hide_column_apps),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                    UsageStatsPanelsColumn(
                        modifier = Modifier.weight(1f),
                        panels = floatingPanels,
                        selectedIds = draftPanelsShow,
                        onTogglePanel = { id, on ->
                            draftPanelsShow = if (on) draftPanelsShow + id else draftPanelsShow - id
                        },
                        columnPanelsLabel = stringResource(R.string.settings_floating_usage_stats_hide_column_panels),
                        noPanelsText = stringResource(R.string.settings_floating_usage_stats_hide_no_panels),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = rememberWrappedOnClick {
                    settingsViewModel.saveUsageStatsFloatingOverlayRules(
                        draftWatchHide,
                        draftPanelsHide,
                        draftWatchShow,
                        draftPanelsShow,
                    )
                    onDismiss()
                }
            ) {
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

@Composable
private fun UsageStatsAppsColumn(
    modifier: Modifier = Modifier,
    apps: List<LaunchableAppEntry>,
    selectedPackages: Set<String>,
    onTogglePackage: (String, Boolean) -> Unit,
    columnAppsLabel: String,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = columnAppsLabel,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        apps.forEach { app ->
            val checked = selectedPackages.contains(app.packageName)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = checked,
                    onCheckedChange = { on -> onTogglePackage(app.packageName, on) },
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
}

@Composable
private fun UsageStatsPanelsColumn(
    modifier: Modifier = Modifier,
    panels: List<FloatingDashboardConfig>,
    selectedIds: Set<String>,
    onTogglePanel: (String, Boolean) -> Unit,
    columnPanelsLabel: String,
    noPanelsText: String,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = columnPanelsLabel,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        if (panels.isEmpty()) {
            Text(
                text = noPanelsText,
                fontSize = 20.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            panels.forEach { panel ->
                val checked = selectedIds.contains(panel.id)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { on -> onTogglePanel(panel.id, on) },
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
