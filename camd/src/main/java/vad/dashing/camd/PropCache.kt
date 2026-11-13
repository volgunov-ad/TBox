package vad.dashing.camd

/**
 * Tiny TTL cache for getprop values that are read on every status request.
 * Callers that wait for a property change read through with [invalidate].
 */
internal class PropCache(
    private val ttlMs: Long = 2_000,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val values = HashMap<String, Pair<Long, String>>()

    @Synchronized
    fun get(name: String, loader: (String) -> String): String {
        val now = clock()
        val hit = values[name]
        if (hit != null && now - hit.first < ttlMs) return hit.second
        val value = loader(name)
        values[name] = now to value
        return value
    }

    @Synchronized
    fun invalidate() {
        values.clear()
    }
}
