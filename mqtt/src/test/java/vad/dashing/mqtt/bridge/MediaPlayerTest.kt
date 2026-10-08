package vad.dashing.mqtt.bridge

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import vad.dashing.mqtt.ha.MediaBundle

class MediaPlayerTest {
    @Test
    fun mergeKeepsMissingFieldsAndTurnsMillisecondsIntoSeconds() {
        val first = mergeMedia(
            previous = null,
            samples = mapOf(
                "media_title" to MediaSample(true, "Song", null),
                "media_artist" to MediaSample(true, "Artist", null),
                "media_playing" to MediaSample(true, "on", null),
                "media_position_ms" to MediaSample(true, null, 12_500.0),
                "media_duration_ms" to MediaSample(true, null, 180_000.0),
                "hu_media_volume" to MediaSample(true, null, 15.0),
            ),
        )
        assertEquals("playing", first.state)
        assertEquals(12, first.positionSec)
        assertEquals(180, first.durationSec)
        assertEquals(15, first.volume)
        val moved = mergeMedia(
            previous = first,
            samples = mapOf("media_position_ms" to MediaSample(true, null, 20_000.0)),
        )
        assertTrue(moved.sameExceptPosition(first))
        assertEquals(20, moved.positionSec)
        assertEquals("Artist", moved.artist)
    }

    @Test
    fun pauseAndStoppedTrackDoNotStayPlaying() {
        val paused = mergeMedia(
            previous = null,
            samples = mapOf(
                "media_title" to MediaSample(true, "Song", null),
                "media_playing" to MediaSample(true, "off", null),
            ),
        )
        assertEquals("paused", paused.state)
        val idle = mergeMedia(
            previous = paused,
            samples = mapOf(
                "media_title" to MediaSample(false, null, null),
                "media_playing" to MediaSample(false, null, null),
            ),
        )
        assertEquals("idle", idle.state)
        assertEquals("", idle.title)
    }

    @Test
    fun jsonRoundTripAndCommands() {
        val view = MediaView("playing", "Song", "Artist", 3, null, 10)
        val parsed = mediaViewOf(view.json())
        assertEquals(view, parsed)
        assertNull(mediaViewOf("not-json"))
        assertEquals(MediaCommand.Play, parseMediaCommand(" play "))
        assertEquals(MediaCommand.Volume(15), parseMediaCommand("VOLUME 15"))
        assertNull(parseMediaCommand("STOP"))
        val pause = mediaInvoke(bundle(), MediaCommand.Pause, playing = false)
        assertNull(pause)
        val volume = mediaInvoke(bundle(), MediaCommand.Volume(15), playing = true)
        val action = JSONObject(volume!!.request.let { (it as InvokeRequest.Actions).json })
            .getJSONArray("actions")
            .getJSONObject(0)
        assertEquals("set_media_volume", action.getString("actionType"))
        assertEquals(15, action.getInt("intValue"))
        assertNull(volume.publishedState)
        assertNull(mediaInvoke(bundle(), MediaCommand.Volume(40), playing = true))
    }

    private fun bundle() = MediaBundle(
        signalIds = setOf("media_title"),
        source = "app",
        playAction = "media_play",
        pauseToggleAction = "media_play_pause",
        nextAction = "media_next",
        previousAction = "media_previous",
        volumeAction = "set_media_volume",
        volumeMin = 0,
        volumeMax = 31,
    )
}
