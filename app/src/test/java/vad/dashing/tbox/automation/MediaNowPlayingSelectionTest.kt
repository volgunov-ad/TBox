package vad.dashing.tbox.automation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import vad.dashing.tbox.MediaPlayerState

class MediaNowPlayingSelectionTest {
    @Test
    fun prefersThePlayingSessionThatHasMetadata() {
        val paused = MediaPlayerState(player = null, artist = "Old", track = "Paused", isPlaying = false)
        val playing = MediaPlayerState(player = null, artist = "Now", track = "Live", isPlaying = true)
        val selected = selectMediaPlayerState(mapOf("paused" to paused, "playing" to playing))
        assertEquals("Live", selected?.track)
        assertEquals("Now", selected?.artist)
    }

    @Test
    fun usesAPausedSessionWhenNothingIsPlaying() {
        val silent = MediaPlayerState(player = null, isPlaying = true)
        val paused = MediaPlayerState(player = null, artist = "Band", track = "Song", isPlaying = false)
        val selected = selectMediaPlayerState(mapOf("silent" to silent, "paused" to paused))
        assertEquals("Song", selected?.track)
    }

    @Test
    fun returnsNullWhenThereIsNoSession() {
        assertNull(selectMediaPlayerState(emptyMap()))
    }
}
