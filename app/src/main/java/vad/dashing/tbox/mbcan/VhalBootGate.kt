package vad.dashing.tbox.mbcan

/**
 * Notification Listener can start [vad.dashing.tbox.BackgroundService] before
 * `sys.boot_completed`. A9 mbCAN does not need Car service; A10 VHAL does.
 * Defer the first connect only while that property is present and not yet "1".
 * A missing/unreadable property must not add a delay to an already-running head unit.
 */
internal object VhalBootGate {
    const val PROPERTY = "sys.boot_completed"
    const val MAX_WAIT_MS = 25_000L
    const val POLL_MS = 500L

    fun shouldDefer(bootCompletedRaw: String?, gateAlreadyPassed: Boolean): Boolean {
        if (gateAlreadyPassed) return false
        if (bootCompletedRaw == null) return false
        return bootCompletedRaw != "1"
    }

    fun readBootCompleted(): String? {
        return runCatching {
            val clazz = Class.forName("android.os.SystemProperties")
            val get = clazz.getMethod("get", String::class.java, String::class.java)
            get.invoke(null, PROPERTY, "") as? String
        }.getOrNull()
    }
}
