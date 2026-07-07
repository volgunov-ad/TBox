package vad.dashing.tbox

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.UserHandle

/**
 * Binds a picked app widget id to this app's [AppWidgetHost] after [AppWidgetManager.ACTION_APPWIDGET_PICK].
 *
 * The system picker may return a provider without granting bind permission to our host; explicit
 * [AppWidgetManager.bindAppWidgetIdIfAllowed] (and [AppWidgetManager.ACTION_APPWIDGET_BIND] when needed)
 * matches the flow used by third-party launchers on automotive head units.
 */
object ExternalAppWidgetBinder {

    enum class BindOutcome {
        /** Widget id is already bound to this package or bind succeeded. */
        Success,

        /** User must approve bind via [AppWidgetManager.ACTION_APPWIDGET_BIND]. */
        NeedsPermission,
    }

    enum class PickBindStatus {
        /** Pick/bind left a usable widget id — continue to configure/save. */
        ReadyToConfigure,

        /** No widget info yet; system bind UI may be required. */
        NeedsBindPermission,

        /** Bind UI was denied or unavailable and widget info is still missing. */
        Unavailable,
    }

    fun isWidgetInfoAvailable(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ): Boolean = appWidgetManager.getAppWidgetInfo(appWidgetId) != null

    /**
     * After [bindAppWidgetIdIfAllowed], decide whether to continue without blocking the user.
     *
     * Many head units already bind the picked id during [AppWidgetManager.ACTION_APPWIDGET_PICK]
     * even when [bindAppWidgetIdIfAllowed] returns false.
     */
    fun statusAfterPickBindAttempt(
        bindIfAllowedSucceeded: Boolean,
        widgetInfoPresent: Boolean,
    ): PickBindStatus {
        if (bindIfAllowedSucceeded || widgetInfoPresent) {
            return PickBindStatus.ReadyToConfigure
        }
        return PickBindStatus.NeedsBindPermission
    }

    fun statusAfterBindPermissionUi(
        bindUiResultOk: Boolean,
        bindIfAllowedSucceeded: Boolean,
        widgetInfoPresent: Boolean,
    ): PickBindStatus {
        if (bindIfAllowedSucceeded || widgetInfoPresent) {
            return PickBindStatus.ReadyToConfigure
        }
        if (bindUiResultOk) {
            return PickBindStatus.Unavailable
        }
        return PickBindStatus.Unavailable
    }

    fun resolveProviderInfo(
        appWidgetManager: AppWidgetManager,
        pickResult: Intent?,
        appWidgetId: Int,
    ): AppWidgetProviderInfo? {
        appWidgetManager.getAppWidgetInfo(appWidgetId)?.let { return it }
        val provider = pickResult?.providerComponent() ?: return null
        val profile = pickResult.providerProfile()
        return findInstalledProvider(appWidgetManager, provider, profile)
    }

    fun bindIfAllowed(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
    ): BindOutcome {
        if (tryBind(appWidgetManager, appWidgetId, providerInfo)) {
            return BindOutcome.Success
        }
        return BindOutcome.NeedsPermission
    }

    fun tryBindIfAllowed(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
    ): Boolean = tryBind(appWidgetManager, appWidgetId, providerInfo)

    fun createBindPermissionIntent(
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
    ): Intent {
        return Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, providerInfo.provider)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, providerInfo.profile)
            }
        }
    }

    fun buildGrantBindAdbCommand(packageName: String, userId: Int = 0): String {
        return "adb shell cmd appwidget grantbind --package $packageName --user $userId"
    }

    fun findInstalledProvider(
        appWidgetManager: AppWidgetManager,
        provider: ComponentName,
        profile: UserHandle? = null,
    ): AppWidgetProviderInfo? {
        val providers = installedProviders(appWidgetManager, profile)
        return providers.firstOrNull { it.provider == provider }
    }

    private fun installedProviders(
        appWidgetManager: AppWidgetManager,
        profile: UserHandle?,
    ): List<AppWidgetProviderInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && profile != null) {
            appWidgetManager.getInstalledProvidersForProfile(profile)
        } else {
            @Suppress("DEPRECATION")
            appWidgetManager.installedProviders
        }
    }

    private fun tryBind(
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        providerInfo: AppWidgetProviderInfo,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            appWidgetManager.bindAppWidgetIdIfAllowed(
                appWidgetId,
                providerInfo.profile,
                providerInfo.provider,
                null,
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider, null)
        } else {
            @Suppress("DEPRECATION")
            appWidgetManager.bindAppWidgetIdIfAllowed(appWidgetId, providerInfo.provider)
        }
    }

    private fun Intent.providerComponent(): ComponentName? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, ComponentName::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER)
        }
    }

    private fun Intent.providerProfile(): UserHandle? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, UserHandle::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE)
        }
    }
}
