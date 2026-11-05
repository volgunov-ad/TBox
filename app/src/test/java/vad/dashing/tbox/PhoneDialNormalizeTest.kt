package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.phone.PhoneCallState
import vad.dashing.tbox.WidgetsRepository

class PhoneDialNormalizeTest {

    @Test
    fun normalize_keepsPlusAndDigits_stripsSeparators() {
        assertEquals("+79200287693", normalizeDialPhoneNumber("+7 (920) 028-76-93"))
        assertEquals("900", normalizeDialPhoneNumber(" 900 "))
        assertEquals("*123#", normalizeDialPhoneNumber("*123#"))
    }

    @Test
    fun normalize_emptyWithoutDigits() {
        assertEquals("", normalizeDialPhoneNumber(""))
        assertEquals("", normalizeDialPhoneNumber("+++"))
        assertEquals("", normalizeDialPhoneNumber("()-"))
    }

    @Test
    fun phoneDial_hiddenFromMainDashboardTab() {
        assertTrue(isPhoneDialWidgetDataKey(PHONE_DIAL_WIDGET_DATA_KEY))
        assertTrue(!WidgetsRepository.isOfferedOnMainDashboardTab(PHONE_DIAL_WIDGET_DATA_KEY))
        assertTrue(WidgetsRepository.isOfferedOnMainDashboardTab(HTTP_REQUEST_WIDGET_DATA_KEY))
    }

    @Test
    fun callState_optionsMatchPlan() {
        assertEquals(
            listOf("idle", "dialing", "active", "incoming", "held", "unknown"),
            PhoneCallState.OPTIONS,
        )
    }
}
