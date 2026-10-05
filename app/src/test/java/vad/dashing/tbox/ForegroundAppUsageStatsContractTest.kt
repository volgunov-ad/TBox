package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Investigation pins for the shared UsageStats / foreground-app sampler that automations
 * (#276 / de6f52e2) made 1 Hz and 10 s wide. Production code is not changed here.
 *
 * Compared with the pre-automation helper: poll was 3 s and queryEvents lookback was 5 min
 * (plus queryUsageStats fallback). Current design is more frequent but much narrower.
 */
class ForegroundAppUsageStatsContractTest {

    @Before
    fun resetMonitor() {
        ForegroundAppMonitor.clear()
        ForegroundAppMonitor.setAutomationWatching(false)
    }

    @Test
    fun samplerUsesOneSecondPollAndTenSecondWindow() {
        assertEquals(1_000L, ForegroundAppMonitor.POLL_MS)
        assertEquals(10_000L, ForegroundAppMonitor.SAMPLE_WINDOW_MS)
        // Guard against accidentally restoring the old 5-minute lookback as the sample window.
        assertTrue(ForegroundAppMonitor.SAMPLE_WINDOW_MS < 60_000L)
        assertTrue(
            "poll must stay below the old 3 s panel-only cadence only when automation shares it",
            ForegroundAppMonitor.POLL_MS <= 3_000L,
        )
    }

    @Test
    fun automationWatchingFlagIsStickyUntilCleared() {
        assertFalse(ForegroundAppMonitor.automationWatching.value)
        ForegroundAppMonitor.setAutomationWatching(true)
        assertTrue(ForegroundAppMonitor.automationWatching.value)
        ForegroundAppMonitor.setAutomationWatching(false)
        assertFalse(ForegroundAppMonitor.automationWatching.value)
    }

    @Test
    fun overlayRulesStateEquality_blocksIdenticalSync() {
        // BackgroundService skips syncFloatingDashboards when newState == lastState.
        // If Sets were compared by identity, 1 Hz polls would remount panels every second.
        val a = UsageStatsOverlayRulesState(
            foregroundPackage = "ru.yandex.yandexnavi",
            isMainActivityVisible = false,
            suppressFloatingPanelUsageStatsHide = false,
            suppressFloatingPanelUsageStatsForceShow = false,
            watchHidePackages = setOf("ru.yandex.yandexnavi"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("ru.yandex.yandexnavi"),
            showPanelIds = setOf("panel_b"),
        )
        val b = a.copy(
            watchHidePackages = setOf("ru.yandex.yandexnavi"),
            hidePanelIds = setOf("panel_a"),
            watchShowPackages = setOf("ru.yandex.yandexnavi"),
            showPanelIds = setOf("panel_b"),
        )
        assertEquals(a, b)
        assertNotEquals(a, a.copy(foregroundPackage = "ru.yandex.yandexmaps"))
    }

    @Test
    fun stickySample_survivesEmptyPollsWithoutFlappingToNull() {
        val own = "vad.dashing.tbox"
        var sticky: String? = null
        // Navigator is foreground, then UsageStats returns empty for several polls.
        repeat(5) { tick ->
            val sample = if (tick == 0) "ru.yandex.yandexnavi" else null
            sticky = ForegroundAppSampling.nextSticky(
                previous = sticky,
                sample = sample,
                ownPackage = own,
                mainInForeground = false,
            )
        }
        assertEquals("ru.yandex.yandexnavi", sticky)
    }

    @Test
    fun yandexNaviForceShow_blockedWhileMainActivityVisible() {
        // Same thrash class that previously crashed with nav widgets over Main (#177 era).
        val s = UsageStatsOverlayRulesState(
            foregroundPackage = "ru.yandex.yandexnavi",
            isMainActivityVisible = true,
            suppressFloatingPanelUsageStatsHide = false,
            suppressFloatingPanelUsageStatsForceShow = false,
            watchHidePackages = setOf("vad.dashing.tbox"),
            hidePanelIds = setOf("nav_panel"),
            watchShowPackages = setOf("ru.yandex.yandexnavi"),
            showPanelIds = setOf("nav_panel"),
        )
        assertFalse(s.isUsageStatsForceShowing("nav_panel", "vad.dashing.tbox"))
        assertTrue(s.isUsageStatsForceHidden("nav_panel", "vad.dashing.tbox"))
    }

    @Test
    fun needsSample_onlyWhenPanelRulesOrAutomationWatching() {
        // Mirrors BackgroundService.buildUsageStatsOverlayRulesState gate.
        fun needsSample(
            hasPanelRules: Boolean,
            automationWatching: Boolean,
        ): Boolean = hasPanelRules || automationWatching

        assertFalse(needsSample(hasPanelRules = false, automationWatching = false))
        assertTrue(needsSample(hasPanelRules = true, automationWatching = false))
        assertTrue(needsSample(hasPanelRules = false, automationWatching = true))
    }

    @Test
    fun pollLoadEstimate_isLowerEventSpanThanLegacyFiveMinuteLookback() {
        // events scanned per minute ≈ (60 / poll_s) * window_s of history (upper bound).
        val legacyPollS = 3.0
        val legacyWindowS = 300.0
        val legacyScanSecondsPerMinute = (60.0 / legacyPollS) * legacyWindowS

        val currentPollS = ForegroundAppMonitor.POLL_MS / 1000.0
        val currentWindowS = ForegroundAppMonitor.SAMPLE_WINDOW_MS / 1000.0
        val currentScanSecondsPerMinute = (60.0 / currentPollS) * currentWindowS

        // 1 Hz × 10 s = 600; old 1/3 Hz × 300 s = 6000 — ~10× less event history scanned.
        assertTrue(currentScanSecondsPerMinute < legacyScanSecondsPerMinute / 5.0)
        assertEquals(600.0, currentScanSecondsPerMinute, 0.01)
    }
}
