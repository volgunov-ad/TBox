package vad.dashing.tbox

/**
 * Staged composition of main-screen panels on one page so a swipe onto a heavy page
 * does not mount every panel (and their HVAC / music / external widgets) in one frame.
 *
 * All panels still appear automatically — no extra user action — with short pauses between mounts.
 */
internal object MainScreenPagePanelMountPlan {

    /** When at least this many panels are on the page, mount them in HU-sized steps. */
    const val STAGED_MOUNT_MIN_PANEL_COUNT = 5

    /** Pause between staged mount batches. */
    const val STAGED_MOUNT_DELAY_MS = 80L

    /**
     * Delay after a panel enters composition before media / mbCAN interest registration.
     * Lets the first layout paint before heavy push subscriptions.
     */
    const val HEAVY_SUBSCRIPTIONS_DELAY_MS = 120L

    fun shouldUseStagedMount(panelCount: Int): Boolean =
        panelCount >= STAGED_MOUNT_MIN_PANEL_COUNT

    fun stagedMountStepSize(headUnitMode: HeadUnitCanMode): Int =
        StagedUiPanelMount.stepSize(headUnitMode)

    /**
     * Next [mountedCount] after one staged step. Last remainder may be smaller than [stepSize].
     */
    fun nextMountedCount(currentMounted: Int, panelCount: Int, stepSize: Int): Int {
        if (panelCount <= 0) return 0
        val step = stepSize.coerceAtLeast(1)
        return (currentMounted + step).coerceAtMost(panelCount)
    }

    /**
     * How many panels from the start of [orderedPanels] should be composed for [mountedCount].
     * [mountedCount] is 0…size; callers grow it over time.
     */
    fun visiblePrefixCount(panelCount: Int, mountedCount: Int): Int {
        if (panelCount <= 0) return 0
        if (!shouldUseStagedMount(panelCount)) return panelCount
        return mountedCount.coerceIn(0, panelCount)
    }
}
