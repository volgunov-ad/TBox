package vad.dashing.tbox.ui

import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import android.content.Context
import vad.dashing.tbox.R

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
    private var entries: List<LaunchableAppEntry>? = null

    fun getOrLoad(iconSizePx: Int, load: () -> List<LaunchableAppEntry>): List<LaunchableAppEntry> {
        synchronized(this) {
            if (cachedIconSizePx == iconSizePx && entries != null) {
                return entries!!
            }
            val list = load()
            cachedIconSizePx = iconSizePx
            entries = list
            return list
        }
    }

    fun clear() {
        synchronized(this) {
            cachedIconSizePx = null
            entries = null
        }
    }
}

/** Drop decoded picker icons when the host Compose tree is torn down (Activity or overlay). */
internal fun disposeAppLauncherPickerIconCache() {
    LaunchableAppsWithIconsCache.clear()
}

private fun loadLaunchableAppEntries(appContext: Context, iconSizePx: Int): List<LaunchableAppEntry> {
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
            val bitmap = runCatching {
                ri.loadIcon(pm).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
            }.getOrNull()
            LaunchableAppEntry(packageName = pkg, label = label, icon = bitmap)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

@Composable
internal fun rememberLaunchableAppEntries(): List<LaunchableAppEntry> {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val iconSizePx = remember(appContext) {
        (48f * appContext.resources.displayMetrics.density).toInt().coerceIn(32, 96)
    }
    return remember(appContext, iconSizePx) {
        LaunchableAppsWithIconsCache.getOrLoad(iconSizePx) {
            loadLaunchableAppEntries(appContext, iconSizePx)
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
    modifier: Modifier = Modifier
) {
    if (!state.isAppLauncherWidgetSelected) return
    val apps = rememberLaunchableAppEntries()
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
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = if (selectedLabel != null) {
                stringResource(R.string.widget_app_launcher_selected, selectedLabel)
            } else {
                stringResource(R.string.widget_app_launcher_none_selected)
            },
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Text(
            text = stringResource(R.string.widget_app_launcher_pick_title),
            fontSize = 22.sp,
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
                    fontSize = 18.sp
                )
            },
            singleLine = true,
            enabled = state.togglesEnabled
        )
        filtered.forEach { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.togglesEnabled) {
                        state.launcherAppPackage = app.packageName
                    }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = state.launcherAppPackage == app.packageName,
                    onClick = { state.launcherAppPackage = app.packageName },
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
        if (state.launcherAppPackage.isBlank()) {
            Text(
                text = stringResource(R.string.widget_app_launcher_required),
                color = MaterialTheme.colorScheme.error,
                fontSize = 20.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
