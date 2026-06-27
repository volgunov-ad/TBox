package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateTimeWidgetFormatTest {

    @Test
    fun normalizeDateTimeWidgetFormat_acceptsRussianDateTokens() {
        assertEquals(
            "dd.MM.yyyy (EEEE)",
            normalizeDateTimeWidgetFormat(DATE_WIDGET_DATA_KEY, "дд.ММ.гггг (день)"),
        )
        assertEquals(
            "dd.MM.yyyy (EEE)",
            normalizeDateTimeWidgetFormat(DATE_WIDGET_DATA_KEY, "дд.ММ.гггг (дн)"),
        )
    }

    @Test
    fun normalizeDateTimeWidgetFormat_acceptsRussianTimeTokens() {
        assertEquals(
            "HH:mm:ss",
            normalizeDateTimeWidgetFormat(TIME_WIDGET_DATA_KEY, "чч:мм:сс"),
        )
    }

    @Test
    fun isValidDateTimeWidgetFormat_acceptsEnglishAndRussianPatterns() {
        assertTrue(isValidDateTimeWidgetFormat(DATE_WIDGET_DATA_KEY, "dd.MM.yyyy (EEEE)"))
        assertTrue(isValidDateTimeWidgetFormat(DATE_WIDGET_DATA_KEY, "дд.ММ.гггг (дн)"))
        assertTrue(isValidDateTimeWidgetFormat(TIME_WIDGET_DATA_KEY, "HH:mm:ss"))
        assertTrue(isValidDateTimeWidgetFormat(TIME_WIDGET_DATA_KEY, "чч:мм"))
    }

    @Test
    fun isValidDateTimeWidgetFormat_rejectsInvalidPatterns() {
        assertFalse(isValidDateTimeWidgetFormat(DATE_WIDGET_DATA_KEY, "dd.MM.yyyy qqq"))
        assertFalse(isValidDateTimeWidgetFormat("voltage", "dd.MM.yyyy"))
    }
}
