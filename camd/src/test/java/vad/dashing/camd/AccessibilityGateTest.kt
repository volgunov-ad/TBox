package vad.dashing.camd

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityGateTest {

    private val pkg = "vad.dashing.camd"
    private val cls = "vad.dashing.camd.CamdAccessibilityService"

    @Test
    fun nullAndEmptySettingsAreDisabled() {
        assertFalse(AccessibilityGate.isEnabledInSetting(null, pkg, cls))
        assertFalse(AccessibilityGate.isEnabledInSetting("", pkg, cls))
        assertFalse(AccessibilityGate.isEnabledInSetting("   ", pkg, cls))
    }

    @Test
    fun matchesFullComponent() {
        assertTrue(
            AccessibilityGate.isEnabledInSetting(
                "vad.dashing.camd/vad.dashing.camd.CamdAccessibilityService",
                pkg,
                cls,
            )
        )
    }

    @Test
    fun matchesShortComponent() {
        assertTrue(
            AccessibilityGate.isEnabledInSetting("vad.dashing.camd/.CamdAccessibilityService", pkg, cls)
        )
    }

    @Test
    fun matchesAmongOtherServices() {
        val raw = "com.other.app/com.other.app.Helper:" +
            "vad.dashing.camd/.CamdAccessibilityService:" +
            "com.third/.Standalone"
        assertTrue(AccessibilityGate.isEnabledInSetting(raw, pkg, cls))
    }

    @Test
    fun wrongClassOrPackageIsRejected() {
        assertFalse(
            AccessibilityGate.isEnabledInSetting("vad.dashing.camd/.OtherService", pkg, cls)
        )
        assertFalse(
            AccessibilityGate.isEnabledInSetting("com.other/.CamdAccessibilityService", pkg, cls)
        )
        assertFalse(
            AccessibilityGate.isEnabledInSetting(
                "vad.dashing.camd/vad.dashing.camd.CamdAccessibilityServiceX",
                pkg,
                cls,
            )
        )
    }

    @Test
    fun malformedComponentsAreIgnored() {
        assertFalse(AccessibilityGate.isEnabledInSetting("vad.dashing.camd", pkg, cls))
        assertFalse(
            AccessibilityGate.isEnabledInSetting(
                "vad.dashing.camd.CamdAccessibilityService",
                pkg,
                cls,
            )
        )
    }

    @Test
    fun emptyComponentsAroundValidEntryAreSkipped() {
        assertTrue(
            AccessibilityGate.isEnabledInSetting(":vad.dashing.camd/.CamdAccessibilityService:", pkg, cls)
        )
    }
}
