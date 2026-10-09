package vad.dashing.mqtt.service

import kotlinx.coroutines.flow.MutableStateFlow

data class BridgeStatus(
    val monitorUp: Boolean = false,
    val brokerUp: Boolean = false,
    val availability: String = "offline",
    val publishedCount: Int = 0,
    val lastError: String = "",
    val monitorError: String = "",
    val brokerError: String = "",
    val wireguardEnabled: Boolean = false,
    val tunnelError: String = "",
    /** Wall clock of the last up/down change of the broker link; 0 until the first change. */
    val brokerSinceMs: Long = 0L,
    val monitorSinceMs: Long = 0L,
)

object BridgeStatusStore {
    val state = MutableStateFlow(BridgeStatus())
}
