package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import vad.dashing.tbox.FloatingDashboardWidgetConfig

class DashboardMusicWidgetGestureLogicTest {

    private val packages = listOf(
        "ru.yandex.music",
        "com.maxmpz.audioplayer",
        "com.aimp.player"
    )

    @Test
    fun resolveInitialSelectedPackage_returnsConfiguredWhenPresent() {
        val config = FloatingDashboardWidgetConfig(
            dataKey = "musicWidget",
            mediaPlayers = packages,
            mediaSelectedPlayer = "com.maxmpz.audioplayer"
        )

        val result = resolveInitialSelectedPackage(config, packages)

        assertEquals("com.maxmpz.audioplayer", result)
    }

    @Test
    fun resolveInitialSelectedPackage_fallsBackToFirstWhenMissing() {
        val config = FloatingDashboardWidgetConfig(
            dataKey = "musicWidget",
            mediaPlayers = packages,
            mediaSelectedPlayer = "com.unknown.player"
        )

        val result = resolveInitialSelectedPackage(config, packages)

        assertEquals("ru.yandex.music", result)
    }

    @Test
    fun resolveNextCarouselPackage_wrapsInBothDirections() {
        val previousFromFirst = resolveNextCarouselPackage(
            carouselPackages = packages,
            currentPackage = "ru.yandex.music",
            moveToPrevious = true
        )
        val nextFromLast = resolveNextCarouselPackage(
            carouselPackages = packages,
            currentPackage = "com.aimp.player",
            moveToPrevious = false
        )

        assertEquals("com.aimp.player", previousFromFirst)
        assertEquals("ru.yandex.music", nextFromLast)
    }

    @Test
    fun resolveNextCarouselPackage_usesFirstWhenCurrentNotFound() {
        val result = resolveNextCarouselPackage(
            carouselPackages = packages,
            currentPackage = "missing",
            moveToPrevious = false
        )

        assertEquals("com.maxmpz.audioplayer", result)
    }

    @Test
    fun isInResizeHandleArea_returnsTrueForBottomRightCorner() {
        val result = isInResizeHandleArea(
            offset = Offset(95f, 95f),
            width = 100f,
            height = 100f
        )

        assertEquals(true, result)
    }

    @Test
    fun isInResizeHandleArea_returnsFalseOutsideBottomRightCorner() {
        val result = isInResizeHandleArea(
            offset = Offset(40f, 40f),
            width = 100f,
            height = 100f
        )

        assertEquals(false, result)
    }

    @Test
    fun resizeHandleOffsetForDimension_matchesLegacyThresholds() {
        assertEquals(30f, resizeHandleOffsetForDimension(60f))
        assertEquals(50f, resizeHandleOffsetForDimension(100f))
        assertEquals(60f, resizeHandleOffsetForDimension(101f))
    }
}
