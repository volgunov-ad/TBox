package vad.dashing.tbox.phone

/**
 * Which transports [PhoneLinkRouter] should have running. Pure so the AUTO rules can be
 * tested without Bluetooth.
 */
data class TransportPlan(
    val direct: Boolean,
    val radio: Boolean,
    val searchSince: Long,
)

fun transportPlan(
    path: PhonePath,
    foreground: Boolean,
    directConfirmed: Boolean,
    radioConfirmed: Boolean,
    keepDirectForTelemetry: Boolean,
    radioOn: Boolean,
    searchSince: Long,
    now: Long,
    passesWithoutLink: Int,
    directFirstMs: Long = DIRECT_FIRST_MS,
): TransportPlan = when (path) {
    PhonePath.DIRECT -> TransportPlan(direct = true, radio = false, searchSince = searchSince)
    PhonePath.COMPANION -> TransportPlan(direct = false, radio = true, searchSince = searchSince)
    PhonePath.AUTO -> when {
        directConfirmed -> TransportPlan(direct = true, radio = false, searchSince = 0L)
        radioConfirmed -> TransportPlan(
            direct = keepDirectForTelemetry,
            radio = foreground,
            searchSince = 0L,
        )
        else -> {
            val since = if (searchSince == 0L) now else searchSince
            val radio = foreground &&
                (radioOn || now - since >= directFirstMs || passesWithoutLink > 0)
            TransportPlan(direct = true, radio = radio, searchSince = since)
        }
    }
}

const val DIRECT_FIRST_MS: Long = 8_000L

/**
 * Settings is the only way forward after a permanent denial. The first result of a fresh
 * launch is not that: Android 11+ reports no rationale when the dialog is dismissed, and
 * that used to stick the button on the settings screen.
 * Blocked when rationale was offered before the request and is gone after it, or after
 * the second denial in this session.
 */
fun markBluetoothPermissionBlocked(
    granted: Boolean,
    showedRationaleBefore: Boolean,
    showsRationaleAfter: Boolean,
    denialsThisSession: Int,
): Boolean {
    if (granted) return false
    if (showedRationaleBefore && !showsRationaleAfter) return true
    return denialsThisSession >= 2
}
