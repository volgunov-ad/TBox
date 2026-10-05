package vad.dashing.tbox.mbcan

import java.lang.reflect.InvocationTargetException

/**
 * Reflection hides the Car/VHAL failure inside [InvocationTargetException], whose own
 * [Throwable.message] is usually null. Walk [InvocationTargetException.targetException]
 * and [Throwable.cause] so the journal shows the real type and message.
 */
internal object VhalThrowableDetail {
    fun describe(error: Throwable): String {
        val parts = ArrayList<String>(4)
        var current: Throwable? = error
        var guard = 0
        while (current != null && guard++ < MAX_CHAIN) {
            val message = current.message?.takeIf { it.isNotBlank() } ?: "-"
            parts += "${current.javaClass.simpleName}: $message"
            val next = next(current)
            if (next == null || next === current) break
            current = next
        }
        return parts.joinToString(" <- ")
    }

    private fun next(error: Throwable): Throwable? {
        val target = (error as? InvocationTargetException)?.targetException
        if (target != null && target !== error) return target
        val cause = error.cause
        if (cause != null && cause !== error) return cause
        return null
    }

    private const val MAX_CHAIN = 6
}
