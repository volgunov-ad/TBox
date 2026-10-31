package vad.dashing.tbox.localaccess

/**
 * Same-device API access for TBox Monitor companion apps (TBox MQTT, VAD Voice).
 *
 * Monitor serves a ContentProvider and learns the real caller package from Binder, so the
 * user confirms a companion once on the head unit and never copies or pairs a token.
 * The signing key is public, so the caller certificate is pinned at consent time but is not
 * by itself a reason to trust a package.
 */
object LocalAccessContract {
    const val MONITOR_PACKAGE = "vad.dashing.tbox"
    const val AUTHORITY = "vad.dashing.tbox.localaccess"
    const val URI = "content://$AUTHORITY"

    /** Issues a fresh token to a confirmed caller, invalidating its previous one. */
    const val METHOD_GET_ACCESS = "getAccess"

    /** Server state without a token, for explaining why the HTTP API does not answer. */
    const val METHOD_GET_STATUS = "getStatus"

    /** `true` opens the consent dialog in Monitor when the caller is not confirmed yet. */
    const val EXTRA_INTERACTIVE = "interactive"

    const val KEY_STATUS = "status"
    const val KEY_ACCESS_TOKEN = "accessToken"
    const val KEY_PORT = "port"
    const val KEY_SERVER_ENABLED = "serverEnabled"

    const val STATUS_GRANTED = "granted"
    const val STATUS_OK = "ok"
    const val STATUS_PENDING = "pending"
    const val STATUS_CONSENT_REQUIRED = "consent_required"
    const val STATUS_DENIED = "denied"

    /** Monitor is installed but its background service is not running. */
    const val STATUS_UNAVAILABLE = "unavailable"
    const val STATUS_UNKNOWN_CLIENT = "unknown_client"

    /** Client side only: Monitor is missing or predates the provider. */
    const val STATUS_NOT_SUPPORTED = "not_supported"
}
