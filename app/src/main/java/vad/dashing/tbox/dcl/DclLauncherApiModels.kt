package vad.dashing.tbox.dcl

import android.graphics.Rect
import android.os.Bundle

/** Pixel rectangle `"left,top,right,bottom"` from DCL geometry fields. */
data class DclBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    fun toRect(): Rect = Rect(left, top, right, bottom)

    fun toCsv(): String = "$left,$top,$right,$bottom"

    companion object {
        val DEFAULT_ADAS: DclBounds = DclBounds(0, 51, 344, 998)
        val DEFAULT_APP_AREA: DclBounds = DclBounds(344, 51, 1920, 998)
        val DEFAULT_DISPLAY: DclBounds = DclBounds(0, 0, 1920, 1080)

        /**
         * Parses `"l,t,r,b"`. Invalid / incomplete strings return [fallback]
         * (or [DEFAULT_APP_AREA] when fallback is null).
         */
        fun parse(boundsStr: String?, fallback: DclBounds? = null): DclBounds {
            val parts = boundsStr?.split(',')?.map { it.trim() }.orEmpty()
            if (parts.size == 4) {
                val l = parts[0].toIntOrNull()
                val t = parts[1].toIntOrNull()
                val r = parts[2].toIntOrNull()
                val b = parts[3].toIntOrNull()
                if (l != null && t != null && r != null && b != null) {
                    return DclBounds(l, t, r, b)
                }
            }
            return fallback ?: DEFAULT_APP_AREA
        }
    }
}

data class DclCapabilities(
    val apiVersion: Int,
    val launchFullscreen: Boolean,
    val launchAppArea: Boolean,
    val uiStateQuery: Boolean,
    val uiStateEvents: Boolean,
    val adasPanel: Boolean,
    val screenModes: List<String>,
    val splitSupported: Boolean?,
)

/**
 * Snapshot of DCL chrome + layout. Geometry fields use defaults from the vendor gist
 * when absent so floating overlays still have a usable coordinate system.
 */
data class DclUiState(
    val screenMode: String,
    val foregroundPackage: String,
    val leftPackage: String,
    val rightPackage: String,
    val topBarHeightPx: Int,
    val bottomBarHeightPx: Int,
    val adasPanelBounds: String,
    val appAreaBounds: String,
    val displayWidthPx: Int,
    val displayHeightPx: Int,
    val overlayOpen: Boolean,
    val revision: Long,
    val updatedAtEpochMs: Long?,
    val adasPanelVisible: Boolean?,
    val topBarVisible: Boolean?,
    val bottomBarVisible: Boolean?,
    val immersive: Boolean?,
) {
    fun adasBounds(): DclBounds =
        DclBounds.parse(adasPanelBounds, DclBounds.DEFAULT_ADAS)

    fun appArea(): DclBounds =
        DclBounds.parse(appAreaBounds, DclBounds.DEFAULT_APP_AREA)
}

data class DclApiError(
    val errorCode: String?,
    val message: String?,
)

sealed class DclCallResult<out T> {
    data class Ok<T>(val value: T) : DclCallResult<T>()
    data class Error(val error: DclApiError) : DclCallResult<Nothing>()
    /** Launcher missing, provider absent, or [android.content.ContentResolver.call] threw. */
    data object Unavailable : DclCallResult<Nothing>()
}

data class DclLaunchResult(
    val status: String,
    val packageName: String?,
    val mode: String?,
    val error: DclApiError?,
) {
    val success: Boolean
        get() = status == DclLauncherApiContract.STATUS_OK ||
            status == DclLauncherApiContract.STATUS_ACCEPTED
}

/** Pure Bundle/Intent parsing for the DCL Launcher API (unit-testable). */
object DclLauncherApiCodec {
    fun parseCapabilities(bundle: Bundle): DclCallResult<DclCapabilities> {
        val status = bundle.getString(DclLauncherApiContract.KEY_STATUS)
        if (status == DclLauncherApiContract.STATUS_ERROR) {
            return DclCallResult.Error(parseError(bundle))
        }
        if (status != null && status != DclLauncherApiContract.STATUS_OK) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = bundle.getString(DclLauncherApiContract.KEY_ERROR_CODE),
                    message = bundle.getString(DclLauncherApiContract.KEY_MESSAGE) ?: status,
                ),
            )
        }
        val screenModesRaw = bundle.getString(DclLauncherApiContract.KEY_SCREEN_MODES).orEmpty()
        val uiStateQuery = when {
            bundle.containsKey(DclLauncherApiContract.KEY_UI_STATE_QUERY) ->
                bundle.getBoolean(DclLauncherApiContract.KEY_UI_STATE_QUERY)
            bundle.containsKey(DclLauncherApiContract.KEY_UI_CHROME_QUERY) ->
                bundle.getBoolean(DclLauncherApiContract.KEY_UI_CHROME_QUERY)
            else -> false
        }
        val uiStateEvents = when {
            bundle.containsKey(DclLauncherApiContract.KEY_UI_STATE_EVENTS) ->
                bundle.getBoolean(DclLauncherApiContract.KEY_UI_STATE_EVENTS)
            bundle.containsKey(DclLauncherApiContract.KEY_UI_CHROME_EVENTS) ->
                bundle.getBoolean(DclLauncherApiContract.KEY_UI_CHROME_EVENTS)
            else -> false
        }
        return DclCallResult.Ok(
            DclCapabilities(
                apiVersion = bundle.getInt(DclLauncherApiContract.KEY_API_VERSION, 1),
                launchFullscreen = bundle.getBoolean(DclLauncherApiContract.KEY_LAUNCH_FULLSCREEN, false),
                launchAppArea = bundle.getBoolean(DclLauncherApiContract.KEY_LAUNCH_APP_AREA, false),
                uiStateQuery = uiStateQuery,
                uiStateEvents = uiStateEvents,
                adasPanel = bundle.getBoolean(DclLauncherApiContract.KEY_ADAS_PANEL, false),
                screenModes = screenModesRaw.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                splitSupported = if (bundle.containsKey(DclLauncherApiContract.KEY_SPLIT_SUPPORTED)) {
                    bundle.getBoolean(DclLauncherApiContract.KEY_SPLIT_SUPPORTED)
                } else {
                    null
                },
            ),
        )
    }

    fun parseUiState(bundle: Bundle): DclCallResult<DclUiState> {
        val status = bundle.getString(DclLauncherApiContract.KEY_STATUS)
        if (status == DclLauncherApiContract.STATUS_ERROR) {
            return DclCallResult.Error(parseError(bundle))
        }
        if (status != null && status != DclLauncherApiContract.STATUS_OK) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = bundle.getString(DclLauncherApiContract.KEY_ERROR_CODE),
                    message = bundle.getString(DclLauncherApiContract.KEY_MESSAGE) ?: status,
                ),
            )
        }
        return DclCallResult.Ok(uiStateFromBundle(bundle))
    }

    /** Broadcast extras may omit `status`; treat missing status as ok when geometry keys exist. */
    fun parseUiStateFromExtras(extras: Bundle?): DclUiState? {
        if (extras == null) return null
        val status = extras.getString(DclLauncherApiContract.KEY_STATUS)
        if (status == DclLauncherApiContract.STATUS_ERROR) return null
        if (status != null && status != DclLauncherApiContract.STATUS_OK) return null
        return uiStateFromBundle(extras)
    }

    fun parseLaunchResult(bundle: Bundle): DclLaunchResult {
        val status = bundle.getString(DclLauncherApiContract.KEY_STATUS)
            ?: DclLauncherApiContract.STATUS_ERROR
        return DclLaunchResult(
            status = status,
            packageName = bundle.getString(DclLauncherApiContract.EXTRA_PACKAGE_NAME),
            mode = bundle.getString(DclLauncherApiContract.EXTRA_MODE),
            error = if (status == DclLauncherApiContract.STATUS_ERROR) parseError(bundle) else null,
        )
    }

    fun parseError(bundle: Bundle): DclApiError =
        DclApiError(
            errorCode = bundle.getString(DclLauncherApiContract.KEY_ERROR_CODE),
            message = bundle.getString(DclLauncherApiContract.KEY_MESSAGE),
        )

    fun parseBounds(
        boundsStr: String?,
        fallback: DclBounds = DclBounds.DEFAULT_APP_AREA,
    ): DclBounds = DclBounds.parse(boundsStr, fallback)

    fun parseBoundsToRect(
        boundsStr: String?,
        fallback: DclBounds = DclBounds.DEFAULT_APP_AREA,
    ): Rect = parseBounds(boundsStr, fallback).toRect()

    private fun uiStateFromBundle(bundle: Bundle): DclUiState =
        DclUiState(
            screenMode = bundle.getString(
                DclLauncherApiContract.KEY_SCREEN_MODE,
                DclLauncherApiContract.SCREEN_MODE_HOME,
            ) ?: DclLauncherApiContract.SCREEN_MODE_HOME,
            foregroundPackage = bundle.getString(DclLauncherApiContract.KEY_FOREGROUND_PACKAGE, "")
                ?: "",
            leftPackage = bundle.getString(DclLauncherApiContract.KEY_LEFT_PACKAGE, "") ?: "",
            rightPackage = bundle.getString(DclLauncherApiContract.KEY_RIGHT_PACKAGE, "") ?: "",
            topBarHeightPx = bundle.getInt(
                DclLauncherApiContract.KEY_TOP_BAR_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_TOP_BAR_HEIGHT_PX,
            ),
            bottomBarHeightPx = bundle.getInt(
                DclLauncherApiContract.KEY_BOTTOM_BAR_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_BOTTOM_BAR_HEIGHT_PX,
            ),
            adasPanelBounds = bundle.getString(
                DclLauncherApiContract.KEY_ADAS_PANEL_BOUNDS,
                DclLauncherApiContract.DEFAULT_ADAS_PANEL_BOUNDS,
            ) ?: DclLauncherApiContract.DEFAULT_ADAS_PANEL_BOUNDS,
            appAreaBounds = bundle.getString(
                DclLauncherApiContract.KEY_APP_AREA_BOUNDS,
                DclLauncherApiContract.DEFAULT_APP_AREA_BOUNDS,
            ) ?: DclLauncherApiContract.DEFAULT_APP_AREA_BOUNDS,
            displayWidthPx = bundle.getInt(
                DclLauncherApiContract.KEY_DISPLAY_WIDTH_PX,
                DclLauncherApiContract.DEFAULT_DISPLAY_WIDTH_PX,
            ),
            displayHeightPx = bundle.getInt(
                DclLauncherApiContract.KEY_DISPLAY_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_DISPLAY_HEIGHT_PX,
            ),
            overlayOpen = bundle.getBoolean(DclLauncherApiContract.KEY_OVERLAY_OPEN, false),
            revision = bundle.getLong(DclLauncherApiContract.KEY_REVISION, 0L),
            updatedAtEpochMs = if (bundle.containsKey(DclLauncherApiContract.KEY_UPDATED_AT_EPOCH_MS)) {
                bundle.getLong(DclLauncherApiContract.KEY_UPDATED_AT_EPOCH_MS)
            } else {
                null
            },
            adasPanelVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_ADAS_PANEL_VISIBLE),
            topBarVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_TOP_BAR_VISIBLE),
            bottomBarVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_BOTTOM_BAR_VISIBLE),
            immersive = optionalBoolean(bundle, DclLauncherApiContract.KEY_IMMERSIVE),
        )

    private fun optionalBoolean(bundle: Bundle, key: String): Boolean? =
        if (bundle.containsKey(key)) bundle.getBoolean(key) else null
}
