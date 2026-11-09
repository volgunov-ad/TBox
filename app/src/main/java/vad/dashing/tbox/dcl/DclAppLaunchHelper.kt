package vad.dashing.tbox.dcl

import android.content.Context
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vad.dashing.tbox.MainActivityIntentHelper
import vad.dashing.tbox.R
import vad.dashing.tbox.TboxRepository

/**
 * Launches an app via DCL with raw-startActivity fallback when the API is unavailable.
 * Never auto-clears the DCL-environment preference.
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
            when (result) {
                is DclCallResult.Ok -> {
                    if (!result.value.success) {
                        val message = result.value.error?.message
                            ?: result.value.error?.errorCode
                            ?: "DCL launch failed"
                        showToast(appContext, message)
                        TboxRepository.addLog(
                            "WARN",
                            "DCL",
                            "launchApp $pkg mode=$mode: $message",
                        )
                    }
                }
                is DclCallResult.Error -> {
                    val message = result.error.message
                        ?: result.error.errorCode
                        ?: "DCL launch error"
                    showToast(appContext, message)
                    TboxRepository.addLog(
                        "WARN",
                        "DCL",
                        "launchApp $pkg mode=$mode error: $message",
                    )
                }
                DclCallResult.Unavailable -> {
                    TboxRepository.addLog(
                        "WARN",
                        "DCL",
                        "API unavailable — raw launch for $pkg (mode=$mode)",
                    )
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            appContext,
                            appContext.getString(R.string.dcl_launcher_api_unavailable_fallback),
                            Toast.LENGTH_SHORT,
                        ).show()
                        if (onRawFallback != null) {
                            onRawFallback()
                        } else {
                            launchRaw(appContext, pkg)
                        }
                    }
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

    /** Blocking variant for MainActivity cold-start redirect (already off the critical UI path). */
    fun launchAppAreaBlocking(
        context: Context,
        packageName: String,
        activityClass: String? = null,
    ): Boolean {
        val result = DclLauncherApiClient.launchAppBlocking(
            context = context.applicationContext,
            packageName = packageName,
            mode = DclLauncherApiContract.MODE_APP_AREA,
            activityClass = activityClass,
        )
        return when (result) {
            is DclCallResult.Ok -> result.value.success
            is DclCallResult.Error,
            DclCallResult.Unavailable,
            -> false
        }
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

    private suspend fun showToast(context: Context, message: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
