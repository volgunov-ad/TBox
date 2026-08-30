package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetTypeSectionsTest {
    @Test
    fun everyAvailableWidgetDataKeyHasSection() {
        val available = WidgetsRepository.getAvailableDataKeysWidgets(noTboxConnect = false)
            .filter { it.isNotEmpty() }
            .toSet()
        val mapped = WidgetTypeSections.mappedDataKeys()
        val missing = available - mapped
        val extra = mapped - available
        assertTrue(
            "Unmapped widget dataKeys (add to WidgetTypeSections): $missing",
            missing.isEmpty(),
        )
        assertTrue(
            "Stale WidgetTypeSections keys not in catalog: $extra",
            extra.isEmpty(),
        )
        assertEquals(available.size, mapped.size)
    }

    @Test
    fun sectionsAreStableForKnownKeys() {
        assertEquals(WidgetTypeSectionId.Chassis, WidgetTypeSections.sectionFor(AVH_WIDGET_DATA_KEY))
        assertEquals(WidgetTypeSectionId.Climate, WidgetTypeSections.sectionFor(HVAC_AC_MAX_WIDGET_DATA_KEY))
        assertEquals(WidgetTypeSectionId.Audio, WidgetTypeSections.sectionFor(MUSIC_COVER_WIDGET_DATA_KEY))
        assertEquals(WidgetTypeSectionId.Esp32, WidgetTypeSections.sectionFor("espRelay0"))
        assertEquals(WidgetTypeSectionId.System, WidgetTypeSections.sectionFor(DAY_NIGHT_THEME_WIDGET_DATA_KEY))
        assertEquals(WidgetTypeSectionId.Trips, WidgetTypeSections.sectionFor("activeTripWidget"))
        assertEquals(WidgetTypeSectionId.Trips, WidgetTypeSections.sectionFor("motorHours"))
        assertEquals(
            WidgetTypeSectionId.GeopositionNetwork,
            WidgetTypeSections.sectionFor(ROAD_MATCH_MAP_WIDGET_DATA_KEY),
        )
        assertEquals(WidgetTypeSectionId.GeopositionNetwork, WidgetTypeSections.sectionFor("netWidget"))
        assertEquals(WidgetTypeSectionId.Telemetry, WidgetTypeSections.sectionFor("voltage"))
    }
}
