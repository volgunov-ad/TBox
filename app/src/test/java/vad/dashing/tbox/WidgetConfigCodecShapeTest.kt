package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetConfigCodecShapeTest {

    @Test
    fun normalizeWidgetShape_clampsToAllowedRange() {
        assertEquals(0, normalizeWidgetShape(-5))
        assertEquals(15, normalizeWidgetShape(15))
        assertEquals(50, normalizeWidgetShape(99))
    }

    @Test
    fun widgetConfigCodec_roundTripsDateTimeFormat() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = DATE_WIDGET_DATA_KEY,
                    dateTimeFormat = "дд.ММ.гггг (дн)",
                ),
                FloatingDashboardWidgetConfig(
                    dataKey = TIME_WIDGET_DATA_KEY,
                    dateTimeFormat = "HH:mm:ss",
                ),
            ),
        )

        val parsed = parseWidgetConfigsFromString(json)

        assertEquals("dd.MM.yyyy (EEE)", parsed[0].dateTimeFormat)
        assertEquals("HH:mm:ss", parsed[1].dateTimeFormat)
    }

    @Test
    fun widgetConfigCodec_skipsDateTimeFormatForOtherWidgets() {
        val json = serializeWidgetConfigs(
            listOf(
                FloatingDashboardWidgetConfig(
                    dataKey = "voltage",
                    dateTimeFormat = "dd.MM.yyyy",
                ),
            ),
        )

        val parsed = parseWidgetConfigsFromString(json)

        assertEquals("", parsed[0].dateTimeFormat)
    }
}
