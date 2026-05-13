package vad.dashing.tbox.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel

/** Comma-separated package list from settings, preserving first-seen order. */
fun parseImmersivePolicyPackagesOrdered(csv: String): List<String> {
    val seen = mutableSetOf<String>()
    val out = mutableListOf<String>()
    for (part in csv.split(',')) {
        val p = part.trim()
        if (p.isNotEmpty() && seen.add(p)) out.add(p)
    }
    return out
}

/**
 * Keeps prior order for packages still selected, then appends newly selected packages sorted
 * alphabetically (stable UX when toggling checkboxes).
 */
fun mergeImmersivePackageSelection(
    previousOrdered: List<String>,
    selected: Set<String>,
): List<String> {
    val kept = previousOrdered.filter { it in selected }
    val tail = (selected - kept.toSet()).toList().sorted()
    return kept + tail
}

private fun loadAppLabel(pm: PackageManager, packageName: String): String =
    try {
        pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
    } catch (_: PackageManager.NameNotFoundException) {
        packageName
    }

@Composable
fun ImmersivePolicyPackagePickerDialog(
    onDismissRequest: () -> Unit,
    settingsViewModel: SettingsViewModel,
    previousOrderedSelection: List<String>,
    onConfirm: (List<String>) -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val pm = context.packageManager
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)
    val launchablePkgs = remember(apps) { apps.mapTo(mutableSetOf()) { it.packageName } }

    val snapshotKey = previousOrderedSelection.joinToString("\u0001")
    var working by remember(snapshotKey) {
        mutableStateOf(previousOrderedSelection.filter { it.isNotBlank() }.toSet())
    }

    var filterText by rememberSaveable { mutableStateOf("") }
    val needle = filterText.trim().lowercase()
    val filteredApps = remember(apps, needle) {
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }

    val orphanSelected = remember(working, launchablePkgs) {
        working.filter { it !in launchablePkgs }.sortedBy { loadAppLabel(pm, it).lowercase() }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.92f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(
                text = stringResource(R.string.settings_immersive_policy_dialog_title),
                fontSize = 22.sp,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.settings_immersive_policy_dialog_hint),
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    label = {
                        Text(
                            text = stringResource(R.string.widget_app_launcher_search),
                            fontSize = 18.sp
                        )
                    },
                    singleLine = true,
                    enabled = enabled
                )
                if (orphanSelected.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_immersive_policy_dialog_other_apps),
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    orphanSelected.forEach { pkg ->
                        val isChecked = pkg in working
                        val label = loadAppLabel(pm, pkg)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = enabled) {
                                    working = working.toMutableSet().apply {
                                        if (isChecked) remove(pkg) else add(pkg)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    working = working.toMutableSet().apply {
                                        if (checked) add(pkg) else remove(pkg)
                                    }
                                },
                                enabled = enabled
                            )
                            Column(
                                modifier = Modifier
                                    .padding(start = 8.dp)
                                    .weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 20.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = pkg,
                                    fontSize = 16.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                filteredApps.forEach { app ->
                    val pkg = app.packageName
                    val isChecked = pkg in working
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                working = working.toMutableSet().apply {
                                    if (isChecked) remove(pkg) else add(pkg)
                                }
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                working = working.toMutableSet().apply {
                                    if (checked) add(pkg) else remove(pkg)
                                }
                            },
                            enabled = enabled
                        )
                        if (app.icon != null) {
                            Image(
                                bitmap = app.icon,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(40.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.widget_app_launcher_no_icon),
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(40.dp)
                            )
                        }
                        Text(
                            text = app.label,
                            fontSize = 20.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(R.string.settings_immersive_policy_dialog_cancel))
                }
                TextButton(
                    enabled = enabled,
                    onClick = {
                        val merged = mergeImmersivePackageSelection(previousOrderedSelection, working)
                        onConfirm(merged)
                        onDismissRequest()
                    }
                ) {
                    Text(stringResource(R.string.settings_immersive_policy_dialog_ok))
                }
            }
        },
        dismissButton = {}
    )
}
