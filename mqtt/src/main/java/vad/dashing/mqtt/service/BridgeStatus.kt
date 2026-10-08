package vad.dashing.mqtt.service

import kotlinx.coroutines.flow.MutableStateFlow

data class BridgeStatus(
    val monitorUp: Boolean = false,
    val brokerUp: Boolean = false,
    val availability: String = "offline",
    val publishedCount: Int = 0,
    val lastError: String = "",
)

object BridgeStatusStore {
    val state = MutableStateFlow(BridgeStatus())
}
