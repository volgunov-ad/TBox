package vad.dashing.tbox.dcl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DclLauncherLivenessTest {

    @Test
    fun processLikelyDead_falseWhenDclVisible() {
        assertFalse(
            DclLauncherLiveness.processLikelyDead(
                runningPackageNames = setOf(
                    "vad.dashing.tbox",
                    DclLauncherApiContract.LAUNCHER_PACKAGE,
                    "com.android.systemui",
                ),
                selfPackageName = "vad.dashing.tbox",
            ),
        )
    }

    @Test
    fun processLikelyDead_trueWhenOthersVisibleButNotDcl() {
        assertTrue(
            DclLauncherLiveness.processLikelyDead(
                runningPackageNames = setOf(
                    "vad.dashing.tbox",
                    "com.android.systemui",
                    "com.example.maps",
                ),
                selfPackageName = "vad.dashing.tbox",
            ),
        )
    }

    @Test
    fun processLikelyDead_falseWhenOnlySelfVisible() {
        // Filtered process list — do not force raw fallback.
        assertFalse(
            DclLauncherLiveness.processLikelyDead(
                runningPackageNames = setOf("vad.dashing.tbox"),
                selfPackageName = "vad.dashing.tbox",
            ),
        )
    }

    @Test
    fun processLikelyDead_falseWhenNullOrEmpty() {
        assertFalse(
            DclLauncherLiveness.processLikelyDead(
                runningPackageNames = null,
                selfPackageName = "vad.dashing.tbox",
            ),
        )
        assertFalse(
            DclLauncherLiveness.processLikelyDead(
                runningPackageNames = emptyList(),
                selfPackageName = "vad.dashing.tbox",
            ),
        )
    }

    @Test
    fun bypassBinder_whenPackageMissingOrProcessDeadOrCapabilityMissing() {
        assertTrue(
            dclShouldBypassBinderForRawFallback(
                packageInstalled = false,
                processLikelyDead = false,
                capabilityMissing = false,
            ),
        )
        assertTrue(
            dclShouldBypassBinderForRawFallback(
                packageInstalled = true,
                processLikelyDead = true,
                capabilityMissing = false,
            ),
        )
        assertTrue(
            dclShouldBypassBinderForRawFallback(
                packageInstalled = true,
                processLikelyDead = false,
                capabilityMissing = true,
            ),
        )
        assertFalse(
            dclShouldBypassBinderForRawFallback(
                packageInstalled = true,
                processLikelyDead = false,
                capabilityMissing = false,
            ),
        )
    }
}
