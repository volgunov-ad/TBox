package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.APP_GRID_LAUNCHER_MAX_SLOTS
import vad.dashing.tbox.R
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.normalizeAppGridPackages

fun orderedAppGridPackagesForStorage(selected: Set<String>): List<String> =
    normalizeAppGridPackages(selected.sorted())

@Composable
internal fun AppGridWidgetSettingsSection(
    settingsViewModel: SettingsViewModel,
    selectedPackages: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)
    var filterText by rememberSaveable { mutableStateOf("") }
    val needle = filterText.trim().lowercase()
    val filtered = remember(apps, needle) {
        if (needle.isEmpty()) {
            apps
        } else {
            apps.filter {
                it.label.lowercase().contains(needle) ||
                    it.packageName.lowercase().contains(needle)
            }
        }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(R.string.widget_app_grid_title),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.widget_app_grid_hint),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(
                R.string.widget_app_grid_slots_used,
                selectedPackages.size,
                APP_GRID_LAUNCHER_MAX_SLOTS
            ),
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
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
        filtered.forEach { app ->
            val pkg = app.packageName
            val isChecked = pkg in selectedPackages
            val atCap = selectedPackages.size >= APP_GRID_LAUNCHER_MAX_SLOTS
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled && (isChecked || !atCap)) {
                        val next = selectedPackages.toMutableSet()
                        if (isChecked) next.remove(pkg) else if (!atCap) next.add(pkg)
                        onSelectionChange(normalizeAppGridPackages(next).toSet())
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        val next = selectedPackages.toMutableSet()
                        if (checked) {
                            if (next.size < APP_GRID_LAUNCHER_MAX_SLOTS) next.add(pkg)
                        } else {
                            next.remove(pkg)
                        }
                        onSelectionChange(normalizeAppGridPackages(next).toSet())
                    },
                    enabled = enabled && (isChecked || !atCap)
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
}
