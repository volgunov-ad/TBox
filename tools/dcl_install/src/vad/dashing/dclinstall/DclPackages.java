package vad.dashing.dclinstall;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

/** Installed DCL version, including while the package is hidden for a dealer visit. */
final class DclPackages {
    private DclPackages() {}

    static String dclVersion(PackageManager packages) {
        return version(packages, InstallPlan.DCL_PACKAGE, PackageManager.MATCH_UNINSTALLED_PACKAGES);
    }

    /** A firmware package that getPackageInfo cannot see is hidden. These packages are not uninstalled. */
    static boolean hidden(PackageManager packages, String packageName) {
        return version(packages, packageName, 0) == null;
    }

    private static String version(PackageManager packages, String packageName, int flags) {
        try {
            PackageInfo info = packages.getPackageInfo(packageName, flags);
            if (info == null) return null;
            return info.versionName == null ? "" : info.versionName;
        } catch (PackageManager.NameNotFoundException missing) {
            return null;
        }
    }
}
