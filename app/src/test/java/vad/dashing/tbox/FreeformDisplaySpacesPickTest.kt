package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import vad.dashing.tbox.freeform.FreeformDisplaySpaces
import vad.dashing.tbox.freeform.FreeformLaunchBounds
import vad.dashing.tbox.freeform.FreeformLaunchSide

/**
 * Guards the Jetour multi-VD freeform regression: applicationContext / overlay Context
 * report display 0 (1920×981), but companions and the MainScreen overlay must use the
 * inset app VD (display 5 @ 1320×856).
 *
 * Robolectric is required for [android.graphics.Rect] used by launch-bounds geometry.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FreeformDisplaySpacesPickTest {

    /** Snapshot from HU logs: `displays=[0:1920x981,1:536x212,2:536x524,3:536x130,4:1920x108,5:1320x856]`. */
    private fun jetourHuDisplays(): List<FreeformDisplaySpaces.DisplaySize> = listOf(
        FreeformDisplaySpaces.DisplaySize(0, 1920, 981),
        FreeformDisplaySpaces.DisplaySize(1, 536, 212),
        FreeformDisplaySpaces.DisplaySize(2, 536, 524),
        FreeformDisplaySpaces.DisplaySize(3, 536, 130),
        FreeformDisplaySpaces.DisplaySize(4, 1920, 108),
        FreeformDisplaySpaces.DisplaySize(5, 1320, 856),
    )

    @Test
    fun pickAppVirtualDisplay_jetourHuLayout_picksInsetVd5() {
        val picked = FreeformDisplaySpaces.pickAppVirtualDisplay(jetourHuDisplays())
        assertEquals(5, picked!!.displayId)
        assertEquals(1320, picked.widthPx)
        assertEquals(856, picked.heightPx)
    }

    @Test
    fun pickAppVirtualDisplay_onlyDefault_returnsNull() {
        val displays = listOf(FreeformDisplaySpaces.DisplaySize(0, 1920, 1080))
        assertNull(FreeformDisplaySpaces.pickAppVirtualDisplay(displays))
    }

    @Test
    fun pickAppVirtualDisplay_ignoresThinStrips() {
        val displays = listOf(
            FreeformDisplaySpaces.DisplaySize(0, 1920, 1080),
            FreeformDisplaySpaces.DisplaySize(4, 1920, 108),
        )
        assertNull(FreeformDisplaySpaces.pickAppVirtualDisplay(displays))
    }

    @Test
    fun pickAppVirtualDisplay_ignoresNarrowClusterPanels() {
        // display 2 is tall enough on short side but long side 536 < 800
        val displays = listOf(
            FreeformDisplaySpaces.DisplaySize(0, 1920, 981),
            FreeformDisplaySpaces.DisplaySize(2, 536, 524),
        )
        assertNull(FreeformDisplaySpaces.pickAppVirtualDisplay(displays))
    }

    @Test
    fun pickAppVirtualDisplay_picksLargestInsetAmongSeveral() {
        val displays = listOf(
            FreeformDisplaySpaces.DisplaySize(0, 1920, 1080),
            FreeformDisplaySpaces.DisplaySize(3, 900, 600),
            FreeformDisplaySpaces.DisplaySize(7, 1280, 800),
        )
        val picked = FreeformDisplaySpaces.pickAppVirtualDisplay(displays)
        assertEquals(7, picked!!.displayId)
    }

    @Test
    fun pickAppVirtualDisplay_rejectsNonDefaultLargerThanDefault() {
        val displays = listOf(
            FreeformDisplaySpaces.DisplaySize(0, 1280, 720),
            FreeformDisplaySpaces.DisplaySize(2, 1920, 1080),
        )
        assertNull(FreeformDisplaySpaces.pickAppVirtualDisplay(displays))
    }

    @Test
    fun pickAppVirtualDisplay_empty_returnsNull() {
        assertNull(FreeformDisplaySpaces.pickAppVirtualDisplay(emptyList()))
    }

    /**
     * Regression: shortcut / window-mode launch uses applicationContext → context display 0.
     * Resolve must still return inset VD 5, not default 0.
     */
    @Test
    fun resolveFromCatalog_prefersInsetVdEvenWhenContextIsDefault0() {
        val contextAsApplication = FreeformDisplaySpaces.DisplaySize(0, 1920, 981)
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = jetourHuDisplays(),
            contextDisplay = contextAsApplication,
        )
        assertEquals(5, resolved.displayId)
        assertEquals(1320, resolved.widthPx)
        assertEquals(856, resolved.heightPx)
    }

    @Test
    fun resolveFromCatalog_prefersInsetVdEvenWhenContextIsNull() {
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = jetourHuDisplays(),
            contextDisplay = null,
        )
        assertEquals(5, resolved.displayId)
    }

    @Test
    fun resolveFromCatalog_fallsBackToContextWhenNoInsetVd() {
        val catalog = listOf(FreeformDisplaySpaces.DisplaySize(0, 1920, 1080))
        val context = FreeformDisplaySpaces.DisplaySize(0, 1920, 1080)
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(catalog, context)
        assertEquals(0, resolved.displayId)
        assertEquals(1920, resolved.widthPx)
        assertEquals(1080, resolved.heightPx)
    }

    @Test
    fun resolveFromCatalog_fallsBackToDefaultWhenContextNullAndNoInset() {
        val catalog = listOf(FreeformDisplaySpaces.DisplaySize(0, 1920, 1080))
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(catalog, contextDisplay = null)
        assertEquals(0, resolved.displayId)
    }

    @Test
    fun resolveFromCatalog_emptyCatalog_usesSafeDefault() {
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = emptyList(),
            contextDisplay = null,
        )
        assertEquals(0, resolved.displayId)
        assertEquals(1280, resolved.widthPx)
        assertEquals(720, resolved.heightPx)
    }

    /**
     * End-to-end geometry regression: resolving default-0 space yields a ~full-panel half
     * overlay; correct inset VD yields the companion-side split on 1320×856.
     */
    @Test
    fun resolveThenComplement_jetour_right50_matchesInsetNotFullPanel() {
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = jetourHuDisplays(),
            contextDisplay = FreeformDisplaySpaces.DisplaySize(0, 1920, 981),
        )
        assertEquals("must not stay on default display 0", 5, resolved.displayId)
        assertEquals(1320, resolved.widthPx)
        assertEquals(856, resolved.heightPx)
        val overlay = FreeformLaunchBounds.computeComplementOverlayGeometry(
            activityDisplayWidth = resolved.widthPx,
            activityDisplayHeight = resolved.heightPx,
            overlayDisplayWidth = resolved.widthPx,
            overlayDisplayHeight = resolved.heightPx,
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
        )
        assertEquals(0, overlay.startX)
        assertEquals(0, overlay.startY)
        assertEquals(660, overlay.width)
        assertEquals(856, overlay.height)

        // Wrong path (pre-fix): percent of display 0
        val wrong = FreeformLaunchBounds.computeComplementOverlayGeometry(
            activityDisplayWidth = 1920,
            activityDisplayHeight = 981,
            overlayDisplayWidth = 1920,
            overlayDisplayHeight = 981,
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
        )
        assertEquals(960, wrong.width)
        assertEquals(981, wrong.height)
        assertTrue(
            "Inset overlay must be smaller than full-panel regression geo",
            overlay.width < wrong.width && overlay.height < wrong.height,
        )
    }

    @Test
    fun resolveThenComplement_jetour_left30_matchesInset() {
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = jetourHuDisplays(),
            contextDisplay = FreeformDisplaySpaces.DisplaySize(0, 1920, 981),
        )
        val overlay = FreeformLaunchBounds.computeComplementOverlayGeometry(
            activityDisplayWidth = resolved.widthPx,
            activityDisplayHeight = resolved.heightPx,
            overlayDisplayWidth = resolved.widthPx,
            overlayDisplayHeight = resolved.heightPx,
            side = FreeformLaunchSide.LEFT,
            percent = 30,
        )
        // Companion 30% of 1320 → 396; TBox from x=396 width=924
        assertEquals(396, overlay.startX)
        assertEquals(0, overlay.startY)
        assertEquals(924, overlay.width)
        assertEquals(856, overlay.height)
    }

    @Test
    fun computeAppBounds_onResolvedInset_notOnDefault0() {
        val resolved = FreeformDisplaySpaces.resolveFromCatalog(
            catalog = jetourHuDisplays(),
            contextDisplay = FreeformDisplaySpaces.DisplaySize(0, 1920, 981),
        )
        val (app, tbox) = FreeformLaunchBounds.computeAppAndTboxBounds(
            displayWidth = resolved.widthPx,
            displayHeight = resolved.heightPx,
            side = FreeformLaunchSide.RIGHT,
            percent = 50,
        )
        assertEquals(660, app.left)
        assertEquals(1320, app.right)
        assertEquals(856, app.bottom)
        assertEquals(0, tbox.left)
        assertEquals(660, tbox.right)
    }
}
