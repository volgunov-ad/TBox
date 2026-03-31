package vad.dashing.tbox.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import androidx.compose.ui.geometry.Offset
import vad.dashing.tbox.FloatingDashboardWidgetConfig
import vad.dashing.tbox.SupportedMediaPlayer

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
    fun resolvePlayerLaunchPackage_redirectsCanonicalBluetoothToStockPlayer() {
        val canonicalResult = resolvePlayerLaunchPackage(SupportedMediaPlayer.BLUETOOTH_PHONE.packageName)
        assertEquals("com.wt.multimedia.local", canonicalResult)
    }

    @Test
    fun resolvePlayerLaunchPackage_doesNotRedirectUnknownOemBluetoothPackages() {
        assertEquals("com.wt.wtbtservice", resolvePlayerLaunchPackage("com.wt.wtbtservice"))
    }

    @Test
    fun resolvePlayerLaunchPackage_keepsOtherPlayerPackagesUnchanged() {
        val result = resolvePlayerLaunchPackage("com.maxmpz.audioplayer")

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

    @Test
    fun calculatePlaybackProgress_returnsZeroWhenNotPlaying() {
        val result = calculatePlaybackProgress(
            isPlaying = false,
            durationMs = 120000L,
            positionMs = 45000L
        )

        assertEquals(0f, result)
    }

    @Test
    fun calculatePlaybackProgress_returnsClampedProgress() {
        val half = calculatePlaybackProgress(
            isPlaying = true,
            durationMs = 200000L,
            positionMs = 100000L
        )
        val overflow = calculatePlaybackProgress(
            isPlaying = true,
            durationMs = 100000L,
            positionMs = 200000L
        )

        assertEquals(0.5f, half)
        assertEquals(1f, overflow)
    }

    @Test
    fun estimatePlaybackPositionMs_keepsStaticPositionWhenNotPlaying() {
        val result = estimatePlaybackPositionMs(
            isPlaying = false,
            durationMs = 180000L,
            positionMs = 40000L,
            playbackSpeed = 1f,
            positionUpdateTimeMs = 1000L,
            nowElapsedRealtimeMs = 16000L
        )

        assertEquals(40000L, result)
    }

    @Test
    fun estimatePlaybackPositionMs_advancesByElapsedTimeWhenPlaying() {
        val result = estimatePlaybackPositionMs(
            isPlaying = true,
            durationMs = 180000L,
            positionMs = 40000L,
            playbackSpeed = 1f,
            positionUpdateTimeMs = 1000L,
            nowElapsedRealtimeMs = 6000L
        )

        assertEquals(45000L, result)
    }

    @Test
    fun estimatePlaybackPositionMs_clampsToDuration() {
        val result = estimatePlaybackPositionMs(
            isPlaying = true,
            durationMs = 50000L,
            positionMs = 49000L,
            playbackSpeed = 1f,
            positionUpdateTimeMs = 1000L,
            nowElapsedRealtimeMs = 6000L
        )

        assertEquals(50000L, result)
    }
}
