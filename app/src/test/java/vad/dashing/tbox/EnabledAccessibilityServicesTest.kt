package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class EnabledAccessibilityServicesTest {
    private val ours = "vad.dashing.tbox/vad.dashing.tbox.OverlayAccessibilityService"
    private val other = "com.oem.a11y/com.oem.a11y.Svc"

    @Test
    fun with_addsToEmptyOrNull() {
        assertEquals(ours, EnabledAccessibilityServices.with(null, ours))
        assertEquals(ours, EnabledAccessibilityServices.with("", ours))
        assertEquals(ours, EnabledAccessibilityServices.with("null", ours))
    }

    @Test
    fun with_keepsOtherServicesAndDoesNotDuplicate() {
        assertEquals("$other:$ours", EnabledAccessibilityServices.with(other, ours))
        assertEquals("$other:$ours", EnabledAccessibilityServices.with("$other:$ours", ours))
    }

    @Test
    fun with_recognizesShortClassForm() {
        val short = "vad.dashing.tbox/.OverlayAccessibilityService"
        assertEquals(short, EnabledAccessibilityServices.with(short, ours))
    }

    @Test
    fun without_removesOnlyOurs() {
        assertEquals(other, EnabledAccessibilityServices.without("$ours:$other", ours))
        assertEquals(other, EnabledAccessibilityServices.without("vad.dashing.tbox/.OverlayAccessibilityService:$other", ours))
        assertEquals("", EnabledAccessibilityServices.without(ours, ours))
        assertEquals("", EnabledAccessibilityServices.without(null, ours))
    }
}
