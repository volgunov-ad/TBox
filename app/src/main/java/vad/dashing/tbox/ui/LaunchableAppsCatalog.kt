package vad.dashing.tbox.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vad.dashing.tbox.LauncherAppIconPaths

internal data class LaunchableAppEntry(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?
)

/**
 * In-process list of launcher apps with decoded icons, plus a revision that bumps when the
 * installed package catalog changes. The icon list survives closing the widget dialog so reopening
 * the picker does not re-query and re-decode; it is cleared on host destroy and whenever packages
 * are added/removed/changed (so newly installed apps appear without restarting TBox Monitor).
 */
internal object LaunchableAppsCatalog {
    private val _packagesRevision = MutableStateFlow(0)
    val packagesRevision: StateFlow<Int> = _packagesRevision.asStateFlow()

    private var cachedIconSizePx: Int? = null
    private var cachedIconRevision: Int? = null
    private var cachedPackagesRevision: Int? = null
    private var cachedLookup: LauncherAppIconPaths.Lookup? = null
    private var entries: List<LaunchableAppEntry>? = null
    private var cachedPackageNames: Set<String>? = null

    private val watchLock = Any()
    private var packageChangeReceiver: BroadcastReceiver? = null
    private var watchingAppContext: Context? = null

    fun getOrLoad(
        iconSizePx: Int,
        iconRevision: Int,
        packagesRevision: Int,
        lookup: LauncherAppIconPaths.Lookup,
        load: () -> List<LaunchableAppEntry>,
    ): List<LaunchableAppEntry> {
        synchronized(this) {
            if (cachedIconSizePx == iconSizePx &&
                cachedIconRevision == iconRevision &&
                cachedPackagesRevision == packagesRevision &&
                cachedLookup == lookup &&
                entries != null
            ) {
                return entries!!
            }
            val list = load()
            cachedIconSizePx = iconSizePx
            cachedIconRevision = iconRevision
            cachedPackagesRevision = packagesRevision
            cachedLookup = lookup
            entries = list
            cachedPackageNames = list.mapTo(linkedSetOf()) { it.packageName }
            return list
        }
    }

    /** Drop decoded picker icons (Activity / overlay teardown). Keeps [packagesRevision]. */
    fun clearIcons() {
        synchronized(this) {
            cachedIconSizePx = null
            cachedIconRevision = null
            cachedPackagesRevision = null
            cachedLookup = null
            entries = null
            // Keep cachedPackageNames so ON_RESUME / next load can still detect catalog drift.
        }
    }

    fun invalidateForPackageCatalogChange() {
        synchronized(this) {
            cachedIconSizePx = null
            cachedIconRevision = null
            cachedPackagesRevision = null
            cachedLookup = null
            entries = null
            cachedPackageNames = null
            _packagesRevision.value = _packagesRevision.value + 1
        }
    }

    /**
     * Cheap check used on UI resume: re-query launcher package names without decoding icons.
     * Invalidates the cache only when the set of launchable packages actually changed.
     */
    fun refreshIfLaunchablePackagesChanged(appContext: Context) {
        val names = queryLaunchablePackageNames(appContext)
        val shouldInvalidate = synchronized(this) {
            val previous = cachedPackageNames
            previous != null && previous != names
        }
        if (shouldInvalidate) {
            invalidateForPackageCatalogChange()
        }
    }

    fun ensurePackageChangeWatcher(appContext: Context) {
        val app = appContext.applicationContext
        synchronized(watchLock) {
            if (packageChangeReceiver != null && watchingAppContext === app) return
            stopPackageChangeWatcherLocked()
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    if (isPackageCatalogChangeAction(intent?.action)) {
                        invalidateForPackageCatalogChange()
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // System package broadcasts require an exported receiver on API 33+.
                app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                app.registerReceiver(receiver, filter)
            }
            packageChangeReceiver = receiver
            watchingAppContext = app
        }
    }

    fun stopPackageChangeWatcher() {
        synchronized(watchLock) {
            stopPackageChangeWatcherLocked()
        }
    }

    private fun stopPackageChangeWatcherLocked() {
        val receiver = packageChangeReceiver ?: return
        val app = watchingAppContext
        packageChangeReceiver = null
        watchingAppContext = null
        if (app != null) {
            runCatching { app.unregisterReceiver(receiver) }
        }
    }

    internal fun isPackageCatalogChangeAction(action: String?): Boolean =
        when (action) {
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_PACKAGE_CHANGED,
            Intent.ACTION_PACKAGE_REPLACED -> true
            else -> false
        }

    /** Test seam: reset static state between unit tests. */
    internal fun resetForTests() {
        synchronized(watchLock) {
            stopPackageChangeWatcherLocked()
        }
        synchronized(this) {
            clearIcons()
            cachedPackageNames = null
            _packagesRevision.value = 0
        }
    }
}

internal fun queryLaunchablePackageNames(appContext: Context): Set<String> {
    val pm = appContext.packageManager
    val intent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }
    @Suppress("QueryPermissionsNeeded", "DEPRECATION")
    val resolves: List<ResolveInfo> = pm.queryIntentActivities(intent, 0)
    return resolves.mapTo(linkedSetOf()) { it.activityInfo.packageName }
}

internal fun loadLaunchableAppEntries(
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
