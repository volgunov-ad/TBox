package vad.dashing.tbox.um980fw

/**
 * Exclusive byte pipe to UM980 UART for firmware update (USB session or ESP bridge).
 */
interface Um980BinaryTransport {
    /** Current host-side baud (USB line coding / ESP↔UM980). */
    fun currentBaud(): Int

    fun setBaud(baud: Int): Boolean

    /**
     * Apply host baud for firmware upgrade. Default = [setBaud].
     * USB adapters should close/reopen so vendor baud init actually takes effect
     * (setBaudLive alone is unreliable at 460800 during exclusive IO).
     */
    fun reopenAtBaud(baud: Int): Boolean = setBaud(baud)

    fun write(bytes: ByteArray): Boolean

    /**
     * Read available bytes up to [maxBytes], waiting up to [timeoutMs].
     * Empty array on timeout.
     */
    fun read(maxBytes: Int, timeoutMs: Long): ByteArray

    fun beginExclusive()

    fun endExclusive()
}

enum class Um980FwResetMode {
    SOFT,
    HARD,
}
