package vad.dashing.mqtt.ha

/**
 * Retained discovery configs this client published. Anything that drops out of the next
 * set is cleared with an empty retained payload so Home Assistant drops the orphan.
 */
class DiscoveryCleanup(
    initial: Set<String> = emptySet(),
) {
    private val published = initial.toMutableSet()

    fun publishedTopics(): Set<String> = published.toSet()

    /**
     * Topics to clear. [keepObjectIds] are selected ids missing from the catalog for less
     * than the grace window: their retained configs stay. The remembered set changes only
     * in [commit], after the clears succeed.
     */
    fun plan(next: Set<String>, keepObjectIds: Set<String> = emptySet()): List<String> =
        (published - next)
            .filter { topic ->
                val id = discoveryObjectId(topic)
                id == null || id !in keepObjectIds
            }
            .sorted()

    /** Previously published topics that were not cleared, plus configs that were published now. */
    fun remembered(confirmed: Set<String>, cleared: Set<String>): Set<String> =
        (published - cleared) + confirmed

    fun commit(next: Set<String>) {
        published.clear()
        published.addAll(next)
    }
}
