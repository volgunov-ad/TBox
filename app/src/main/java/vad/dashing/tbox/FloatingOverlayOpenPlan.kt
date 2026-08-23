package vad.dashing.tbox

/**
 * Plans staggered floating-overlay opens so many panels (e.g. after a `.tboxtheme` import)
 * do not mount in one main-thread burst on low-RAM head units.
 *
 * All [pending] panels are opened automatically — no extra user action — with short pauses
 * between adds. Order follows dashboard config (bottom → top within overlapping clusters).
 */
internal object FloatingOverlayOpenPlan {

    /** When at least this many overlays need mounting, use staged open instead of one burst. */
    const val STAGED_OPEN_MIN_PENDING_COUNT = 5

    /** Pause between staged [WindowManager.addView] batches. */
    const val STAGED_OPEN_DELAY_MS = 100L

    fun shouldUseStagedOpen(pendingCount: Int): Boolean =
        pendingCount >= STAGED_OPEN_MIN_PENDING_COUNT

    fun stagedOpenStepSize(headUnitMode: HeadUnitCanMode): Int =
        StagedUiPanelMount.stepSize(headUnitMode)

    /** Pending overlays grouped by HU step size (A9: pairs, A10: singles). */
    fun pendingOpenBatches(
        pending: List<FloatingDashboardConfig>,
        stepSize: Int,
    ): List<List<FloatingDashboardConfig>> =
        pending.chunked(stepSize.coerceAtLeast(1))

    /**
     * Expanded bounds from saved layout (collapse ignored — same as pre-collapse planning).
     */
    fun expandedBoundsFromConfig(config: FloatingDashboardConfig): PanelPxBounds =
        PanelPxBounds(
            x = config.startX.coerceAtLeast(0),
            y = config.startY.coerceAtLeast(0),
            width = config.width.coerceAtLeast(1),
            height = config.height.coerceAtLeast(1),
        )

    /**
     * Panels that should mount, in dashboard config order (stable z-order when opened sequentially).
     */
    fun pendingOpensInConfigOrder(
        visibleConfigs: List<FloatingDashboardConfig>,
        alreadyMountedIds: Set<String>,
        shouldOpen: (FloatingDashboardConfig) -> Boolean,
    ): List<FloatingDashboardConfig> =
        visibleConfigs.filter { cfg ->
            cfg.id !in alreadyMountedIds && shouldOpen(cfg)
        }

    /**
     * Bounds map for every visible panel (used for final z-order remount check).
     */
    fun boundsByIdForVisible(visibleConfigs: List<FloatingDashboardConfig>): Map<String, PanelPxBounds> =
        visibleConfigs.associate { cfg -> cfg.id to expandedBoundsFromConfig(cfg) }
}
