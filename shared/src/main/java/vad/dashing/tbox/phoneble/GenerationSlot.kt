package vad.dashing.tbox.phoneble

import java.util.concurrent.atomic.AtomicReference

/**
 * One value owned by a worker generation. An older worker cannot publish over a newer one,
 * which is how a stopped RFCOMM attempt used to replace the live socket.
 */
class GenerationSlot<T> {
    class Holder<T>(val generation: Int, val value: T)

    private val ref = AtomicReference<Holder<T>?>(null)

    fun current(activeGeneration: Int): T? {
        val holder = ref.get() ?: return null
        return if (holder.generation == activeGeneration) holder.value else null
    }

    /**
     * Publishes [value] for [generation] while [activeGeneration] still returns [generation]
     * and no newer holder is stored. The rejected value, and any older holder that was
     * replaced, is passed to [close].
     */
    fun publish(
        generation: Int,
        activeGeneration: () -> Int,
        value: T,
        close: (T) -> Unit,
    ): Boolean {
        if (generation != activeGeneration()) {
            close(value)
            return false
        }
        val mine = Holder(generation, value)
        while (true) {
            val current = ref.get()
            if (current != null && current.generation > generation) {
                close(value)
                return false
            }
            if (!ref.compareAndSet(current, mine)) continue
            if (generation != activeGeneration()) {
                ref.compareAndSet(mine, current)
                close(value)
                return false
            }
            if (current != null && current.value !== value) close(current.value)
            return true
        }
    }

    /** Closes the stored value when it is not newer than [activeGeneration]. */
    fun closeUpTo(activeGeneration: Int, close: (T) -> Unit) {
        val current = ref.get() ?: return
        if (current.generation <= activeGeneration) close(current.value)
    }
}
