package vad.dashing.tbox.location

/**
 * Last CONSTANT (Advanced) tick diagnostics for geo-debug log.
 * Updated by [MockLocationJob]; read by [GeoDebugLogRecorder].
 */
object ConstantDrRuntimeDebug {
    data class Snapshot(
        val active: Boolean = false,
        val shadowDistM: Double? = null,
        val thresholdM: Double? = null,
        val posW: Float? = null,
        val constantHasOrigin: Boolean = false,
        val blendLive: Boolean = false,
        val hardResync: Boolean = false,
        /** F3 map draft: user snapped shadow (not GNSS hard-resync). */
        val manualSeed: Boolean = false,
        val accuracyM: Float? = null,
    )

    @Volatile
    var snapshot: Snapshot = Snapshot()
        private set

    fun publish(snapshot: Snapshot) {
        this.snapshot = snapshot
    }

    fun clear() {
        snapshot = Snapshot()
    }
}
