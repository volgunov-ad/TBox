package vad.dashing.tbox.dcl

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager

/**
 * Conservative checks for a dead / missing DCL launcher process so launches can skip Binder
 * and use raw [android.app.Activity.startActivity] without waiting on a hung provider.
 *
 * A filtered [ActivityManager.getRunningAppProcesses] snapshot (only self) must **not** be
 * treated as "DCL dead" — Binder [DclCallResult.Unavailable] still covers that case.
 */
object DclLauncherLiveness {

    fun isPackageInstalled(context: Context): Boolean =
        isPackageInstalled(context.packageManager)

    fun isPackageInstalled(packageManager: PackageManager): Boolean =
        runCatching {
            packageManager.getPackageInfo(DclLauncherApiContract.LAUNCHER_PACKAGE, 0)
            true
        }.getOrDefault(false)

    /**
     * Collects package names visible in the running-process list. Empty / self-only lists are
     * unreliable on locked-down builds; [processLikelyDead] stays false for those.
     */
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
        if (DclLauncherApiContract.LAUNCHER_PACKAGE in pkgs) return false
        val others = pkgs - selfPackageName.trim()
        return others.isNotEmpty()
    }

    fun processLikelyDead(context: Context): Boolean =
        processLikelyDead(
            runningPackageNames = runningPackageNames(context),
            selfPackageName = context.packageName,
        )
}

/**
 * Skip Binder and use raw startActivity when the launcher package is gone, its process is
 * clearly absent from a trustworthy process list, or the selected mode lacks capability.
 */
fun dclShouldBypassBinderForRawFallback(
    packageInstalled: Boolean,
    processLikelyDead: Boolean,
    capabilityMissing: Boolean,
): Boolean = !packageInstalled || processLikelyDead || capabilityMissing
