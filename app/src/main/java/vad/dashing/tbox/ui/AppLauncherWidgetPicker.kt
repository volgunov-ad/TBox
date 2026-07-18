package vad.dashing.tbox.ui

import vad.dashing.tbox.ui.theme.tboxTitle
import vad.dashing.tbox.ui.theme.tboxTabLabel
import vad.dashing.tbox.ui.theme.tboxHeadline
import vad.dashing.tbox.ui.theme.tboxCaption
import vad.dashing.tbox.ui.theme.tboxButton
import vad.dashing.tbox.ui.theme.tboxBody
import vad.dashing.tbox.ui.theme.TboxTextStyles
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.content.Context
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.R
import vad.dashing.tbox.SetLauncherAppCustomIconResult
import vad.dashing.tbox.SettingsViewModel
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

internal data class LaunchableAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * In-process list of launcher apps with decoded icons. Survives closing the widget dialog so
 * reopening the picker on the same screen does not re-query and re-decode. Cleared when the host
 * [androidx.lifecycle.LifecycleOwner] receives [Lifecycle.Event.ON_DESTROY].
 */
private object LaunchableAppsWithIconsCache {
    private var cachedIconSizePx: Int? = null
    private var cachedIconRevision: Int? = null
    private var cachedLookup: LauncherAppIconPaths.Lookup? = null
    private var entries: List<LaunchableAppEntry>? = null

    fun getOrLoad(
        iconSizePx: Int,
        iconRevision: Int,
        lookup: LauncherAppIconPaths.Lookup,
        load: () -> List<LaunchableAppEntry>,
    ): List<LaunchableAppEntry> {
        synchronized(this) {
            if (cachedIconSizePx == iconSizePx &&
                cachedIconRevision == iconRevision &&
                cachedLookup == lookup &&
                entries != null
            ) {
                return entries!!
            }
            val list = load()
            cachedIconSizePx = iconSizePx
            cachedIconRevision = iconRevision
            cachedLookup = lookup
            entries = list
            return list
        }
    }

    fun clear() {
        synchronized(this) {
            cachedIconSizePx = null
            cachedIconRevision = null
            cachedLookup = null
            entries = null
        }
    }
}

/** Drop decoded picker icons when the host Compose tree is torn down (Activity or overlay). */
internal fun disposeAppLauncherPickerIconCache() {
    LaunchableAppsWithIconsCache.clear()
}

private fun loadLaunchableAppEntries(
    appContext: Context,
    iconSizePx: Int,
    lookup: LauncherAppIconPaths.Lookup,
    @Suppress("UNUSED_PARAMETER") iconRevision: Int,
): List<LaunchableAppEntry> {
    val pm = appContext.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    @Suppress("QueryPermissionsNeeded", "DEPRECATION")
    val resolves: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
    return resolves
        .map { ri ->
            val pkg = ri.activityInfo.packageName
            val label = ri.loadLabel(pm).toString()
            val bitmap = decodeLauncherAppCustomIconIfPresent(appContext, pkg, iconSizePx, lookup)
                ?: runCatching {
                    ri.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                }.getOrNull()
            LaunchableAppEntry(packageName = pkg, label = label, icon = bitmap)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
internal fun rememberLaunchableAppEntries(
    settingsViewModel: SettingsViewModel,
    launcherIconRevision: Int = 0,
): List<LaunchableAppEntry> {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val iconSizePx = remember(appContext) {
        (48f * appContext.resources.displayMetrics.density).toInt().coerceIn(32, 96)
    }
    return remember(appContext, iconSizePx, launcherIconRevision, iconLookup) {
        LaunchableAppsWithIconsCache.getOrLoad(iconSizePx, launcherIconRevision, iconLookup) {
            loadLaunchableAppEntries(appContext, iconSizePx, iconLookup, launcherIconRevision)
        }
    }
}

/**
 * App picker embedded in [WidgetSelectionDialogForm] (same window as overlay / AlertDialog body)
 * so FloatingDashboard overlays are not stacked with a second dialog.
 */
@Composable
internal fun AppLauncherWidgetSettingsSection(
    state: WidgetSelectionDialogState,
    settingsViewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    if (!state.isAppLauncherWidgetSelected) return
    val context = LocalContext.current
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val iconRevision by settingsViewModel.launcherAppIconRevision.collectAsStateWithLifecycle()
    val apps = rememberLaunchableAppEntries(settingsViewModel, iconRevision)
    val selectedLabel = apps.find { it.packageName == state.launcherAppPackage }?.label
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
    var selectedHasCustomIcon by remember { mutableStateOf(false) }
    var selectedRemoveIconLabel by remember { mutableIntStateOf(R.string.widget_app_launcher_remove_icon) }
    LaunchedEffect(state.launcherAppPackage, iconRevision, iconLookup) {
        selectedHasCustomIcon = if (state.launcherAppPackage.isNotBlank()) {
            settingsViewModel.hasCustomLauncherAppIcon(state.launcherAppPackage)
        } else {
            false
        }
        selectedRemoveIconLabel = if (state.launcherAppPackage.isNotBlank()) {
            launcherAppIconRemoveLabelRes(context.filesDir, state.launcherAppPackage, iconLookup)
        } else {
            R.string.widget_app_launcher_remove_icon
        }
    }
    val canPickImage = remember(context) {
        Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*" }
            .resolveActivity(context.packageManager) != null
    }
    var pendingIconPackage by rememberSaveable { mutableStateOf<String?>(null) }
    val pickCustomIcon = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val pkg = pendingIconPackage
            ?: state.launcherAppPackage.takeIf { it.isNotBlank() }
            ?: return@rememberLauncherForActivityResult
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
            if (msg != null) {
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
    Column(modifier = modifier.fillMaxWidth()) {
        SettingSwitch(
            isChecked = state.launcherFreeformEnabled,
            onCheckedChange = { state.launcherFreeformEnabled = it },
            text = stringResource(R.string.widget_app_launcher_freeform_enable),
            description = stringResource(R.string.widget_app_launcher_freeform_enable_desc),
            enabled = state.togglesEnabled,
        )
        if (state.launcherFreeformEnabled) {
            val localizedSideOptions = FreeformLaunchSide.entries.map { side ->
                FreeformSideDropdownOption(side, stringResource(side.labelRes))
            }
            SettingDropdownGeneric(
                selectedValue = localizedSideOptions.first {
                    it.side == state.launcherFreeformSide
                },
                onValueChange = { state.launcherFreeformSide = it.side },
                text = stringResource(R.string.widget_app_launcher_freeform_side),
                description = "",
                enabled = state.togglesEnabled,
                options = localizedSideOptions,
            )
            val percentOptions = remember { FreeformLaunchBounds.percentOptions() }
            SettingDropdownGeneric(
                selectedValue = FreeformLaunchBounds.normalizePercent(state.launcherFreeformPercent)
                    .let { current ->
                        percentOptions.minByOrNull { kotlin.math.abs(it - current) }
                            ?: FreeformLaunchBounds.DEFAULT_PERCENT
                    },
                onValueChange = { state.launcherFreeformPercent = it },
                text = stringResource(R.string.widget_app_launcher_freeform_percent),
                description = stringResource(R.string.widget_app_launcher_freeform_percent_desc),
                enabled = state.togglesEnabled,
                options = percentOptions,
            )
            Text(
                text = stringResource(R.string.widget_app_launcher_freeform_hint),
                style = MaterialTheme.typography.tboxCaption,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Text(
            text = if (selectedLabel != null) {
                stringResource(R.string.widget_app_launcher_selected, selectedLabel)
            } else {
                stringResource(R.string.widget_app_launcher_none_selected)
            },
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.widget_app_launcher_pick_title),
            style = MaterialTheme.typography.tboxButton,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 6.dp)
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
                    style = MaterialTheme.typography.tboxCaption
                )
            },
            singleLine = true,
            enabled = state.togglesEnabled
        )
        filtered.forEach { app ->
            val isSelected = state.launcherAppPackage == app.packageName
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableWithSound(enabled = state.togglesEnabled) {
                        state.launcherAppPackage = app.packageName
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = isSelected,
                    onClick = rememberWrappedOnClick { state.launcherAppPackage = app.packageName },
                    enabled = state.togglesEnabled
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
                        style = MaterialTheme.typography.tboxCaption,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(start = 4.dp)
                            .size(40.dp)
                    )
                }
                Row(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = app.label,
                        style = MaterialTheme.typography.tboxBody,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = rememberWrappedOnClick {
                                    if (canPickImage) {
                                        pendingIconPackage = app.packageName
                                        pickCustomIcon.launch("image/*")
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.settings_main_screen_wallpaper_no_picker),
                                            Toast.LENGTH_LONG
                                        ).show()
                                    }
                                },
                                enabled = state.togglesEnabled,
                            ) {
                                Text(
                                    text = stringResource(R.string.widget_app_launcher_change_icon),
                                    style = MaterialTheme.typography.tboxCaption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            OutlinedButton(
                                onClick = rememberWrappedOnClick {
                                    settingsViewModel.clearCustomLauncherAppIcon(app.packageName)
                                },
                                enabled = state.togglesEnabled && selectedHasCustomIcon
                            ) {
                                Text(
                                    text = stringResource(selectedRemoveIconLabel),
                                    style = MaterialTheme.typography.tboxCaption,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
        if (state.launcherAppPackage.isBlank()) {
            Text(
                text = stringResource(R.string.widget_app_launcher_required),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.tboxBody,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

private data class FreeformSideDropdownOption(
    val side: FreeformLaunchSide,
    val label: String,
) {
    override fun toString(): String = label
}
