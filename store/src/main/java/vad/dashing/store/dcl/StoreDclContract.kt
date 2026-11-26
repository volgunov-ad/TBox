package vad.dashing.store.dcl

/**
 * Minimal Binder contract for DCL `launchApp` used by Dashing Store.
 *
 * Full Monitor client: `vad.dashing.tbox.dcl.DclLauncherApiContract`.
 * Vendor auth must allow [STORE_PACKAGE] in addition to Monitor — see
 * docs/DCL_LAUNCHER_API_FOR_VENDOR_RU.md.
 */
object StoreDclContract {
    const val LAUNCHER_PACKAGE = "ras.dashing.tbox.launcher"
    const val STORE_PACKAGE = "vad.dashing.store"
    const val AUTHORITY = "ras.dashing.tbox.launcher.api"
    const val URI = "content://$AUTHORITY"

    const val PERMISSION_CALL_LAUNCHER_API =
        "ras.dashing.tbox.launcher.permission.CALL_LAUNCHER_API"

    const val METHOD_LAUNCH_APP = "launchApp"

    const val EXTRA_PACKAGE_NAME = "packageName"
    const val EXTRA_MODE = "mode"
    const val EXTRA_REQUEST_ID = "requestId"

    const val MODE_APP_AREA = "app_area"

    const val KEY_STATUS = "status"
    const val KEY_ERROR_CODE = "errorCode"
    const val KEY_MESSAGE = "message"

    const val STATUS_OK = "ok"
    const val STATUS_ACCEPTED = "accepted"
    const val STATUS_ERROR = "error"

    const val ERROR_UNSUPPORTED = "unsupported"
    const val ERROR_INTERNAL = "internal"
    const val ERROR_FORBIDDEN = "forbidden"

    /** Per-Binder-call watchdog; same budget as Monitor [DclLauncherApiClient.CALL_TIMEOUT_MS]. */
    const val CALL_TIMEOUT_MS = 3_000L
}
