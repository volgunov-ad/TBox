package vad.dashing.tbox

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaLastPlayedPlayerTest {

    private fun state(playing: Boolean, startedAt: Long = 0L) = MediaPlayerState(
        player = null,
        isPlaying = playing,
        hasSession = true,
        lastBecamePlayingElapsedRealtimeMs = startedAt,
    )

    @Test
    fun currentlyPlaying_picksTheLastStartedPlayer() {
        val states = mapOf(
            "ru.yandex.music" to state(playing = true, startedAt = 100L),
            "com.spotify.music" to state(playing = true, startedAt = 200L),
            "com.vk.music" to state(playing = false, startedAt = 300L),
        )
        assertEquals("com.spotify.music", currentlyPlayingPackage(states))
    }

    @Test
    fun currentlyPlaying_isNullWhenNothingPlays() {
        assertNull(currentlyPlayingPackage(mapOf("ru.yandex.music" to state(playing = false))))
        assertNull(currentlyPlayingPackage(emptyMap()))
    }

    @Test
    fun launchTarget_prefersRequestedThenWidgetListThenLastPlayed() {
        assertEquals(
            "com.vk.music",
            mediaLaunchTarget(setOf("ru.yandex.music"), "com.vk.music", "com.spotify.music"),
        )
        assertEquals(
            "ru.yandex.music",
            mediaLaunchTarget(setOf("ru.yandex.music"), "", "com.spotify.music"),
        )
        assertEquals("com.spotify.music", mediaLaunchTarget(emptySet(), "", "com.spotify.music"))
        assertEquals("ru.yandex.mobile.fmradio", mediaLaunchTarget(emptySet(), "", "ru.yandex.radio"))
    }

    @Test
    fun launchTarget_isNullWithoutAnyPlayer() {
        assertNull(mediaLaunchTarget(emptySet(), "", null))
        assertNull(mediaLaunchTarget(emptySet(), " ", "not a package"))
    }
}
