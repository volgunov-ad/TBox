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
    /**
     * Optional `launchFullWidth` from getCapabilities.
     * `null` = key absent (attempt Binder launch; unsupported → raw fallback).
     * `false` = explicitly unsupported → treat as capability missing.
     * `true` = gate allows launch.
     */
    val launchFullWidth: Boolean?,
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
    /** DCL notification / control-center shade expanded; absent on older launchers. */
    val notificationShadeExpanded: Boolean?,
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
    fun parseCapabilities(bundle: Bundle): DclCallResult<DclCapabilities> =
        runCatching { parseCapabilitiesUnsafe(bundle) }
            .getOrElse {
                DclCallResult.Error(
                    DclApiError(
                        errorCode = DclLauncherApiContract.ERROR_INTERNAL,
                        message = "capabilities parse failed: ${it.javaClass.simpleName}",
                    ),
                )
            }

    fun parseUiState(bundle: Bundle): DclCallResult<DclUiState> =
        runCatching { parseUiStateUnsafe(bundle) }
            .getOrElse {
                DclCallResult.Error(
                    DclApiError(
                        errorCode = DclLauncherApiContract.ERROR_INTERNAL,
                        message = "uiState parse failed: ${it.javaClass.simpleName}",
                    ),
                )
            }

    /**
     * Broadcast extras may omit `status`; treat missing status as ok when a real snapshot
     * marker is present (status / revision / screenMode). Empty / poison extras → null.
     */
    fun parseUiStateFromExtras(extras: Bundle?): DclUiState? {
        if (extras == null || extras.isEmpty) return null
        if (!hasUsableUiStateMarkers(extras)) return null
        return runCatching {
            val status = safeString(extras, DclLauncherApiContract.KEY_STATUS)
            if (status == DclLauncherApiContract.STATUS_ERROR) return@runCatching null
            if (status != null && status != DclLauncherApiContract.STATUS_OK) {
                return@runCatching null
            }
            uiStateFromBundle(extras)
        }.getOrNull()
    }

    /** Reject empty / status-less poison pushes that would publish a full default home snapshot. */
    fun hasUsableUiStateMarkers(extras: Bundle): Boolean {
        if (extras.isEmpty) return false
        return extrasContains(extras, DclLauncherApiContract.KEY_STATUS) ||
            extrasContains(extras, DclLauncherApiContract.KEY_REVISION) ||
            extrasContains(extras, DclLauncherApiContract.KEY_SCREEN_MODE) ||
            extrasContains(extras, DclLauncherApiContract.KEY_FOREGROUND_PACKAGE)
    }

    fun parseLaunchResult(bundle: Bundle): DclLaunchResult =
        runCatching {
            val status = safeString(bundle, DclLauncherApiContract.KEY_STATUS)
                ?: DclLauncherApiContract.STATUS_ERROR
            DclLaunchResult(
                status = status,
                packageName = safeString(bundle, DclLauncherApiContract.EXTRA_PACKAGE_NAME),
                mode = safeString(bundle, DclLauncherApiContract.EXTRA_MODE),
                error = if (status == DclLauncherApiContract.STATUS_ERROR) {
                    parseError(bundle)
                } else {
                    null
                },
            )
        }.getOrElse {
            DclLaunchResult(
                status = DclLauncherApiContract.STATUS_ERROR,
                packageName = null,
                mode = null,
                error = DclApiError(
                    errorCode = DclLauncherApiContract.ERROR_INTERNAL,
                    message = "launch result parse failed: ${it.javaClass.simpleName}",
                ),
            )
        }

    fun parseError(bundle: Bundle): DclApiError =
        DclApiError(
            errorCode = safeString(bundle, DclLauncherApiContract.KEY_ERROR_CODE),
            message = safeString(bundle, DclLauncherApiContract.KEY_MESSAGE),
        )

    fun parseBounds(
        boundsStr: String?,
        fallback: DclBounds = DclBounds.DEFAULT_APP_AREA,
    ): DclBounds = DclBounds.parse(boundsStr, fallback)

    fun parseBoundsToRect(
        boundsStr: String?,
        fallback: DclBounds = DclBounds.DEFAULT_APP_AREA,
    ): Rect = parseBounds(boundsStr, fallback).toRect()

    private fun parseCapabilitiesUnsafe(bundle: Bundle): DclCallResult<DclCapabilities> {
        val status = safeString(bundle, DclLauncherApiContract.KEY_STATUS)
        if (status == DclLauncherApiContract.STATUS_ERROR) {
            return DclCallResult.Error(parseError(bundle))
        }
        if (status != null && status != DclLauncherApiContract.STATUS_OK) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = safeString(bundle, DclLauncherApiContract.KEY_ERROR_CODE),
                    message = safeString(bundle, DclLauncherApiContract.KEY_MESSAGE) ?: status,
                ),
            )
        }
        val screenModesRaw =
            safeString(bundle, DclLauncherApiContract.KEY_SCREEN_MODES).orEmpty()
        val uiStateQuery = when {
            extrasContains(bundle, DclLauncherApiContract.KEY_UI_STATE_QUERY) ->
                safeBoolean(bundle, DclLauncherApiContract.KEY_UI_STATE_QUERY, false)
            extrasContains(bundle, DclLauncherApiContract.KEY_UI_CHROME_QUERY) ->
                safeBoolean(bundle, DclLauncherApiContract.KEY_UI_CHROME_QUERY, false)
            else -> false
        }
        val uiStateEvents = when {
            extrasContains(bundle, DclLauncherApiContract.KEY_UI_STATE_EVENTS) ->
                safeBoolean(bundle, DclLauncherApiContract.KEY_UI_STATE_EVENTS, false)
            extrasContains(bundle, DclLauncherApiContract.KEY_UI_CHROME_EVENTS) ->
                safeBoolean(bundle, DclLauncherApiContract.KEY_UI_CHROME_EVENTS, false)
            else -> false
        }
        return DclCallResult.Ok(
            DclCapabilities(
                apiVersion = safeInt(bundle, DclLauncherApiContract.KEY_API_VERSION, 1),
                launchFullscreen = safeBoolean(
                    bundle,
                    DclLauncherApiContract.KEY_LAUNCH_FULLSCREEN,
                    false,
                ),
                launchAppArea = safeBoolean(
                    bundle,
                    DclLauncherApiContract.KEY_LAUNCH_APP_AREA,
                    false,
                ),
                launchFullWidth = if (
                    extrasContains(bundle, DclLauncherApiContract.KEY_LAUNCH_FULL_WIDTH)
                ) {
                    safeBoolean(bundle, DclLauncherApiContract.KEY_LAUNCH_FULL_WIDTH, false)
                } else {
                    null
                },
                uiStateQuery = uiStateQuery,
                uiStateEvents = uiStateEvents,
                adasPanel = safeBoolean(bundle, DclLauncherApiContract.KEY_ADAS_PANEL, false),
                screenModes = screenModesRaw.split(',')
                    .map { it.trim() }
                    .filter { it.isNotEmpty() },
                splitSupported = if (extrasContains(bundle, DclLauncherApiContract.KEY_SPLIT_SUPPORTED)) {
                    safeBoolean(bundle, DclLauncherApiContract.KEY_SPLIT_SUPPORTED, false)
                } else {
                    null
                },
            ),
        )
    }

    private fun parseUiStateUnsafe(bundle: Bundle): DclCallResult<DclUiState> {
        val status = safeString(bundle, DclLauncherApiContract.KEY_STATUS)
        if (status == DclLauncherApiContract.STATUS_ERROR) {
            return DclCallResult.Error(parseError(bundle))
        }
        if (status != null && status != DclLauncherApiContract.STATUS_OK) {
            return DclCallResult.Error(
                DclApiError(
                    errorCode = safeString(bundle, DclLauncherApiContract.KEY_ERROR_CODE),
                    message = safeString(bundle, DclLauncherApiContract.KEY_MESSAGE) ?: status,
                ),
            )
        }
        return DclCallResult.Ok(uiStateFromBundle(bundle))
    }

    private fun uiStateFromBundle(bundle: Bundle): DclUiState =
        DclUiState(
            screenMode = safeString(
                bundle,
                DclLauncherApiContract.KEY_SCREEN_MODE,
                DclLauncherApiContract.SCREEN_MODE_HOME,
            ) ?: DclLauncherApiContract.SCREEN_MODE_HOME,
            foregroundPackage = safeString(
                bundle,
                DclLauncherApiContract.KEY_FOREGROUND_PACKAGE,
                "",
            ) ?: "",
            leftPackage = safeString(bundle, DclLauncherApiContract.KEY_LEFT_PACKAGE, "") ?: "",
            rightPackage = safeString(bundle, DclLauncherApiContract.KEY_RIGHT_PACKAGE, "") ?: "",
            topBarHeightPx = safeInt(
                bundle,
                DclLauncherApiContract.KEY_TOP_BAR_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_TOP_BAR_HEIGHT_PX,
            ),
            bottomBarHeightPx = safeInt(
                bundle,
                DclLauncherApiContract.KEY_BOTTOM_BAR_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_BOTTOM_BAR_HEIGHT_PX,
            ),
            adasPanelBounds = safeString(
                bundle,
                DclLauncherApiContract.KEY_ADAS_PANEL_BOUNDS,
                DclLauncherApiContract.DEFAULT_ADAS_PANEL_BOUNDS,
            ) ?: DclLauncherApiContract.DEFAULT_ADAS_PANEL_BOUNDS,
            appAreaBounds = safeString(
                bundle,
                DclLauncherApiContract.KEY_APP_AREA_BOUNDS,
                DclLauncherApiContract.DEFAULT_APP_AREA_BOUNDS,
            ) ?: DclLauncherApiContract.DEFAULT_APP_AREA_BOUNDS,
            displayWidthPx = safeInt(
                bundle,
                DclLauncherApiContract.KEY_DISPLAY_WIDTH_PX,
                DclLauncherApiContract.DEFAULT_DISPLAY_WIDTH_PX,
            ),
            displayHeightPx = safeInt(
                bundle,
                DclLauncherApiContract.KEY_DISPLAY_HEIGHT_PX,
                DclLauncherApiContract.DEFAULT_DISPLAY_HEIGHT_PX,
            ),
            overlayOpen = safeBoolean(bundle, DclLauncherApiContract.KEY_OVERLAY_OPEN, false),
            revision = safeLong(bundle, DclLauncherApiContract.KEY_REVISION, 0L),
            updatedAtEpochMs = if (extrasContains(bundle, DclLauncherApiContract.KEY_UPDATED_AT_EPOCH_MS)) {
                safeLong(bundle, DclLauncherApiContract.KEY_UPDATED_AT_EPOCH_MS, 0L)
            } else {
                null
            },
            adasPanelVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_ADAS_PANEL_VISIBLE),
            topBarVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_TOP_BAR_VISIBLE),
            bottomBarVisible = optionalBoolean(bundle, DclLauncherApiContract.KEY_BOTTOM_BAR_VISIBLE),
            immersive = optionalBoolean(bundle, DclLauncherApiContract.KEY_IMMERSIVE),
            notificationShadeExpanded = optionalBoolean(
                bundle,
                DclLauncherApiContract.KEY_NOTIFICATION_SHADE_EXPANDED,
            ),
        )

    private fun optionalBoolean(bundle: Bundle, key: String): Boolean? =
        if (extrasContains(bundle, key)) safeBoolean(bundle, key, false) else null

    private fun extrasContains(bundle: Bundle, key: String): Boolean =
        runCatching { bundle.containsKey(key) }.getOrDefault(false)

    private fun safeString(bundle: Bundle, key: String, default: String? = null): String? =
        runCatching {
            when (val value = bundle.get(key)) {
                null -> default
                is String -> value
                else -> value.toString()
            }
        }.getOrDefault(default)

    private fun safeInt(bundle: Bundle, key: String, default: Int): Int =
        runCatching {
            when (val value = bundle.get(key)) {
                null -> default
                is Int -> value
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: default
                else -> default
            }
        }.getOrDefault(default)

    private fun safeLong(bundle: Bundle, key: String, default: Long): Long =
        runCatching {
            when (val value = bundle.get(key)) {
                null -> default
                is Long -> value
                is Int -> value.toLong()
                is Number -> value.toLong()
                is String -> value.toLongOrNull() ?: default
                else -> default
            }
        }.getOrDefault(default)

    private fun safeBoolean(bundle: Bundle, key: String, default: Boolean): Boolean =
        runCatching {
            when (val value = bundle.get(key)) {
                null -> default
                is Boolean -> value
                is Number -> value.toInt() != 0
                is String -> when (value.lowercase()) {
                    "true", "1", "yes" -> true
                    "false", "0", "no" -> false
                    else -> default
                }
                else -> default
            }
        }.getOrDefault(default)
}
