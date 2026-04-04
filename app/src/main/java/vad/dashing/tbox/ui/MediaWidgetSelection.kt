package vad.dashing.tbox.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import vad.dashing.tbox.R
import vad.dashing.tbox.canonicalMediaPlayerPackage
import vad.dashing.tbox.defaultMediaPlayerPackages
import vad.dashing.tbox.normalizeMediaPlayerPackages
import vad.dashing.tbox.orderedMediaPlayerPackages

fun normalizeMediaPlayersSelection(rawPackages: Collection<String>): Set<String> {
    val normalized = normalizeMediaPlayerPackages(rawPackages)
    return if (normalized.isEmpty()) {
        defaultMediaPlayerPackages()
    } else {
        normalized
    }
}

@Composable
fun MediaPlayersInlineSelection(
    selectedPlayers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    enabled: Boolean
) {
    val apps = rememberLaunchableAppEntries()
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.widget_music_players),
            fontSize = 24.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.widget_music_players_hint),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = filterText,
            onValueChange = { filterText = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 8.dp),
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
            val pkg = canonicalMediaPlayerPackage(app.packageName) ?: return@forEach
            val isChecked = pkg in selectedPlayers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        val updated = selectedPlayers.toMutableSet()
                        if (isChecked) {
                            updated.remove(pkg)
                        } else {
                            updated.add(pkg)
                        }
                        onSelectionChange(normalizeMediaPlayerPackages(updated))
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = isChecked,
                    onCheckedChange = { checked ->
                        val updated = selectedPlayers.toMutableSet()
                        if (checked) {
                            updated.add(pkg)
                        } else {
                            updated.remove(pkg)
                        }
                        onSelectionChange(normalizeMediaPlayerPackages(updated))
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
}

fun orderedMediaPlayersForStorage(selectedPlayers: Set<String>): List<String> {
    return orderedMediaPlayerPackages(selectedPlayers)
}
