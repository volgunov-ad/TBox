package vad.dashing.tbox.dcl

import android.content.Context
import android.net.Uri
import android.os.Bundle
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Blocking Binder calls into DCL Launcher. Suspend wrappers hop to [Dispatchers.IO]
 * because [android.content.ContentResolver.call] must not run on the main thread.
 */
object DclLauncherApiClient {
    private val uri: Uri = Uri.parse(DclLauncherApiContract.URI)

    suspend fun getCapabilities(context: Context): DclCallResult<DclCapabilities> =
        withContext(Dispatchers.IO) { getCapabilitiesBlocking(context) }

    suspend fun getUiState(context: Context): DclCallResult<DclUiState> =
        withContext(Dispatchers.IO) { getUiStateBlocking(context) }

    /**
     * Same as [getUiState] via the `getUiChrome` method alias.
     */
    suspend fun getUiChrome(context: Context): DclCallResult<DclUiState> =
        withContext(Dispatchers.IO) { getUiChromeBlocking(context) }

    suspend fun launchApp(
        context: Context,
        packageName: String,
        mode: String = DclLauncherApiContract.MODE_APP_AREA,
        activityClass: String? = null,
        requestId: String? = UUID.randomUUID().toString(),
        action: String? = null,
        displayId: Int? = null,
    ): DclCallResult<DclLaunchResult> =
        withContext(Dispatchers.IO) {
            launchAppBlocking(
                context = context,
                packageName = packageName,
                mode = mode,
                activityClass = activityClass,
                requestId = requestId,
                action = action,
                displayId = displayId,
            )
        }

    suspend fun setSmartAcc(
        context: Context,
        enabled: Boolean,
        targetSpeed: Int? = null,
        configEnabled: Boolean? = null,
    ): DclCallResult<Unit> =
        withContext(Dispatchers.IO) {
            setSmartAccBlocking(context, enabled, targetSpeed, configEnabled)
        }

    suspend fun toggleSmartAcc(context: Context): DclCallResult<Unit> =
        withContext(Dispatchers.IO) { toggleSmartAccBlocking(context) }

    suspend fun getSmartAccState(context: Context): DclCallResult<DclSmartAccState> =
        withContext(Dispatchers.IO) { getSmartAccStateBlocking(context) }

    fun getCapabilitiesBlocking(context: Context): DclCallResult<DclCapabilities> {
        val bundle = call(context, DclLauncherApiContract.METHOD_GET_CAPABILITIES)
            ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseCapabilities(bundle)
    }

    fun getUiStateBlocking(context: Context): DclCallResult<DclUiState> {
        val bundle = call(context, DclLauncherApiContract.METHOD_GET_UI_STATE)
            ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseUiState(bundle)
    }

    fun getUiChromeBlocking(context: Context): DclCallResult<DclUiState> {
        val bundle = call(context, DclLauncherApiContract.METHOD_GET_UI_CHROME)
            ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseUiState(bundle)
    }

    fun launchAppBlocking(
        context: Context,
        packageName: String,
        mode: String = DclLauncherApiContract.MODE_APP_AREA,
        activityClass: String? = null,
        requestId: String? = UUID.randomUUID().toString(),
        action: String? = null,
        displayId: Int? = null,
    ): DclCallResult<DclLaunchResult> {
        if (packageName.isBlank()) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = DclLauncherApiContract.ERROR_INVALID_ARGS,
                    message = "packageName is required",
                ),
            )
        }
        if (mode != DclLauncherApiContract.MODE_APP_AREA &&
            mode != DclLauncherApiContract.MODE_FULL_WIDTH &&
            mode != DclLauncherApiContract.MODE_FULLSCREEN
        ) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = DclLauncherApiContract.ERROR_INVALID_ARGS,
                    message = "mode must be app_area, full_width, or fullscreen",
                ),
            )
        }
        val extras = Bundle().apply {
            putString(DclLauncherApiContract.EXTRA_PACKAGE_NAME, packageName)
            putString(DclLauncherApiContract.EXTRA_MODE, mode)
            if (!activityClass.isNullOrEmpty()) {
                putString(DclLauncherApiContract.EXTRA_ACTIVITY_CLASS, activityClass)
            }
            if (!requestId.isNullOrEmpty()) {
                putString(DclLauncherApiContract.EXTRA_REQUEST_ID, requestId)
            }
            if (!action.isNullOrEmpty()) {
                putString(DclLauncherApiContract.EXTRA_ACTION, action)
            }
            if (displayId != null) {
                putInt(DclLauncherApiContract.EXTRA_DISPLAY_ID, displayId)
            }
        }
        val bundle = call(context, DclLauncherApiContract.METHOD_LAUNCH_APP, extras = extras)
            ?: return DclCallResult.Unavailable
        return DclCallResult.Ok(DclLauncherApiCodec.parseLaunchResult(bundle))
    }

    fun setSmartAccBlocking(
        context: Context,
        enabled: Boolean,
        targetSpeed: Int? = null,
        configEnabled: Boolean? = null,
    ): DclCallResult<Unit> {
        val extras = DclLauncherApiCodec.buildSetSmartAccExtras(
            enabled = enabled,
            targetSpeed = targetSpeed,
            configEnabled = configEnabled,
        )
        val bundle = call(
            context,
            DclLauncherApiContract.METHOD_SET_SMART_ACC,
            arg = DclLauncherApiCodec.setSmartAccArg(enabled),
            extras = extras,
        ) ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseStatusOk(bundle)
    }

    fun toggleSmartAccBlocking(context: Context): DclCallResult<Unit> {
        val bundle = call(context, DclLauncherApiContract.METHOD_TOGGLE_SMART_ACC)
            ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseStatusOk(bundle)
    }

    fun getSmartAccStateBlocking(context: Context): DclCallResult<DclSmartAccState> {
        val bundle = call(context, DclLauncherApiContract.METHOD_GET_SMART_ACC_STATE)
            ?: return DclCallResult.Unavailable
        return DclLauncherApiCodec.parseSmartAccState(bundle)
    }

    private fun call(
        context: Context,
        method: String,
        arg: String? = null,
        extras: Bundle? = null,
    ): Bundle? =
        try {
            context.contentResolver.call(uri, method, arg, extras)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: SecurityException) {
            null
        } catch (_: RuntimeException) {
            // Some OEM ContentResolver paths wrap remote failures as RuntimeException.
            null
        }
}
