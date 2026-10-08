package vad.dashing.mqtt.bridge

/**
 * Drops retained commands, echoes of the last published state, a second command
 * while invoke is in flight, and more than one accepted command per second.
 */
class CommandGuard {
    private val lastAcceptedAt = HashMap<String, Long>()
    private val inFlight = HashSet<String>()

    fun decide(
        objectId: String,
        payload: String,
        retained: Boolean,
        lastPublished: String?,
        isButton: Boolean,
        nowMs: Long,
    ): String? {
        if (retained) return "retained"
        if (!isButton && lastPublished != null && payload == lastPublished) return "unchanged"
        if (objectId in inFlight) return "in_flight"
        val previous = lastAcceptedAt[objectId]
        if (previous != null && nowMs - previous < 1_000L) return "rate"
        lastAcceptedAt[objectId] = nowMs
        inFlight.add(objectId)
        return null
    }

    fun finish(objectId: String) {
        inFlight.remove(objectId)
    }
}
