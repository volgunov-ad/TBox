package vad.dashing.tbox.externalapi

object ExternalApiConstants {
    const val API_VERSION = 1
    const val CATALOG_VERSION = 4

    const val DEFAULT_PORT = 8765
    const val MIN_PORT = 1024
    const val MAX_PORT = 65535

    const val PAIRING_TIMEOUT_MS = 120_000L

    /** Idle tokens older than this many calendar months are removed after the API starts. */
    const val TOKEN_IDLE_MONTHS = 6

    /** Sweep runs once the server has been up long enough for an early request to refresh usage. */
    const val TOKEN_EXPIRY_CHECK_DELAY_MS = 60_000L

    /** Successful calls update memory immediately; disk is rewritten at this interval. */
    const val TOKEN_USAGE_PERSIST_INTERVAL_MS = 60L * 60L * 1000L

    const val PATH_HEALTH = "/v1/health"
    const val PATH_PAIR_REQUEST = "/v1/pair/request"
    const val PATH_PAIR_STATUS = "/v1/pair/status"
    const val PATH_CATALOG = "/v1/catalog"
    const val PATH_SIGNALS = "/v1/signals"
    const val PATH_ACTIONS_INVOKE = "/v1/actions/invoke"
    const val PATH_AUTOMATIONS = "/v1/automations"
    const val PATH_AUTOMATIONS_RUN_PREFIX = "/v1/automations/"
    const val PATH_WEB_PANEL = "/"
    const val PATH_WEB_PANEL_ALIAS = "/panel"

    const val MAX_ACTIONS_PER_REQUEST = 20
    const val MAX_SIGNAL_IDS_PER_REQUEST = 50
}
