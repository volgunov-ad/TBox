package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test

class PolicyControlImmersiveTest {

    @Test
    fun merge_replaces_existing_immersive_and_keeps_other_policies() {
        val existing = "immersive.navigation=*;immersive.full=com.old.app;something=value"
        val merged = mergeImmersiveFullPolicy(existing, listOf("vad.dashing.tbox", "ru.app"))
        assertEquals(
            "immersive.navigation=*;something=value;immersive.full=vad.dashing.tbox,ru.app",
            merged
        )
    }

    @Test
    fun merge_only_immersive_when_no_other_segments() {
        assertEquals(
            "immersive.full=a,b",
            mergeImmersiveFullPolicy(null, listOf("a", "b"))
        )
    }

    @Test
    fun merge_empty_packages_removes_immersive_segment_only() {
        val existing = "foo=bar;immersive.full=x"
        assertEquals("foo=bar", mergeImmersiveFullPolicy(existing, emptyList()))
    }

    @Test
    fun merge_trims_and_dedupes_packages() {
        assertEquals(
            "immersive.full=a,b",
            mergeImmersiveFullPolicy("", listOf(" a ", "b", "a"))
        )
    }
}
