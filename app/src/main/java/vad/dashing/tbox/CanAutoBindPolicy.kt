package vad.dashing.tbox

/**
 * When the head-unit CAN backend may still be probed (A9 mbCAN vs A10 VHAL).
 *
 * A successful bind or a manual settings choice pins that mode. Later startups
 * retry it only and do not switch to the other stack. The pin follows the mode
 * named in [lastResult], because a failed probe can persist the other mode
 * before that attempt actually connects.
 */
internal object CanAutoBindPolicy {
    const val USER_RESULT_PREFIX = "user:"
    const val PINNED_OK_PREFIX = "pinned_ok:"
    const val PINNED_UNAVAILABLE_PREFIX = "pinned_unavailable:"
    /** Neither stack connected on that start. Older builds also set the lock with this result. */
    const val LOCKED_AFTER_FAIL_PREFIX = "locked_after_fail:"

    fun decide(
        enabled: Boolean,
        locked: Boolean,
        lastResult: String,
        current: HeadUnitCanMode,
    ): Decision {
        if (!enabled) return Decision(Startup.Disabled, current)
        val remembered = modeFromSuccessfulResult(lastResult)
        if (remembered != null) return Decision(Startup.Pinned, remembered)
        if (locked && !lastResult.startsWith(LOCKED_AFTER_FAIL_PREFIX)) {
            return Decision(Startup.Pinned, current)
        }
        return Decision(Startup.ProbeWithFallback, current)
    }

    fun modeFromSuccessfulResult(lastResult: String): HeadUnitCanMode? {
        val prefix = SUCCESS_PREFIXES.firstOrNull { lastResult.startsWith(it) } ?: return null
        val modeValue = lastResult.removePrefix(prefix).substringBefore(':')
        return when (modeValue) {
            HeadUnitCanMode.Android9MbCan.storageValue -> HeadUnitCanMode.Android9MbCan
            HeadUnitCanMode.Android10Vhal.storageValue -> HeadUnitCanMode.Android10Vhal
            else -> null
        }
    }

    enum class Startup {
        Disabled,
        Pinned,
        ProbeWithFallback,
    }

    data class Decision(
        val startup: Startup,
        val mode: HeadUnitCanMode,
    )

    private val SUCCESS_PREFIXES = listOf(
        "primary_ok:",
        "alternative_ok:",
        PINNED_OK_PREFIX,
        USER_RESULT_PREFIX,
    )
}
