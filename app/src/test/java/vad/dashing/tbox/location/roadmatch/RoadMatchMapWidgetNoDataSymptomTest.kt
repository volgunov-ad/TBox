package vad.dashing.tbox.location.roadmatch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Field symptom: road-match map widget showed «Нет данных привязки».
 *
 * That RU string is [R.string.road_match_map_widget_no_data] — the widget's
 * default when [RoadMatchOverlayState.shadow] is not visible and
 * [fallbackReason] is not `no_graph` / `no_edge`. Production code unchanged.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RoadMatchMapWidgetNoDataSymptomTest {

    @Before
    fun clear() {
        RoadGraphStore.clear()
        RoadMatchOverlayRepository.clear()
    }

    @Test
    fun clearedOverlay_isDisabledWithoutVisibleShadow() {
        RoadMatchOverlayRepository.clear()
        val s = RoadMatchOverlayRepository.state.value
        assertEquals("disabled", s.fallbackReason)
        assertFalse(s.shadow.visible)
        // Widget maps this to road_match_map_widget_no_data («Нет данных привязки»).
        assertEquals("no_data", widgetCenteredMessageKey(s))
    }

    @Test
    fun noGraph_keepsShadowVisible_soCenteredNoDataLabelIsNotShown() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.62,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(skippedReason = "no_graph"),
            graphs = emptyList(),
        )
        assertTrue(s.shadow.visible)
        assertEquals("no_graph", s.fallbackReason)
        // Centered label is gated on !shadow.visible — so «Нет данных привязки» is NOT this case.
        assertNull(widgetCenteredMessageKey(s))
    }

    @Test
    fun noPose_hidesShadow_andMapsToNoDataString() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = Double.NaN,
            shadowLon = 37.62,
            shadowBearingDeg = 90f,
        )
        assertFalse(s.shadow.visible)
        assertEquals("no_pose", s.fallbackReason)
        assertEquals("no_data", widgetCenteredMessageKey(s))
    }

    @Test
    fun healthyShadow_hasNoFallbackEvenWithoutMatchedEdge() {
        val s = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.62,
            shadowBearingDeg = 90f,
            debug = RoadMatchRuntime.DebugSnapshot(skippedReason = "throttled"),
            graphs = emptyList(),
        )
        assertTrue(s.shadow.visible)
        assertNull(s.fallbackReason)
        // Shadow visible → widget draws canvas, not the centered no-data label.
        assertNull(widgetCenteredMessageKey(s))
    }

    @Test
    fun oomStyleGraphClear_thenRepublish_keepsShadow_unlessOverlayCleared() {
        // MockLocationJob OOM handler clears RoadGraphStore + resets matcher, but does
        // not call RoadMatchOverlayRepository.clear(). A later publish with a valid
        // shadow still shows the green marker (possibly without roads).
        RoadGraphStore.clear()
        val afterOom = RoadMatchOverlayBuilder.build(
            matchEnabled = true,
            shadowLat = 55.75,
            shadowLon = 37.62,
            shadowBearingDeg = 10f,
            debug = RoadMatchRuntime.DebugSnapshot(skippedReason = "oom_load"),
            graphs = emptyList(),
        )
        assertTrue(afterOom.shadow.visible)
        assertNull(afterOom.fallbackReason) // oom_load ≠ no_graph
        assertNull(widgetCenteredMessageKey(afterOom))

        RoadMatchOverlayRepository.clear()
        assertEquals("no_data", widgetCenteredMessageKey(RoadMatchOverlayRepository.state.value))
    }

    /**
     * Mirrors [vad.dashing.tbox.ui.DashboardRoadMatchMapWidgetItem]: centered label only
     * when `!shadow.visible`; string pick uses fallbackReason.
     */
    private fun widgetCenteredMessageKey(state: RoadMatchOverlayState): String? {
        if (state.shadow.visible) return null
        return when (state.fallbackReason) {
            "no_graph" -> "no_graph"
            "no_edge" -> "no_edge"
            else -> "no_data"
        }
    }
}
