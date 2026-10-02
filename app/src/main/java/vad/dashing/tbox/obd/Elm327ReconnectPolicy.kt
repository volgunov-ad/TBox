package vad.dashing.tbox.obd

/**
 * How hard to poke classic Bluetooth while ELM327 is missing.
 *
 * A link that just dropped is retried quickly. A dongle that has not answered
 * (or that stayed gone after the short ladder) gets one cheap attempt and a
 * long pause, so other HU Bluetooth devices are not paged every half minute.
 */
internal object Elm327ReconnectPolicy {
    val LINKED_BACKOFF_MS = longArrayOf(3_000L, 10_000L, 30_000L)
    val ABSENT_BACKOFF_MS = longArrayOf(30_000L, 120_000L, 300_000L, 600_000L)

    const val LINKED_RFCOMM_ATTEMPTS = 3
    const val ABSENT_RFCOMM_ATTEMPTS = 1
    const val LINKED_CONNECT_TIMEOUT_MS = 15_000L
    const val ABSENT_CONNECT_TIMEOUT_MS = 8_000L

    /** Background createBond while we still expect the dongle (it was linked). */
    const val LINKED_BOND_RETRY_MS = 60_000L

    /** Background createBond while the dongle has not been on this link. */
    const val ABSENT_BOND_RETRY_MS = 300_000L

    /** One PIN, absent device: do not hold the controller in pairing for half a minute. */
    const val ABSENT_BOND_TIMEOUT_MS = 12_000L

    const val FULL_BOND_TIMEOUT_MS = 35_000L

    /**
     * [BluetoothAdapter.isEnabled] can flicker while we page a missing device.
     * Wait this long and recheck before [BluetoothAdapter.enable], which drops every ACL.
     */
    const val BT_OFF_SETTLE_MS = 2_500L

    private val DEFAULT_PINS = listOf("1234", "0000", "6789", "8888")

    fun backoffMs(hadSuccessfulLink: Boolean, failureIndex: Int): Long {
        val index = failureIndex.coerceAtLeast(0)
        return if (aggressiveReconnect(hadSuccessfulLink, index)) {
            LINKED_BACKOFF_MS[index.coerceAtMost(LINKED_BACKOFF_MS.lastIndex)]
        } else {
            val absentIndex = if (hadSuccessfulLink) {
                index - LINKED_BACKOFF_MS.size
            } else {
                index
            }
            ABSENT_BACKOFF_MS[absentIndex.coerceIn(0, ABSENT_BACKOFF_MS.lastIndex)]
        }
    }

    /** Full RFCOMM fallback only while the short ladder still applies. */
    fun aggressiveReconnect(hadSuccessfulLink: Boolean, failureIndex: Int): Boolean =
        hadSuccessfulLink && failureIndex.coerceAtLeast(0) < LINKED_BACKOFF_MS.size

    fun rfcommAttemptCount(hadSuccessfulLink: Boolean, failureIndex: Int): Int =
        if (aggressiveReconnect(hadSuccessfulLink, failureIndex)) {
            LINKED_RFCOMM_ATTEMPTS
        } else {
            ABSENT_RFCOMM_ATTEMPTS
        }

    fun connectTimeoutMs(hadSuccessfulLink: Boolean, failureIndex: Int): Long =
        if (aggressiveReconnect(hadSuccessfulLink, failureIndex)) {
            LINKED_CONNECT_TIMEOUT_MS
        } else {
            ABSENT_CONNECT_TIMEOUT_MS
        }

    fun bondRetryMs(hadSuccessfulLink: Boolean, failureIndex: Int): Long =
        if (aggressiveReconnect(hadSuccessfulLink, failureIndex)) {
            LINKED_BOND_RETRY_MS
        } else {
            ABSENT_BOND_RETRY_MS
        }

    fun bondTimeoutMs(hadSuccessfulLink: Boolean, failureIndex: Int): Long =
        if (aggressiveReconnect(hadSuccessfulLink, failureIndex)) {
            FULL_BOND_TIMEOUT_MS
        } else {
            ABSENT_BOND_TIMEOUT_MS
        }

    /**
     * One PIN per background attempt. Saved PIN first, then the usual ELM defaults,
     * rotating with [attemptIndex].
     */
    fun pinForAttempt(preferredPin: String, attemptIndex: Int): String {
        val preferred = preferredPin.trim()
        val pins = buildList {
            if (preferred.isNotEmpty()) add(preferred)
            DEFAULT_PINS.filterTo(this) { it != preferred }
        }
        return pins[attemptIndex.coerceAtLeast(0) % pins.size]
    }
}
