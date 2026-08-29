package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundAppSamplingTest {
    private val own = "vad.dashing.tbox"

    @Test
    fun emptySample_keepsPrevious() {
        assertEquals(
            "com.maps",
            ForegroundAppSampling.nextSticky("com.maps", null, own, mainInForeground = false),
        )
        assertEquals(
            "com.maps",
            ForegroundAppSampling.nextSticky("com.maps", "  ", own, mainInForeground = false),
        )
    }

    @Test
    fun newSample_replacesPrevious() {
        assertEquals(
            "com.nav",
            ForegroundAppSampling.nextSticky("com.maps", "com.nav", own, mainInForeground = false),
        )
    }

    @Test
    fun ownPackage_countsOnlyWhenMainResumed() {
        assertNull(
            ForegroundAppSampling.nextSticky(null, own, own, mainInForeground = false),
        )
        assertEquals(
            own,
            ForegroundAppSampling.nextSticky(null, own, own, mainInForeground = true),
        )
    }

    @Test
    fun stickyOwnPackage_droppedWhenMainLeaves() {
        assertNull(
            ForegroundAppSampling.nextSticky(own, null, own, mainInForeground = false),
        )
    }

    @Test
    fun firstEmptySample_isNull() {
        assertNull(
            ForegroundAppSampling.nextSticky(null, null, own, mainInForeground = false),
        )
    }
}
