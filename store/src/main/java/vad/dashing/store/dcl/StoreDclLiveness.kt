package vad.dashing.store.dcl

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Conservative checks for a dead / missing DCL launcher so Store can skip Binder
 * and use raw [android.content.Context.startActivity] without waiting on a hung provider.
 *
 * Mirrors Monitor `DclLauncherLiveness`: a filtered process list (only self) must **not**
 * be treated as "DCL dead".
 */
object StoreDclLiveness {

    fun isPackageInstalled(context: Context): Boolean =
        isPackageInstalled(context.packageManager)

    fun isPackageInstalled(packageManager: PackageManager): Boolean =
        runCatching {
            packageManager.getPackageInfo(StoreDclContract.LAUNCHER_PACKAGE, 0)
            true
        }.getOrDefault(false)

    fun runningPackageNames(context: Context): Set<String>? {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return null
        val processes = runCatching { am.runningAppProcesses }.getOrNull() ?: return null
        val names = linkedSetOf<String>()
        for (info in processes) {
            val processName = info.processName?.trim().orEmpty()
            if (processName.isNotEmpty()) names.add(processName)
            val pkgs = info.pkgList ?: continue
            for (pkg in pkgs) {
                val trimmed = pkg?.trim().orEmpty()
                if (trimmed.isNotEmpty()) names.add(trimmed)
            }
        }
        return names
    }

    fun processLikelyDead(
        runningPackageNames: Collection<String>?,
        selfPackageName: String,
    ): Boolean {
        if (runningPackageNames == null) return false
        val pkgs = runningPackageNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        if (pkgs.isEmpty()) return false
        if (StoreDclContract.LAUNCHER_PACKAGE in pkgs) return false
        val others = pkgs - selfPackageName.trim()
        return others.isNotEmpty()
    }

    fun processLikelyDead(context: Context): Boolean =
        processLikelyDead(
            runningPackageNames = runningPackageNames(context),
            selfPackageName = context.packageName,
        )
}

/** Skip Binder when the launcher package is gone or its process is clearly absent. */
fun storeDclShouldBypassBinder(
    packageInstalled: Boolean,
    processLikelyDead: Boolean,
): Boolean = !packageInstalled || processLikelyDead
