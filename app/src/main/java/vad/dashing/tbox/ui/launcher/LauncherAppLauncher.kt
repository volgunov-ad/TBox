package vad.dashing.tbox.ui.launcher

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ResolveInfo
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import android.widget.Toast
import vad.dashing.tbox.BackgroundService
import vad.dashing.tbox.LastAppTracker
import vad.dashing.tbox.LauncherHomeActivity
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.orderedMediaPlayerPackages

private const val TAG = "LauncherAppLaunch"

internal val LAUNCHER_MEDIA_PACKAGES = listOf(
    "com.wt.multimedia.platform3",
    "com.wt.multimedia.local",
    "com.tencent.wecarflow",
    "com.wt.autopai.radio",
    "com.wt.multimedia",
    "com.tencent.wecarnavi",
)

internal val LAUNCHER_CLIMATE_PACKAGES = listOf(
    "com.wt.airconditioner",
    "com.mengbo.acsettings",
)

private val MEDIA_EXPLICIT_ACTIVITIES = listOf(
    "com.wt.multimedia.local" to "com.wt.multimedia.local.launchercard.LauncherMediaCardActivity",
    "com.wt.multimedia.platform3" to "com.wt.multimedia.platform3.MainActivity",
)

private val OEM_EXPLICIT_ACTIVITIES = mapOf(
    "com.wt.multimedia.local" to listOf(
        "com.wt.multimedia.local.launchercard.LauncherMediaCardActivity",
    ),
    "com.wt.multimedia.platform3" to listOf(
        "com.wt.multimedia.platform3.MainActivity",
    ),
)

private val LAUNCH_CATEGORIES = listOf(
    Intent.CATEGORY_LAUNCHER,
    "android.intent.category.LEANBACK_LAUNCHER",
    "android.intent.category.CAR_LAUNCHER",
    null,
)

/** Launch app in the right panel zone (left car strip stays visible). */
internal fun launchLauncherAppEmbedded(
    context: Context,
    packageName: String,
    activityName: String? = null,
) {
    launchWithTarget(context, packageName, activityName, embedded = true, fullscreen = false)
}

/** Launch app fullscreen above the bottom control strip. */
internal fun launchLauncherAppFullscreen(
    context: Context,
    packageName: String,
    activityName: String? = null,
) {
    launchWithTarget(context, packageName, activityName, embedded = false, fullscreen = true)
}

internal fun launchLauncherApp(
    context: Context,
    packageName: String,
    activityName: String? = null,
) {
    launchLauncherAppEmbedded(context, packageName, activityName)
}

private fun launchWithTarget(
    context: Context,
    packageName: String,
    activityName: String?,
    embedded: Boolean,
    fullscreen: Boolean,
) {
    if (packageName.isBlank()) return
    Log.w(
        TAG,
        "launch pkg=$packageName activity=$activityName embedded=$embedded fullscreen=$fullscreen",
    )
    val intent = resolveLaunchIntent(context, packageName)
        ?: activityName?.takeIf { it.isNotBlank() }?.let { activity ->
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setClassName(packageName, activity)
            }
        }
    if (intent == null) {
        Log.w(TAG, "No launch intent for $packageName activity=$activityName")
        Toast.makeText(
            context,
            "Не удалось найти запуск для $packageName",
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    startResolved(
        context = context,
        packageName = packageName,
        intent = intent,
        preferEmbedded = embedded && !fullscreen,
        forceFullscreen = fullscreen,
    )
}

internal fun launchTBoxSettings(context: Context) {
    if (context.findLauncherActivity() is LauncherHomeActivity) {
        dispatchLaunchViaService(context, openMainActivity = true)
        return
    }
    val intent = MainActivityIntentHelper.createBringToFrontIntent(context)
    startResolved(context, context.packageName, intent)
}

internal fun launchFirstResolvableApp(
    context: Context,
    packages: List<String>,
    explicitActivities: List<Pair<String, String>> = emptyList(),
) {
    for ((pkg, activity) in explicitActivities) {
        if (!isPackageInstalled(context, pkg)) continue
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(pkg, activity)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            startResolved(context, pkg, intent)
            return
        }
    }
    for (pkg in packages) {
        resolveLaunchIntent(context, pkg)?.let { intent ->
            startResolved(context, pkg, intent)
            return
        }
    }
    Log.w(TAG, "No launch intent for packages=$packages")
}

internal fun launchMediaPlayer(context: Context, preferredPackage: String? = null) {
    val defaultPackage = preferredPackage
        ?: LauncherAppConfigStore.defaultMediaPackage(context)
    if (!defaultPackage.isNullOrBlank() && isPackageInstalled(context, defaultPackage)) {
        MEDIA_EXPLICIT_ACTIVITIES
            .firstOrNull { it.first == defaultPackage }
            ?.let { (_, activity) ->
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setClassName(defaultPackage, activity)
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    startResolved(context, defaultPackage, intent)
                    return
                }
            }
        resolveLaunchIntent(context, defaultPackage)?.let { intent ->
            startResolved(context, defaultPackage, intent)
            return
        }
    }
    val candidates = discoverInstalledMediaPackages(context, defaultPackage)
    launchFirstResolvableApp(
        context = context,
        packages = candidates,
        explicitActivities = MEDIA_EXPLICIT_ACTIVITIES,
    )
}

internal fun launchClimateApp(context: Context) {
    for (pkg in LAUNCHER_CLIMATE_PACKAGES) {
        if (resolveLaunchIntent(context, pkg) != null) {
            launchLauncherApp(context, pkg)
            return
        }
    }
}

internal fun discoverAllMediaPlayerPackages(
    context: Context,
    preferredPackage: String? = LauncherAppConfigStore.defaultMediaPackage(context),
): List<String> {
    val pm = context.packageManager
    val packages = linkedSetOf<String>()

    runCatching {
        val browseIntent = android.content.Intent("android.media.browse.MediaBrowserService")
        pm.queryIntentServices(browseIntent, android.content.pm.PackageManager.GET_META_DATA)
            .forEach { packages.add(it.serviceInfo.packageName) }
    }

    runCatching {
        val musicIntent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_APP_MUSIC)
        }
        pm.queryIntentActivities(musicIntent, android.content.pm.PackageManager.MATCH_ALL)
            .forEach { packages.add(it.activityInfo.packageName) }
    }

    packages.addAll(discoverInstalledMediaPackages(context, null))

    runCatching {
        pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            .forEach { info ->
                val pkg = info.packageName
                if (pkg.contains("music", true) ||
                    pkg.contains("media", true) ||
                    pkg.contains("radio", true) ||
                    pkg.contains("player", true) ||
                    pkg.contains("audio", true) ||
                    pkg.contains("spotify", true) ||
                    pkg.contains("yandex", true)
                ) {
                    packages.add(pkg)
                }
            }
    }

    return packages
        .filter { isPackageInstalled(context, it) }
        .sortedBy { pkg ->
            runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString().lowercase()
            }.getOrDefault(pkg)
        }
        .prioritize(preferredPackage)
}

internal fun discoverInstalledMediaPackages(
    context: Context,
    preferredPackage: String? = LauncherAppConfigStore.defaultMediaPackage(context),
): List<String> {
    val ordered = orderedMediaPlayerPackages(LAUNCHER_MEDIA_PACKAGES)
    val installedOrdered = ordered.filter { isPackageInstalled(context, it) }
        .prioritize(preferredPackage)
    if (installedOrdered.isNotEmpty()) return installedOrdered

    val fromOem = LauncherOemAppSort.loadPriorityPackages(context)
        .filter { pkg ->
            isPackageInstalled(context, pkg) && (
                pkg.contains("multimedia") ||
                    pkg.contains("media") ||
                    pkg.contains("radio") ||
                    pkg.contains("wecarflow") ||
                    pkg.contains("music")
                )
        }
        .prioritize(preferredPackage)
    if (fromOem.isNotEmpty()) return fromOem

    return LAUNCHER_MEDIA_PACKAGES.filter { isPackageInstalled(context, it) }
        .prioritize(preferredPackage)
}

private fun List<String>.prioritize(preferredPackage: String?): List<String> {
    if (preferredPackage.isNullOrBlank() || preferredPackage !in this) return this
    return listOf(preferredPackage) + filterNot { it == preferredPackage }
}

internal fun resolveLaunchIntent(context: Context, packageName: String): Intent? {
    if (!isPackageInstalled(context, packageName)) return null

    context.packageManager.getLaunchIntentForPackage(packageName)
        ?.also { Log.d(TAG, "Resolved $packageName via getLaunchIntentForPackage: ${it.component}") }
        ?.let { return it }

    context.packageManager.getLeanbackLaunchIntentForPackage(packageName)
        ?.also { Log.d(TAG, "Resolved $packageName via getLeanbackLaunchIntentForPackage: ${it.component}") }
        ?.let { return it }

    OEM_EXPLICIT_ACTIVITIES[packageName]?.forEach { activity ->
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setClassName(packageName, activity)
        }
        if (intent.resolveActivity(context.packageManager) != null) {
            Log.d(TAG, "Resolved $packageName via explicit fallback: $activity")
            return intent
        }
    }

    val queryFlags = PackageManager.MATCH_DISABLED_COMPONENTS
    for (category in LAUNCH_CATEGORIES) {
        val probe = Intent(Intent.ACTION_MAIN).apply {
            category?.let { addCategory(it) }
            setPackage(packageName)
        }
        val match = context.packageManager.queryIntentActivities(probe, queryFlags)
            .bestLaunchMatch()
            ?.activityInfo
            ?: continue
        val resolved = Intent(Intent.ACTION_MAIN).apply {
            category?.let { addCategory(it) }
            setClassName(match.packageName, match.name)
            setPackage(packageName)
        }
        Log.d(TAG, "Resolved $packageName via category=$category: ${match.packageName}/${match.name}")
        return resolved
    }
    Log.w(TAG, "Unable to resolve launch intent for installed package=$packageName")
    return null
}

private fun List<ResolveInfo>.bestLaunchMatch(): ResolveInfo? =
    firstOrNull { it.activityInfo?.exported == true } ?: firstOrNull()

internal fun isPackageInstalled(context: Context, packageName: String): Boolean =
    runCatching {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    }.getOrDefault(false)

/**
 * HOME launcher must start apps in a new task. With [flags]=0 the target activity was joining
 * [LauncherHomeActivity]'s singleTask stack and never surfaced on the head unit.
 */
private fun applyHomeLauncherLaunchFlags(intent: Intent) {
    intent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED,
    )
}

private fun startResolved(
    context: Context,
    packageName: String,
    intent: Intent,
    preferEmbedded: Boolean = true,
    forceFullscreen: Boolean = false,
) {
    val activity = context.findLauncherActivity()
    val launchIntent = Intent(intent).apply {
        if (action.isNullOrBlank()) action = Intent.ACTION_MAIN
        if (categories?.isEmpty() != false) addCategory(Intent.CATEGORY_LAUNCHER)
    }
    val component = launchIntent.component
    Log.w(
        TAG,
        "startResolved pkg=$packageName component=$component embedded=$preferEmbedded fullscreen=$forceFullscreen",
    )

    if (activity is LauncherHomeActivity) {
        if (dispatchLaunchViaService(
                context = context,
                packageName = packageName,
                component = component,
                preferEmbedded = preferEmbedded,
                forceFullscreen = forceFullscreen,
            )
        ) {
            return
        }
        showLaunchFailed(context, packageName, component, null)
        return
    }

    if (forceFullscreen) {
        val bounds = LauncherEmbeddedBoundsState.fullScreenBounds()
        if (bounds != null && isFreeformEnabled(context)) {
            if (tryLaunchIntentInBounds(context, packageName, launchIntent, bounds)) return
        }
    } else if (preferEmbedded) {
        val bounds = LauncherEmbeddedBoundsState.embeddedBounds()
        if (bounds != null && isFreeformEnabled(context)) {
            if (tryLaunchIntentInBounds(context, packageName, launchIntent, bounds)) return
        }
    }

    if (component != null && tryStartMainActivity(context, packageName, component, activity)) {
        LastAppTracker.recordLaunch(context, packageName)
        return
    }

    val fullscreenIntent = Intent(launchIntent).apply {
        applyHomeLauncherLaunchFlags(this)
    }
    runCatching { (activity ?: context).startActivity(fullscreenIntent) }
        .onSuccess {
            LastAppTracker.recordLaunch(context, packageName)
            Log.w(TAG, "startActivity OK pkg=$packageName")
            return
        }
        .onFailure {
            Log.w(TAG, "startActivity failed pkg=$packageName", it)
            showLaunchFailed(context, packageName, component, it)
        }
}

private fun dispatchLaunchViaService(
    context: Context,
    packageName: String = "",
    component: ComponentName? = null,
    openMainActivity: Boolean = false,
    preferEmbedded: Boolean = true,
    forceFullscreen: Boolean = false,
): Boolean =
    runCatching {
        val app = context.applicationContext
        val serviceIntent = Intent(app, BackgroundService::class.java)
        if (openMainActivity) {
            serviceIntent.action = BackgroundService.ACTION_OPEN_MAIN_ACTIVITY
        } else {
            serviceIntent.action = BackgroundService.ACTION_LAUNCH_APP
            serviceIntent.putExtra(BackgroundService.EXTRA_LAUNCH_PACKAGE, packageName)
            serviceIntent.putExtra(BackgroundService.EXTRA_LAUNCH_EMBEDDED, preferEmbedded && !forceFullscreen)
            serviceIntent.putExtra(BackgroundService.EXTRA_LAUNCH_FULLSCREEN, forceFullscreen)
            component?.let {
                serviceIntent.putExtra(BackgroundService.EXTRA_LAUNCH_COMPONENT, it.flattenToString())
            }
        }
        app.startService(serviceIntent)
        Log.w(
            TAG,
            "service dispatch action=${serviceIntent.action} pkg=$packageName embedded=${preferEmbedded && !forceFullscreen} fullscreen=$forceFullscreen",
        )
        true
    }.onFailure {
        Log.w(TAG, "service dispatch failed pkg=$packageName", it)
    }.isSuccess

private fun Context.findLauncherActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return this as? Activity
}

private fun showLaunchFailed(
    context: Context,
    packageName: String,
    component: ComponentName?,
    error: Throwable?,
) {
    Log.e(TAG, "All launch strategies failed for $packageName component=$component", error)
    Toast.makeText(
        context,
        "Не удалось открыть ${packageName.substringAfterLast('.')}",
        Toast.LENGTH_LONG,
    ).show()
}

private fun tryStartMainActivity(
    context: Context,
    packageName: String,
    component: ComponentName,
    activity: Activity?,
): Boolean {
    val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
    val options = activity?.let {
        android.app.ActivityOptions.makeBasic().toBundle()
    }
    return runCatching {
        launcherApps.startMainActivity(component, Process.myUserHandle(), null, options)
    }.onSuccess {
        Log.w(TAG, "LauncherApps.startMainActivity OK pkg=$packageName component=$component")
    }.onFailure {
        Log.w(TAG, "LauncherApps.startMainActivity failed pkg=$packageName component=$component", it)
    }.isSuccess
}
