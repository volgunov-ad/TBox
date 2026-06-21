package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.mbcan.MbCanKnownVehiclePropertyId

class DriveModeThemeKeyTest {

    @Test
    fun resolveDriveModeThemeKey_prefersStandardDriveMode() {
        val key = DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = 2, wet6dct = 1)
        assertEquals(2, key)
    }

    @Test
    fun resolveDriveModeThemeKey_fallsBackTo6dct() {
        val key = DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = null, wet6dct = 0)
        assertEquals(100, key)
    }

    @Test
    fun resolveDriveModeThemeKey_returnsNullWhenUnknown() {
        assertNull(DriveModeThemeWatcher.resolveDriveModeThemeKey(drive = 99, wet6dct = 99))
    }

    @Test
    fun resolveActivationRequest_returnsNullWhenPathsEmpty() {
        assertNull(
            DriveModeThemeWatcher.resolveActivationRequest(
                paths = emptyMap(),
                drive = 2,
                wet6dct = null,
            ),
        )
    }

    @Test
    fun resolveActivationRequest_mapsCurrentModeToCacheKey() {
        val request = DriveModeThemeWatcher.resolveActivationRequest(
            paths = mapOf(2 to "content://theme/eco.tboxtheme"),
            drive = 2,
            wet6dct = null,
        )
        assertNotNull(request)
        assertEquals(2, request!!.modeRawValue)
        assertEquals("content://theme/eco.tboxtheme", request.sourceUri)
        assertEquals(ThemeCacheKeys.driveModeCacheKey(2), request.cacheKey)
    }

    @Test
    fun resolveActivationRequest_ignoresUnrelatedModeAssignments() {
        val first = DriveModeThemeWatcher.resolveActivationRequest(
            paths = mapOf(2 to "content://theme/eco.tboxtheme"),
            drive = 2,
            wet6dct = null,
        )
        val second = DriveModeThemeWatcher.resolveActivationRequest(
            paths = mapOf(
                2 to "content://theme/eco.tboxtheme",
                3 to "content://theme/sport.tboxtheme",
            ),
            drive = 2,
            wet6dct = null,
        )
        assertEquals(first, second)
    }

    @Test
    fun isDriveModeThemeAlreadyApplied_requiresMatchingCacheKeyAndFingerprint() {
        val request = DriveModeThemeWatcher.resolveActivationRequest(
            paths = mapOf(2 to "content://theme/eco.tboxtheme"),
            drive = 2,
            wet6dct = null,
        )!!
        val manifest = ThemeMaterialization.ThemeManifest(
            cacheKey = request.cacheKey,
            sourceUri = request.sourceUri,
            sourceDisplayName = "eco.tboxtheme",
            materializedAtMillis = 0L,
            fingerprint = "abc123",
            sections = emptySet(),
        )

        assertTrue(
            DriveModeThemeWatcher.isDriveModeThemeAlreadyApplied(
                request = request,
                activeThemeUri = " ${request.cacheKey} ",
                activeThemeFingerprint = " abc123 ",
                manifest = manifest,
            ),
        )
        assertFalse(
            DriveModeThemeWatcher.isDriveModeThemeAlreadyApplied(
                request = request,
                activeThemeUri = request.cacheKey,
                activeThemeFingerprint = "different",
                manifest = manifest,
            ),
        )
    }

    @Test
    fun panelVisibility_ignoresPageAboveCount() {
        val panel = MainScreenPanelConfig(
            id = "p",
            name = "P",
            enabled = true,
            widgetsConfig = emptyList(),
            rows = 1,
            cols = 1,
            relX = 0f,
            relY = 0f,
            relWidth = 0.2f,
            relHeight = 0.2f,
            background = false,
            clickAction = false,
            pageNumber = 4,
        )
        assertEquals(false, panel.isVisibleOnMainScreenPage(pageCount = 2, currentPage = 1))
    }
}
