package vad.dashing.tbox.phoneble

/**
 * Central-side BLE recovery timers for [vad.dashing.tbox.phone.PhoneRadio].
 *
 * Android can freeze the process in doze with a hung LE scan, a connectGatt that
 * never reaches link-up, a zombie GATT that stays "connected", or a write that
 * never gets a callback. Kill+relaunch used to clear those; these thresholds
 * drive the same recovery without touching pairing material.
 */
object PhoneBleLinkPolicy {
    /** OS sometimes stops LE scan without [android.bluetooth.le.ScanCallback.onScanFailed]. */
    const val SCAN_STALE_MS: Long = 12_000L

    /** connectGatt can hang with a non-null handle and never reach link-up. */
    const val CONNECT_STALE_MS: Long = 15_000L

    /**
     * Linked but no sealed snapshot for this long: GATT is dead, USB/HU is down,
     * or a write pump is wedged. Phone refreshes about every 2 s, so this is
     * several missed cycles — not a short USB blip.
     */
    const val LINK_STALE_MS: Long = 12_000L

    /** writeCharacteristic accepted but onCharacteristicWrite never arrived. */
    const val WRITE_STALE_MS: Long = 8_000L

    fun hungScan(scanning: Boolean, hasGatt: Boolean, attemptAgeMs: Long, force: Boolean): Boolean =
        scanning && !hasGatt && (force || attemptAgeMs >= SCAN_STALE_MS)

    fun hungConnect(linkUp: Boolean, hasGatt: Boolean, attemptAgeMs: Long, force: Boolean): Boolean =
        !linkUp && hasGatt && (force || attemptAgeMs >= CONNECT_STALE_MS)

    /**
     * @param snapAgeMs elapsed since the last accepted sealed snap, or since link-up
     *   when no snap has arrived yet (pass link age in that case).
     */
    fun staleLinked(linkUp: Boolean, snapAgeMs: Long, force: Boolean): Boolean =
        linkUp && (force || snapAgeMs >= LINK_STALE_MS)

    fun hungWrite(writing: Boolean, writeAgeMs: Long, force: Boolean): Boolean =
        writing && (force || writeAgeMs >= WRITE_STALE_MS)

    /** Resume kick: skip only a fresh in-flight scan/connect; always reopen a stale link. */
    fun resumeShouldKick(
        linkUp: Boolean,
        scanningOrConnecting: Boolean,
        attemptAgeMs: Long,
        snapAgeMs: Long,
    ): Boolean {
        if (linkUp) return staleLinked(linkUp = true, snapAgeMs = snapAgeMs, force = false)
        if (scanningOrConnecting && attemptAgeMs < CONNECT_STALE_MS) return false
        return true
    }
}
