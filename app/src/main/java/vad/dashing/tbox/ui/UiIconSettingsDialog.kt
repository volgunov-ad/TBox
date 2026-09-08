package vad.dashing.tbox.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.SetLauncherAppCustomIconResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.UiIconPaths
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxCaption

private data class LocalizedUiIconEntry(
    val entry: UiIconCatalogEntry,
    val title: String,
)

@Composable
fun UiIconSettingsDialog(
    settingsViewModel: SettingsViewModel,
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    val context = LocalContext.current
    val revision by settingsViewModel.uiIconRevision.collectAsStateWithLifecycle()
    val lookup = rememberLauncherAppIconLookup(settingsViewModel)
    var filter by rememberSaveable { mutableStateOf("") }
    var pendingIconKey by rememberSaveable { mutableStateOf<String?>(null) }
    val canPickImage = remember(context) {
        Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            .resolveActivity(context.packageManager) != null
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val iconKey = pendingIconKey
        pendingIconKey = null
        if (uri == null || iconKey == null) return@rememberLauncherForActivityResult
        settingsViewModel.setCustomUiIconFromUri(iconKey, uri) { result ->
            val messageRes = when (result) {
                SetLauncherAppCustomIconResult.Success -> R.string.ui_icons_saved
                SetLauncherAppCustomIconResult.DimensionsTooLarge ->
                    R.string.widget_app_launcher_icon_too_large
                SetLauncherAppCustomIconResult.NotImageOrUnreadable ->
                    R.string.widget_app_launcher_icon_invalid
                SetLauncherAppCustomIconResult.CopyFailed ->
                    R.string.widget_app_launcher_icon_copy_failed
                SetLauncherAppCustomIconResult.InvalidPackage -> R.string.ui_icons_invalid_key
            }
            Toast.makeText(context, messageRes, Toast.LENGTH_LONG).show()
        }
    }
    val localizedEntries = UiIconCatalog.entries.map { entry ->
        val base = context.getString(entry.nameSource.resourceId)
        val title = entry.nameSource.variantLabel.takeIf { it.isNotBlank() }
            ?.let { "$base — $it" }
            ?: base
        LocalizedUiIconEntry(entry, title)
    }
    val needle = filter.trim().lowercase()
    val filteredEntries = localizedEntries.filter { localized ->
        needle.isEmpty() ||
            localized.title.lowercase().contains(needle) ||
            localized.entry.key.contains(needle)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 860.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                AppAlertDialogTitle(stringResource(R.string.ui_icons_settings_title))
                Text(
                    text = stringResource(R.string.ui_icons_settings_description),
                    style = MaterialTheme.typography.tboxBody,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                OutlinedTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    label = { Text(stringResource(R.string.ui_icons_search)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredEntries, key = { it.entry.key }) { localized ->
                        UiIconSettingsRow(
                            localized = localized,
                            filesDirLookup = lookup,
                            revision = revision,
                            onChoose = {
                                if (canPickImage) {
                                    pendingIconKey = localized.entry.key
                                    picker.launch("image/*")
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.settings_main_screen_wallpaper_no_picker,
                                        Toast.LENGTH_LONG,
                                    ).show()
                                }
                            },
                            onReset = {
                                settingsViewModel.clearCustomUiIcon(localized.entry.key)
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Button(onClick = rememberWrappedOnClick(onDismiss)) {
                        AppAlertDialogButtonLabel(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun UiIconSettingsRow(
    localized: LocalizedUiIconEntry,
    filesDirLookup: LauncherAppIconPaths.Lookup,
    revision: Int,
    onChoose: () -> Unit,
    onReset: () -> Unit,
) {
    val context = LocalContext.current
    val entry = localized.entry
    val resolvedFile = remember(entry.key, filesDirLookup, revision) {
        UiIconPaths.resolveIconFile(context.filesDir, entry.key, filesDirLookup)
    }
    val sourceText = when {
        UiIconPaths.hasThemeCacheIcon(context.filesDir, entry.key, filesDirLookup) ->
            stringResource(R.string.ui_icons_source_theme)
        resolvedFile != null -> stringResource(R.string.ui_icons_source_local)
        else -> stringResource(R.string.ui_icons_source_builtin)
    }
    val description = if (entry.category == UiIconCategory.NAVIGATION) {
        stringResource(R.string.ui_icons_navigation_description, localized.title)
    } else {
        stringResource(R.string.ui_icons_widget_description, localized.title)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            UiIconCatalogPreview(
                entry = entry,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = localized.title,
                    style = MaterialTheme.typography.tboxButton,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = sourceText,
                    style = MaterialTheme.typography.tboxCaption,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = rememberWrappedOnClick(onChoose)) {
                    Text(stringResource(R.string.ui_icons_choose), style = MaterialTheme.typography.tboxCaption)
                }
                OutlinedButton(
                    onClick = rememberWrappedOnClick(onReset),
                    enabled = resolvedFile != null,
                ) {
                    Text(stringResource(R.string.ui_icons_reset), style = MaterialTheme.typography.tboxCaption)
                }
            }
        }
    }
}

@Composable
private fun UiIconCatalogPreview(
    entry: UiIconCatalogEntry,
    modifier: Modifier,
) {
    val tint = MaterialTheme.colorScheme.onSurface
    val drawable = entry.drawableRes
    if (drawable != null) {
        CustomizableUiImage(
            iconKey = entry.key,
            drawableRes = drawable,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
        )
        return
    }
    val field = LeftMenuTabField.entries.firstOrNull { it.iconKey == entry.key } ?: return
    CustomizableUiIcon(
        iconKey = entry.key,
        fallback = field.menuIcon(),
        contentDescription = null,
        modifier = modifier,
        tint = tint,
    )
}
