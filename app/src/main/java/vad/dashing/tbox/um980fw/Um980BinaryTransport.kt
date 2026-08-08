package vad.dashing.tbox.um980fw

/**
 * Exclusive byte pipe to UM980 UART for firmware update (USB session or ESP bridge).
 */
interface Um980BinaryTransport {
    /** Current host-side baud (USB line coding / ESP↔UM980). */
    fun currentBaud(): Int

    fun setBaud(baud: Int): Boolean

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
