package vad.dashing.tbox

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import vad.dashing.tbox.update.InstallPermissionHelper

/** User-facing permissions that can be granted via UI and/or ADB. */
enum class AppPermissionId {
    Overlay,
    WriteSettings,
    WriteSecureSettings,
    UsageStats,
    NotificationListener,
    InstallPackages,
    Storage,
    Location,
}

enum class AppPermissionGrantKind {
    /** Open a system Settings screen (special access / AppOps). */
    OpenSettings,
    /** Request via runtime permission dialog. */
    RequestRuntime,
    /** User must run an ADB command; no Settings toggle. */
    AdbOnly,
}

data class AppPermissionStatus(
    val id: AppPermissionId,
    val granted: Boolean,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    val grantKind: AppPermissionGrantKind,
    /** Non-blank when [grantKind] is [AppPermissionGrantKind.AdbOnly]. */
    val adbCommand: String = "",
)

object AppPermissions {
    fun snapshot(context: Context): List<AppPermissionStatus> {
        return listOf(
            AppPermissionStatus(
                id = AppPermissionId.Overlay,
                granted = Settings.canDrawOverlays(context),
                titleRes = R.string.permissions_overlay_title,
                descriptionRes = R.string.permissions_overlay_desc,
                grantKind = AppPermissionGrantKind.OpenSettings,
            ),
            AppPermissionStatus(
                id = AppPermissionId.WriteSettings,
                granted = Settings.System.canWrite(context),
                titleRes = R.string.permissions_write_settings_title,
                descriptionRes = R.string.permissions_write_settings_desc,
                grantKind = AppPermissionGrantKind.OpenSettings,
            ),
            AppPermissionStatus(
                id = AppPermissionId.WriteSecureSettings,
                granted = hasWriteSecureSettings(context),
                titleRes = R.string.permissions_write_secure_title,
                descriptionRes = R.string.permissions_write_secure_desc,
                grantKind = AppPermissionGrantKind.AdbOnly,
                adbCommand = buildWriteSecureSettingsAdbCommand(context.packageName),
            ),
            AppPermissionStatus(
                id = AppPermissionId.UsageStats,
                granted = UsageStatsHideFloatingHelper.hasUsageAccessPermission(context),
                titleRes = R.string.permissions_usage_stats_title,
                descriptionRes = R.string.permissions_usage_stats_desc,
                grantKind = AppPermissionGrantKind.OpenSettings,
            ),
            AppPermissionStatus(
                id = AppPermissionId.NotificationListener,
                granted = hasNotificationListenerAccess(context),
                titleRes = R.string.permissions_notification_listener_title,
                descriptionRes = R.string.permissions_notification_listener_desc,
                grantKind = AppPermissionGrantKind.OpenSettings,
            ),
            AppPermissionStatus(
                id = AppPermissionId.InstallPackages,
                granted = InstallPermissionHelper.canInstallPackages(context),
                titleRes = R.string.permissions_install_packages_title,
                descriptionRes = R.string.permissions_install_packages_desc,
                grantKind = AppPermissionGrantKind.OpenSettings,
            ),
            AppPermissionStatus(
                id = AppPermissionId.Storage,
                granted = hasStorageAccess(context),
                titleRes = R.string.permissions_storage_title,
                descriptionRes = R.string.permissions_storage_desc,
                grantKind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    AppPermissionGrantKind.OpenSettings
                } else {
                    AppPermissionGrantKind.RequestRuntime
                },
            ),
            AppPermissionStatus(
                id = AppPermissionId.Location,
                granted = hasLocationAccess(context),
                titleRes = R.string.permissions_location_title,
                descriptionRes = R.string.permissions_location_desc,
                grantKind = AppPermissionGrantKind.RequestRuntime,
            ),
        )
    }

    fun createGrantIntent(context: Context, id: AppPermissionId): Intent? {
        val pkg = context.packageName
        return when (id) {
            AppPermissionId.Overlay -> Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$pkg".toUri(),
            )
            AppPermissionId.WriteSettings -> Intent(
                Settings.ACTION_MANAGE_WRITE_SETTINGS,
                "package:$pkg".toUri(),
            )
            AppPermissionId.UsageStats -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            AppPermissionId.NotificationListener -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            AppPermissionId.InstallPackages ->
                InstallPermissionHelper.createUnknownSourcesSettingsIntent(context)
            AppPermissionId.Storage -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = "package:$pkg".toUri()
                    }
                } else {
                    null
                }
            }
            AppPermissionId.WriteSecureSettings,
            AppPermissionId.Location -> null
        }
    }

    fun runtimePermissionsFor(id: AppPermissionId): Array<String> {
        return when (id) {
            AppPermissionId.Location -> arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            AppPermissionId.Storage -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    emptyArray()
                } else {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    )
                }
            }
            else -> emptyArray()
        }
    }

    fun buildWriteSecureSettingsAdbCommand(packageName: String): String {
        return "adb shell pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
    }

    private fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasNotificationListenerAccess(context: Context): Boolean {
        val expected = ComponentName(
            context,
            MediaControlNotificationListenerService::class.java,
        )
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        if (enabledListeners.isBlank()) return false
        return enabledListeners
            .split(':')
            .mapNotNull { ComponentName.unflattenFromString(it) }
            .any { it == expected }
    }

    private fun hasStorageAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasLocationAccess(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
