package vad.dashing.tbox

import java.util.concurrent.atomic.AtomicLong

enum class WidgetButtonBindingTap {
    SINGLE,
    DOUBLE,
}

/**
 * Process-wide registry of on-screen tile bindings.
 * Compose panels register while the tile is visible; physical buttons dispatch here.
 */
object WidgetButtonBindingRegistry {
    data class Registration(
        val id: Long,
        val binding: WidgetButtonBinding,
        val onSingle: () -> Unit,
        val onDouble: () -> Unit,
    )

    private val nextId = AtomicLong(1L)
    private val lock = Any()
    private val entries = linkedMapOf<Long, Registration>()

    fun register(
        binding: WidgetButtonBinding,
        onSingle: () -> Unit,
        onDouble: () -> Unit,
    ): Long {
        val normalized = normalizeWidgetButtonBinding(binding) ?: return -1L
        val id = nextId.getAndIncrement()
        synchronized(lock) {
            entries[id] = Registration(
                id = id,
                binding = normalized,
                onSingle = onSingle,
                onDouble = onDouble,
            )
        }
        return id
    }

    fun unregister(id: Long) {
        if (id < 0L) return
        synchronized(lock) {
            entries.remove(id)
        }
    }

    fun dispatch(binding: WidgetButtonBinding, tap: WidgetButtonBindingTap) {
        val normalized = normalizeWidgetButtonBinding(binding) ?: return
        val targets = synchronized(lock) {
            entries.values.filter { it.binding == normalized }
        }
        for (target in targets) {
            try {
                when (tap) {
                    WidgetButtonBindingTap.SINGLE -> target.onSingle()
                    WidgetButtonBindingTap.DOUBLE -> target.onDouble()
                }
            } catch (e: Exception) {
                TboxRepository.addLog(
                    "ERROR",
                    "WidgetBtnBind",
                    "Tile tap dispatch failed ($normalized): ${e.message}",
                )
            }
        }
    }

    /** Test helper. */
    internal fun clearForTests() {
        synchronized(lock) {
            entries.clear()
        }
    }

    /** Test helper. */
    internal fun sizeForTests(): Int = synchronized(lock) { entries.size }
}
