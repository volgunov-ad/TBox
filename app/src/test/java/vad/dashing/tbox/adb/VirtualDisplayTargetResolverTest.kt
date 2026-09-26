package vad.dashing.tbox.adb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.tbox.adb.VirtualDisplayTargetResolver.ResolveResult

class VirtualDisplayTargetResolverTest {

    private fun jetourCatalog(appDisplayId: Int = 5) = listOf(
        HuDisplayInfo(0, 1920, 981),
        HuDisplayInfo(1, 536, 212),
        HuDisplayInfo(appDisplayId, 1320, 856),
    )

    @Test
    fun forPicker_hidesDisplay0() {
        val picker = VirtualDisplayTargetResolver.forPicker(jetourCatalog())
        assertFalse(picker.any { it.displayId == 0 })
        assertEquals(2, picker.size)
    }

    @Test
    fun resolve_keepsIdWhenStillPresent() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 5,
            preferredWidth = 1320,
            preferredHeight = 856,
            catalog = jetourCatalog(5),
        )
        val matched = result as ResolveResult.Matched
        assertEquals(5, matched.display.displayId)
        assertFalse(matched.remapped)
    }

    @Test
    fun resolve_remapsBySizeWhenIdChanges() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 5,
            preferredWidth = 1320,
            preferredHeight = 856,
            catalog = jetourCatalog(appDisplayId = 7),
        )
        val matched = result as ResolveResult.Matched
        assertEquals(7, matched.display.displayId)
        assertTrue(matched.remapped)
    }

    @Test
    fun resolve_sizeToleranceAllowsTwoPxDrift() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 5,
            preferredWidth = 1321,
            preferredHeight = 855,
            catalog = jetourCatalog(7),
        )
        val matched = result as ResolveResult.Matched
        assertEquals(7, matched.display.displayId)
    }

    @Test
    fun resolve_legacyIdOnly_failsWhenMissing() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 5,
            preferredWidth = null,
            preferredHeight = null,
            catalog = jetourCatalog(7),
        )
        assertTrue(result is ResolveResult.Failed)
        assertEquals(
            VirtualDisplayTargetResolver.FailReason.NoMatch,
            (result as ResolveResult.Failed).reason,
        )
    }

    @Test
    fun resolve_rejectsDisplay0WithoutSize() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 0,
            preferredWidth = null,
            preferredHeight = null,
            catalog = jetourCatalog(),
        )
        assertTrue(result is ResolveResult.Failed)
    }

    @Test
    fun resolve_emptyCatalog_fails() {
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 5,
            preferredWidth = 1320,
            preferredHeight = 856,
            catalog = emptyList(),
        )
        assertEquals(
            VirtualDisplayTargetResolver.FailReason.EmptyCatalog,
            (result as ResolveResult.Failed).reason,
        )
    }

    @Test
    fun resolve_prefersInsetAppRoleAmongSizeMatches() {
        val catalog = listOf(
            HuDisplayInfo(0, 1920, 981),
            HuDisplayInfo(3, 1320, 856),
            HuDisplayInfo(8, 1320, 856),
        )
        // pickAppVirtualDisplay picks largest inset under default → both same size;
        // maxBy area then first? Actually both same area - maxByOrNull is stable on first max.
        // With same area, maxByOrNull returns the last max in Kotlin? 
        // Actually maxByOrNull returns the first element with max if equal... 
        // Kotlin maxByOrNull: "If there are multiple maximal elements, returns first"
        // So pickAppVirtualDisplay uses maxByOrNull { area } → first of equal = display 3.
        val result = VirtualDisplayTargetResolver.resolve(
            preferredId = 99,
            preferredWidth = 1320,
            preferredHeight = 856,
            catalog = catalog,
        )
        val matched = result as ResolveResult.Matched
        assertEquals(3, matched.display.displayId)
    }
}
