package vad.dashing.store.dcl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Opens an installed app via DCL Binder `launchApp(mode=app_area)` when the launcher
 * package is present and answering; otherwise (or on Unavailable / unsupported-class
 * errors including `forbidden` until DCL allowlists Store) falls back to ordinary
 * [Context.startActivity] with the package launch intent.
 *
 * Same idea as Monitor `DclAppLaunchHelper.launchAppArea`, without the DCL-environment
 * preference (Store always prefers DCL when it looks alive).
 */
object StoreDclAppLauncher {
    private const val TAG = "StoreDclLaunch"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val uri: Uri = Uri.parse(StoreDclContract.URI)

    /**
     * Returns false only when the package has no launch activity.
     * Otherwise schedules DCL-or-raw open and returns true immediately.
     */
    fun open(context: Context, packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isBlank()) return false
        val appContext = context.applicationContext
        val launchIntent = appContext.packageManager.getLaunchIntentForPackage(pkg)
            ?: return false
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val packageInstalled = StoreDclLiveness.isPackageInstalled(appContext)
        val processDead = StoreDclLiveness.processLikelyDead(appContext)
        if (storeDclShouldBypassBinder(packageInstalled, processDead)) {
            Log.i(
                TAG,
                when {
                    !packageInstalled -> "DCL package missing — raw open $pkg"
                    else -> "DCL process dead — raw open $pkg"
                },
            )
            return startRaw(appContext, launchIntent)
        }

        scope.launch {
            val outcome = runCatching {
                withTimeoutOrNull(StoreDclContract.CALL_TIMEOUT_MS) {
                    withContext(Dispatchers.IO) {
                        launchAppBlocking(appContext, pkg)
                    }
                } ?: StoreDclLaunchOutcome.Unavailable
            }.getOrElse { StoreDclLaunchOutcome.Unavailable }

            if (outcome is StoreDclLaunchOutcome.Success) return@launch
            // Store always completes Open: Unavailable / unsupported / forbidden / busy /
            // not_installed → ordinary startActivity (launch intent already resolved).
            Log.w(TAG, "launchApp $pkg fallback: ${outcome.describe()}")
            withContext(Dispatchers.Main) {
                startRaw(appContext, launchIntent)
            }
        }
        return true
    }

    private fun startRaw(context: Context, launchIntent: Intent): Boolean =
        runCatching { context.startActivity(launchIntent) }.isSuccess

    private fun launchAppBlocking(
        context: Context,
        packageName: String,
    ): StoreDclLaunchOutcome {
        val extras = Bundle().apply {
            putString(StoreDclContract.EXTRA_PACKAGE_NAME, packageName)
            putString(StoreDclContract.EXTRA_MODE, StoreDclContract.MODE_APP_AREA)
            putString(StoreDclContract.EXTRA_REQUEST_ID, UUID.randomUUID().toString())
        }
        val bundle = call(context, extras) ?: return StoreDclLaunchOutcome.Unavailable
        return parseLaunchOutcome(bundle)
    }

    private fun call(context: Context, extras: Bundle): Bundle? =
        try {
            context.contentResolver.call(
                uri,
                StoreDclContract.METHOD_LAUNCH_APP,
                null,
                extras,
            )
        } catch (_: Exception) {
            // DeadObjectException / RemoteException / SecurityException / missing provider.
            null
        }
}

sealed class StoreDclLaunchOutcome {
    data object Success : StoreDclLaunchOutcome()
    data object Unavailable : StoreDclLaunchOutcome()
    data class Failed(val errorCode: String?, val message: String?) : StoreDclLaunchOutcome()

    fun describe(): String =
        when (this) {
            Success -> "ok"
            Unavailable -> "unavailable"
            is Failed -> errorCode ?: message ?: "failed"
        }
}

fun parseLaunchOutcome(bundle: Bundle): StoreDclLaunchOutcome {
    val status = bundle.getString(StoreDclContract.KEY_STATUS)?.trim().orEmpty()
    if (status == StoreDclContract.STATUS_OK || status == StoreDclContract.STATUS_ACCEPTED) {
        return StoreDclLaunchOutcome.Success
    }
    if (status.isEmpty() && !bundle.containsKey(StoreDclContract.KEY_ERROR_CODE)) {
        // Some builds return empty status on success; treat missing error as success.
        return StoreDclLaunchOutcome.Success
    }
    return StoreDclLaunchOutcome.Failed(
        errorCode = bundle.getString(StoreDclContract.KEY_ERROR_CODE),
        message = bundle.getString(StoreDclContract.KEY_MESSAGE),
    )
}

/**
 * Whether a Binder outcome should fall back to raw startActivity.
 * Store always falls back on any non-success so Open still works when DCL is dead,
 * wedged, or has not yet allowlisted [StoreDclContract.STORE_PACKAGE] (`forbidden`).
 */
fun storeDclShouldUseRawFallback(outcome: StoreDclLaunchOutcome): Boolean =
    outcome !is StoreDclLaunchOutcome.Success
