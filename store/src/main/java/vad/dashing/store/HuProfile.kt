package vad.dashing.store

import android.content.pm.PackageManager

enum class HuKind(val catalogTag: String) {
    A9("a9"),
    A10("a10"),
    UNKNOWN("unknown"),
}

object HuProfile {
    private const val A9_LAUNCHER = "com.wt.launcher3"
    private const val A10_LAUNCHER = "com.adayo.launcher"

    fun detect(packageManager: PackageManager): HuKind {
        val hasA10 = isPackagePresent(packageManager, A10_LAUNCHER)
        val hasA9 = isPackagePresent(packageManager, A9_LAUNCHER)
        return when {
            hasA10 && !hasA9 -> HuKind.A10
            hasA9 && !hasA10 -> HuKind.A9
            hasA10 -> HuKind.A10
            hasA9 -> HuKind.A9
            else -> HuKind.UNKNOWN
        }
    }

    private fun isPackagePresent(packageManager: PackageManager, packageName: String): Boolean =
        runCatching {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)
}
