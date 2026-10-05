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

    fun buildWriteSecureSettingsShellCommand(packageName: String): String {
        return "pm grant $packageName android.permission.WRITE_SECURE_SETTINGS"
    }

    fun buildWriteSecureSettingsAdbCommand(packageName: String): String {
        return "adb shell ${buildWriteSecureSettingsShellCommand(packageName)}"
    }

    /**
     * Shell commands that grant [id] via adbd (same set as `scripts/hu-device-test`).
     * Empty when the id needs a prior `settings get` (notification listener) or is unknown.
     */
    fun buildAutoGrantShellCommands(
        id: AppPermissionId,
        packageName: String,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): List<String> {
        return when (id) {
            AppPermissionId.Overlay -> listOf(
                "appops set $packageName SYSTEM_ALERT_WINDOW allow",
            )
            AppPermissionId.WriteSettings -> listOf(
                "appops set $packageName WRITE_SETTINGS allow",
            )
            AppPermissionId.WriteSecureSettings -> listOf(
                buildWriteSecureSettingsShellCommand(packageName),
            )
            AppPermissionId.UsageStats -> listOf(
                "appops set $packageName GET_USAGE_STATS allow",
            )
            AppPermissionId.NotificationListener -> emptyList()
            AppPermissionId.InstallPackages -> {
                if (sdkInt >= Build.VERSION_CODES.O) {
                    listOf("appops set $packageName REQUEST_INSTALL_PACKAGES allow")
                } else {
                    emptyList()
                }
            }
            AppPermissionId.Storage -> {
                if (sdkInt >= Build.VERSION_CODES.R) {
                    listOf("appops set $packageName MANAGE_EXTERNAL_STORAGE allow")
                } else {
                    listOf(
                        "pm grant $packageName android.permission.READ_EXTERNAL_STORAGE",
                        "pm grant $packageName android.permission.WRITE_EXTERNAL_STORAGE",
                    )
                }
            }
            AppPermissionId.Location -> buildList {
                add("pm grant $packageName android.permission.ACCESS_FINE_LOCATION")
                add("pm grant $packageName android.permission.ACCESS_COARSE_LOCATION")
                if (sdkInt >= Build.VERSION_CODES.Q) {
                    add("pm grant $packageName android.permission.ACCESS_BACKGROUND_LOCATION")
                }
            }
        }
    }

    fun notificationListenerComponent(packageName: String): String =
        "$packageName/$packageName.MediaControlNotificationListenerService"

    /** Empty if [component] is already listed; otherwise a single `settings put` command. */
    fun buildNotificationListenerEnableCommand(
        component: String,
        currentListeners: String,
    ): String? {
        val current = currentListeners.trim()
        if (current.contains(component)) return null
        val value = if (current.isBlank() || current == "null") {
            component
        } else {
            "$current:$component"
        }
        return "settings put secure enabled_notification_listeners $value"
    }

    fun hasWriteSecureSettings(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasNotificationListenerAccess(context: Context): Boolean {
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

    fun hasStorageAccess(context: Context): Boolean {
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

    fun hasLocationAccess(context: Context): Boolean {
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
