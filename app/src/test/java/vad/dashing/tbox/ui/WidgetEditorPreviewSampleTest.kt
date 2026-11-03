package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.MUSIC_WIDGET_DATA_KEY
import vad.dashing.tbox.WidgetsRepository

class WidgetEditorPreviewSampleTest {

    @Test
    fun metricSample_includesUnitWhenEnabled() {
        val sample = widgetPreviewSample(
            dataKey = "carSpeed",
            config = FloatingDashboardWidgetConfig(dataKey = "carSpeed", showUnit = true),
            unit = "км/ч",
        )
        assertEquals(WidgetPreviewKind.METRIC, sample.kind)
        // Live tiles insert word-joiners around '/' and a thin space before the unit.
        val plain = sample.valueText.filter { it.isLetterOrDigit() || it == '.' || it == ',' || it == '/' }
        assertTrue(plain.contains("87"))
        assertTrue(plain.contains("км/ч"))
    }

    @Test
    fun metricSample_respectsValueAccuracyAndHidesUnit() {
        val sample = widgetPreviewSample(
            dataKey = "engineRPM",
            config = FloatingDashboardWidgetConfig(
                dataKey = "engineRPM",
                showUnit = false,
                valueAccuracy = 0,
            ),
            unit = "об/мин",
        )
        assertEquals(WidgetPreviewKind.METRIC, sample.kind)
        assertEquals("2150", sample.valueText)
    }

    @Test
    fun controlWidgets_getControlKind() {
        val sample = widgetPreviewSample(
            dataKey = "hvacAcWidget",
            config = FloatingDashboardWidgetConfig(dataKey = "hvacAcWidget"),
            unit = "",
        )
        assertEquals(WidgetPreviewKind.CONTROL, sample.kind)
        assertEquals("", sample.valueText)
        assertTrue(!WidgetsRepository.supportsValueAccuracy("hvacAcWidget"))
    }

    @Test
    fun specialWidgets_useFixedLabels() {
        assertEquals(
            "12:34",
            widgetPreviewSample("timeWidget", FloatingDashboardWidgetConfig(dataKey = "timeWidget"), "").valueText,
        )
        assertEquals(
            "♪",
            widgetPreviewSample(
                MUSIC_WIDGET_DATA_KEY,
                FloatingDashboardWidgetConfig(dataKey = MUSIC_WIDGET_DATA_KEY),
                "",
            ).valueText,
        )
        assertEquals(
            WidgetPreviewKind.SPECIAL,
            widgetPreviewSample(
                MUSIC_WIDGET_DATA_KEY,
                FloatingDashboardWidgetConfig(dataKey = MUSIC_WIDGET_DATA_KEY),
                "",
            ).kind,
        )
    }

    @Test
    fun sampleMetricNumber_picksDomainDefaults() {
        assertEquals(2150, sampleMetricNumber("engineRPM").first)
        assertEquals(0, sampleMetricNumber("engineRPM").second)
        assertEquals(87.0, sampleMetricNumber("carSpeed").first)
        assertEquals(22.5, sampleMetricNumber("outsideTemperature").first)
    }
}
