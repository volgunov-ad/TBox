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
    val lastPublishAtMs: Long = 0L,
    /** Newest first. */
    val events: List<LinkEvent> = emptyList(),
)

data class LinkEvent(
    val atMs: Long,
    val link: String,
    val up: Boolean,
    val detail: String = "",
)

/** Up/down changes of the links since the service started, newest first. */
class LinkJournal(private val capacity: Int = 10) {
    private val events = ArrayDeque<LinkEvent>()

    fun add(event: LinkEvent) {
        events.addFirst(event)
        while (events.size > capacity) events.removeLast()
    }

    fun snapshot(): List<LinkEvent> = events.toList()
}

object BridgeStatusStore {
    val state = MutableStateFlow(BridgeStatus())
}
