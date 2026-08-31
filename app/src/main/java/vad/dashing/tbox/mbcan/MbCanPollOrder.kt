package vad.dashing.tbox.mbcan

/**
 * Shared poll stays sequential on the JNI/VHAL apply thread.
 * Newly subscribed or currently visible signals go first so Car Settings
 * does not wait behind widgets or later sections.
 */
internal object MbCanPollOrder {
    fun <T> merge(active: Collection<T>, priority: Collection<T>): List<T> {
        if (priority.isEmpty()) return active.toList()
        val activeSet = active.toSet()
        val ordered = LinkedHashSet<T>()
        priority.forEach { item ->
            if (item in activeSet) ordered.add(item)
        }
        active.forEach { ordered.add(it) }
        return ordered.toList()
    }

    fun <T> prepend(priority: Collection<T>, existing: Collection<T>): LinkedHashSet<T> {
        val next = LinkedHashSet<T>()
        next.addAll(priority)
        next.addAll(existing)
        return next
    }
}
