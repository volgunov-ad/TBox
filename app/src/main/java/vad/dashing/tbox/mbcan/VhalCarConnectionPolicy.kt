package vad.dashing.tbox.mbcan

/**
 * Decides whether an existing A10 [CarPropertyBridge] session can be reused.
 *
 * After [android.content.ServiceConnection.onServiceDisconnected], the bridge object may still
 * exist and availability may still say Available — but the Car binder is dead. Reusing that
 * session leaves floating-panel widgets stuck on stale/null StateFlows with no push updates.
 */
internal object VhalCarConnectionPolicy {
    fun shouldReuseExistingBridge(
        bridgePresent: Boolean,
        serviceConnected: Boolean,
        availabilityAvailable: Boolean,
    ): Boolean = bridgePresent && serviceConnected && availabilityAvailable
}
