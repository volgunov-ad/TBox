package vad.dashing.tbox.dcl

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository

/**
 * Launches an app via DCL with raw-startActivity fallback when the API is unavailable,
 * the launcher package/process is dead, or the selected mode lacks capability / returns
 * unsupported-class errors (design §3.1). Never auto-clears the DCL-environment preference.
 */
object DclAppLaunchHelper {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(
        context: Context,
        packageName: String,
        mode: String,
        activityClass: String? = null,
        onRawFallback: (() -> Unit)? = null,
    ) {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return
        val appContext = context.applicationContext
        scope.launch {
            val capabilityMissing =
                dclModeCapabilityMissing(mode, DclLauncherApiRepository.capabilities.value)
            val packageInstalled = DclLauncherLiveness.isPackageInstalled(appContext)
            val processDead = DclLauncherLiveness.processLikelyDead(appContext)
            if (dclShouldBypassBinderForRawFallback(
                    packageInstalled = packageInstalled,
                    processLikelyDead = processDead,
                    capabilityMissing = capabilityMissing,
                )
            ) {
                val reason = when {
                    !packageInstalled -> "launcher package missing"
                    processDead -> "launcher process dead"
                    else -> "capability missing for mode=$mode"
                }
                if (!packageInstalled || processDead) {
                    DclLauncherApiRepository.markUnavailable(reason)
                }
                TboxRepository.addLog(
                    "WARN",
                    "DCL",
                    "$reason — raw launch for $pkg",
                )
                runRawFallback(appContext, pkg, onRawFallback)
                return@launch
            }
            val result = runCatching {
                DclLauncherApiRepository.launchApp(
                    packageName = pkg,
                    mode = mode,
                    activityClass = activityClass,
                    context = appContext,
                )
            }.getOrElse {
                DclCallResult.Unavailable
            }
            when {
                result is DclCallResult.Ok && result.value.success -> Unit
                dclLaunchShouldUseRawFallback(result) -> {
                    val detail = when (result) {
                        is DclCallResult.Ok ->
                            result.value.error?.message
                                ?: result.value.error?.errorCode
                                ?: "launch not successful"
                        is DclCallResult.Error ->
                            result.error.message
                                ?: result.error.errorCode
                                ?: "DCL launch error"
                        DclCallResult.Unavailable -> "API unavailable"
                    }
                    if (result == DclCallResult.Unavailable) {
                        DclLauncherApiRepository.markUnavailable("launchApp unavailable")
                    }
                    TboxRepository.addLog(
                        "WARN",
                        "DCL",
                        "launchApp $pkg mode=$mode fallback: $detail",
                    )
                    runRawFallback(appContext, pkg, onRawFallback)
                }
                else -> {
                    val message = when (result) {
                        is DclCallResult.Ok ->
                            result.value.error?.message
                                ?: result.value.error?.errorCode
                                ?: "DCL launch failed"
                        is DclCallResult.Error ->
                            result.error.message
                                ?: result.error.errorCode
                                ?: "DCL launch error"
                        DclCallResult.Unavailable -> "DCL unavailable"
                    }
                    showToast(appContext, message)
                    TboxRepository.addLog(
                        "WARN",
                        "DCL",
                        "launchApp $pkg mode=$mode: $message",
                    )
                }
            }
        }
    }

    fun launchAppArea(
        context: Context,
        packageName: String,
        activityClass: String? = null,
        onRawFallback: (() -> Unit)? = null,
    ) {
        launch(
            context = context,
            packageName = packageName,
            mode = DclLauncherApiContract.MODE_APP_AREA,
            activityClass = activityClass,
            onRawFallback = onRawFallback,
        )
    }

    fun launchFullWidth(
        context: Context,
        packageName: String,
        onRawFallback: (() -> Unit)? = null,
    ) {
        launch(
            context = context,
            packageName = packageName,
            mode = DclLauncherApiContract.MODE_FULL_WIDTH,
            onRawFallback = onRawFallback,
        )
    }

    fun launchFullscreen(
        context: Context,
        packageName: String,
        onRawFallback: (() -> Unit)? = null,
    ) {
        launch(
            context = context,
            packageName = packageName,
            mode = DclLauncherApiContract.MODE_FULLSCREEN,
            onRawFallback = onRawFallback,
        )
    }

    /**
     * Suspending Binder `launchApp` for the app_area mode. See [launchModeBlocking].
     */
    suspend fun launchAppAreaBlocking(
        context: Context,
        packageName: String,
        activityClass: String? = null,
    ): Boolean =
        launchModeBlocking(
            context = context,
            packageName = packageName,
            mode = DclLauncherApiContract.MODE_APP_AREA,
            activityClass = activityClass,
        )

    /**
     * Suspending Binder `launchApp` for any supported mode (`app_area` / `full_width` /
     * `fullscreen`), bounded by [DclLauncherApiClient.CALL_TIMEOUT_MS]: a wedged DCL
     * provider yields `false` (caller falls back to Adayo / normal launch) instead of
     * stalling the redirect. Safe from any dispatcher (Binder hops to [Dispatchers.IO]).
     */
    suspend fun launchModeBlocking(
        context: Context,
        packageName: String,
        mode: String,
        activityClass: String? = null,
    ): Boolean {
        val appContext = context.applicationContext
        val capabilityMissing =
            dclModeCapabilityMissing(mode, DclLauncherApiRepository.capabilities.value)
        val packageInstalled = DclLauncherLiveness.isPackageInstalled(appContext)
        val processDead = DclLauncherLiveness.processLikelyDead(appContext)
        if (dclShouldBypassBinderForRawFallback(
                packageInstalled = packageInstalled,
                processLikelyDead = processDead,
                capabilityMissing = capabilityMissing,
            )
        ) {
            if (!packageInstalled || processDead) {
                DclLauncherApiRepository.markUnavailable(
                    when {
                        !packageInstalled -> "launcher package missing"
                        else -> "launcher process dead"
                    },
                )
            }
            return false
        }
        return withTimeoutOrNull(DclLauncherApiClient.CALL_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                val result = runCatching {
                    DclLauncherApiClient.launchAppBlocking(
                        context = appContext,
                        packageName = packageName,
                        mode = mode,
                        activityClass = activityClass,
                    )
                }.getOrElse { DclCallResult.Unavailable }
                when (result) {
                    is DclCallResult.Ok -> result.value.success
                    is DclCallResult.Error -> false
                    DclCallResult.Unavailable -> {
                        DclLauncherApiRepository.markUnavailable("launchModeBlocking unavailable")
                        false
                    }
                }
            }
        } ?: false
    }

    fun launchRaw(context: Context, packageName: String) {
        try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return
            MainActivityIntentHelper.applyExternalAppLaunchFlags(launchIntent, context)
            context.startActivity(launchIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun runRawFallback(
        appContext: Context,
        packageName: String,
        onRawFallback: (() -> Unit)?,
    ) {
        withContext(Dispatchers.Main) {
            // Toast only when DCL actually responds but the call failed. When the launcher
            // is not installed / not answering, the launch must look completely normal
            // (silent); the WARN journal entry above stays the only trace.
            if (dclFallbackToastVisible(DclLauncherApiRepository.available.value)) {
                Toast.makeText(
                    appContext,
                    appContext.getString(R.string.dcl_launcher_api_unavailable_fallback),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            if (onRawFallback != null) {
                onRawFallback()
            } else {
                launchRaw(appContext, packageName)
            }
        }
    }

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
