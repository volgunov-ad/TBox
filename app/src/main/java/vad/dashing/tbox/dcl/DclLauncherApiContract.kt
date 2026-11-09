package vad.dashing.tbox.dcl

/**
 * Binder contract for the DCL Launcher ContentProvider that TBox Monitor calls.
 *
 * Authority lives in `ras.dashing.tbox.launcher`. Auth is Binder UID/package only —
 * DCL does not issue a token and does not show a second consent UI.
 *
 * Source of truth for field names: docs/DCL_LAUNCHER_API_INTEGRATION_FOR_TBM_RU.md
 * (vendor gist) and docs/DCL_LAUNCHER_API_FOR_VENDOR_RU.md.
 */
object DclLauncherApiContract {
    const val LAUNCHER_PACKAGE = "ras.dashing.tbox.launcher"
    const val AUTHORITY = "ras.dashing.tbox.launcher.api"
    const val URI = "content://$AUTHORITY"

    /** Optional custom permission declared by DCL; user-builds may skip enforcement. */
    const val PERMISSION_CALL_LAUNCHER_API =
        "ras.dashing.tbox.launcher.permission.CALL_LAUNCHER_API"

    const val METHOD_GET_CAPABILITIES = "getCapabilities"
    const val METHOD_GET_UI_STATE = "getUiState"
    /** Alias of [METHOD_GET_UI_STATE] with the same Bundle shape. */
    const val METHOD_GET_UI_CHROME = "getUiChrome"
    const val METHOD_LAUNCH_APP = "launchApp"

    const val ACTION_UI_STATE_CHANGED = "ras.dashing.tbox.launcher.action.UI_STATE_CHANGED"
    /** Alias of [ACTION_UI_STATE_CHANGED] with the same extras. */
    const val ACTION_UI_CHROME_CHANGED = "ras.dashing.tbox.launcher.action.UI_CHROME_CHANGED"

    const val KEY_STATUS = "status"
    const val KEY_API_VERSION = "apiVersion"
    const val KEY_LAUNCH_FULLSCREEN = "launchFullscreen"
    const val KEY_LAUNCH_APP_AREA = "launchAppArea"
    const val KEY_UI_STATE_QUERY = "uiStateQuery"
    const val KEY_UI_STATE_EVENTS = "uiStateEvents"
    const val KEY_UI_CHROME_QUERY = "uiChromeQuery"
    const val KEY_UI_CHROME_EVENTS = "uiChromeEvents"
    const val KEY_ADAS_PANEL = "adasPanel"
    const val KEY_SCREEN_MODES = "screenModes"
    const val KEY_SPLIT_SUPPORTED = "splitSupported"

    const val KEY_SCREEN_MODE = "screenMode"
    const val KEY_FOREGROUND_PACKAGE = "foregroundPackage"
    const val KEY_LEFT_PACKAGE = "leftPackage"
    const val KEY_RIGHT_PACKAGE = "rightPackage"
    const val KEY_TOP_BAR_HEIGHT_PX = "topBarHeightPx"
    const val KEY_BOTTOM_BAR_HEIGHT_PX = "bottomBarHeightPx"
    const val KEY_ADAS_PANEL_BOUNDS = "adasPanelBounds"
    const val KEY_APP_AREA_BOUNDS = "appAreaBounds"
    const val KEY_DISPLAY_WIDTH_PX = "displayWidthPx"
    const val KEY_DISPLAY_HEIGHT_PX = "displayHeightPx"
    const val KEY_OVERLAY_OPEN = "overlayOpen"
    const val KEY_REVISION = "revision"
    const val KEY_UPDATED_AT_EPOCH_MS = "updatedAtEpochMs"
    const val KEY_ADAS_PANEL_VISIBLE = "adasPanelVisible"
    const val KEY_TOP_BAR_VISIBLE = "topBarVisible"
    const val KEY_BOTTOM_BAR_VISIBLE = "bottomBarVisible"
    const val KEY_IMMERSIVE = "immersive"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_MESSAGE = "message"

    const val EXTRA_PACKAGE_NAME = "packageName"
    const val EXTRA_MODE = "mode"
    const val EXTRA_ACTIVITY_CLASS = "activityClass"
    const val EXTRA_REQUEST_ID = "requestId"
    const val EXTRA_ACTION = "action"
    const val EXTRA_DISPLAY_ID = "displayId"

    const val MODE_APP_AREA = "app_area"
    const val MODE_FULLSCREEN = "fullscreen"

    const val SCREEN_MODE_HOME = "home"
    const val SCREEN_MODE_SINGLE = "single"
    const val SCREEN_MODE_FULLSCREEN = "fullscreen"
    const val SCREEN_MODE_SPLIT = "split"

    const val STATUS_OK = "ok"
    const val STATUS_ACCEPTED = "accepted"
    const val STATUS_ERROR = "error"

    const val ERROR_FORBIDDEN = "forbidden"
    const val ERROR_INVALID_ARGS = "invalid_args"
    const val ERROR_UNSUPPORTED = "unsupported"
    const val ERROR_INTERNAL = "internal"
    const val ERROR_NOT_INSTALLED = "not_installed"
    const val ERROR_NO_LAUNCH_ACTIVITY = "no_launch_activity"
    const val ERROR_BUSY = "busy"

    /** Client-side only: provider missing / launcher not installed / call threw. */
    const val STATUS_UNAVAILABLE = "unavailable"

    const val DEFAULT_TOP_BAR_HEIGHT_PX = 51
    const val DEFAULT_BOTTOM_BAR_HEIGHT_PX = 82
    const val DEFAULT_DISPLAY_WIDTH_PX = 1920
    const val DEFAULT_DISPLAY_HEIGHT_PX = 1080
    const val DEFAULT_ADAS_PANEL_BOUNDS = "0,51,344,998"
    const val DEFAULT_APP_AREA_BOUNDS = "344,51,1920,998"
}
