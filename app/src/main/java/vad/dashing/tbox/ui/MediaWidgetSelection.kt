package vad.dashing.tbox.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.R
import vad.dashing.tbox.SetLauncherAppCustomIconResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.SupportedMediaPlayer
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
    settingsViewModel: SettingsViewModel,
    selectedPlayers: Set<String>,
    onSelectionChange: (Set<String>) -> Unit,
    enabled: Boolean,
) {
    val context = LocalContext.current
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(iconRevision)
    var pendingIconPackage by remember { mutableStateOf<String?>(null) }
    val pickCustomIcon = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val pkg = pendingIconPackage ?: return@rememberLauncherForActivityResult
        pendingIconPackage = null
        if (uri == null) return@rememberLauncherForActivityResult
        settingsViewModel.setCustomLauncherAppIconFromUri(pkg, uri) { result ->
            val msg = when (result) {
                SetLauncherAppCustomIconResult.Success ->
                    context.getString(R.string.widget_app_launcher_icon_saved)
                SetLauncherAppCustomIconResult.DimensionsTooLarge ->
                    context.getString(R.string.widget_app_launcher_icon_too_large)
                SetLauncherAppCustomIconResult.NotImageOrUnreadable ->
                    context.getString(R.string.widget_app_launcher_icon_invalid)
                SetLauncherAppCustomIconResult.CopyFailed ->
                    context.getString(R.string.widget_app_launcher_icon_copy_failed)
                SetLauncherAppCustomIconResult.InvalidPackage -> null
            }
            if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
        }
    }
    val appPackageSet = remember(apps) { apps.mapTo(mutableSetOf()) { it.packageName } }
    val extraSupportedPlayers = remember(appPackageSet) {
        SupportedMediaPlayer.entries.filter { it.packageName !in appPackageSet }
    }
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
    val filteredExtraPlayers = remember(needle, extraSupportedPlayers, context) {
        if (needle.isEmpty()) {
            extraSupportedPlayers
        } else {
            extraSupportedPlayers.filter { player ->
                context.getString(player.titleRes).lowercase().contains(needle) ||
                    player.packageName.lowercase().contains(needle)
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
        filteredExtraPlayers.forEach { player ->
            val pkg = player.packageName
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
                Icon(
                    painter = painterResource(id = player.iconRes),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp)
                )
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = stringResource(player.titleRes),
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isChecked) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingIconPackage = pkg
                                    pickCustomIcon.launch("image/*")
                                },
                                enabled = enabled,
                            ) {
                                Text(
                                    stringResource(R.string.widget_app_launcher_change_icon),
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            MusicPlayerIconDeleteButton(
                                packageName = pkg,
                                enabled = enabled,
                                settingsViewModel = settingsViewModel,
                            )
                        }
                    }
                }
            }
        }
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
                Column(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = app.label,
                        fontSize = 20.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (isChecked) {
                        Row(
                            modifier = Modifier.padding(top = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    pendingIconPackage = pkg
                                    pickCustomIcon.launch("image/*")
                                },
                                enabled = enabled,
                            ) {
                                Text(
                                    stringResource(R.string.widget_app_launcher_change_icon),
                                    fontSize = 14.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            MusicPlayerIconDeleteButton(
                                packageName = pkg,
                                enabled = enabled,
                                settingsViewModel = settingsViewModel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlayerIconDeleteButton(
    packageName: String,
    enabled: Boolean,
    settingsViewModel: SettingsViewModel,
) {
    var hasCustom by remember(packageName) { mutableStateOf(false) }
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    LaunchedEffect(packageName, iconRevision) {
        hasCustom = settingsViewModel.hasCustomLauncherAppIcon(packageName)
    }
    OutlinedButton(
        onClick = { settingsViewModel.clearCustomLauncherAppIcon(packageName) },
        enabled = enabled && hasCustom,
    ) {
        Text(
            stringResource(R.string.widget_app_launcher_remove_icon),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun orderedMediaPlayersForStorage(selectedPlayers: Set<String>): List<String> {
    return orderedMediaPlayerPackages(selectedPlayers)
}
