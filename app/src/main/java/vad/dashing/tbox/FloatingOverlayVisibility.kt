package vad.dashing.tbox

/**
 * Pure visibility / sync-policy helpers for floating overlays (unit-tested without WindowManager).
 */
internal object FloatingOverlayVisibility {

    /**
     * Whether a floating panel is allowed to be on screen by settings + usage-stats rules
     * (before temporary widget hide / overlayOff / permission checks).
     */
    fun shouldShowBySettingsAndUsageStats(
        panelId: String,
        enabled: Boolean,
        myPackageName: String,
        rules: UsageStatsOverlayRulesState,
    ): Boolean {
        if (rules.isUsageStatsForceHidden(panelId, myPackageName)) return false
        if (enabled) return true
        return rules.isUsageStatsForceShowing(panelId, myPackageName)
    }

    /** Temp widget hide or usage-stats hide — skip mount / close if already shown. */
    fun isTemporarilyHidden(
        panelId: String,
        myPackageName: String,
        hiddenFloatingPanelIds: Set<String>,
        rules: UsageStatsOverlayRulesState,
    ): Boolean =
        hiddenFloatingPanelIds.contains(panelId) ||
            rules.isUsageStatsForceHidden(panelId, myPackageName)

    /**
     * After «hide other floating panels» double-tap: reorder only when revealing panels again
     * (avoid remount flicker while hiding).
     */
    fun syncReorderZOrderAfterHideToggle(revealing: Boolean): Boolean = revealing
}
