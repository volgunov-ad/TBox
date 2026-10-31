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

    /** Topics to clear. The remembered set changes only in [commit], after the clears succeed. */
    fun plan(next: Set<String>): List<String> = (published - next).sorted()

    fun commit(next: Set<String>) {
        published.clear()
        published.addAll(next)
    }
}
