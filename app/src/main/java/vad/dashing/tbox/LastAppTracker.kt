package vad.dashing.tbox

import android.app.Activity
import android.app.Application

/**
 * Records the last third-party app launched from TBox launcher surfaces.
 */
object LastAppTracker {

    private val ignoredPackages = setOf(
        "vad.dashing.tbox",
        "com.wt.launcher3",
        "com.android.launcher3",
    )

    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: android.os.Bundle?) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: android.os.Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                val pkg = activity.packageName
                if (pkg in ignoredPackages) return
                val label = runCatching {
                    activity.packageManager.getApplicationLabel(
                        activity.applicationInfo
                    ).toString()
                }.getOrElse { pkg }
                LauncherStateStore.saveLastLaunchedApp(activity, pkg, label)
            }
        })
    }

    fun recordLaunch(context: android.content.Context, packageName: String) {
        if (packageName.isBlank() || packageName in ignoredPackages) return
        val label = runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        }.getOrElse { packageName }
        LauncherStateStore.saveLastLaunchedApp(context, packageName, label)
    }
}
