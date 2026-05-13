package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Test
import vad.dashing.tbox.ui.mergeImmersivePackageSelection
import vad.dashing.tbox.ui.parseImmersivePolicyPackagesOrdered

class ImmersivePolicyPackageSelectionTest {

    @Test
    fun parse_trims_splits_and_dedupes_preserving_order() {
        assertEquals(
            listOf("a", "b"),
            parseImmersivePolicyPackagesOrdered(" a , b , a ")
        )
    }

    @Test
    fun merge_keeps_prior_order_then_appends_new_sorted() {
        assertEquals(
            listOf("z", "a", "m"),
            mergeImmersivePackageSelection(listOf("z", "a"), setOf("z", "a", "m"))
        )
    }

    @Test
    fun merge_all_new_sorted_when_no_overlap() {
        assertEquals(
            listOf("a", "b"),
            mergeImmersivePackageSelection(listOf("x", "y"), setOf("b", "a"))
        )
    }
}
