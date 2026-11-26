package vad.dashing.store.dcl

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreDclLivenessTest {

    @Test
    fun processLikelyDead_falseWhenDclVisible() {
        assertFalse(
            StoreDclLiveness.processLikelyDead(
                runningPackageNames = setOf(
                    StoreDclContract.STORE_PACKAGE,
                    StoreDclContract.LAUNCHER_PACKAGE,
                    "com.android.systemui",
                ),
                selfPackageName = StoreDclContract.STORE_PACKAGE,
            ),
        )
    }

    @Test
    fun processLikelyDead_trueWhenOthersVisibleButNotDcl() {
        assertTrue(
            StoreDclLiveness.processLikelyDead(
                runningPackageNames = setOf(
                    StoreDclContract.STORE_PACKAGE,
                    "com.android.systemui",
                    "vad.dashing.tbox",
                ),
                selfPackageName = StoreDclContract.STORE_PACKAGE,
            ),
        )
    }

    @Test
    fun processLikelyDead_falseWhenOnlySelfVisible() {
        assertFalse(
            StoreDclLiveness.processLikelyDead(
                runningPackageNames = setOf(StoreDclContract.STORE_PACKAGE),
                selfPackageName = StoreDclContract.STORE_PACKAGE,
            ),
        )
    }

    @Test
    fun processLikelyDead_falseWhenNullOrEmpty() {
        assertFalse(
            StoreDclLiveness.processLikelyDead(
                runningPackageNames = null,
                selfPackageName = StoreDclContract.STORE_PACKAGE,
            ),
        )
        assertFalse(
            StoreDclLiveness.processLikelyDead(
                runningPackageNames = emptyList(),
                selfPackageName = StoreDclContract.STORE_PACKAGE,
            ),
        )
    }

    @Test
    fun bypassBinder_whenPackageMissingOrProcessDead() {
        assertTrue(
            storeDclShouldBypassBinder(
                packageInstalled = false,
                processLikelyDead = false,
            ),
        )
        assertTrue(
            storeDclShouldBypassBinder(
                packageInstalled = true,
                processLikelyDead = true,
            ),
        )
        assertFalse(
            storeDclShouldBypassBinder(
                packageInstalled = true,
                processLikelyDead = false,
            ),
        )
    }
}
