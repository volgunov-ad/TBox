package vad.dashing.mqtt.bridge

/**
 * Holds back a fast numeric value until [intervalMs] has passed since the last publish.
 * The caller keeps the fresh sample, so the next poll sends the latest number, not an intermediate one.
 */
class FastPublishGate {
    private val lastSentAtMs = HashMap<String, Long>()

    fun allow(objectId: String, nowMs: Long, intervalMs: Long): Boolean {
        if (intervalMs <= 0L) return true
        val last = lastSentAtMs[objectId]
        if (last != null && nowMs - last < intervalMs) return false
        lastSentAtMs[objectId] = nowMs
        return true
    }

    fun retain(objectIds: Set<String>) {
        lastSentAtMs.keys.retainAll(objectIds)
    }
}
