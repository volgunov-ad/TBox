package vad.dashing.tbox.ui

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import vad.dashing.tbox.LauncherAppIconPaths
import vad.dashing.tbox.SettingsViewModel

/**
 * All installed packages (including uninstalled-for-user / hidden where PackageManager
 * still exposes them via [PackageManager.GET_UNINSTALLED_PACKAGES]), for App List advanced mode.
 * Does not use [LaunchableAppsCatalog] so launchable pickers stay unaffected.
 */
internal fun loadInstalledAppEntries(
    appContext: Context,
    iconSizePx: Int,
    lookup: LauncherAppIconPaths.Lookup,
    @Suppress("UNUSED_PARAMETER") iconRevision: Int,
): List<LaunchableAppEntry> {
    val pm = appContext.packageManager
    @Suppress("DEPRECATION")
    val apps: List<ApplicationInfo> = runCatching {
        pm.getInstalledApplications(PackageManager.GET_UNINSTALLED_PACKAGES)
    }.getOrElse {
        @Suppress("DEPRECATION")
        pm.getInstalledApplications(0)
    }
    return apps
        .mapNotNull { info ->
            val pkg = info.packageName ?: return@mapNotNull null
            val label = runCatching { pm.getApplicationLabel(info).toString() }
                .getOrDefault(pkg)
            val bitmap = decodeLauncherAppCustomIconIfPresent(appContext, pkg, iconSizePx, lookup)
                ?: runCatching {
                    pm.getApplicationIcon(info).toBitmap(iconSizePx, iconSizePx).asImageBitmap()
                }.getOrNull()
            LaunchableAppEntry(packageName = pkg, label = label, icon = bitmap)
        }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
}

/** Default HOME launcher package, or null if unresolved. */
internal fun resolveHomeLauncherPackage(pm: PackageManager): String? {
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
    }
    @Suppress("DEPRECATION")
    val resolved = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
    return resolved?.activityInfo?.packageName?.takeIf { it.isNotBlank() }
}

internal fun packageHasLaunchIntent(pm: PackageManager, packageName: String): Boolean {
    return runCatching {
        pm.getLaunchIntentForPackage(packageName) != null
    }.getOrDefault(false)
}

@Composable
internal fun rememberInstalledAppEntries(
    settingsViewModel: SettingsViewModel,
    launcherIconRevision: Int = 0,
): List<LaunchableAppEntry> {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val iconLookup = rememberLauncherAppIconLookup(settingsViewModel)
    val iconSizePx = remember(appContext) {
        (48f * appContext.resources.displayMetrics.density).toInt().coerceIn(32, 96)
    }
    val packagesRevision by LaunchableAppsCatalog.packagesRevision.collectAsStateWithLifecycle()
    DisposableEffect(appContext, lifecycleOwner) {
        LaunchableAppsCatalog.ensurePackageChangeWatcher(appContext)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                LaunchableAppsCatalog.refreshIfLaunchablePackagesChanged(appContext)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return remember(appContext, iconSizePx, launcherIconRevision, iconLookup, packagesRevision) {
        loadInstalledAppEntries(appContext, iconSizePx, iconLookup, launcherIconRevision)
    }
}
