package vad.dashing.tbox

/**
 * P0 sticky-hide for floating panels under multi virtual-display.
 *
 * UsageStats collapses history to the chronologically last resume. When watched app A is
 * still painted on VD1 and app B resumes on VD2, the scalar FG becomes B and a naive hide
 * match would clear. This tracker keeps the hide reason until A is truly gone.
 *
 * **Enter / refresh:** debounced focused package (incl. OEM AVM overlay) ∈ hide-watch.
 * **Keep:** focused may switch to a non-watched package; hide still applies via sticky.
 * **Clear (first match):**
 * 1. Hide-watch empty or sticky package removed from watch.
 * 2. OEM overlay channel reports AVM gone while sticky was AVM (explicit leave).
 * 3. Focused package is another hide-watched package → transfer sticky to it.
 * 4. Sticky package's latest activity in the UsageStats window is backgrounded.
 * 5. Sticky package not re-sighted (no resume) for [safetyTtlMs] (ghost / force-stop).
 */
internal object UsageStatsStickyHide {
    /** Default safety TTL: longer than the 10 s sample window so idle dual-VD apps stay sticky. */
    const val SAFETY_TTL_MS = 60_000L

    data class State(
        val stickyHidePackage: String? = null,
        /** elapsedRealtime when sticky package was last entered / re-sighted as resumed. */
        val lastSeenElapsedMs: Long = 0L,
    ) {
        companion object {
            val EMPTY = State()
        }
    }

    enum class PackageActivitySignal {
        /** Latest FG/BG event for the package in the window is resume / move-to-foreground. */
        RESUMED,
        /** Latest FG/BG event is pause / move-to-background. */
        BACKGROUNDED,
        /** No FG/BG events for the package in the window. */
        ABSENT,
    }

    fun next(
        previous: State,
        focusedPackage: String?,
        hideWatch: Set<String>,
        stickyPackageActivity: PackageActivitySignal,
        nowElapsedMs: Long,
        safetyTtlMs: Long = SAFETY_TTL_MS,
        oemOverlayPackage: String? = null,
    ): State {
        if (hideWatch.isEmpty()) return State.EMPTY

        val focused = focusedPackage?.trim()?.takeIf { it.isNotEmpty() }
        val prevSticky = previous.stickyHidePackage?.trim()?.takeIf { it.isNotEmpty() }

        if (prevSticky != null && !usageStatsWatchContains(hideWatch, prevSticky)) {
            // Fall through: may immediately enter a new sticky from focused.
        } else if (
            prevSticky != null &&
            prevSticky.equals(OemOverlayAppMonitor.AVM_PACKAGE, ignoreCase = true) &&
            oemOverlayPackage.isNullOrBlank()
        ) {
            // OEM channel says AVM is gone — do not wait for UsageStats TTL.
            if (focused == null || !usageStatsWatchContains(hideWatch, focused)) {
                return State.EMPTY
            }
        }

        if (focused != null && usageStatsWatchContains(hideWatch, focused)) {
            return State(stickyHidePackage = focused.trim(), lastSeenElapsedMs = nowElapsedMs)
        }

        val sticky = prevSticky?.takeIf { usageStatsWatchContains(hideWatch, it) }
            ?: return State.EMPTY

        return when (stickyPackageActivity) {
            PackageActivitySignal.RESUMED ->
                State(stickyHidePackage = sticky, lastSeenElapsedMs = nowElapsedMs)
            PackageActivitySignal.BACKGROUNDED ->
                State.EMPTY
            PackageActivitySignal.ABSENT -> {
                val age = nowElapsedMs - previous.lastSeenElapsedMs
                if (age >= safetyTtlMs.coerceAtLeast(0L)) {
                    State.EMPTY
                } else {
                    State(stickyHidePackage = sticky, lastSeenElapsedMs = previous.lastSeenElapsedMs)
                }
            }
        }
    }
}
