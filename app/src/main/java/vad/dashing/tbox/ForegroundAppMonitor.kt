package vad.dashing.tbox

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Latest UsageStats foreground package after the 10 s event window and MainActivity filter.
 * Sticky across empty polls. Not the 2-tick panel debounce.
 */
object ForegroundAppMonitor {
    const val SAMPLE_WINDOW_MS = 10_000L
    const val POLL_MS = 1_000L

    private val _packageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = _packageName.asStateFlow()

    private val _automationWatching = MutableStateFlow(false)
    val automationWatching: StateFlow<Boolean> = _automationWatching.asStateFlow()

    fun setAutomationWatching(active: Boolean) {
        _automationWatching.value = active
    }

    fun publish(packageName: String?) {
        _packageName.value = packageName
    }

    fun clear() {
        _packageName.value = null
    }
}

object ForegroundAppSampling {
    fun nextSticky(
        previous: String?,
        sample: String?,
        ownPackage: String,
        mainInForeground: Boolean,
    ): String? {
        val filtered = filterOwnPackage(sample, ownPackage, mainInForeground)
        if (!filtered.isNullOrBlank()) return filtered
        return filterOwnPackage(previous, ownPackage, mainInForeground)
    }

    /** Overlay (AVM 360) wins over UsageStats — it never stays as a resumed Activity. */
    fun withOverlay(usagePackage: String?, overlayPackage: String?): String? {
        val overlay = overlayPackage?.trim()?.takeIf { it.isNotEmpty() }
        return overlay ?: usagePackage
    }

    fun filterOwnPackage(
        packageName: String?,
        ownPackage: String,
        mainInForeground: Boolean,
    ): String? {
        val pkg = packageName?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (pkg == ownPackage && !mainInForeground) return null
        return pkg
    }
}
